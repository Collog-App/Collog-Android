package live.collog.app.features.home

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.models.fromDto
import live.collog.app.features.home.services.QuestionGenerator
import live.collog.app.features.home.viewmodels.HomeViewModel
import live.collog.app.features.home.views.changeRate
import live.collog.app.features.home.views.fixed1
import live.collog.app.features.home.views.formatNotificationTime
import live.collog.app.features.home.views.interpretation
import live.collog.app.features.home.views.signedPercent1
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.chart.from
import live.collog.app.shared.services.api.AcousticTrendDto
import live.collog.app.shared.services.api.AcousticTrendPointDto
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.services.api.RepeatObservationDto
import live.collog.app.shared.services.api.ReportDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `HomeSummaryTests`. 아래쪽 몇 개는 화면 문구·날짜·숫자 형식이 iOS와 같은지 고정한다. */
class HomeSummaryTests {
    @Test
    fun firstAnalysisShowsConversationWithoutAcousticTrend() {
        val summary = FamilyHealthSummary.fromDto(report(), memberName = "부모님", baseline = null)
        assertNotNull(summary)
        summary!!
        assertNull(summary.trend)
        assertEquals(listOf("산책했다고 이야기했어요."), summary.conversationGroups.first().items)
        assertEquals("1", summary.stats.first().value)
        assertEquals("통화 내용을 먼저 정리했어요", summary.headline)
    }

    @Test
    fun zeroAnalyzedCallsDoNotExposeOldConversationContent() {
        assertNull(FamilyHealthSummary.fromDto(report(count = 0), memberName = "부모님", baseline = null))
    }

    @Test
    fun readyReportWithoutSignalDoesNotAssertOverallHealth() {
        val summary = FamilyHealthSummary.fromDto(report(state = "READY"), memberName = "부모님", baseline = baseline())
        assertEquals("이번 기간의 통화 기록이에요", summary!!.headline)
    }

    @Test
    fun collectingReportStaysNeutralEvenWithReadyMetricBaseline() {
        val summary = FamilyHealthSummary.fromDto(report(), memberName = "부모님", baseline = baseline())
        assertEquals("통화 내용을 먼저 정리했어요", summary!!.headline)
    }

    @Test
    fun chartDoesNotTreatObservedExtremesAsPersonalBaseline() {
        val data = AcousticTrendDto(
            metric = "SPEECH_RATE",
            points = listOf(
                AcousticTrendPointDto(date = "2026-09-12", value = 190.0, unit = null),
                AcousticTrendPointDto(date = "2026-09-13", value = 210.0, unit = null),
            ),
        )
        assertFalse(TrendSeries.from(data, null)!!.hasPersonalBaseline)
        assertTrue(TrendSeries.from(data, baseline())!!.hasPersonalBaseline)
    }

    @Test
    fun homeLoadsFirstSummaryAndQuestionsWithoutTrend() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = { request ->
                when (request.url.encodedPath) {
                    "/v1/parents/me/calls" -> 200 to """{"calls":[]}"""
                    "/v1/parents/me/baseline" -> 200 to """{"baselines":[]}"""
                    "/v1/parents/me/reports" -> 200 to """
                        {"parentId":"me","period":"WEEKLY","from":"2026-09-07","to":"2026-09-13",
                        "state":"BASELINE_COLLECTING","disclaimer":"","promotedSignals":[],"acuteSignals":[],
                        "conversationItems":{"activity":["산책했다고 이야기했어요."]},
                        "repeatObservation":{"count":0,"callsWithRepeat":0,"label":""},
                        "acousticTrends":[],"analyzedCallCount":1}
                    """.trimIndent()
                    else -> error("Unexpected home request ${request.url.encodedPath}")
                }
            }
            val model = HomeViewModel()
            model.refresh(fixture.environment, contact = null)
            assertEquals(1, model.healthSummary?.conversationGroups?.size)
            assertEquals(1, model.healthFeedback?.followUps?.size)
            assertNull(model.loadError)
            assertTrue(model.isLoaded)
            assertEquals("아직 통화 기록이 없어요", model.lastCallText)
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun failedReportShowsErrorAndKeepsLoadedFlag() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = { request ->
                when (request.url.encodedPath) {
                    "/v1/parents/me/calls" -> 200 to """{"calls":[]}"""
                    "/v1/parents/me/baseline" -> 200 to """{"baselines":[]}"""
                    else -> 500 to """{"code":"SERVER","message":"서버 오류"}"""
                }
            }
            val model = HomeViewModel()
            model.refresh(fixture.environment, contact = null)
            assertNull(model.healthSummary)
            assertEquals("서버 오류", model.loadError)
            assertTrue(model.isLoaded)
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun guestModeShowsSamplesWithoutRequests() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.environment.settings.isGuestMode = true
            val model = HomeViewModel()
            model.refresh(fixture.environment, contact = null)
            assertEquals(FamilyHealthSummary.sample, model.healthSummary)
            assertEquals(HealthFeedback.sample, model.healthFeedback)
            assertEquals("최근 통화했어요", model.lastCallText)
            assertTrue(fixture.paths.isEmpty())
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun lastCallTextCountsCalendarDays() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = Instant.parse("2026-10-24T05:00:00Z") // 서울 14시
        fun call(at: String) = CallSummaryDto(
            callId = at, parentId = "me", state = "ANALYZED", startedAt = Instant.parse(at), recorded = true,
        )
        fun text(vararg at: String) = HomeViewModel.lastCallText(at.map(::call), now, zone)

        assertEquals("아직 통화 기록이 없어요", text())
        assertEquals("오늘 통화했어요", text("2026-10-23T15:30:00Z")) // 서울 10-24 00:30
        assertEquals("어제 통화했어요", text("2026-10-23T14:30:00Z")) // 서울 10-23 23:30
        assertEquals("그저께 통화했어요", text("2026-10-22T03:00:00Z"))
        assertEquals("5일 전에 통화했어요", text("2026-10-19T03:00:00Z"))
        assertEquals("오늘 통화했어요", text("2026-10-25T03:00:00Z")) // 미래도 오늘
        assertEquals("어제 통화했어요", text("2026-10-01T03:00:00Z", "2026-10-23T03:00:00Z")) // 가장 최근 통화 기준
    }

    @Test
    fun fallbackQuestionsAreThreeFreshOnes() {
        val first = QuestionGenerator.fallback(emptyList())
        assertEquals(3, first.size)
        assertEquals(3, first.toSet().size)
        val second = QuestionGenerator.fallback(first)
        assertEquals(3, second.size)
        assertTrue(second.none { it in first })
    }

    @Test
    fun percentTextFollowsSwiftRounding() {
        // 값은 iOS에서 String(format: "%+.1f%%") / "%.1f%%"로 직접 뽑았다
        assertEquals("+1.4%", signedPercent1(1.45))
        assertEquals("+0.1%", signedPercent1(0.125))
        assertEquals("+2.2%", signedPercent1(2.25))
        assertEquals("-3.4%", signedPercent1(-3.35))
        assertEquals("+12.0%", signedPercent1(12.0))
        assertEquals("-0.0%", signedPercent1(-0.03))
        assertEquals("+0.0%", signedPercent1(0.0))
        assertEquals("3.4", fixed1(3.35))
    }

    @Test
    fun notificationTimeMatchesIosKoreanNumericShortened() {
        val seoul = ZoneId.of("Asia/Seoul")
        // iOS `date.formatted(date: .numeric, time: .shortened)` (ko_KR, Asia/Seoul)
        assertEquals("1970. 1. 1. 오전 9:00", formatNotificationTime(Instant.ofEpochSecond(0), seoul))
        assertEquals("2026. 10. 15. 오전 2:46", formatNotificationTime(Instant.ofEpochSecond(1_792_000_000), seoul))
        assertEquals("2026. 10. 15. 오후 12:46", formatNotificationTime(Instant.ofEpochSecond(1_792_036_000), seoul))
        assertEquals("2026. 10. 15. 오후 5:51", formatNotificationTime(Instant.ofEpochSecond(1_792_054_300), seoul))
        assertEquals("2026. 10. 14. 오후 11:46", formatNotificationTime(Instant.ofEpochSecond(1_792_000_000 - 3 * 3600), seoul))
    }

    @Test
    fun interpretationExplainsTrendAgainstBaseline() {
        assertEquals("음성 변화를 비교할 기록이 부족해요. 대화 내용은 먼저 확인할 수 있어요.", interpretation(null))

        val data = AcousticTrendDto(
            metric = "SPEECH_RATE",
            points = listOf(
                AcousticTrendPointDto("2026-09-12", 190.0),
                AcousticTrendPointDto("2026-09-13", 210.0),
            ),
        )
        val collecting = TrendSeries.from(data, null)!!
        assertEquals("측정값을 모으고 있어요. 아직 평소와의 차이를 판단하기 어려워요.", interpretation(collecting))
        assertEquals(10.526315789473683, changeRate(collecting)!!, 1e-9)

        val withBaseline = TrendSeries.from(data, baseline())!!
        assertEquals(
            "말씀 속도은 평소 범위 안에 있어요. 첫 기록보다 10.5% 높아졌어요.",
            interpretation(withBaseline),
        )
    }

    private fun baseline() = BaselineDto(
        metric = "SPEECH_RATE", timeSlot = "ALL", kind = "ROLLING", status = "READY",
        sampleCount = 10, requiredCount = 10, median = 200.0, mad = 5.0,
    )

    private fun report(count: Int = 1, state: String = "BASELINE_COLLECTING") = ReportDto(
        parentId = "parent", period = "WEEKLY", from = "2026-09-07", to = "2026-09-13",
        state = state, emptyMessage = null, disclaimer = "", advisory = null,
        promotedSignals = emptyList(), acuteSignals = emptyList(),
        conversationItems = mapOf("activity" to listOf("산책했다고 이야기했어요.")),
        repeatObservation = RepeatObservationDto(count = 0, callsWithRepeat = 0, label = ""),
        acousticTrends = emptyList(), recentAcousticHistory = null, analyzedCallCount = count,
        containsDemoData = false, demoDataNotice = null,
    )
}
