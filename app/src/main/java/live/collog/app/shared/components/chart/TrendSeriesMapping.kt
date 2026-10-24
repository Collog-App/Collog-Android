package live.collog.app.shared.components.chart

import kotlin.math.max
import live.collog.app.shared.services.api.AcousticTrendDto
import live.collog.app.shared.services.api.ApiFormat
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.MetricLabel

private const val NORMAL_RANGE_FACTOR = 1.5 / 0.6745

/** iOS `TrendSeries.init?(trend:baseline:)`. 점이 둘보다 적으면 null. */
fun TrendSeries.Companion.from(trend: AcousticTrendDto, baseline: BaselineDto?): TrendSeries? {
    val points = trend.points.mapNotNull { point ->
        val value = point.value ?: return@mapNotNull null
        val date = ApiFormat.parseIsoDate(point.date) ?: return@mapNotNull null
        TrendPoint(date, ApiFormat.shortDate(date), value)
    }
    if (points.size < 2) return null

    val values = points.map { it.value }
    val baselineMedian = baseline?.median
    val baselineMad = baseline?.mad
    val range = if (baseline != null && baseline.isReady && baselineMedian != null && baselineMad != null) {
        val spread = baselineMad * NORMAL_RANGE_FACTOR
        (baselineMedian - spread)..(baselineMedian + spread)
    } else {
        val minimum = values.min()
        val maximum = values.max()
        val padding = max((maximum - minimum) * 0.2, 0.5)
        (minimum + padding)..max(maximum - padding, minimum + padding + 0.1)
    }

    return TrendSeries(
        metricName = MetricLabel.korean(trend.metric),
        unit = MetricLabel.unit(trend.metric),
        points = points,
        normalRange = range,
        hasPersonalBaseline = baseline?.isReady == true,
    )
}
