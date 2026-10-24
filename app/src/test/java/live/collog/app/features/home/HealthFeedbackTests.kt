package live.collog.app.features.home

import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.models.fromDto
import live.collog.app.shared.services.api.RepeatObservationDto
import live.collog.app.shared.services.api.ReportDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `HealthFeedbackTests`. */
class HealthFeedbackTests {
    @Test
    fun firstAnalyzedCallProvidesQuestionsWithoutTrendsOrAdvisory() {
        val feedback = HealthFeedback.fromDto(
            report(
                mapOf(
                    "sleep" to listOf("잠들기까지 한 시간이 걸렸다고 이야기했어요."),
                    "activity" to listOf("어제 산책했다고 이야기했어요."),
                ),
            ),
        )
        assertNotNull(feedback)
        feedback!!

        assertEquals(listOf("활동", "수면"), feedback.followUps.map { it.category })
        assertEquals("잠들기까지 한 시간이 걸렸다고 이야기했어요.", feedback.followUps.last().summary)
        assertEquals("최근 통화에서 나눈 수면 이야기는 요즘 어때요?", feedback.followUps.last().question)
        assertEquals("최근 통화에서 나눈 활동 이야기에서 달라진 점이 있어요?", feedback.followUps.first().question)
    }

    @Test
    fun absentTopicsDoNotProducePersonalizedAdvice() {
        val feedback = HealthFeedback.fromDto(report(emptyMap()))
        assertNotNull(feedback)
        feedback!!

        assertTrue(feedback.followUps.isEmpty())
        assertEquals("다시 물어볼 대화 내용이 아직 없어요", feedback.headline)
    }

    @Test
    fun unrecognizedAndBlankTopicsAreIgnoredAndDuplicatesRemoved() {
        val feedback = HealthFeedback.fromDto(
            report(
                mapOf(
                    "sleep" to listOf("  밤에 자주 깼다고 이야기했어요.  ", "밤에 자주 깼다고 이야기했어요.", " \n"),
                    "unknown" to listOf("서버에서 추가한 미지원 항목"),
                ),
            ),
        )
        assertNotNull(feedback)
        feedback!!

        assertEquals(1, feedback.followUps.size)
        assertEquals("밤에 자주 깼다고 이야기했어요.", feedback.followUps.first().summary)
    }

    @Test
    fun noAnalysisDoesNotUseStaleConversationItems() {
        assertNull(HealthFeedback.fromDto(report(mapOf("sleep" to listOf("이전 대화")), count = 0)))
    }

    @Test
    fun symptomAndMedicationQuestionsDoNotAssumeTreatmentChanges() {
        val feedback = HealthFeedback.fromDto(
            report(
                mapOf(
                    "symptom" to listOf("몸 상태를 이야기했어요."),
                    "medication" to listOf("복약 시간을 이야기했어요."),
                ),
            ),
        )
        assertNotNull(feedback)
        feedback!!

        assertEquals("최근 통화에서 나눈 몸 상태 이야기는 요즘 어때요?", feedback.followUps.first().question)
        assertEquals("최근 통화에서 나눈 약 이야기에서 더 알려주실 내용이 있어요?", feedback.followUps.last().question)
    }

    @Test
    fun tagsCarryTheReportRange() {
        val feedback = HealthFeedback.fromDto(report(emptyMap()))!!
        assertEquals(listOf("대화 요약", "9/7 ~ 9/13"), feedback.tags)
        assertEquals("다음 통화에서 물어보기", feedback.title)
    }

    private fun report(items: Map<String, List<String>>, count: Int = 1) = ReportDto(
        parentId = "parent", period = "WEEK", from = "2026-09-07", to = "2026-09-13",
        state = "BASELINE_COLLECTING", emptyMessage = null, disclaimer = "", advisory = null,
        promotedSignals = emptyList(), acuteSignals = emptyList(), conversationItems = items,
        repeatObservation = RepeatObservationDto(count = 0, callsWithRepeat = 0, label = ""),
        acousticTrends = emptyList(), recentAcousticHistory = null, analyzedCallCount = count,
        containsDemoData = false, demoDataNotice = null,
    )
}
