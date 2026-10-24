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
 *
 * 시작 요청 직후 멈추면 "startForeground를 부르지 않았다"는 크래시가 나므로, 멈출 때도
 * 서비스에 STOP 명령을 보내 `startForeground` 다음에 스스로 멈추게 한다.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra(EXTRA_NAME) ?: lastName
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, CallNotifications.ID_ONGOING, CallNotifications.ongoing(this, name), types)
        } catch (error: Exception) {
            Log.w("Collog", "통화 포그라운드 서비스를 시작하지 못했어요", error)
        }
        val stale = intent?.getIntExtra(EXTRA_GENERATION, generation) != generation
        if ((intent?.action == ACTION_STOP && !stale) || !requested) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_NAME = "name"
        private const val ACTION_STOP = "live.collog.app.action.STOP_CALL_SERVICE"
        private const val EXTRA_GENERATION = "generation"

        @Volatile private var requested = false

        // 통화를 끝내자마자 새로 걸면 앞의 STOP이 늦게 도착할 수 있어 세대로 구분한다.
        @Volatile private var generation = 0
        @Volatile private var lastName = "콜록"

        fun start(context: Context, peerName: String) {
            requested = true
            lastName = peerName
            generation += 1
            val intent = Intent(context, CallService::class.java)
                .putExtra(EXTRA_NAME, peerName)
                .putExtra(EXTRA_GENERATION, generation)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (error: Exception) {
                Log.w("Collog", "통화 서비스를 시작하지 못했어요", error)
            }
        }

        fun stop(context: Context) {
            if (!requested) return
            requested = false
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, CallService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_GENERATION, generation),
                )
            } catch (error: Exception) {
                context.stopService(Intent(context, CallService::class.java))
            }
        }
    }
}
