package live.collog.app.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    /** RootScreen이 마지막으로 연 리포트 알림 번호. 첫 화면을 그리기 전에 온 알림(꺼진 앱에서 누름)도 열려고 둔다. */
    var openedReportNotificationRevision = 0
    var reportNotifications by mutableStateOf<List<ReceivedReportNotification>>(emptyList())
        private set

    init {
        session.onAccountChanged = {
            family.reset()
            reportNotifications = emptyList()
        }
        if (!session.isAuthenticated) family.reset()
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
        // 로그아웃하면 부른 화면이 사라져 그 범위가 취소된다. 서버 로그아웃과 Google 정리는 끝까지 한다
        // (iOS는 화면과 상관없는 Task에서 돈다).
        withContext(NonCancellable) {
            if (refreshToken != null) runCatching { api.logout(refreshToken) }
            afterSignOut?.invoke()
        }
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
