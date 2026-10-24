package live.collog.app.features.home.models

import live.collog.app.features.report.models.WeeklyReport
import live.collog.app.features.report.models.fromDto
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.chart.from
import live.collog.app.shared.services.api.ApiFormat
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.MetricLabel
import live.collog.app.shared.services.api.ReportDto

/** iOS `FamilyHealthSummary.init?(dto:memberName:baseline:)`. 분석된 통화가 없으면 null. */
fun FamilyHealthSummary.Companion.fromDto(
    dto: ReportDto,
    memberName: String,
    baseline: BaselineDto?,
): FamilyHealthSummary? {
    if (dto.analyzedCallCount <= 0) return null
    val history = dto.recentAcousticHistory ?: dto.acousticTrends
    val trend = history.firstOrNull { it.metric == "SPEECH_RATE" }
        ?.let { TrendSeries.from(it, baseline) }
    val report = WeeklyReport.fromDto(dto, trend)
    val signal = dto.promotedSignals.firstOrNull() ?: dto.acuteSignals.firstOrNull()
    val headline: String
    val detail: String
    if (signal != null) {
        headline = MetricLabel.korean(signal.metric) + "에 변화가 관찰됐어요"
        detail = signal.summaryText ?: signal.acuteText ?: "통화에서 측정한 값의 변화예요."
    } else if (dto.state == "BASELINE_COLLECTING" || baseline?.isReady != true) {
        headline = "통화 내용을 먼저 정리했어요"
        detail = "평소와 비교할 음성 기록을 모으고 있어요."
    } else {
        headline = "이번 기간의 통화 기록이에요"
        detail = "분석한 ${dto.analyzedCallCount}건의 통화에서 나눈 이야기를 확인해보세요."
    }
    return FamilyHealthSummary(
        memberName = memberName,
        periodText = ApiFormat.shortRange(dto.from, dto.to),
        headline = headline,
        detail = detail,
        trend = trend,
        stats = report.summaryStats,
        conversationGroups = report.conversationGroups,
    )
}
