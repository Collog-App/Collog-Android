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

    fun apply(response: TokenResponse) {
        credentialStore.write(response)
        storageError = null
        if (user?.id != response.user.id || user?.role != response.user.role) {
            generation = UUID.randomUUID()
            refreshTask?.cancel()
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
                if (user?.id == refreshedUser.id && user?.role == refreshedUser.role) {
                    refreshedUser = refreshedUser.copy(familyId = user?.familyId ?: refreshedUser.familyId)
                }
                apply(TokenResponse(response.accessToken, response.refreshToken, refreshedUser))
                response.accessToken
            } catch (error: ApiError.Unauthenticated) {
                if (currentGeneration == generation) signOut()
                throw error
            }
        }
        refreshTask = task
        try {
            return task.await()
        } finally {
            if (currentGeneration == generation) refreshTask = null
        }
    }
}
