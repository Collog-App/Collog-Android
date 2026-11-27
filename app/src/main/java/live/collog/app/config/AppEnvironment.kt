package live.collog.app.config

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import live.collog.app.shared.services.AppEventCode
import live.collog.app.shared.services.AppEventLog
import live.collog.app.shared.services.AuthSession
import live.collog.app.shared.services.Billing
import live.collog.app.shared.services.FamilyStore
import live.collog.app.shared.services.api.CollogApi
import live.collog.app.shared.services.api.CollogApiClient
import okhttp3.OkHttpClient

/** iOS `AppEnvironment`. 화면은 [LocalAppEnvironment]로 받는다. */
class AppEnvironment(
    val settings: AppSettings,
    val session: AuthSession,
    val family: FamilyStore = FamilyStore(),
    /** 가족 요금제와 결제 화면(결제 1차). 테스트 기본값은 SDK 없는 빌드와 같다. */
    val billing: Billing = Billing(),
    private val http: OkHttpClient = CollogApiClient.DefaultHttp,
    eventPreferences: SharedPreferences? = null,
    eventScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    var beforeSignOut: (suspend () -> Unit)? = null
    /** 로그아웃·탈퇴 뒤 Google 로그인 상태를 지우고 건강 앱 권한을 거둔다(CollogApplication이 넣는다). */
    var afterSignOut: (suspend () -> Unit)? = null
    /** 이용 기록. [eventPreferences]가 없으면(단위 테스트 기본값) 남기지 않는다. */
    val events = AppEventLog(this, eventPreferences, eventScope)
    /** 리포트 푸시 알림을 누른 시각. 리포트 화면이 [takeReportViewSource]로 한 번 쓰고 지운다. */
    private var reportNotificationTappedAtMillis: Long? = null
    var reportNotificationRevision by mutableIntStateOf(0)
    /** 분석 완료 알림을 받을 때마다(누르지 않아도) 오른다. 리포트 화면이 이미 불러 둔 주를 다시 읽는다. */
    var reportDataRevision by mutableIntStateOf(0)
        private set
    /** RootScreen이 마지막으로 연 리포트 알림 번호. 첫 화면을 그리기 전에 온 알림(꺼진 앱에서 누름)도 열려고 둔다. */
    var openedReportNotificationRevision = 0
    /** 마지막으로 누른 리포트 알림의 통화. RootScreen이 리포트 탭 위에 그 통화의 분석 화면을 연다. */
    var openedReportCallId: String? = null
        private set
    var reportNotifications by mutableStateOf<List<ReceivedReportNotification>>(emptyList())
        private set

    init {
        session.onAccountChanged = {
            family.reset()
            billing.reset()
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
        events.clear()
        // 로그아웃하면 부른 화면이 사라져 그 범위가 취소된다. 서버 로그아웃과 Google 정리는 끝까지 한다
        // (iOS는 화면과 상관없는 Task에서 돈다).
        withContext(NonCancellable) {
            if (refreshToken != null) runCatching { api.logout(refreshToken) }
            afterSignOut?.invoke()
        }
    }

    /**
     * 리포트 화면에 들어올 때 부른다: 방금(10초 안) 리포트 푸시 알림을 눌러 열었으면 NOTIFICATION, 아니면 DIRECT.
     * 한 번 쓰면 지워, 남아 있던 표시가 나중의 일반 열람에 붙지 않는다.
     */
    fun takeReportViewSource(nowMillis: Long = System.currentTimeMillis()): String {
        val tappedAt = reportNotificationTappedAtMillis
        reportNotificationTappedAtMillis = null
        return if (tappedAt != null && nowMillis - tappedAt <= REPORT_NOTIFICATION_WINDOW_MILLIS) {
            AppEventCode.NOTIFICATION
        } else {
            AppEventCode.DIRECT
        }
    }

    fun receiveReportNotification(callId: String, title: String, message: String, openReport: Boolean) {
        if (!session.isAuthenticated) return
        if (reportNotifications.none { it.id == callId }) {
            reportNotifications = listOf(ReceivedReportNotification(callId, title, message, Instant.now())) +
                reportNotifications
        }
        reportDataRevision += 1
        if (openReport) {
            openedReportCallId = callId
            reportNotificationTappedAtMillis = System.currentTimeMillis()
            reportNotificationRevision += 1
        }
    }
}

data class ReceivedReportNotification(
    val id: String,
    val title: String,
    val message: String,
    val date: Instant,
)

/** 리포트 푸시 알림을 누른 뒤 리포트 화면이 들어오기까지 기다리는 시간. 넘으면 알림으로 들어온 것으로 보지 않는다. */
private const val REPORT_NOTIFICATION_WINDOW_MILLIS = 10_000L

val LocalAppEnvironment = staticCompositionLocalOf<AppEnvironment> {
    error("AppEnvironment가 제공되지 않았어요")
}
