package live.collog.app.features.call.services

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalAudioTrackOptions
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.config.AppEnvironment
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.models.CallSafety
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.ApiQuestion
import live.collog.app.shared.services.api.CallAccepted
import live.collog.app.shared.services.api.CollogApi

/**
 * iOS `CallCenter`(+Lifecycle, +CallKit). 발신·수신·끊기와 통화 상태 확인을 맡는다.
 * 미디어는 `CallCenterMedia.kt`, 녹음은 `CallCenterRecording.kt`, 기기 등록·수신 푸시는
 * `CallCenterNotifications.kt`에 있다. 모든 함수는 메인 스레드에서 부른다.
 */
class CallCenter(
    val environment: AppEnvironment,
    internal val platform: CallPlatform,
    internal val scope: CoroutineScope = MainScope(),
) {
    data class ActiveCall(
        val id: String,
        val uuid: UUID,
        val direction: Direction,
        val peerId: String?,
        val peerName: String,
        val phase: CallPhase,
        val questions: List<String>,
        val notice: String? = null,
        val recordingEnabled: Boolean = false,
        val isMuted: Boolean = false,
        val isSpeakerEnabled: Boolean = false,
    )

    enum class Direction { Incoming, Outgoing }

    data class PendingOutgoing(val uuid: UUID, val calleeId: String, val name: String)

    var activeCall by mutableStateOf<ActiveCall?>(null)
        internal set
    var pendingOutgoing by mutableStateOf<PendingOutgoing?>(null)
        internal set
    var incomingCallReported by mutableStateOf(false)
        internal set
    var incomingAnswerRequested by mutableStateOf(false)
        internal set
    var incomingAnswerError by mutableStateOf<String?>(null)
        internal set
    var events by mutableStateOf<List<String>>(emptyList())
        private set
    var deviceRegistrationError by mutableStateOf<String?>(null)
        internal set
    var notificationPermissionError by mutableStateOf<String?>(null)
        internal set
    var callError by mutableStateOf<String?>(null)
    var needsMicrophoneSettings by mutableStateOf(false)
        internal set

    val canAnswerIncomingCall: Boolean
        get() = activeCall?.direction == Direction.Incoming && activeCall?.phase == CallPhase.Ringing &&
            incomingCallReported && !incomingAnswerRequested

    val hasCallInProgress: Boolean get() = activeCall != null || pendingOutgoing != null

    internal var pushToken: String? = null
    internal var room: Room? = null
    internal val systemCalls = mutableSetOf<UUID>()
    internal val systemAnswered = mutableSetOf<UUID>()
    internal val answeredCallIds = mutableSetOf<String>()
    internal val answeringCallIds = mutableSetOf<String>()
    /** 받기를 서버에 보낸 통화와 그 요청 ID. 응답을 잃었을 때 이 기기가 받은 것인지 가리는 데 쓴다. */
    internal val acceptRequestIds = mutableMapOf<String, String>()
    /** 알림의 받기를 시스템 통화 등록이 끝나기 전에 눌렀다. 등록이 끝나면 받는다. */
    internal var answerWhenReported = false
    internal var serverAccepted = false
    internal var callOwnerId: String? = null
    internal var microphoneJob: Job? = null
    internal var muteJob: Job? = null
    internal var roomEventsJob: Job? = null
    internal var statusUnavailable = false
    internal var recordingStopped = false
    internal var pendingCapture: LocalAudioTrackOptions? = null
    internal var analysisWriter: AnalysisPcmWriter? = null
    internal var analysisTrack: LocalAudioTrack? = null
    internal var rawCaptureRequired = false
    internal var statusJob: Job? = null
    internal var deviceRegistrationJob: Job? = null
    internal var needsDeviceRegistration = false
    internal var serverQuestions: List<ApiQuestion> = emptyList()
    internal var spokenCallId: String? = null
    internal var notificationAuthorizationJob: Job? = null
    internal var reportNotificationsAuthorized = false
    internal var uploadJob: Job? = null
    internal val uploads: RawAudioUploadQueue? by lazy { makeUploadQueue() }

    internal var isAudioSessionActive = false
    internal var isMediaConnected = false
    internal var didPublishMicrophone = false
    internal var microphoneReady = false

    fun start() {
        // 울리던 중 프로세스가 죽었으면 남은 수신 알림을 지운다(새 수신 푸시는 이 뒤에 처리된다).
        platform.cancelIncoming()
        AnalysisPcmWriter.purgeAbandonedRecordings(platform.analysisDirectory)
        environment.beforeSignOut = { prepareSignOut() }
        if (environment.session.isAuthenticated) updateNotificationAuthorization()
        scope.launch {
            pushToken = platform.fetchPushToken()
            log(if (pushToken == null) "푸시 토큰 없음" else "푸시 토큰 수신")
            registerDeviceIfPossible()
        }
        resumePendingUploads()
    }

    fun log(message: String) {
        events = (listOf(message) + events).take(30)
        Log.i("Collog", message)
    }

    internal fun updateCall(transform: (ActiveCall) -> ActiveCall) {
        activeCall = activeCall?.let(transform)
    }

    // region Lifecycle (iOS CallCenter+Lifecycle)

    fun sessionDidChange() {
        val owner = callOwnerId
        if (owner != null && owner != environment.session.user?.id) {
            activeCall?.let { reportEnded(it.uuid, EndCause.Local) }
            discardAnalysisRecording()
            teardown()
        }
        uploads?.discardInvalidOwners()
        resumePendingUploads()
    }

    suspend fun prepareSignOut() {
        val call = activeCall
        val acceptedHere = call?.let { it.id in answeredCallIds } ?: false
        val acceptRequestId = call?.let { acceptRequestIds[it.id] }
        val api = environment.api
        if (call != null) reportEnded(call.uuid, EndCause.Remote)
        discardAnalysisRecording()
        uploads?.discardAll()
        teardown()
        call ?: return
        try {
            if (call.direction == Direction.Incoming && !acceptedHere) {
                declineUnanswered(api, call.id, acceptRequestId)
            } else {
                api.endCall(call.id)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("로그아웃 전 통화 종료 요청을 보내지 못했어요")
        }
    }

    /** iOS `startOutgoingCall` + `CXStartCallAction` 처리. */
    fun startOutgoingCall(calleeId: String, name: String, @Suppress("UNUSED_PARAMETER") questions: List<String>) {
        if (activeCall != null || pendingOutgoing != null) return
        val userId = environment.session.user?.id ?: return
        if (!environment.session.isAuthenticated) return
        callOwnerId = userId
        val pending = PendingOutgoing(UUID.randomUUID(), calleeId, name)
        pendingOutgoing = pending
        scope.launch {
            val permitted = platform.requestMicrophone()
            if (pendingOutgoing?.uuid != pending.uuid) return@launch
            if (!permitted) {
                failCall(pending.uuid, "통화하려면 휴대폰 설정에서 마이크를 허용해주세요", microphone = true)
                return@launch
            }
            // 화면이 앞에 있을 때 시작해야 마이크 포그라운드 서비스가 허용된다.
            platform.startOngoing(name)
            val added = platform.callSystem.add(
                SystemCall(pending.uuid, name, calleeId, incoming = false),
                systemEvents(pending.uuid),
            )
            if (pendingOutgoing?.uuid != pending.uuid) {
                if (added) platform.callSystem.disconnect(pending.uuid, EndCause.Local)
                return@launch
            }
            if (added) systemCalls += pending.uuid else log("시스템 통화 등록 실패, 앱 안에서만 통화해요")
            activateAudio()
            performStartCall(pending)
        }
    }

    private suspend fun performStartCall(pending: PendingOutgoing) {
        try {
            val created = environment.api.createCall(pending.calleeId)
            if (pendingOutgoing?.uuid != pending.uuid) {
                endQuietly(created.callId)
                return
            }
            val call = ActiveCall(
                id = created.callId,
                uuid = pending.uuid,
                direction = Direction.Outgoing,
                peerId = pending.calleeId,
                peerName = pending.name,
                phase = CallPhase.Connecting,
                questions = created.questions.map { it.text },
                notice = if (created.recordingEnabled) null else created.recordingDisabledMessage,
                recordingEnabled = created.recordingEnabled,
            )
            activeCall = call
            serverQuestions = created.questions
            rawCaptureRequired = created.rawCaptureRequired ?: false
            monitorCall(created.callId)

            connectMedia(
                callId = created.callId,
                url = created.livekitUrl,
                token = created.accessToken,
                roomName = created.roomName,
                constraints = created.audioConstraints,
            )
            if (activeCall?.id == created.callId && activeCall?.phase != CallPhase.Active) {
                updateCall { it.copy(phase = CallPhase.Ringing) }
                speakQuestionsIfReady()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (activeCall?.uuid == pending.uuid) {
                handleMediaError(error, pending.uuid)
            } else {
                failCall(pending.uuid, error.message ?: "통화를 시작하지 못했어요")
            }
        }
    }

    fun answerIncomingCall() {
        val ringing = activeCall?.takeIf { it.direction == Direction.Incoming && it.phase == CallPhase.Ringing }
        if (ringing != null && !incomingCallReported) {
            answerWhenReported = true
            return
        }
        if (!canAnswerIncomingCall) return
        val call = activeCall ?: return
        incomingAnswerRequested = true
        incomingAnswerError = null
        scope.launch { performAnswer(call.uuid, fromSystem = false) }
    }

    /** iOS `CXAnswerCallAction` 처리. 시스템(블루투스·워치)이 받은 경우 [fromSystem]이 true다. */
    internal suspend fun performAnswer(uuid: UUID, fromSystem: Boolean) {
        val call = activeCall?.takeIf { it.uuid == uuid && it.direction == Direction.Incoming } ?: return
        if (call.id in answeredCallIds || call.id in answeringCallIds) return
        answeringCallIds += call.id
        incomingAnswerRequested = true
        incomingAnswerError = null
        val requestId = UUID.randomUUID().toString()
        try {
            val permitted = platform.requestMicrophone()
            if (activeCall?.uuid != uuid) return
            if (!permitted) {
                failCall(uuid, "통화하려면 휴대폰 설정에서 마이크를 허용해주세요", microphone = true)
                return
            }
            if (!fromSystem && uuid in systemCalls && uuid !in systemAnswered &&
                !platform.callSystem.answer(uuid)
            ) {
                if (activeCall?.uuid == uuid) {
                    incomingAnswerRequested = false
                    incomingAnswerError = "전화를 받지 못했어요. 다시 시도해주세요."
                    log("수신 요청 실패: 시스템이 받기를 거절했어요")
                }
                return
            }
            platform.cancelIncoming()
            updateCall { it.copy(phase = CallPhase.Connecting) }
            platform.startOngoing(call.peerName)
            activateAudio()
            acceptRequestIds[call.id] = requestId
            val accepted = acceptCallWithRetry(call.id, requestId)
            if (activeCall?.id != call.id) {
                endQuietly(call.id)
                return
            }
            answeredCallIds += call.id
            serverAccepted = true
            rawCaptureRequired = accepted.rawCaptureRequired
            applyRecordingStatus(accepted.recordingEnabled ?: accepted.rawCaptureRequired, message = null)
            connectMedia(
                callId = call.id,
                url = accepted.livekitUrl,
                token = accepted.accessToken,
                roomName = accepted.roomName,
                constraints = accepted.audioConstraints,
            )
            if (activeCall?.id != call.id) return
            markCallActive()
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiError.Server) {
            if (error.status in listOf(409, 410)) {
                if (activeCall?.id == call.id) {
                    finishRemoteCall(if (error.status == 409) EndCause.Remote else EndCause.Missed)
                }
            } else {
                handleMediaError(error, uuid)
            }
        } catch (error: Exception) {
            handleMediaError(error, uuid)
        } finally {
            answeringCallIds -= call.id
        }
    }

    private suspend fun acceptCallWithRetry(callId: String, requestId: String): CallAccepted {
        repeat(2) {
            try {
                return environment.api.acceptCall(callId, requestId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!CallSafety.canRetry(error) || activeCall?.id != callId) throw error
                delay(1_000)
            }
        }
        return environment.api.acceptCall(callId, requestId)
    }

    fun endActiveCall(): Job? {
        val uuid = activeCall?.uuid ?: pendingOutgoing?.uuid ?: return null
        return scope.launch { performEnd(uuid, fromSystem = false) }
    }

    /** 알림이 가리키는 통화가 이미 없을 때(프로세스가 다시 시작됨 등) 서버에 거절만 알린다. */
    fun declineStaleCall(callId: String): Job? {
        platform.cancelIncoming()
        if (activeCall?.id == callId || !environment.session.isAuthenticated) return null
        return scope.launch {
            try {
                environment.api.declineCall(callId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log("수신 거절 실패: ${error.message}")
            }
        }
    }

    /** 가리키는 통화가 없는 알림·서비스를 내린다. */
    fun clearStaleNotifications() {
        if (hasCallInProgress) return
        platform.cancelIncoming()
        platform.stopOngoing()
    }

    private suspend fun endQuietly(callId: String) {
        try {
            environment.api.endCall(callId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("종료 보고 실패: ${error.message}")
        }
    }

    /**
     * 받기 전의 수신을 서버에 거절로 알린다. 이 기기가 받기를 보냈는데 응답만 잃었으면 서버에서는 이미 받아져
     * 거절이 409다. 같은 요청 ID로 받기를 다시 보내 이 기기가 받은 것이 맞을 때만 끝낸다
     * (같은 사람의 다른 기기가 받은 통화는 끊지 않는다. 서버 받기는 같은 요청 ID에만 다시 200을 준다).
     */
    private suspend fun declineUnanswered(api: CollogApi, callId: String, acceptRequestId: String?) {
        try {
            api.declineCall(callId)
        } catch (error: ApiError.Server) {
            if (error.status != 409 || acceptRequestId == null) throw error
            api.acceptCall(callId, acceptRequestId)
            api.endCall(callId)
        }
    }

    /** iOS `CXEndCallAction` 처리. 받기 전의 수신은 거절로, 그 밖은 종료로 서버에 알린다. */
    internal suspend fun performEnd(uuid: UUID, fromSystem: Boolean) {
        if (activeCall?.uuid != uuid && pendingOutgoing?.uuid != uuid) return
        val call = activeCall
        val answered = call?.let { it.direction == Direction.Outgoing || it.id in answeredCallIds } ?: false
        val acceptRequestId = call?.let { acceptRequestIds[it.id] }
        if (!fromSystem) {
            reportEnded(uuid, if (call?.direction == Direction.Incoming && !answered) EndCause.Rejected else EndCause.Local)
        } else {
            systemCalls -= uuid
        }
        teardown()
        call ?: return
        answeredCallIds -= call.id
        try {
            if (answered) environment.api.endCall(call.id) else declineUnanswered(environment.api, call.id, acceptRequestId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("종료 보고 실패: ${error.message}")
        }
    }

    /** iOS `provider.reportCall(with:endedAt:reason:)`: 시스템 통화 목록에서 내린다. */
    internal fun reportEnded(uuid: UUID, cause: EndCause) {
        platform.cancelIncoming()
        if (systemCalls.remove(uuid)) scope.launch { platform.callSystem.disconnect(uuid, cause) }
    }

    internal fun systemEvents(uuid: UUID) = object : SystemCallEvents {
        // Telecom 콜백은 5초 안에 끝나야 하므로 실제 처리는 따로 돌린다.
        override suspend fun onAnswer() {
            systemAnswered += uuid
            scope.launch { performAnswer(uuid, fromSystem = true) }
        }

        override suspend fun onDisconnect() {
            scope.launch { performEnd(uuid, fromSystem = true) }
        }

        override fun onMuteChanged(muted: Boolean) {
            scope.launch {
                val call = activeCall ?: return@launch
                if (call.uuid == uuid && call.isMuted != muted) setMuted(muted)
            }
        }
    }

    fun teardown() {
        // 원인을 따로 알리지 않은 채 남은 시스템 통화(발신 준비 중 로그아웃 등)는 여기서 내린다.
        val call = activeCall
        if (call != null && call.isSpeakerEnabled && call.uuid !in systemCalls) platform.setSpeakerFallback(false)
        systemCalls.toList().forEach { reportEnded(it, EndCause.Local) }
        systemAnswered.clear()
        platform.stopQuestions()
        serverQuestions = emptyList()
        spokenCallId = null
        statusJob?.cancel()
        statusJob = null
        val callId = activeCall?.id
        if (callId != null) {
            answeredCallIds -= callId
            answeringCallIds -= callId
            acceptRequestIds -= callId
            finishAnalysisRecording(callId)
        } else {
            analysisTrack = null
            analysisWriter?.discard()
            analysisWriter = null
        }
        rawCaptureRequired = false
        serverAccepted = false
        statusUnavailable = false
        recordingStopped = false
        callOwnerId = null
        microphoneJob?.cancel()
        microphoneJob = null
        muteJob?.cancel()
        muteJob = null
        roomEventsJob?.cancel()
        roomEventsJob = null
        isAudioSessionActive = false
        isMediaConnected = false
        didPublishMicrophone = false
        microphoneReady = false
        pendingCapture = null
        pendingOutgoing = null
        activeCall = null
        incomingCallReported = false
        answerWhenReported = false
        incomingAnswerRequested = false
        incomingAnswerError = null
        val previousRoom = room
        room = null
        previousRoom?.release()
        platform.cancelIncoming()
        platform.stopOngoing()
    }

    fun failCall(uuid: UUID, message: String, microphone: Boolean = false) {
        if (activeCall?.uuid != uuid && pendingOutgoing?.uuid != uuid) return
        val call = activeCall
        val acceptedHere = call?.let { it.id in answeredCallIds } ?: false
        val acceptRequestId = call?.let { acceptRequestIds[it.id] }
        callError = message
        needsMicrophoneSettings = microphone
        reportEnded(uuid, EndCause.Local)
        teardown()
        call ?: return
        scope.launch {
            try {
                if (call.direction == Direction.Incoming && !acceptedHere) {
                    declineUnanswered(environment.api, call.id, acceptRequestId)
                } else {
                    environment.api.endCall(call.id)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log("종료 보고 실패: ${error.message}")
            }
        }
    }

    internal fun monitorCall(callId: String) {
        statusJob?.cancel()
        statusJob = scope.launch {
            var lastSuccess = Instant.now()
            while (true) {
                if (activeCall?.id != callId) return@launch
                try {
                    val status = environment.api.callStatus(callId)
                    if (activeCall?.id != callId) return@launch
                    lastSuccess = Instant.now()
                    if (statusUnavailable && activeCall?.recordingEnabled == true) updateCall { it.copy(notice = null) }
                    statusUnavailable = false
                    if (status.hasEnded) {
                        if (status.recordingEnabled == false) {
                            applyRecordingStatus(false, status.recordingDisabledMessage)
                        }
                        finishRemoteCall()
                        return@launch
                    }
                    status.recordingEnabled?.let { applyRecordingStatus(it, status.recordingDisabledMessage) }
                    if (status.state == "ACTIVE") {
                        if (activeCall?.direction == Direction.Incoming && callId !in answeredCallIds) {
                            if (callId !in answeringCallIds) {
                                finishRemoteCall(EndCause.Remote)
                                return@launch
                            }
                        } else {
                            serverAccepted = true
                            markCallActive()
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (activeCall?.id != callId) return@launch
                    if (CallSafety.isPermanentStatusError(error)) {
                        discardAnalysisRecording()
                        finishRemoteCall(EndCause.Local)
                        return@launch
                    }
                    updateCall { it.copy(notice = "통화 상태를 확인하고 있어요. 인터넷 상태를 확인해 주세요.") }
                    statusUnavailable = true
                    if (Duration.between(lastSuccess, Instant.now()).seconds >= 30) {
                        activeCall?.let { failCall(it.uuid, "통화 상태를 확인할 수 없어 통화를 종료했어요.") }
                        return@launch
                    }
                }
                delay(2_000)
            }
        }
    }

    fun finishRemoteCall(cause: EndCause = EndCause.Remote) {
        val call = activeCall ?: return
        reportEnded(call.uuid, cause)
        teardown()
    }

    internal fun handleMediaError(error: Throwable, uuid: UUID) {
        if (activeCall?.uuid != uuid) return
        if (error is RoomDeletedException) {
            log("서버 통화 종료")
            finishRemoteCall()
        } else {
            failCall(uuid, error.message ?: "통화를 연결하지 못했어요")
        }
    }

    // endregion
}

/** LiveKit 방이 서버에서 지워짐(iOS `LiveKitError.roomDeleted`). */
class RoomDeletedException : Exception("통화가 종료됐어요")

val LocalCallCenter = staticCompositionLocalOf<CallCenter> { error("CallCenter가 제공되지 않았어요") }
