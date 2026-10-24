package live.collog.app.features.home.models

import live.collog.app.shared.services.api.ApiFormat
import live.collog.app.shared.services.api.ReportDto

/** iOS `ConversationFollowUp`. */
data class ConversationFollowUp(
    val category: String,
    val summary: String,
    val question: String,
) {
    val id: String get() = category + "\n" + summary
}

/** iOS `HealthFeedback`. */
data class HealthFeedback(
    val headline: String,
    val tags: List<String>,
    val followUps: List<ConversationFollowUp>,
) {
    val title: String get() = "다음 통화에서 물어보기"

    companion object {
        private val topics = listOf(
            Triple("symptom", "몸 상태", "최근 통화에서 나눈 몸 상태 이야기는 요즘 어때요?"),
            Triple("medication", "복약", "최근 통화에서 나눈 약 이야기에서 더 알려주실 내용이 있어요?"),
            Triple("activity", "활동", "최근 통화에서 나눈 활동 이야기에서 달라진 점이 있어요?"),
            Triple("sleep", "수면", "최근 통화에서 나눈 수면 이야기는 요즘 어때요?"),
        )

        /** iOS `HealthFeedback.init?(dto:)`. 분석된 통화가 없으면 null. */
        fun fromDto(dto: ReportDto): HealthFeedback? {
            if (dto.analyzedCallCount <= 0) return null
            val followUps = topics.flatMap { (key, label, question) ->
                val seen = mutableSetOf<String>()
                (dto.conversationItems[key] ?: emptyList()).mapNotNull { text ->
                    // Swift trimmingCharacters(.whitespacesAndNewlines)
                    val summary = text.trim()
                    if (summary.isEmpty() || !seen.add(summary)) null
                    else ConversationFollowUp(category = label, summary = summary, question = question)
                }
            }
            return HealthFeedback(
                headline = followUps.firstOrNull()?.let { "지난 ${it.category} 이야기를 이어가 보세요" }
                    ?: "다시 물어볼 대화 내용이 아직 없어요",
                tags = listOf("대화 요약", ApiFormat.shortRange(dto.from, dto.to)),
                followUps = followUps,
            )
        }

        val sample = HealthFeedback(
            headline = "지난 수면 이야기를 이어가 보세요",
            tags = listOf("예시 대화", "3일 전 통화"),
            followUps = listOf(
                ConversationFollowUp(
                    category = "수면",
                    summary = "밤에 잠들기까지 시간이 걸렸다고 이야기했어요.",
                    question = "최근 통화에서 나눈 수면 이야기는 요즘 어때요?",
                ),
            ),
        )

        fun sample(contact: FamilyContact?): HealthFeedback {
            if (contact?.relation != "FATHER") return sample
            return HealthFeedback(
                headline = "지난 활동 이야기를 이어가 보세요",
                tags = listOf("예시 대화", "그저께 통화"),
                followUps = listOf(
                    ConversationFollowUp(
                        category = "활동",
                        summary = "집 근처에서 산책했다고 이야기했어요.",
                        question = "최근 통화에서 나눈 활동 이야기에서 달라진 점이 있어요?",
                    ),
                ),
            )
        }
    }
}
