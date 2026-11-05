package live.collog.app.features.timeline.models

import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import live.collog.app.shared.components.chart.TrendPoint
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CollogApi
import live.collog.app.shared.services.api.MetricLabel
import live.collog.app.shared.services.api.ReportDto

private const val NORMAL_RANGE_FACTOR = 1.5 / 0.6745

/**
 * 새 피그마의 「최근 6주」 말씀 속도 추세(리포트 음향 추세·가족 건강).
 * 서버에 주별 집계가 없어 주간 리포트를 주마다 받아 그 주 통화들의 평균으로 점 하나를 만든다.
 * 한 번 받은 주는 [means]에 두고 다시 받지 않는다(가족이 바뀌면 새로 만든다).
 */
class WeeklySpeechHistory(
    private val api: CollogApi,
    private val parentId: String,
    private val baseline: BaselineDto?,
) {
    /** 주 offset → 그 주 평균 말씀 속도. 통화가 없던 주는 null. 아직 못 받은 주는 키가 없다. */
    private val means = mutableMapOf<Int, Double?>()

    fun record(offset: Int, dto: ReportDto) {
        means[offset] = weekMean(dto)
    }

    /** [offset] 주까지의 [weeks]주 추세. 통화가 있던 주가 없으면 null. 못 받은 주는 건너뛴다. */
    suspend fun trend(offset: Int, weeks: Int = 6): TrendSeries? {
        val range = (offset - weeks + 1..offset).filter { it <= 0 }
        coroutineScope {
            range.filter { it !in means }.map { week ->
                async {
                    try {
                        val bounds = TimelineWeekPage.bounds(week)
                        record(week, api.report(parentId, date = TimelineWeekPage.isoDate.format(bounds.anchor)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 못 받은 주는 점 없이 둔다
                    }
                }
            }.awaitAll()
        }

        val points = range.mapNotNull { week ->
            val mean = means[week] ?: return@mapNotNull null
            TrendPoint(
                TimelineWeekPage.instant(TimelineWeekPage.bounds(week).start),
                TimelineWeekPage(week).title,
                mean,
            )
        }
        if (points.isEmpty()) return null
        return series(points)
    }

    private fun series(points: List<TrendPoint>): TrendSeries {
        val values = points.map { it.value }
        val median = baseline?.median
        val mad = baseline?.mad
        val normal = if (baseline != null && baseline.isReady && median != null && mad != null) {
            val spread = mad * NORMAL_RANGE_FACTOR
            (median - spread)..(median + spread)
        } else {
            val minimum = values.min()
            val maximum = values.max()
            val padding = max((maximum - minimum) * 0.2, 0.5)
            (minimum + padding)..max(maximum - padding, minimum + padding + 0.1)
        }
        return TrendSeries(
            metricName = MetricLabel.korean("SPEECH_RATE"),
            unit = MetricLabel.unit("SPEECH_RATE"),
            points = points,
            normalRange = normal,
            hasPersonalBaseline = baseline?.isReady == true,
        )
    }

    companion object {
        /** 홈 「가족 건강」용: 기준선을 받고 이번 주까지의 6주 추세를 만든다. 실패하면 null. */
        suspend fun loadTrend(api: CollogApi, parentId: String): TrendSeries? {
            val baseline = try {
                api.baselines(parentId).firstOrNull { it.kind == "ROLLING" && it.isReady && it.metric == "SPEECH_RATE" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            return WeeklySpeechHistory(api, parentId, baseline).trend(0)
        }

        /** 그 주 리포트의 말씀 속도 점들의 평균. 없으면 null. */
        fun weekMean(dto: ReportDto): Double? {
            val history = dto.recentAcousticHistory ?: dto.acousticTrends
            val values = history.firstOrNull { it.metric == "SPEECH_RATE" }?.points?.mapNotNull { it.value }
            return if (values.isNullOrEmpty()) null else values.average()
        }
    }
}
