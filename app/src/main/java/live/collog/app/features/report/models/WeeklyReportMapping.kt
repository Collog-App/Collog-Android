package live.collog.app.features.report.models

import java.math.BigDecimal
import java.math.RoundingMode
import live.collog.app.features.report.views.components.MetricTrend
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.services.api.MetricLabel
import live.collog.app.shared.services.api.ReportDto
import live.collog.app.shared.services.api.SignalDto

/** iOS `WeeklyReport.init(dto:trend:)`. 분석된 통화가 없으면 서버 상태와 관계없이 빈 리포트. */
fun WeeklyReport.Companion.fromDto(dto: ReportDto, trend: TrendSeries?): WeeklyReport {
    if (dto.analyzedCallCount <= 0) return empty

    val labels = mapOf("symptom" to "증상", "medication" to "복약", "activity" to "활동", "sleep" to "수면")
    val symbols = mapOf(
        "symptom" to "stethoscope",
        "medication" to "pills",
        "activity" to "figure.walk",
        "sleep" to "moon",
    )
    val groups = listOf("symptom", "medication", "activity", "sleep").mapNotNull { key ->
        val items = dto.conversationItems[key]
        if (items.isNullOrEmpty()) return@mapNotNull null
        ConversationGroup(
            category = labels[key] ?: key,
            symbol = symbols[key] ?: "text.bubble",
            items = items,
        )
    }

    val signals = (dto.promotedSignals.map { it to true } + dto.acuteSignals.map { it to false })
        .map { (signal, promoted) ->
            ChangeSignalItem(
                metricName = MetricLabel.korean(signal.metric),
                summary = summary(signal),
                tone = if (promoted || signal.acute) SignalTone.Watch else SignalTone.Steady,
                isPromoted = promoted,
            )
        }

    val itemCount = dto.conversationItems.values.sumOf { it.size }

    return WeeklyReport(
        state = state(dto.state),
        noticeText = dto.emptyMessage,
        summaryStats = listOf(
            CallStat("분석된 통화", "${dto.analyzedCallCount}", "건", null),
            CallStat("기록된 대화", "$itemCount", "개", null),
        ),
        changeSignals = signals,
        conversationGroups = groups,
        repeatObservation = RepeatObservation(
            countText = "${dto.repeatObservation.count}",
            perMinuteText = "통화 ${dto.repeatObservation.callsWithRepeat}건에서 관찰",
            caption = "대화 중 다시 물어보신 횟수예요",
        ),
        acousticTrend = trend,
        metricTrends = metricTrends(dto),
        disclaimer = dto.disclaimer,
    )
}

private fun metricTrends(dto: ReportDto): List<MetricTrend> {
    val history = dto.recentAcousticHistory ?: dto.acousticTrends
    return history.mapNotNull { item ->
        val values = item.points.mapNotNull { it.value }
        val latest = values.lastOrNull()
        if (values.size < 2 || latest == null) return@mapNotNull null

        MetricTrend(
            label = MetricLabel.korean(item.metric),
            value = "${roundHalfAwayFromZero(latest)}",
            unit = MetricLabel.unit(item.metric),
            values = values,
            shape = if (item.metric == "COUGH_EVENTS") MetricTrend.Shape.Bar else MetricTrend.Shape.Line,
        )
    }
}

private fun state(raw: String): ReportState = when (raw) {
    "EMPTY" -> ReportState.Empty
    "BASELINE_COLLECTING" -> ReportState.BaselineCollecting
    else -> ReportState.Ready
}

private fun summary(signal: SignalDto): String {
    signal.summaryText?.let { return it }
    signal.acuteText?.let { return it }
    val comparison = signal.vsRolling ?: signal.vsAnchor
    if (comparison != null) return "평소 대비 ${signedPercent(comparison.deltaPct)}%"
    return "변화를 관찰하고 있어요"
}

/** Swift `String(format: "%+.0f")`: 항상 부호를 붙이고, 딱 .5면 짝수 쪽으로, 음수가 0이 돼도 `-0`. */
internal fun signedPercent(value: Double): String {
    val negative = value < 0 || (value == 0.0 && 1 / value < 0)
    val digits = BigDecimal(Math.abs(value)).setScale(0, RoundingMode.HALF_EVEN).toPlainString()
    return (if (negative) "-" else "+") + digits
}
