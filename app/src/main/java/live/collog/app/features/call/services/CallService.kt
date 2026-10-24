package live.collog.app.features.call.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 통화 중 앱이 뒤로 가도 마이크와 연결을 유지하는 포그라운드 서비스(iOS `UIBackgroundModes` voip·audio 대응).
 * 통화 중 CallStyle 알림을 들고 있다.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "콜록"
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, CallNotifications.ID_ONGOING, CallNotifications.ongoing(this, name), types)
        } catch (error: Exception) {
            Log.w("Collog", "통화 포그라운드 서비스를 시작하지 못했어요", error)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_NAME = "name"

        fun start(context: Context, peerName: String) {
            val intent = Intent(context, CallService::class.java).putExtra(EXTRA_NAME, peerName)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                Log.w("Collog", "통화 서비스를 시작하지 못했어요", error)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}
