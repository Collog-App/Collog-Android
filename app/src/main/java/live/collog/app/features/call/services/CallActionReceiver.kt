package live.collog.app.features.call.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import live.collog.app.CollogApplication

/**
 * 알림의 거절·끊기 버튼. 받기는 화면을 띄워야 해서 CallActivity가 받는다.
 * 서버에 거절·종료를 알릴 때까지 `goAsync()`로 기다려, 프로세스가 얼어도 요청이 사라지지 않게 한다.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? CollogApplication ?: return
        val callCenter = app.callCenter
        val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID)
        if (intent.action != CallNotifications.ACTION_DECLINE && intent.action != CallNotifications.ACTION_HANG_UP) return
        val current = callCenter.activeCall
        val job = when {
            callId == null || current?.id == callId -> callCenter.endActiveCall()
            // 알림이 가리키는 통화가 이미 없다(프로세스가 다시 시작됨 등). 서버에 거절만 알린다.
            else -> callCenter.declineStaleCall(callId)
        }
        if (job == null) {
            callCenter.clearStaleNotifications()
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            withTimeoutOrNull(9_000) { job.join() }
            pending.finish()
        }
    }
}
