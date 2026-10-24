package live.collog.app.features.home.models

import live.collog.app.features.report.models.ConversationGroup
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.components.stat.StatNote
import live.collog.app.shared.components.stat.StatTrend

/** iOS `FamilyHealthSummary`. */
data class FamilyHealthSummary(
    val memberName: String,
    val periodText: String,
    val headline: String,
    val detail: String,
    val trend: TrendSeries?,
    val stats: List<CallStat>,
    val conversationGroups: List<ConversationGroup> = emptyList(),
) {
    companion object {
        val sample: FamilyHealthSummary by lazy {
            FamilyHealthSummary(
                memberName = "어머니",
                periodText = "최근 6주",
                headline = "말씀 속도가 평소 범위 안이에요",
                detail = "6주 동안 큰 변화 없이 유지되고 있어요.",
                trend = TrendSeries.speechRateSample,
                stats = CallStat.samples,
            )
        }

        fun sample(contact: FamilyContact?): FamilyHealthSummary {
            if (contact?.relation != "FATHER") return sample
            return FamilyHealthSummary(
                memberName = contact.name,
                periodText = "최근 6주",
                headline = "말씀 속도가 편안하게 유지되고 있어요",
                detail = "최근 기록에서 작은 변화만 보여요.",
                trend = TrendSeries.speechRateFatherSample,
                stats = listOf(
                    CallStat("통화 길이", "11", "분", StatNote("평소 10분")),
                    CallStat("말씀 속도", "194", "음절/분", StatNote("2.11%", StatTrend.Up)),
                ),
            )
        }
    }
}
