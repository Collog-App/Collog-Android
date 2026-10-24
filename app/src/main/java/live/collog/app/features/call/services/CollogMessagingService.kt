package live.collog.app.features.call.services

import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import live.collog.app.CollogApplication

/**
 * FCM 수신(iOS PushKit·APNs 대응). 서버는 데이터 메시지만 보낸다.
 * - `type=incoming_call`: callId, callUUID, callerId, calleeId, callerName, expiresAt
 * - `type=report_ready`: callId, title, body
 */
class CollogMessagingService : FirebaseMessagingService() {
    private val main = Handler(Looper.getMainLooper())

    private val app: CollogApplication get() = application as CollogApplication

    override fun onNewToken(token: String) {
        main.post { app.callCenter.setPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "incoming_call" -> main.post { app.callCenter.handleIncomingPush(data) }
            "report_ready" -> {
                val callId = data["callId"] ?: return
                val title = data["title"] ?: "Collog"
                val body = data["body"] ?: "새 통화 기록을 확인해 주세요."
                CallNotifications.showReport(this, callId, title, body)
                main.post { app.environment.receiveReportNotification(callId, title, body, openReport = false) }
            }
        }
    }
}
