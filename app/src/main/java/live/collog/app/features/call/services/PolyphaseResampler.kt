package live.collog.app.features.call.services

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * iOS `AVAudioConverter`가 하던 표본율 변환. Kaiser 창 sinc 저역 통과를 다상(polyphase)으로 계산한다.
 * 묶음 사이에 입력 이력과 위상을 이어 가므로 10 ms 묶음 경계에서 끊김이 없다. 스레드 안전하지 않다.
 */
internal class PolyphaseResampler(val sourceRate: Int, targetRate: Int) {
    private val up: Int
    private val down: Int
    private val phases: Int
    private val taps: Int
    private val bank: FloatArray
    private val history: FloatArray

    // 다음 출력의 위치. 이번 묶음 첫 입력을 0으로, 입력 표본 1/up 단위로 센다.
    private var position = 0L

    init {
        require(sourceRate > 0 && targetRate > 0)
        val divisor = gcd(sourceRate, targetRate)
        up = targetRate / divisor
        down = sourceRate / divisor
        phases = min(up, MAX_PHASES)

        // 통과 대역 끝 0.4375, 저지 대역 시작 0.5 (낮은 쪽 표본율 기준). 입력 표본당 주기로 바꾼다.
        val base = min(sourceRate, targetRate).toDouble()
        val cutoff = CUTOFF * base / sourceRate
        val transition = TRANSITION * base / sourceRate
        val order = (STOPBAND_DB - 8) / (2.285 * 2 * PI * transition)
        val halfWidth = ceil(order / 2).toInt()
        taps = 2 * halfWidth + 1

        val beta = 0.1102 * (STOPBAND_DB - 8.7)
        val windowScale = besselI0(beta)
        bank = FloatArray(phases * taps)
        val row = DoubleArray(taps)
        for (phase in 0 until phases) {
            val fraction = phase.toDouble() / phases
            var sum = 0.0
            for (k in 0 until taps) {
                val t = k + fraction - halfWidth
                val x = t / halfWidth
                val window = if (abs(x) >= 1) 0.0 else besselI0(beta * sqrt(1 - x * x)) / windowScale
                row[k] = 2 * cutoff * sinc(2 * cutoff * t) * window
                sum += row[k]
            }
            // 위상마다 직류 이득을 정확히 1로 맞춘다
            for (k in 0 until taps) bank[phase * taps + k] = (row[k] / sum).toFloat()
        }
        history = FloatArray(taps - 1)
    }

    fun process(input: FloatArray): FloatArray {
        val buffer = FloatArray(history.size + input.size)
        history.copyInto(buffer)
        input.copyInto(buffer, history.size)

        val limit = input.size.toLong() * up
        val count = if (position >= limit) 0 else ((limit - position + down - 1) / down).toInt()
        val output = FloatArray(count)
        var pos = position
        for (n in 0 until count) {
            val newest = (pos / up).toInt() + taps - 1
            val offset = ((pos % up) * phases / up).toInt() * taps
            var sum = 0f
            for (k in 0 until taps) sum += bank[offset + k] * buffer[newest - k]
            output[n] = sum
            pos += down
        }
        position = pos - limit
        buffer.copyInto(history, 0, buffer.size - history.size)
        return output
    }

    private companion object {
        const val CUTOFF = 0.46875
        const val TRANSITION = 0.0625
        const val STOPBAND_DB = 80.0

        // 표준 표본율(8k~96k)은 모두 이 안이라 위상이 정확하다. 넘으면 가장 가까운 아래 위상을 쓴다.
        const val MAX_PHASES = 1024

        tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

        fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

        fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            var k = 1
            while (term > sum * 1e-12) {
                val half = x / (2 * k)
                term *= half * half
                sum += term
                k++
            }
            return sum
        }
    }
}
