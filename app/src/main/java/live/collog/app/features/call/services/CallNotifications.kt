package live.collog.app.features.call.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import live.collog.app.MainActivity
import live.collog.app.R
import live.collog.app.shared.ui.designsystem.CollogColors
import androidx.compose.ui.graphics.toArgb

/**
 * 통화·리포트 알림. iOS는 CallKit이 수신 화면을 그리지만, 안드로이드는 앱이
 * CallStyle 알림과 전체 화면 인텐트로 직접 띄운다.
 */
object CallNotifications {
    const val CHANNEL_INCOMING = "incoming_calls"
    const val CHANNEL_ONGOING = "ongoing_calls"
    const val CHANNEL_REPORTS = "reports"

    const val ID_INCOMING = 1001
    const val ID_ONGOING = 1002
    private const val ID_REPORT_BASE = 2000

    const val ACTION_ANSWER = "live.collog.app.action.ANSWER"
    const val ACTION_SHOW_CALL = "live.collog.app.action.SHOW_CALL"
    const val ACTION_DECLINE = "live.collog.app.action.DECLINE"
    const val ACTION_HANG_UP = "live.collog.app.action.HANG_UP"
    const val ACTION_OPEN_REPORT = "live.collog.app.action.OPEN_REPORT"
    const val EXTRA_CALL_ID = "callId"
    const val EXTRA_TITLE = "title"
    const val EXTRA_MESSAGE = "message"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringtone = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_INCOMING, "걸려 온 전화", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), ringtone)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_ONGOING, "통화 중", NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                },
                NotificationChannel(CHANNEL_REPORTS, "통화 기록", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    fun incoming(context: Context, call: CallCenter.ActiveCall): Notification {
        val person = Person.Builder().setName(call.peerName).setImportant(true).build()
        val decline = broadcast(context, ACTION_DECLINE, 1)
        val answer = activity(context, ACTION_ANSWER, 2)
        val show = activity(context, ACTION_SHOW_CALL, 3)
        return NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_collog)
            .setColor(CollogColors.greenNormal.toArgb())
            .setContentTitle(call.peerName)
            .setContentText("콜록 전화")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(show)
            .setFullScreenIntent(show, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
    }

    fun ongoing(context: Context, peerName: String): Notification {
        val person = Person.Builder().setName(peerName).setImportant(true).build()
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_collog)
            .setColor(CollogColors.greenNormal.toArgb())
            .setContentTitle(peerName)
            .setContentText("콜록 통화 중")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(activity(context, ACTION_SHOW_CALL, 4))
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, broadcast(context, ACTION_HANG_UP, 5)))
            .build()
    }

    fun showIncoming(context: Context, call: CallCenter.ActiveCall) {
        notify(context, ID_INCOMING, incoming(context, call))
    }

    fun cancelIncoming(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_INCOMING)
    }

    /** iOS 리포트 푸시(`report.callId`)의 대응. 누르면 리포트 탭을 연다. */
    fun showReport(context: Context, callId: String, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_REPORT)
            .putExtra(EXTRA_CALL_ID, callId)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_MESSAGE, message)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(
            context,
            ID_REPORT_BASE + (callId.hashCode() and 0xFFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REPORTS)
            .setSmallIcon(R.drawable.ic_stat_collog)
            .setColor(CollogColors.greenNormal.toArgb())
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notify(context, ID_REPORT_BASE + (callId.hashCode() and 0xFFF), notification)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // 알림 권한이 없다. 화면의 안내 문구(notificationPermissionError)로 알린다.
        }
    }

    private fun activity(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
