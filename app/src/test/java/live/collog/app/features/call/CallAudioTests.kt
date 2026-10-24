package live.collog.app.features.call

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import live.collog.app.features.call.services.AnalysisPcmWriter
import live.collog.app.features.call.services.PolyphaseResampler
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS `CallAudioTests` 중 녹음기에 해당하는 것. 로그아웃 순서 테스트는 CallCenter 쪽이라 여기서 옮기지 않는다.
 * 뒤쪽은 표본율 변환 품질(주파수·진폭 유지, 접힘 억제, 묶음 경계)을 확인한다.
 */
class CallAudioTests {
    private val directory: File = Files.createTempDirectory("analysis-pcm").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun mutedLocalAudioIsWrittenAsSilence() {
        val input = pcm16(ShortArray(160) { 1_000 })
        val writer = AnalysisPcmWriter(directory)
        writer.start()
        writer.setMuted(true)
        writer.feed(input, sampleRate = 16_000)
        assertEquals(1_000.toShort(), input.order(ByteOrder.LITTLE_ENDIAN).getShort(0))
        val recording = requireNotNull(writer.finish())
        val wav = readWav(recording.file)

        assertEquals(160, wav.samples.size)
        assertTrue(wav.samples.all { it == 0.toShort() })
        assertEquals(wav.samples.size / 16_000.0, recording.durationSeconds, 0.0)
        writer.discard()
    }

    @Test
    fun stereoConversionProducesMonoRecordingWithoutChangingInput() {
        // LiveKit은 4바이트 표본을 float로, 채널은 섞어서(interleaved) 넘긴다
        val input = float32(FloatArray(4_800 * 2) { 0.25f })
        val before = input.array().copyOf()
        val writer = AnalysisPcmWriter(directory)
        writer.start()
        repeat(10) { writer.feed(input, sampleRate = 48_000, channels = 2, bits = 32) }
        val recording = requireNotNull(writer.finish())
        val wav = readWav(recording.file)

        assertEquals(1, wav.channels)
        assertEquals(16_000, wav.sampleRate)
        assertTrue(wav.samples.size > 15_000)
        assertTrue(wav.samples.size <= 16_000)
        assertEquals(wav.samples.size / 16_000.0, recording.durationSeconds, 0.0)
        assertEquals(0, input.position())
        assertArrayEquals(before, input.array())
        assertEquals(0.25f, input.order(ByteOrder.LITTLE_ENDIAN).getFloat(0))
        // 직류 이득 1: 필터가 채워진 뒤로는 0.25 그대로다
        assertTrue(wav.samples.drop(1_000).all { abs(it - 8_192) <= 1 })
        writer.discard()
    }

    @Test
    fun finishedRecordingIgnoresLateRenderCallbacks() {
        val input = pcm16(ShortArray(160) { 1_000 })
        val writer = AnalysisPcmWriter(directory)
        writer.start()
        writer.feed(input, sampleRate = 16_000)
        val recording = requireNotNull(writer.finish())
        writer.feed(input, sampleRate = 16_000)
        val wav = readWav(recording.file)

        assertEquals(160, wav.samples.size)
        assertEquals(0.01, recording.durationSeconds, 0.0)
        assertEquals(recording.levelText, requireNotNull(writer.finish()).levelText)
        assertEquals("peak -30.3 / rms -30.3 dBFS", recording.levelText)
        writer.discard()
    }

    @Test
    fun concurrentRenderCallbacksPreserveEveryFrame() {
        val writer = AnalysisPcmWriter(directory)
        writer.start()
        val gate = CountDownLatch(1)
        val threads = List(20) {
            thread {
                gate.await()
                writer.feed(pcm16(ShortArray(160) { 1_000 }), sampleRate = 16_000)
            }
        }
        gate.countDown()
        threads.forEach { it.join() }
        val recording = requireNotNull(writer.finish())

        assertEquals(3_200, readWav(recording.file).samples.size)
        assertEquals(0.2, recording.durationSeconds, 0.0)
        writer.discard()
    }

    @Test
    fun channelsAreAveragedForEverySampleFormat() {
        val stereo = record(pcm16(ShortArray(320) { if (it % 2 == 0) 16_384 else 0 }), 16_000, channels = 2)
        assertTrue(stereo.all { it == 8_192.toShort() })
        val unsigned8 = record(ByteBuffer.wrap(ByteArray(160) { 192.toByte() }), 16_000, bits = 8)
        assertTrue(unsigned8.all { it == 16_384.toShort() })
        val float = record(float32(FloatArray(320) { if (it % 2 == 0) 0.5f else -0.25f }), 16_000, channels = 2, bits = 32)
        assertTrue(float.all { it == 4_096.toShort() })
    }

    @Test
    fun discardDeletesFileAndPurgeRemovesOnlyRecordings() {
        val writer = AnalysisPcmWriter(directory)
        writer.start()
        writer.feed(pcm16(ShortArray(160) { 1_000 }), sampleRate = 16_000)
        writer.discard()
        assertNull(writer.finish())
        assertTrue(directory.listFiles().orEmpty().isEmpty())

        val abandoned = File(directory, "abandoned.wav").apply { writeText("x") }
        val other = File(directory, "keep.json").apply { writeText("x") }
        AnalysisPcmWriter.purgeAbandonedRecordings(directory)
        assertFalse(abandoned.exists())
        assertTrue(other.exists())
    }

    @Test
    fun sineAt1kHzKeepsFrequencyAndAmplitude() {
        val output = recordInChunks(sine(1_000.0, 48_000, 48_000), 48_000, chunk = 480)
        val steady = output.copyOfRange(1_600, 14_400) // 800주기

        assertEquals(16_000, output.size)
        assertEquals(1_000.0, frequency(steady, 16_000), 0.05)
        assertEquals(0.5, rms(steady) * sqrt(2.0), 0.0025)
    }

    @Test
    fun toneAt12kHzIsAttenuatedByAtLeast40dB() {
        val input = sine(12_000.0, 48_000, 48_000)
        val output = recordInChunks(input, 48_000, chunk = 480)
        val attenuation = 20 * log10(rms(output.copyOfRange(1_600, 14_400)) / (0.5 / sqrt(2.0)))
        assertTrue("12 kHz attenuation $attenuation dB", attenuation <= -40)

        // 16비트로 쓰면 남은 성분이 0.5 LSB 아래라 0이 되므로, 필터 출력도 float로 직접 잰다
        val resampler = PolyphaseResampler(48_000, 16_000)
        val filtered = input.toList().chunked(480)
            .flatMap { chunk -> resampler.process(FloatArray(chunk.size) { chunk[it] / 32_768f }).toList() }
            .subList(1_600, 14_400)
        val filteredRms = sqrt(filtered.sumOf { it.toDouble() * it } / filtered.size)
        val filterAttenuation = 20 * log10(filteredRms / (0.5 / sqrt(2.0)))
        assertTrue("12 kHz filter attenuation $filterAttenuation dB", filterAttenuation in -200.0..-40.0)
    }

    @Test
    fun chunkBoundariesDoNotChangeOutput() {
        for ((rate, chunk) in listOf(48_000 to 480, 44_100 to 441, 32_000 to 320, 8_000 to 80)) {
            val input = sine(1_000.0, rate, rate)
            val whole = recordInChunks(input, rate, chunk = input.size)
            val chunked = recordInChunks(input, rate, chunk = chunk)
            val ragged = recordInChunks(input, rate, chunk = 97)

            assertEquals("$rate", 16_000, whole.size)
            assertArrayEquals("$rate", whole, chunked)
            assertArrayEquals("$rate", whole, ragged)
            // 1 kHz 0.5 진폭 정현파의 이웃 표본 차이 상한은 2·0.5·sin(π/16)
            val maxStep = (160 until chunked.size).maxOf { abs(chunked[it] - chunked[it - 1]) }
            assertTrue("$rate step $maxStep", maxStep <= (sin(PI / 16) * 32_768).toInt() + 4)
        }
    }

    @Test
    fun sampleRateChangeRestartsConversion() {
        val writer = AnalysisPcmWriter(directory)
        writer.start()
        val first = sine(1_000.0, 48_000, 24_000)
        val second = sine(1_000.0, 44_100, 22_050)
        first.toList().chunked(480).forEach { writer.feed(pcm16(it.toShortArray()), 48_000) }
        second.toList().chunked(441).forEach { writer.feed(pcm16(it.toShortArray()), 44_100) }
        val recording = requireNotNull(writer.finish())
        val output = readWav(recording.file).samples
        writer.discard()

        assertEquals(16_000, output.size)
        assertEquals(1.0, recording.durationSeconds, 0.0)
        assertEquals(1_000.0, frequency(output.copyOfRange(8_800, 15_200), 16_000), 0.05)
    }

    private fun record(buffer: ByteBuffer, rate: Int, channels: Int = 1, bits: Int = 16): ShortArray {
        val writer = AnalysisPcmWriter(directory)
        writer.start()
        writer.feed(buffer, rate, channels, bits)
        val samples = readWav(requireNotNull(writer.finish()).file).samples
        writer.discard()
        return samples
    }

    private fun recordInChunks(input: ShortArray, rate: Int, chunk: Int): ShortArray {
        val writer = AnalysisPcmWriter(directory)
        writer.start()
        var start = 0
        while (start < input.size) {
            val end = minOf(start + chunk, input.size)
            writer.feed(pcm16(input.copyOfRange(start, end)), rate)
            start = end
        }
        val samples = readWav(requireNotNull(writer.finish()).file).samples
        writer.discard()
        return samples
    }

    private class Wav(val channels: Int, val sampleRate: Int, val samples: ShortArray)

    private companion object {
        fun AnalysisPcmWriter.feed(buffer: ByteBuffer, sampleRate: Int, channels: Int = 1, bits: Int = 16) =
            onData(buffer, bits, sampleRate, channels, sampleRate / 100, 0)

        // LiveKit처럼 리틀 엔디언 바이트를 기본(BIG_ENDIAN) 순서의 버퍼로 감싼다
        fun pcm16(samples: ShortArray): ByteBuffer {
            val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { bytes.putShort(it) }
            return ByteBuffer.wrap(bytes.array())
        }

        fun float32(samples: FloatArray): ByteBuffer {
            val bytes = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { bytes.putFloat(it) }
            return ByteBuffer.wrap(bytes.array())
        }

        fun sine(frequency: Double, rate: Int, count: Int, amplitude: Double = 0.5) =
            ShortArray(count) { (amplitude * sin(2 * PI * frequency * it / rate) * 32_768).roundToInt().toShort() }

        fun rms(samples: ShortArray): Double =
            sqrt(samples.sumOf { (it / 32_768.0) * (it / 32_768.0) } / samples.size)

        // 올라가는 영점 교차를 선형 보간해 첫 교차와 마지막 교차 사이의 주기 수로 잰다
        fun frequency(samples: ShortArray, rate: Int): Double {
            val crossings = (1 until samples.size)
                .filter { samples[it - 1] < 0 && samples[it] >= 0 }
                .map { it - 1 + samples[it - 1] / (samples[it - 1].toDouble() - samples[it]) }
            return (crossings.size - 1) * rate / (crossings.last() - crossings.first())
        }

        fun readWav(file: File): Wav {
            val bytes = file.readBytes()
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
            assertEquals("RIFF", tag(0))
            assertEquals(bytes.size - 8, data.getInt(4))
            assertEquals("WAVE", tag(8))
            assertEquals("fmt ", tag(12))
            assertEquals(16, data.getInt(16))
            assertEquals(1.toShort(), data.getShort(20))
            val channels = data.getShort(22).toInt()
            val rate = data.getInt(24)
            assertEquals(rate * channels * 2, data.getInt(28))
            assertEquals((channels * 2).toShort(), data.getShort(32))
            assertEquals(16.toShort(), data.getShort(34))
            assertEquals("data", tag(36))
            assertEquals(bytes.size - 44, data.getInt(40))
            return Wav(channels, rate, ShortArray((bytes.size - 44) / 2) { data.getShort(44 + it * 2) })
        }
    }
}
