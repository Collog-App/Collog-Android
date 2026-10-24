package live.collog.app.features.call.services

import java.io.File
import java.nio.ByteBuffer
import livekit.org.webrtc.AudioTrackSink

// 자리 파일: port/call-audio 브랜치의 구현으로 바뀐다(시그니처 약속만 둔다).
class AnalysisPcmWriter(private val directory: File) : AudioTrackSink {
    data class Recording(val file: File, val durationSeconds: Double, val levelText: String)

    companion object {
        const val SAMPLE_RATE = 16_000
        fun purgeAbandonedRecordings(directory: File) {}
    }

    fun start() {}
    override fun onData(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        absoluteCaptureTimestampMs: Long,
    ) {}
    fun setMuted(muted: Boolean) {}
    fun finish(): Recording? = null
    fun discard() {}
}
