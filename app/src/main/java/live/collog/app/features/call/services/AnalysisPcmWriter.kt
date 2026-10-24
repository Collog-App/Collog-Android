package live.collog.app.features.call.services

import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import livekit.org.webrtc.AudioTrackSink

/**
 * iOS `AnalysisPCMWriter` + `PCMWriterState`. 마이크 PCM을 16 kHz 모노 16비트 WAV로 남긴다.
 * [onData]는 오디오 스레드, 나머지는 메인에서 부른다. 모든 상태는 [lock] 안에서만 바뀐다.
 */
class AnalysisPcmWriter(private val directory: File) : AudioTrackSink {
    data class Recording(val file: File, val durationSeconds: Double, val levelText: String)

    private val lock = Any()
    private var output: RandomAccessFile? = null
    private var file: File? = null
    private val pending = ByteArray(BUFFER_BYTES)
    private var pendingCount = 0
    private var resampler: PolyphaseResampler? = null
    private var frameCount = 0L
    private var peakSample = 0
    private var squareSum = 0.0
    private var sampleCount = 0.0
    private var muted = false

    fun start() {
        synchronized(lock) {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("분석 PCM 폴더를 만들 수 없어요")
            closeOutput(patchHeader = false)
            val target = File(directory, "${UUID.randomUUID()}.wav")
            val out = RandomAccessFile(target, "rw")
            try {
                out.setLength(0)
                out.write(header(0))
            } catch (error: IOException) {
                out.close()
                target.delete()
                throw error
            }
            output = out
            file = target
            resampler = null
            pendingCount = 0
            frameCount = 0
            peakSample = 0
            squareSum = 0.0
            sampleCount = 0.0
        }
    }

    override fun onData(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        absoluteCaptureTimestampMs: Long,
    ) {
        synchronized(lock) {
            val out = output ?: return
            try {
                val mono = downmix(audioData, bitsPerSample, numberOfChannels)
                if (mono == null || sampleRate <= 0) throw IOException("분석용 PCM 변환 형식을 만들 수 없어요")
                write(convert(mono, sampleRate), out)
            } catch (error: Exception) {
                Log.w("Collog", "분석 PCM 기록 실패: ${error.message}")
            }
        }
    }

    fun finish(): Recording? = synchronized(lock) {
        closeOutput(patchHeader = true)
        resampler = null
        val target = file ?: return null
        Recording(target, frameCount.toDouble() / SAMPLE_RATE, levelText())
    }

    fun setMuted(muted: Boolean) {
        synchronized(lock) { this.muted = muted }
    }

    fun discard() {
        synchronized(lock) {
            closeOutput(patchHeader = false)
            resampler = null
            file?.delete()
            file = null
            frameCount = 0
        }
    }

    // 버퍼의 바이트 순서 표시와 numberOfFrames는 믿지 않는다(LiveKit은 BIG_ENDIAN으로 감싸 10 ms 기준값을 넘긴다)
    private fun downmix(audioData: ByteBuffer, bitsPerSample: Int, channels: Int): FloatArray? {
        if (channels <= 0 || (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 32)) return null
        val data = audioData.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val start = data.position()
        val bytesPerSample = bitsPerSample / 8
        val frames = data.remaining() / (bytesPerSample * channels)
        val mono = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channels) {
                val at = start + (frame * channels + channel) * bytesPerSample
                sum += when (bitsPerSample) {
                    8 -> ((data.get(at).toInt() and 0xFF) - 128) / 128f
                    16 -> data.getShort(at) / 32768f
                    else -> data.getFloat(at).takeIf { it.isFinite() } ?: 0f
                }
            }
            mono[frame] = sum / channels
        }
        return mono
    }

    // iOS처럼 형식이 바뀌면 변환기를 새로 만든다
    private fun convert(mono: FloatArray, sampleRate: Int): FloatArray {
        if (sampleRate == SAMPLE_RATE) {
            resampler = null
            return mono
        }
        val current = resampler?.takeIf { it.sourceRate == sampleRate }
            ?: PolyphaseResampler(sampleRate, SAMPLE_RATE).also { resampler = it }
        return current.process(mono)
    }

    private fun write(samples: FloatArray, out: RandomAccessFile) {
        for (sample in samples) {
            val value = if (muted) 0 else (sample * 32768f).roundToInt().coerceIn(-32768, 32767)
            pending[pendingCount++] = value.toByte()
            pending[pendingCount++] = (value shr 8).toByte()
            if (pendingCount == pending.size) flushPending(out)
            peakSample = max(peakSample, abs(value))
            val normalized = value / 32767.0
            squareSum += normalized * normalized
            sampleCount += 1
        }
        frameCount += samples.size
    }

    private fun flushPending(out: RandomAccessFile) {
        try {
            if (pendingCount > 0) out.write(pending, 0, pendingCount)
        } finally {
            pendingCount = 0
        }
    }

    private fun closeOutput(patchHeader: Boolean) {
        val out = output ?: return
        output = null
        try {
            if (patchHeader) {
                flushPending(out)
                out.seek(0)
                out.write(header(out.length() - HEADER_BYTES))
            }
        } catch (error: IOException) {
            Log.w("Collog", "분석 PCM 기록 실패: ${error.message}")
        } finally {
            pendingCount = 0
            runCatching { out.close() }
        }
    }

    private fun levelText(): String {
        val peak = peakSample.toDouble() / Short.MAX_VALUE
        val rms = if (sampleCount > 0) sqrt(squareSum / sampleCount) else 0.0
        return String.format(
            Locale.ROOT,
            "peak %.1f / rms %.1f dBFS",
            20 * log10(max(peak, 1e-9)),
            20 * log10(max(rms, 1e-9)),
        )
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val HEADER_BYTES = 44

        // 약 1초 분량을 모아 한 번에 쓴다(오디오 스레드의 쓰기 호출을 줄인다)
        private const val BUFFER_BYTES = 32 * 1024

        fun purgeAbandonedRecordings(directory: File) {
            directory.listFiles()?.filter { it.extension == "wav" }?.forEach { it.delete() }
        }

        private fun header(dataBytes: Long): ByteArray =
            ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((36 + dataBytes).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1) // PCM
                putShort(1) // 모노
                putInt(SAMPLE_RATE)
                putInt(SAMPLE_RATE * 2)
                putShort(2)
                putShort(16)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes.toInt())
            }.array()
    }
}
