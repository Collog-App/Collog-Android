package live.collog.app.features.call.services

import android.app.ActivityManager
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
 *
 * 블루투스 이어폰·워치로 받으면 앱이 뒤에 있는 채로 이 서비스가 시작된다. 그때는 마이크 종류를 붙일 수
 * 없어(Android 14+ `SecurityException`, 그 아래는 조용히 무음) 상대가 내 목소리를 못 듣는다. 그래서 통화
 * 종류만으로 붙여 통화를 지키고, 뒤에서는 화면을 띄울 수 없어 「마이크가 꺼져 있어요」 알림을 띄운다.
 * 화면이 보이면 [restoreMicrophone]이 마이크 종류를 다시 붙인다(앱이 앞에 있을 때 붙인 마이크는 다시 뒤로
 * 가도 유지된다).
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name = intent?.getStringExtra(EXTRA_NAME) ?: lastName
        val stale = intent?.getIntExtra(EXTRA_GENERATION, generation) != generation
        val stopping = (intent?.action == ACTION_STOP && !stale) || !requested
        val microphone = startCallForeground(name)
        if (stopping) {
            microphoneMissing = false
            CallNotifications.cancelMicrophoneNeeded(this)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else if (!microphone || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !appVisible())) {
            microphoneMissing = true
            Log.i("Collog", "마이크가 꺼진 통화예요. 통화 화면을 열도록 알려요")
            CallNotifications.showMicrophoneNeeded(this)
        } else {
            microphoneMissing = false
            CallNotifications.cancelMicrophoneNeeded(this)
        }
        return START_NOT_STICKY
    }

    /** 마이크 종류까지 붙었으면 true. 못 붙이면 통화 종류만으로 붙인다. */
    private fun startCallForeground(name: String): Boolean {
        val notification = CallNotifications.ongoing(this, name)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            runCatching { ServiceCompat.startForeground(this, CallNotifications.ID_ONGOING, notification, 0) }
                .onFailure { Log.w("Collog", "통화 포그라운드 서비스를 시작하지 못했어요", it) }
            return true
        }
        val phoneCall = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        return try {
            ServiceCompat.startForeground(
                this, CallNotifications.ID_ONGOING, notification, phoneCall or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            true
        } catch (error: SecurityException) {
            Log.w("Collog", "앱이 뒤에 있어 마이크 없이 통화 서비스를 시작해요", error)
            runCatching { ServiceCompat.startForeground(this, CallNotifications.ID_ONGOING, notification, phoneCall) }
                .onFailure { Log.w("Collog", "통화 포그라운드 서비스를 시작하지 못했어요", it) }
            false
        } catch (error: Exception) {
            Log.w("Collog", "통화 포그라운드 서비스를 시작하지 못했어요", error)
            false
        }
    }

    /** Android 11+ 에서는 화면이 보이는 앱이어야 마이크를 쓸 수 있다(포그라운드 서비스만으로는 안 됨). */
    private fun appVisible(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    companion object {
        private const val EXTRA_NAME = "name"
        private const val ACTION_STOP = "live.collog.app.action.STOP_CALL_SERVICE"
        private const val ACTION_RESTORE_MICROPHONE = "live.collog.app.action.RESTORE_CALL_MICROPHONE"
        private const val EXTRA_GENERATION = "generation"

        @Volatile private var requested = false

        // 마이크 없이 붙은 통화 서비스. 화면이 보이면 다시 붙인다.
        @Volatile private var microphoneMissing = false

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

        /** 화면이 보일 때 부른다. 마이크 없이 붙은 통화 서비스가 있으면 마이크 종류를 다시 붙인다. */
        fun restoreMicrophone(context: Context) {
            if (!requested || !microphoneMissing) return
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, CallService::class.java)
                        .setAction(ACTION_RESTORE_MICROPHONE)
                        .putExtra(EXTRA_GENERATION, generation),
                )
            } catch (error: Exception) {
                Log.w("Collog", "통화 마이크를 다시 켜지 못했어요", error)
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
