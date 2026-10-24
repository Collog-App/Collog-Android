package live.collog.app.features.call.services

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.NoAudioHandler
import io.livekit.android.room.Room
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import live.collog.app.shared.services.PermissionRequester
import live.collog.app.shared.services.api.ApiQuestion
import live.collog.app.shared.services.api.CollogApi
import okhttp3.OkHttpClient

/** [CallPlatform]의 실제 구현. */
class AndroidCallPlatform(
    context: Context,
    scope: CoroutineScope,
    private val permissions: PermissionRequester,
    private val http: OkHttpClient,
) : CallPlatform {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("collog.permissions", Context.MODE_PRIVATE)
    private val speaker by lazy { RingingQuestionSpeaker(this.context, http) }

    override val callSystem: CallSystem by lazy { TelecomCallSystem(this.context, scope) }
    override val analysisDirectory = File(this.context.cacheDir, "collog-analysis")
    override val uploadDirectory = File(this.context.noBackupFilesDir, "PendingCallAudio")
    override val isPushConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    init {
        CallNotifications.ensureChannels(this.context)
    }

    override suspend fun requestMicrophone(): Boolean = permissions.request(listOf(Manifest.permission.RECORD_AUDIO))

    override suspend fun requestMicrophoneIfUndetermined(): Boolean {
        if (permissions.isGranted(Manifest.permission.RECORD_AUDIO)) return true
        if (prefs.getBoolean(KEY_MICROPHONE_ASKED, false) || !permissions.canAsk) return false
        prefs.edit().putBoolean(KEY_MICROPHONE_ASKED, true).apply()
        return requestMicrophone()
    }

    override fun fullScreenCallsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    override fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override suspend fun requestNotificationsIfUndetermined(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return notificationsEnabled()
        if (prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false) || !permissions.canAsk) return notificationsEnabled()
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ASKED, true).apply()
        return permissions.request(listOf(Manifest.permission.POST_NOTIFICATIONS)) && notificationsEnabled()
    }

    override suspend fun fetchPushToken(): String? {
        if (!isPushConfigured) return null
        return suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }

    override fun createRoom(managedAudio: Boolean): Room =
        LiveKit.create(
            context,
            overrides = LiveKitOverrides(
                audioOptions = AudioOptions(audioHandler = if (managedAudio) NoAudioHandler() else null),
            ),
        )

    override fun setSpeakerFallback(enabled: Boolean): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                val speaker = audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } ?: return false
                audio.setCommunicationDevice(speaker)
            } else {
                audio.clearCommunicationDevice()
                true
            }
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = enabled
            true
        }
    }

    override fun speakQuestions(questions: List<ApiQuestion>, callId: String, api: CollogApi, onEvent: (String) -> Unit) {
        speaker.speak(questions, callId, api, onEvent)
    }

    override fun stopQuestions() {
        speaker.stop()
    }

    override fun showIncoming(call: CallCenter.ActiveCall, expiresAt: java.time.Instant?) {
        CallNotifications.showIncoming(context, call, expiresAt)
    }

    override fun cancelIncoming() {
        CallNotifications.cancelIncoming(context)
    }

    override fun startOngoing(peerName: String) {
        CallService.start(context, peerName)
    }

    override fun stopOngoing() {
        CallService.stop(context)
    }

    private companion object {
        const val KEY_NOTIFICATIONS_ASKED = "notificationsAsked"
        const val KEY_MICROPHONE_ASKED = "microphoneAsked"
    }
}
