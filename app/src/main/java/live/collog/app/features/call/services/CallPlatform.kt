package live.collog.app.features.call.services

import io.livekit.android.room.Room
import java.io.File
import live.collog.app.shared.services.api.ApiQuestion
import live.collog.app.shared.services.api.CollogApi

/**
 * [CallCenter]가 쓰는 안드로이드 기능 묶음(권한·알림·Telecom·LiveKit 방·FCM 토큰).
 * 단위 테스트는 가짜 구현을 넣는다. 실제 구현은 [AndroidCallPlatform].
 */
interface CallPlatform {
    val callSystem: CallSystem
    val analysisDirectory: File
    val uploadDirectory: File
    val isPushConfigured: Boolean

    suspend fun requestMicrophone(): Boolean
    fun notificationsEnabled(): Boolean

    /** 아직 묻지 않았을 때만 알림 권한을 묻는다(iOS `notDetermined`일 때만 묻는 것과 같다). */
    suspend fun requestNotificationsIfUndetermined(): Boolean
    suspend fun fetchPushToken(): String?

    /** [managedAudio]면 통화 음성 경로를 Telecom이 맡으므로 LiveKit은 오디오 경로를 건드리지 않는다. */
    fun createRoom(managedAudio: Boolean): Room

    /** Telecom에 올리지 못한 통화에서 스피커를 직접 켜고 끈다. */
    fun setSpeakerFallback(enabled: Boolean): Boolean

    fun speakQuestions(questions: List<ApiQuestion>, callId: String, api: CollogApi, onEvent: (String) -> Unit)
    fun stopQuestions()

    fun showIncoming(call: CallCenter.ActiveCall)
    fun cancelIncoming()
    fun startOngoing(call: CallCenter.ActiveCall)
    fun stopOngoing()
}
