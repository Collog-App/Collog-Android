package live.collog.app.features.call.services

import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalTrackPublication
import kotlinx.coroutines.launch
import live.collog.app.features.call.models.CallSafety
import live.collog.app.shared.services.api.ApiError

// iOS CallCenter+Recording: 동의한 통화에서 내 마이크 음성을 16 kHz로 기록해 올린다.

internal fun CallCenter.makeUploadQueue(): RawAudioUploadQueue? = try {
    RawAudioUploadQueue(
        directory = platform.uploadDirectory,
        currentOwner = { environment.session.user?.id },
        validatePermission = { callId ->
            val status = environment.api.callStatus(callId)
            if (status.recordingEnabled != true) throw ApiError.Unauthenticated
        },
        requestUpload = { callId, duration, sampleRate ->
            environment.api.rawAudioUploadUrl(callId, duration, sampleRate)
        },
        upload = { file, url -> environment.api.uploadRawAudio(file, url) },
        complete = { callId, assetId -> environment.api.completeRawAudio(callId, assetId) },
        scope = scope,
    )
} catch (error: Exception) {
    log("음성 업로드 대기열을 준비하지 못했어요")
    null
}

fun CallCenter.resumePendingUploads() {
    uploads?.discardInvalidOwners()
    val uploads = uploads ?: return
    if (!uploads.hasPending || uploadJob?.isActive == true) return
    uploadJob = scope.launch { uploads.resume() }
}

internal fun CallCenter.attachAnalysisWriter(publication: LocalTrackPublication?) {
    if (analysisWriter != null) return
    val call = activeCall ?: return
    if (!CallSafety.canRecord(call.recordingEnabled, rawCaptureRequired, serverAccepted, call.phase)) return
    val resolved = publication?.track ?: room?.let { microphonePublication(it)?.track }
    val track = resolved as? LocalAudioTrack ?: run {
        log("분석 PCM 실패: local audio track을 찾지 못했다")
        return
    }
    val writer = AnalysisPcmWriter(platform.analysisDirectory)
    try {
        writer.start()
    } catch (error: Exception) {
        writer.discard()
        log("분석 PCM 시작 실패: ${error.message}")
        return
    }
    track.addSink(writer)
    writer.setMuted(call.isMuted)
    analysisWriter = writer
    analysisTrack = track
    log("분석 PCM 기록 시작")
}

internal fun CallCenter.finishAnalysisRecording(callId: String) {
    val writer = analysisWriter ?: return
    analysisWriter = null
    analysisTrack?.removeSink(writer)
    analysisTrack = null

    val recording = writer.finish()
    if (recording == null || recording.durationSeconds <= 0) {
        writer.discard()
        log("분석 PCM 없음")
        return
    }
    log("분석 PCM ${recording.levelText}")
    val ownerId = callOwnerId
    val uploads = uploads
    if (ownerId == null || ownerId != environment.session.user?.id || uploads == null ||
        activeCall?.recordingEnabled != true
    ) {
        writer.discard()
        return
    }
    try {
        uploads.enqueue(
            file = recording.file,
            callId = callId,
            ownerId = ownerId,
            durationSec = recording.durationSeconds,
            sampleRate = AnalysisPcmWriter.SAMPLE_RATE,
        )
        resumePendingUploads()
    } catch (error: Exception) {
        recording.file.delete()
        log("분석 음성을 업로드 대기열에 저장하지 못했어요")
    }
}

internal fun CallCenter.discardAnalysisRecording() {
    analysisWriter?.let { analysisTrack?.removeSink(it) }
    analysisWriter?.discard()
    analysisWriter = null
    analysisTrack = null
    rawCaptureRequired = false
    activeCall?.id?.let { uploads?.discard(it) }
}

internal fun CallCenter.applyRecordingStatus(enabled: Boolean, message: String?) {
    val shouldStop = !enabled && !recordingStopped
    if (!enabled) recordingStopped = true
    updateCall { it.copy(recordingEnabled = enabled && !recordingStopped) }
    if (!enabled) {
        if (shouldStop) discardAnalysisRecording()
        updateCall { it.copy(notice = message ?: "이번 통화는 녹음과 분석 없이 진행해요.") }
    }
}
