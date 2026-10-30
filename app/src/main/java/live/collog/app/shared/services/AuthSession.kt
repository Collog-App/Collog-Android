package live.collog.app.shared.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.CollogApi
import live.collog.app.shared.services.api.CollogApiClient
import live.collog.app.shared.services.api.SessionAuthentication
import live.collog.app.shared.services.api.TokenResponse
import okhttp3.OkHttpClient

/** iOS `AuthSession`. 토큰·사용자를 들고, 401 때 refresh를 한 번만 돌린다. */
class AuthSession(
    private val credentialStore: SessionCredentialStorage,
    private val http: OkHttpClient = CollogApiClient.DefaultHttp,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : SessionAuthentication {
    private var refreshTask: Deferred<String>? = null
    private var generation = UUID.randomUUID()
    var onAccountChanged: (() -> Unit)? = null

    override var accessToken by mutableStateOf<String?>(null)
        private set
    var refreshToken by mutableStateOf<String?>(null)
        private set
    var user by mutableStateOf<ApiUser?>(null)
        private set
    var storageError by mutableStateOf<String?>(null)
        private set

    override val isAuthenticated: Boolean get() = accessToken != null
    override val currentUserId: String? get() = user?.id
    val familyId: String? get() = user?.familyId

    init {
        try {
            credentialStore.read()?.let {
                accessToken = it.accessToken
                refreshToken = it.refreshToken
                user = it.user
            }
        } catch (error: Exception) {
            storageError = error.message
        }
    }

    fun apply(response: TokenResponse) = store(response, cancelRefresh = true)

    /** refresh 안에서 부를 때는 [cancelRefresh]가 false다. 돌고 있는 자기 자신을 취소하면 새 토큰을 받고도 요청이 취소로 끝난다. */
    private fun store(response: TokenResponse, cancelRefresh: Boolean) {
        credentialStore.write(response)
        storageError = null
        if (user?.id != response.user.id) {
            generation = UUID.randomUUID()
            if (cancelRefresh) refreshTask?.cancel()
            refreshTask = null
            onAccountChanged?.invoke()
        }
        accessToken = response.accessToken
        refreshToken = response.refreshToken
        user = response.user
    }

    fun signOut() {
        generation = UUID.randomUUID()
        refreshTask?.cancel()
        refreshTask = null
        try {
            credentialStore.delete()
        } catch (error: Exception) {
            storageError = error.message
        }
        accessToken = null
        refreshToken = null
        user = null
        onAccountChanged?.invoke()
    }

    fun joinFamily(familyId: String) {
        val accessToken = accessToken ?: throw ApiError.Unauthenticated
        val refreshToken = refreshToken ?: throw ApiError.Unauthenticated
        val user = user ?: throw ApiError.Unauthenticated
        apply(TokenResponse(accessToken, refreshToken, user.copy(familyId = familyId)))
    }

    fun updateUser(user: ApiUser) {
        val accessToken = accessToken
        val refreshToken = refreshToken
        if (this.user?.id != user.id || accessToken == null || refreshToken == null) throw ApiError.Unauthenticated
        apply(TokenResponse(accessToken, refreshToken, user))
    }

    override suspend fun refresh(baseUrl: String): String {
        refreshTask?.let { return it.await() }
        val refreshToken = refreshToken ?: run {
            signOut()
            throw ApiError.Unauthenticated
        }
        val currentGeneration = generation
        val task = scope.async {
            val api = CollogApi(CollogApiClient(baseUrl, http = http))
            try {
                val response = api.refreshSession(refreshToken)
                if (currentGeneration != generation) throw ApiError.Unauthenticated
                var refreshedUser = response.user
                if (user?.id == refreshedUser.id) {
                    refreshedUser = refreshedUser.copy(familyId = user?.familyId ?: refreshedUser.familyId)
                }
                store(TokenResponse(response.accessToken, response.refreshToken, refreshedUser), cancelRefresh = false)
                response.accessToken
            } catch (error: ApiError.Unauthenticated) {
                if (currentGeneration == generation) signOut()
                throw error
            }
        }
        refreshTask = task
        // 기다리던 쪽이 취소돼도 갱신은 끝까지 돌고, 끝날 때 비운다(iOS `Task.value`처럼).
        // 기다리던 쪽에서 비우면 다음 401이 옛 refresh 토큰으로 갱신을 또 시작해 로그아웃된다.
        task.invokeOnCompletion { if (refreshTask === task) refreshTask = null }
        return task.await()
    }
}
