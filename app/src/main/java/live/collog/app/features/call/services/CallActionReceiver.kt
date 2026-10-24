package live.collog.app.features.call.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import live.collog.app.CollogApplication

/** 알림의 거절·끊기 버튼. 받기는 화면을 띄워야 해서 MainActivity가 받는다. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? CollogApplication ?: return
        when (intent.action) {
            CallNotifications.ACTION_DECLINE, CallNotifications.ACTION_HANG_UP -> app.callCenter.endActiveCall()
        }
    }
}
