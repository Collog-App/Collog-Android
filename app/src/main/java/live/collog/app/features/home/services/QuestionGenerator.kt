package live.collog.app.features.home.services

/**
 * iOS `QuestionGenerator`. iOS는 기기 안 언어 모델(FoundationModels)을 먼저 쓰고 안 되면 [fallback]을 쓴다.
 * Android에는 같은 모델이 없어 언제나 [fallback]만 쓴다.
 */
object QuestionGenerator {
    @Suppress("RedundantSuspendModifier")
    suspend fun generate(memberName: String, excluding: List<String>): List<String> = fallback(excluding)

    fun fallback(excluding: List<String>): List<String> {
        val pool = listOf(
            "이번 주에 가장 즐거웠던 일은 무엇인가요?",
            "요즘 자주 드시는 반찬은 무엇인가요?",
            "오늘 날씨는 어떻게 느껴지셨어요?",
            "최근에 보고 싶은 사람은 누구인가요?",
            "다음에 함께 먹고 싶은 음식이 있으신가요?",
            "이번 주에 푹 주무신 날이 있었나요?",
            "요즘 집에서 즐겨 하시는 일은 무엇인가요?",
            "오늘 몸을 가볍게 움직이셨나요?",
            "최근에 많이 웃으셨던 일이 있었나요?",
        )
        val fresh = pool.filter { it !in excluding }
        val source = if (fresh.size >= 3) fresh else pool
        return source.shuffled().take(3)
    }
}
