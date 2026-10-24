package live.collog.app.features.call.services

import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalTrackPublication
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.features.call.models.CallPhase
import live.collog.app.shared.services.api.AudioConstraints

// iOS CallCenter+Media.

/** iOS `provider(_:didActivate:)`: 통화 음성이 준비됐다. Telecom이 음성 경로를 잡은 뒤 부른다. */
internal fun CallCenter.activateAudio() {
    if (!hasCallInProgress) return
    isAudioSessionActive = true
    analysisWriter?.setMuted(activeCall?.isMuted ?: true)
    log("통화 오디오 활성화")
    publishMicrophoneIfReady()
    markCallActive()
    speakQuestionsIfReady()
}

fun CallCenter.updateQuestionVoicePreference() {
    if (environment.settings.questionVoiceEnabled) {
        speakQuestionsIfReady()
    } else {
        platform.stopQuestions()
        spokenCallId = null
    }
}

internal fun CallCenter.speakQuestionsIfReady() {
    val call = activeCall ?: return
    if (call.direction != CallCenter.Direction.Outgoing || call.phase != CallPhase.Ringing) return
    if (!isAudioSessionActive || !environment.settings.questionVoiceEnabled || spokenCallId == call.id) return
    spokenCallId = call.id
    platform.speakQuestions(serverQuestions, call.id, environment.api) { log(it) }
}

fun CallCenter.toggleMute() {
    val call = activeCall ?: return
    setMuted(!call.isMuted)
}

/** iOS `CXSetMutedCallAction` 처리. */
internal fun CallCenter.setMuted(muted: Boolean) {
    val call = activeCall ?: return
    updateCall { it.copy(isMuted = muted) }
    analysisWriter?.setMuted(muted)
    val targetRoom = room
    val previousMute = muteJob
    val microphone = microphoneJob
    muteJob = scope.launch {
        previousMute?.join()
        microphone?.join()
        if (room !== targetRoom || activeCall?.uuid != call.uuid) return@launch
        try {
            if (targetRoom != null && (isMediaConnected || didPublishMicrophone)) {
                targetRoom.localParticipant.setMicrophoneEnabled(!muted)
                if (room !== targetRoom || activeCall?.uuid != call.uuid) return@launch
                attachAnalysisWriter(microphonePublication(targetRoom))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failCall(call.uuid, "마이크 상태를 바꾸지 못해 통화를 종료했어요.")
        }
    }
}

fun CallCenter.toggleSpeaker() {
    val call = activeCall ?: return
    if (!isAudioSessionActive) return
    val enabled = !call.isSpeakerEnabled
    scope.launch {
        val changed = if (call.uuid in systemCalls) {
            platform.callSystem.setSpeaker(call.uuid, enabled)
        } else {
            platform.setSpeakerFallback(enabled)
        }
        if (activeCall?.uuid != call.uuid) return@launch
        if (changed) updateCall { it.copy(isSpeakerEnabled = enabled) } else callError = "통화 음성 출력을 바꾸지 못했어요."
    }
}

internal suspend fun CallCenter.connectMedia(
    callId: String,
    url: String,
    token: String,
    roomName: String,
    constraints: AudioConstraints,
) {
    val call = activeCall ?: throw CancellationException()
    val room = platform.createRoom(managedAudio = call.uuid in systemCalls)
    this.room?.release()
    this.room = room
    if (constraints.autoGainControl || constraints.noiseSuppression) {
        log("경고: 서버가 AGC/NS를 켜서 보냈다. 음향 분석 신뢰도가 떨어진다")
    }
    val capture = LocalAudioTrackOptions(
        noiseSuppression = constraints.noiseSuppression,
        echoCancellation = constraints.echoCancellation,
        autoGainControl = constraints.autoGainControl,
        highPassFilter = false,
        typingNoiseDetection = false,
    )
    pendingCapture = capture
    room.audioTrackCaptureDefaults = capture
    roomEventsJob?.cancel()
    roomEventsJob = scope.launch { room.events.collect { handleRoomEvent(room, it) } }
    room.connect(url, token)
    if (this.room !== room || activeCall?.id != callId) {
        if (this.room === room) this.room = null
        room.release()
        throw CancellationException()
    }
    log("LiveKit 접속: room=$roomName")
    isMediaConnected = true
    log("미디어 준비 완료, 통화 오디오 활성화=$isAudioSessionActive")
    publishMicrophoneIfReady()
}

internal fun CallCenter.publishMicrophoneIfReady() {
    if (didPublishMicrophone || !isAudioSessionActive || !isMediaConnected) return
    if (pendingCapture == null) return
    val call = activeCall ?: return
    val room = room ?: return
    didPublishMicrophone = true
    microphoneJob = scope.launch {
        try {
            val enabled = room.localParticipant.setMicrophoneEnabled(!call.isMuted)
            if (this@publishMicrophoneIfReady.room !== room || activeCall?.uuid != call.uuid) return@launch
            if (!enabled && !call.isMuted) throw IllegalStateException("마이크 트랙을 만들지 못했어요")
            microphoneReady = true
            log("마이크 publish 완료")
            markCallActive()
            attachAnalysisWriter(microphonePublication(room))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failCall(call.uuid, "마이크를 사용할 수 없어요. ${error.message.orEmpty()}".trim())
        }
    }
}

internal fun microphonePublication(room: Room): LocalTrackPublication? =
    room.localParticipant.getTrackPublication(Track.Source.MICROPHONE)

internal fun CallCenter.markCallActive() {
    val call = activeCall ?: return
    if (!serverAccepted || !isMediaConnected || !isAudioSessionActive || !microphoneReady) return
    platform.stopQuestions()
    updateCall { it.copy(phase = CallPhase.Active) }
    if (didPublishMicrophone) attachAnalysisWriter(null)
    if (call.direction == CallCenter.Direction.Outgoing && call.phase != CallPhase.Active && call.uuid in systemCalls) {
        scope.launch { platform.callSystem.setActive(call.uuid) }
    }
}

/** iOS `RoomDelegate`. 지금 방의 이벤트만 처리한다. */
private fun CallCenter.handleRoomEvent(room: Room, event: RoomEvent) {
    if (this.room !== room) return
    when (event) {
        is RoomEvent.Reconnecting -> {
            if (activeCall == null) return
            isMediaConnected = false
            analysisWriter?.setMuted(true)
            updateCall { it.copy(phase = CallPhase.Reconnecting) }
        }
        is RoomEvent.Reconnected -> {
            if (activeCall == null) return
            isMediaConnected = true
            scope.launch { restoreAfterReconnect(room) }
        }
        is RoomEvent.Disconnected -> {
            val call = activeCall ?: return
            // LiveKit Android는 오류 객체를 넘기지 않으므로(항상 null) 사유로 나눈다.
            // iOS처럼 방 삭제는 상대 종료로, 그 밖의 끊김은 오류로 끝내고 서버에 알린다.
            when (event.reason) {
                DisconnectReason.ROOM_DELETED -> handleMediaError(RoomDeletedException(), call.uuid)
                DisconnectReason.CLIENT_INITIATED -> finishRemoteCall(EndCause.Local)
                else -> failCall(call.uuid, "통화 연결이 끊어졌어요. 인터넷 상태를 확인한 뒤 다시 걸어주세요.")
            }
        }
        is RoomEvent.ParticipantConnected -> {
            if (!isPeer(event.participant)) return
            markCallActive()
            log("상대 참가: ${event.participant.identity?.value ?: "-"}")
        }
        is RoomEvent.TrackSubscribed -> {
            if (isPeer(event.participant)) log("상대 오디오 트랙 수신, muted=${event.publication.muted}")
        }
        is RoomEvent.ParticipantDisconnected -> {
            val call = activeCall ?: return
            if (!isPeer(event.participant)) return
            log("상대 퇴장")
            finishRemoteCall()
            scope.launch {
                try {
                    environment.api.endCall(call.id)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    log("종료 보고 실패: ${error.message}")
                }
            }
        }
        else -> Unit
    }
}

private suspend fun CallCenter.restoreAfterReconnect(room: Room) {
    if (didPublishMicrophone) {
        val call = activeCall ?: return
        try {
            room.localParticipant.setMicrophoneEnabled(!call.isMuted)
            if (this.room !== room || activeCall?.uuid != call.uuid) return
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            failCall(call.uuid, "마이크 상태를 복구하지 못해 통화를 종료했어요.")
            return
        }
    }
    analysisWriter?.setMuted(activeCall?.isMuted ?: true)
    markCallActive()
    if (!serverAccepted && activeCall?.direction == CallCenter.Direction.Outgoing) {
        updateCall { it.copy(phase = CallPhase.Ringing) }
    }
}

private fun CallCenter.isPeer(participant: RemoteParticipant): Boolean =
    activeCall?.peerId != null && activeCall?.peerId == participant.identity?.value
