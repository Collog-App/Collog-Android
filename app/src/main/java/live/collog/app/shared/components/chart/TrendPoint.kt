package live.collog.app.shared.components.chart

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/** iOS `TrendPoint`: 같은 점인지는 [id]로만 비교한다. */
class TrendPoint(val date: Instant, val label: String, val value: Double) {
    val id: UUID = UUID.randomUUID()

    override fun equals(other: Any?): Boolean = other is TrendPoint && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

/** iOS `TrendSeries`. */
class TrendSeries(
    val metricName: String,
    val unit: String,
    val points: List<TrendPoint>,
    val normalRange: ClosedFloatingPointRange<Double>,
    val hasPersonalBaseline: Boolean = true,
    /** 개인 기준선(4주)이 준비되기 전에 쓰는 「첫 기준점」. 통화가 하나면 그 값, 둘 이상이면 이번 값을 뺀 이전 값들의 중앙값. */
    val initialReference: Double? = null,
) {
    val latest: TrendPoint? get() = points.lastOrNull()

    /** 기준선 전 단계의 비교 문구. 좋고 나쁨을 판단하지 않고 사실만 말한다. */
    fun referenceCaption(point: TrendPoint): String {
        val reference = initialReference ?: return "비교할 기록 수집 중"
        if (points.size == 1) return "첫 기준점"
        val ratio = (point.value - reference) / max(abs(reference), 1.0)
        if (abs(ratio) < 0.02) return "기준점과 비슷"
        return if (ratio > 0) "기준점보다 큼" else "기준점보다 작음"
    }

    val median: Double get() = (normalRange.start + normalRange.endInclusive) / 2

    fun nearest(date: Instant): TrendPoint? =
        points.minByOrNull { abs(Duration.between(it.date, date).toMillis()) }

    fun isWithinNormalRange(point: TrendPoint): Boolean = point.value in normalRange

    companion object {
        /** 기준선이 준비되기 전의 첫 기준점: 이번 값을 뺀 이전 값들의 중앙값(값이 하나뿐이면 그 값). 준비됐으면 null. */
        fun initialReference(values: List<Double>, baselineReady: Boolean): Double? {
            if (baselineReady || values.isEmpty()) return null
            val sorted = (if (values.size > 1) values.dropLast(1) else values).sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
        }

        val speechRateSample: TrendSeries by lazy {
            sample(
                listOf(
                    "7월 3주" to 198.0, "7월 4주" to 205.0, "8월 1주" to 212.0,
                    "8월 2주" to 209.0, "8월 3주" to 216.0, "8월 4주" to 210.0,
                ),
                195.0..220.0,
            )
        }

        val speechRateFatherSample: TrendSeries by lazy {
            sample(
                listOf(
                    "7월 3주" to 184.0, "7월 4주" to 188.0, "8월 1주" to 187.0,
                    "8월 2주" to 192.0, "8월 3주" to 190.0, "8월 4주" to 194.0,
                ),
                180.0..205.0,
            )
        }

        // 오늘부터 한 주씩 거슬러 올라가며 날짜를 붙인다
        private fun sample(values: List<Pair<String, Double>>, normalRange: ClosedFloatingPointRange<Double>): TrendSeries {
            val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            val points = values.mapIndexed { index, (label, value) ->
                TrendPoint(today.plusWeeks((index - (values.size - 1)).toLong()).toInstant(), label, value)
            }
            return TrendSeries("말씀 속도", "음절/분", points, normalRange)
        }
    }
}
