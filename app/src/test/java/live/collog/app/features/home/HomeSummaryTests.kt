package live.collog.app.features.home

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.models.fromDto
import live.collog.app.features.home.viewmodels.HomeViewModel
import live.collog.app.features.home.views.callDateText
import live.collog.app.features.home.views.callDurationText
import live.collog.app.features.home.views.callTimeText
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

    private val mom = FamilyContact("m1", "mom", "엄마", "MOTHER", "어머니")

    @Test
    fun homeLoadsFirstSummaryAndQuestionsWithoutTrend() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = { request ->
                when (request.url.encodedPath) {
                    "/v1/parents/mom/calls" -> 200 to """{"calls":[]}"""
                    "/v1/parents/mom/baseline" -> 200 to """{"baselines":[]}"""
                    "/v1/parents/mom/reports" -> 200 to """
                        {"parentId":"mom","period":"WEEKLY","from":"2026-09-07","to":"2026-09-13",
                        "state":"BASELINE_COLLECTING","disclaimer":"","promotedSignals":[],"acuteSignals":[],
                        "conversationItems":{"activity":["산책했다고 이야기했어요."]},
                        "repeatObservation":{"count":0,"callsWithRepeat":0,"label":""},
                        "acousticTrends":[],"analyzedCallCount":1}
                    """.trimIndent()
                    else -> error("Unexpected home request ${request.url.encodedPath}")
                }
            }
            val model = HomeViewModel()
            model.refresh(fixture.environment, contact = mom)
            assertEquals(1, model.healthSummary?.conversationGroups?.size)
            assertEquals(1, model.healthFeedback?.followUps?.size)
            assertNull(model.loadError)
            assertTrue(model.isLoaded)
            assertTrue(model.lastCallAt.isEmpty())
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
                    "/v1/parents/mom/calls" -> 200 to """{"calls":[]}"""
                    "/v1/parents/mom/baseline" -> 200 to """{"baselines":[]}"""
                    else -> 500 to """{"code":"SERVER","message":"서버 오류"}"""
                }
            }
            val model = HomeViewModel()
            model.refresh(fixture.environment, contact = mom)
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
            // FamilyContact.samples의 「3일 전 통화」·「그저께 통화」와 같다
            assertEquals("3일 전", HomeViewModel.lastCallDayText(model.lastCallAt["sample-mother"]))
            assertEquals("그저께", HomeViewModel.lastCallDayText(model.lastCallAt["sample-father"]))
            assertTrue(model.recentCalls.isEmpty())
            assertTrue(fixture.paths.isEmpty())
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun lastCallDayCountsCalendarDays() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = Instant.parse("2026-10-24T05:00:00Z") // 서울 14시
        fun text(at: String?) = HomeViewModel.lastCallDayText(at?.let(Instant::parse), now, zone)

        assertEquals("기록 없음", text(null))
        assertEquals("오늘", text("2026-10-23T15:30:00Z")) // 서울 10-24 00:30
        assertEquals("어제", text("2026-10-23T14:30:00Z")) // 서울 10-23 23:30
        assertEquals("그저께", text("2026-10-22T03:00:00Z"))
        assertEquals("5일 전", text("2026-10-19T03:00:00Z"))
        assertEquals("오늘", text("2026-10-25T03:00:00Z")) // 미래도 오늘
    }

    @Test
    fun withoutSelectedContactNothingIsLoaded() = runTest {
        // 예전 부모 계정이어도 자기 기록을 대상으로 삼지 않는다(나의 리포트는 두 사람 분석 뒤에 붙는다)
        val fixture = TestEnvironment(role = "PARENT")
        try {
            val model = HomeViewModel()
            model.refresh(fixture.environment, contact = null)
            assertNull(model.healthSummary)
            assertNull(model.loadError)
            assertTrue(model.isLoaded)
            assertTrue(fixture.paths.isEmpty())
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun loadsLatestCallForEachFamilyMember() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = { request ->
                val path = request.url.encodedPath
                when {
                    // 역할이 같은 구성원(CHILD)도 연락처다
                    path.endsWith("/members") -> 200 to """{"members":[
                        {"memberId":"m1","userId":"mom","name":"엄마","relation":"MOTHER","status":"ACTIVE","role":"PARENT"},
                        {"memberId":"m2","userId":"dad","name":"아빠","relation":"FATHER","status":"ACTIVE","role":"CHILD"}]}"""
                    path.endsWith("/daily-questions") -> 200 to """{"questions":[]}"""
                    path == "/v1/parents/mom/calls" -> 200 to """{"calls":[
                        {"callId":"c1","parentId":"mom","state":"ANALYZED","startedAt":"2026-10-01T03:00:00Z","recorded":true},
                        {"callId":"c2","parentId":"mom","state":"ENDED","startedAt":"2026-10-23T03:00:00Z","recorded":true}]}"""
                    path == "/v1/parents/dad/calls" -> 500 to """{"code":"SERVER","message":"서버 오류"}"""
                    path.endsWith("/baseline") -> 200 to """{"baselines":[]}"""
                    else -> 500 to """{"code":"SERVER","message":"서버 오류"}"""
                }
            }
            fixture.environment.family.refresh(fixture.environment)
            val model = HomeViewModel()
            model.refresh(fixture.environment, fixture.environment.family.selectedContact)
            // 고른 엄마의 기록은 한 번만 받고, 아빠 기록을 못 받으면 아빠는 빠진다
            assertEquals(1, fixture.paths.count { it == "/v1/parents/mom/calls" })
            assertEquals(mapOf("m1" to Instant.parse("2026-10-23T03:00:00Z")), model.lastCallAt)
            assertEquals(2, model.recentCalls.size)
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun legacyParentAccountLoadsEachContactLikeEveryoneElse() = runTest {
        val fixture = TestEnvironment(role = "PARENT")
        try {
            fixture.respond = { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/members") -> 200 to """{"members":[
                        {"memberId":"k1","userId":"son","name":"아들","relation":"SON","status":"ACTIVE","role":"CHILD"},
                        {"memberId":"k2","userId":"daughter","name":"딸","relation":"DAUGHTER","status":"ACTIVE","role":"CHILD"}]}"""
                    path.endsWith("/daily-questions") -> 200 to """{"questions":[]}"""
                    path == "/v1/parents/son/calls" -> 200 to """{"calls":[
                        {"callId":"c1","parentId":"son","state":"ANALYZED","startedAt":"2026-10-20T03:00:00Z","recorded":true}]}"""
                    path == "/v1/parents/daughter/calls" -> 200 to """{"calls":[
                        {"callId":"c2","parentId":"daughter","state":"ANALYZED","startedAt":"2026-10-21T03:00:00Z","recorded":true}]}"""
                    path.endsWith("/baseline") -> 200 to """{"baselines":[]}"""
                    else -> 500 to """{"code":"SERVER","message":"서버 오류"}"""
                }
            }
            fixture.environment.family.refresh(fixture.environment)
            val model = HomeViewModel()
            model.refresh(fixture.environment, fixture.environment.family.selectedContact)
            assertEquals(
                mapOf("k1" to Instant.parse("2026-10-20T03:00:00Z"), "k2" to Instant.parse("2026-10-21T03:00:00Z")),
                model.lastCallAt,
            )
            assertTrue(fixture.paths.none { it.startsWith("/v1/parents/me/") })
            assertEquals("/v1/parents/son/reports", fixture.paths.last { it.endsWith("/reports") })
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun callLogTextUsesKoreanDateAndDuration() {
        val seoul = ZoneId.of("Asia/Seoul")
        val at = Instant.parse("2026-10-27T06:12:00Z") // 서울 10-27 15:12
        assertEquals("10월 27일 화요일", callDateText(at, seoul))
        assertEquals("오후 3:12", callTimeText(at, seoul))
        assertEquals("12분 4초", callDurationText(724))
        assertEquals("1분 0초", callDurationText(60))
        assertEquals("45초", callDurationText(45))
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
