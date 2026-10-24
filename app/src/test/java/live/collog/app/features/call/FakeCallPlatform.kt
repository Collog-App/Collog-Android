package live.collog.app.features.call

import io.livekit.android.room.Room
import java.io.File
import java.nio.file.Files
import java.util.UUID
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.CallPlatform
import live.collog.app.features.call.services.CallSystem
import live.collog.app.features.call.services.EndCause
import live.collog.app.features.call.services.SystemCall
import live.collog.app.features.call.services.SystemCallEvents
import live.collog.app.shared.services.api.ApiQuestion
import live.collog.app.shared.services.api.CollogApi

/** 통화 센터 단위 테스트용 시스템 통화. 올리고 내린 기록만 남긴다. */
class FakeCallSystem : CallSystem {
    val added = mutableListOf<SystemCall>()
    val disconnected = mutableListOf<Pair<UUID, EndCause>>()
    val events = mutableMapOf<UUID, SystemCallEvents>()
    var accepts = true
    var answers = true

    override suspend fun add(call: SystemCall, events: SystemCallEvents): Boolean {
        if (!accepts) return false
        added += call
        this.events[call.id] = events
        return true
    }

    override suspend fun answer(id: UUID): Boolean = answers
    override suspend fun setActive(id: UUID): Boolean = true
    override suspend fun disconnect(id: UUID, cause: EndCause) {
        disconnected += id to cause
    }
    override suspend fun setSpeaker(id: UUID, enabled: Boolean): Boolean = true
}

class FakeCallPlatform : CallPlatform {
    override val callSystem = FakeCallSystem()
    private val root: File = Files.createTempDirectory("collog-call").toFile()
    override val analysisDirectory = File(root, "analysis")
    override val uploadDirectory = File(root, "uploads")
    override val isPushConfigured = false

    var microphoneGranted = true
    val shownIncoming = mutableListOf<String>()
    var incomingCancelled = 0
    var ongoing = mutableListOf<String>()
    var stoppedOngoing = 0

    override suspend fun requestMicrophone(): Boolean = microphoneGranted
    override suspend fun requestMicrophoneIfUndetermined(): Boolean = microphoneGranted
    override fun notificationsEnabled(): Boolean = true
    override fun fullScreenCallsAllowed(): Boolean = true
    override suspend fun requestNotificationsIfUndetermined(): Boolean = true
    override suspend fun fetchPushToken(): String? = null
    override fun createRoom(managedAudio: Boolean): Room = error("테스트는 LiveKit에 접속하지 않는다")
    override fun setSpeakerFallback(enabled: Boolean): Boolean = true
    override fun speakQuestions(questions: List<ApiQuestion>, callId: String, api: CollogApi, onEvent: (String) -> Unit) {}
    override fun stopQuestions() {}
    override fun showIncoming(call: CallCenter.ActiveCall, expiresAt: java.time.Instant?) {
        shownIncoming += call.id
    }
    override fun cancelIncoming() {
        incomingCancelled += 1
    }
    override fun startOngoing(peerName: String) {
        ongoing += peerName
    }
    override fun stopOngoing() {
        stoppedOngoing += 1
    }

    fun cleanUp() {
        root.deleteRecursively()
    }
}
