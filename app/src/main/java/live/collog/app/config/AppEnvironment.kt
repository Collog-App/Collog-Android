package live.collog.app.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Instant
import live.collog.app.shared.models.UserRoleOption
import live.collog.app.shared.services.AuthSession
import live.collog.app.shared.services.FamilyStore
import live.collog.app.shared.services.api.CollogApi
import live.collog.app.shared.services.api.CollogApiClient
import okhttp3.OkHttpClient

/** iOS `AppEnvironment`. 화면은 [LocalAppEnvironment]로 받는다. */
class AppEnvironment(
    val settings: AppSettings,
    val session: AuthSession,
    val family: FamilyStore = FamilyStore(),
    private val http: OkHttpClient = CollogApiClient.DefaultHttp,
) {
    var beforeSignOut: (suspend () -> Unit)? = null
    /** 로그아웃 뒤 Google 로그인 상태를 지운다(CollogApplication이 넣는다). */
    var afterSignOut: (suspend () -> Unit)? = null
    var reportNotificationRevision by mutableIntStateOf(0)
    var reportNotifications by mutableStateOf<List<ReceivedReportNotification>>(emptyList())
        private set

    init {
        session.onAccountChanged = {
            family.reset()
            reportNotifications = emptyList()
        }
        if (!session.isAuthenticated) family.reset()
    }

    suspend fun subjectParentId(): String? {
        val user = session.user ?: return null
        if (user.role == UserRoleOption.Parent.rawValue) return user.id
        val familyId = user.familyId ?: return null
        val members = runCatching { api.members(familyId) }.getOrNull()
        return members?.firstOrNull { it.userId != null && it.role != "CHILD" && it.userId != user.id }?.userId
    }

    val api: CollogApi
        get() = CollogApi(
            CollogApiClient(
                baseUrl = settings.resolvedBaseUrl,
                accessToken = session.accessToken,
                http = http,
                authentication = session,
            ),
        )

    suspend fun signOut() {
        beforeSignOut?.invoke()
        val refreshToken = session.refreshToken
        val api = api
        session.signOut()
        if (refreshToken != null) runCatching { api.logout(refreshToken) }
        afterSignOut?.invoke()
    }

    fun receiveReportNotification(callId: String, title: String, message: String, openReport: Boolean) {
        if (!session.isAuthenticated) return
        if (reportNotifications.none { it.id == callId }) {
            reportNotifications = listOf(ReceivedReportNotification(callId, title, message, Instant.now())) +
                reportNotifications
        }
        if (openReport) reportNotificationRevision += 1
    }
}

data class ReceivedReportNotification(
    val id: String,
    val title: String,
    val message: String,
    val date: Instant,
)

val LocalAppEnvironment = staticCompositionLocalOf<AppEnvironment> {
    error("AppEnvironment가 제공되지 않았어요")
}
