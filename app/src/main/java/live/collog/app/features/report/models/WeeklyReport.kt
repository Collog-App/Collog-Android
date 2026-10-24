package live.collog.app.features.report.models

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor
import live.collog.app.features.report.views.components.MetricTrend
import live.collog.app.shared.components.chart.TrendPoint
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.components.stat.StatNote

/** iOS `ReportState`. */
enum class ReportState { Ready, Empty, BaselineCollecting }

/** iOS `SignalTone`. */
enum class SignalTone { Steady, Watch }

/** iOS `ChangeSignalItem`. */
data class ChangeSignalItem(
    val metricName: String,
    val summary: String,
    val tone: SignalTone,
    val isPromoted: Boolean,
) {
    val id: UUID = UUID.randomUUID()
}

/** iOS `ConversationGroup`. */
data class ConversationGroup(
    val category: String,
    val symbol: String,
    val items: List<String>,
) {
    val id: UUID = UUID.randomUUID()
}

/** iOS `RepeatObservation`. */
data class RepeatObservation(
    val countText: String,
    val perMinuteText: String,
    val caption: String,
)

/** iOS `WeeklyReport`. */
data class WeeklyReport(
    val state: ReportState,
    val noticeText: String?,
    val summaryStats: List<CallStat>,
    val changeSignals: List<ChangeSignalItem>,
    val conversationGroups: List<ConversationGroup>,
    val repeatObservation: RepeatObservation,
    val acousticTrend: TrendSeries?,
    val metricTrends: List<MetricTrend>,
    val disclaimer: String,
) {
    companion object {
        val empty = WeeklyReport(
            state = ReportState.Empty,
            noticeText = null,
            summaryStats = emptyList(),
            changeSignals = emptyList(),
            conversationGroups = emptyList(),
            repeatObservation = RepeatObservation(countText = "0", perMinuteText = "", caption = ""),
            acousticTrend = null,
            metricTrends = emptyList(),
            disclaimer = "",
        )

        val sample: WeeklyReport by lazy {
            WeeklyReport(
                state = ReportState.Ready,
                noticeText = null,
                summaryStats = listOf(
                    CallStat("분석된 통화", "3", "건", StatNote("지난주 4건")),
                    CallStat("총 통화 시간", "32", "분", StatNote("지난주 41분")),
                ),
                changeSignals = listOf(
                    ChangeSignalItem("말씀 속도", "4주 연속 조금씩 빨라지고 있어요. 처음 대비 +8%", SignalTone.Watch, true),
                    ChangeSignalItem("통화 길이", "지난달과 비슷하게 유지되고 있어요.", SignalTone.Steady, false),
                ),
                conversationGroups = listOf(
                    ConversationGroup("증상", "stethoscope", listOf("허리가 조금 불편하다고 하셨어요")),
                    ConversationGroup("복약", "pills", listOf("혈압약은 꾸준히 복용 중이라고 하셨어요")),
                    ConversationGroup("활동", "figure.walk", listOf("주말에 가벼운 산책을 하셨어요")),
                    ConversationGroup("수면", "moon", listOf("잠드는 데 오래 걸린다고 하셨어요")),
                ),
                repeatObservation = RepeatObservation("3", "분당 0.4회", "대화 중 다시 물어보신 횟수예요"),
                acousticTrend = TrendSeries.speechRateSample,
                metricTrends = listOf(
                    MetricTrend("말씀 속도", "208", "음절/분", listOf(229.0, 205.0, 212.0, 209.0, 216.0, 208.0), MetricTrend.Shape.Line),
                    MetricTrend("되물으심", "3", "회", listOf(1.0, 2.0, 1.0, 3.0, 2.0, 3.0), MetricTrend.Shape.Bar),
                ),
                disclaimer = "",
            )
        }

        fun sample(relation: String?): WeeklyReport {
            if (relation != "FATHER") return sample
            return WeeklyReport(
                state = ReportState.Ready,
                noticeText = null,
                summaryStats = listOf(
                    CallStat("분석된 통화", "2", "건", StatNote("지난주 3건")),
                    CallStat("총 통화 시간", "27", "분", StatNote("지난주 29분")),
                ),
                changeSignals = listOf(
                    ChangeSignalItem("말씀 속도", "최근 4주 동안 비슷하게 유지되고 있어요.", SignalTone.Steady, true),
                    ChangeSignalItem("통화 길이", "지난주보다 2분 길어졌어요.", SignalTone.Steady, false),
                ),
                conversationGroups = listOf(
                    ConversationGroup("활동", "figure.walk", listOf("아침 산책을 꾸준히 하셨어요")),
                    ConversationGroup("식사", "fork.knife", listOf("점심으로 국수를 드셨어요")),
                    ConversationGroup("수면", "moon", listOf("어젯밤 편하게 주무셨다고 하셨어요")),
                ),
                repeatObservation = RepeatObservation("1", "분당 0.1회", "대화 중 다시 물어보신 횟수예요"),
                acousticTrend = TrendSeries.speechRateFatherSample,
                metricTrends = listOf(
                    MetricTrend("말씀 속도", "194", "음절/분", listOf(184.0, 188.0, 187.0, 192.0, 190.0, 194.0), MetricTrend.Shape.Line),
                    MetricTrend("되물으심", "1", "회", listOf(2.0, 1.0, 1.0, 2.0, 1.0, 1.0), MetricTrend.Shape.Bar),
                ),
                disclaimer = "",
            )
        }

        fun sample(relation: String?, offset: Int): WeeklyReport {
            val base = sample(relation)
            val distance = abs(offset)
            val isFather = relation == "FATHER"
            val callCount = (if (isFather) 2 else 3) + distance % 2
            val totalMinutes = (if (isFather) 27 else 32) + (distance * 3) % 13
            val repeatCount = (if (isFather) 1 else 3) + distance % 3
            val speechDelta = ((distance * 3) % 9 - 4).toDouble()
            val acousticTrend = shiftedTrend(base.acousticTrend ?: TrendSeries.speechRateSample, offset, speechDelta)

            return WeeklyReport(
                state = base.state,
                noticeText = base.noticeText,
                summaryStats = listOf(
                    CallStat(
                        "분석된 통화", "$callCount", "건",
                        StatNote("지난주 ${maxOf(1, callCount - 1 + distance % 3)}건"),
                    ),
                    CallStat(
                        "총 통화 시간", "$totalMinutes", "분",
                        StatNote("지난주 ${maxOf(18, totalMinutes - 4 + distance % 7)}분"),
                    ),
                ),
                changeSignals = sampleSignals(base.changeSignals, relation, distance),
                conversationGroups = rotatedGroups(base.conversationGroups, distance),
                repeatObservation = RepeatObservation(
                    countText = "$repeatCount",
                    perMinuteText = "분당 ${formatOneDecimal(repeatCount.toDouble() / (callCount * 8))}회",
                    caption = base.repeatObservation.caption,
                ),
                acousticTrend = acousticTrend,
                metricTrends = sampleMetricTrends(base.metricTrends, acousticTrend, repeatCount, distance),
                disclaimer = base.disclaimer,
            )
        }

        private fun shiftedTrend(trend: TrendSeries, offset: Int, delta: Double): TrendSeries {
            val count = trend.points.size
            val points = trend.points.mapIndexed { index, point ->
                val pointOffset = offset + index - (count - 1)
                val variation = ((abs(pointOffset) + index) % 3 - 1).toDouble()
                TrendPoint(
                    date = SampleWeek.anchor(pointOffset),
                    label = SampleWeek.title(pointOffset),
                    value = point.value + delta + variation,
                )
            }
            return TrendSeries(trend.metricName, trend.unit, points, trend.normalRange)
        }

        private fun sampleSignals(base: List<ChangeSignalItem>, relation: String?, distance: Int): List<ChangeSignalItem> {
            val motherSummaries = listOf(
                "최근 4주 동안 조금씩 빨라졌어요.",
                "지난주보다 3음절 느려졌어요.",
                "최근 기록이 평소 범위 안에 있어요.",
                "작은 오르내림을 보이며 유지되고 있어요.",
            )
            val fatherSummaries = listOf(
                "최근 4주 동안 비슷하게 유지되고 있어요.",
                "지난주보다 2음절 빨라졌어요.",
                "편안한 속도로 대화하셨어요.",
                "최근 기록 사이 변화가 크지 않아요.",
            )
            val summaries = if (relation == "FATHER") fatherSummaries else motherSummaries
            return base.mapIndexed { index, signal ->
                ChangeSignalItem(
                    metricName = signal.metricName,
                    summary = if (index == 0) summaries[distance % summaries.size] else "지난주와 비슷하게 유지되고 있어요.",
                    tone = if (distance % 3 == 0) signal.tone else SignalTone.Steady,
                    isPromoted = signal.isPromoted,
                )
            }
        }

        private fun rotatedGroups(groups: List<ConversationGroup>, distance: Int): List<ConversationGroup> {
            if (groups.isEmpty()) return emptyList()
            val pivot = distance % groups.size
            return groups.drop(pivot) + groups.take(pivot)
        }

        private fun sampleMetricTrends(
            base: List<MetricTrend>,
            trend: TrendSeries,
            repeatCount: Int,
            distance: Int,
        ): List<MetricTrend> = base.mapIndexed { index, metric ->
            if (index == 0) {
                MetricTrend(
                    label = metric.label,
                    value = "${roundHalfAwayFromZero(trend.latest?.value ?: 0.0)}",
                    unit = metric.unit,
                    values = trend.points.map { it.value },
                    shape = metric.shape,
                )
            } else {
                MetricTrend(
                    label = metric.label,
                    value = "$repeatCount",
                    unit = metric.unit,
                    values = metric.values.mapIndexed { valueIndex, value ->
                        maxOf(0.0, value + ((distance + valueIndex) % 3 - 1).toDouble())
                    },
                    shape = metric.shape,
                )
            }
        }
    }
}

/** Swift `Double.rounded()`: 정확히 .5면 0에서 먼 쪽으로. */
internal fun roundHalfAwayFromZero(value: Double): Long =
    if (value < 0) -floor(-value + 0.5).toLong() else floor(value + 0.5).toLong()

/** Swift `String(format: "%.1f")`: 2진수 실제 값을 반올림하고, 딱 .5면 짝수 쪽으로. */
internal fun formatOneDecimal(value: Double): String =
    java.math.BigDecimal(value).setScale(1, java.math.RoundingMode.HALF_EVEN).toPlainString()

/**
 * iOS `TimelineWeekPage`(타임라인 기능)의 주 계산 중 견본 데이터에 쓰는 부분만 옮긴 대역.
 * 월요일 시작, 첫 주 최소 1일.
 */
private object SampleWeek {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val weekFields = WeekFields.of(DayOfWeek.MONDAY, 1)

    /** `offset`주 뒤(음수는 앞)의 오늘 자정. */
    fun anchor(offset: Int): Instant =
        LocalDate.now(zone).atStartOfDay(zone).plusWeeks(offset.toLong()).toInstant()

    private fun start(offset: Int): LocalDate =
        anchor(offset).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun title(offset: Int): String {
        val start = start(offset)
        return "${start.monthValue}월 ${start.get(weekFields.weekOfMonth())}주"
    }
}
