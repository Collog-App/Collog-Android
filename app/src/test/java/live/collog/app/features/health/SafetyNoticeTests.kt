package live.collog.app.features.health

import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.report.models.WeeklyReport
import live.collog.app.features.report.models.fromDto
import live.collog.app.features.timeline.models.CallAnalysis
import live.collog.app.features.timeline.models.CallAnalysisBundle
import live.collog.app.features.timeline.models.fromBundle
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.ExtractionDto
import live.collog.app.shared.services.api.ReportDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 가드레일 약속(2026-11-08)의 응급 안내: 리포트 `safetyNotices`, 통화 extraction `safetyNotice`. 필드가 없어도 된다. */
class SafetyNoticeTests {
    private val notice = "통화 중에 바로 확인이 필요할 수 있는 말씀이 있었어요. 지금 상태를 직접 확인하고, 필요하면 119나 가까운 병원에 연락하세요."

    private fun report(extra: String, count: Int = 1) =
        CollogJson.decodeFromString(
            ReportDto.serializer(),
            """
            {"parentId":"mom","period":"WEEKLY","from":"2026-11-02","to":"2026-11-08","state":"READY",
             "disclaimer":"","promotedSignals":[],"acuteSignals":[],"conversationItems":{},
             "repeatObservation":{"count":0,"callsWithRepeat":0,"label":""},"acousticTrends":[],
             "analyzedCallCount":$count $extra}
            """.trimIndent(),
        )

    @Test
    fun reportWithoutSafetyNoticesStillDecodes() {
        val dto = report("")
        assertTrue(dto.safetyNotices.isEmpty())
        assertTrue(WeeklyReport.fromDto(dto, null).safetyNotices.isEmpty())
    }

    @Test
    fun reportSafetyNoticesKeepServerTextAndCallDay() {
        val dto = report(
            """, "safetyNotices":[{"callId":"call-1","observedAt":"2026-11-06T23:30:00Z","text":"$notice"},
                                {"callId":"call-2","observedAt":"알 수 없음","text":"$notice"},
                                {"callId":"call-3","observedAt":null,"text":"  "}]""",
        )
        // 기기 시간대가 달라도(로스앤젤레스면 11월 6일 오후) 통화 날짜는 서울 기준이다
        val original = TimeZone.getDefault()
        val notices = try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            WeeklyReport.fromDto(dto, null).safetyNotices
        } finally {
            TimeZone.setDefault(original)
        }

        assertEquals(listOf("call-1", "call-2"), notices.map { it.callId })
        assertEquals(notice, notices[0].text)
        assertEquals("11월 7일 통화", notices[0].dayText) // 서울 아침 8시 반
        assertNull(notices[1].dayText)
    }

    @Test
    fun noticeStaysEvenWhenTheWeekHasNoAnalyzedCall() {
        val dto = report(""", "safetyNotices":[{"callId":"call-1","observedAt":null,"text":"$notice"}]""", count = 0)
        assertEquals(1, WeeklyReport.fromDto(dto, null).safetyNotices.size)
    }

    @Test
    fun callAnalysisShowsExtractionSafetyNotice() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = {
                200 to """{"callId":"call-1","parseStatus":"OK","facts":[],"safetyNotice":"$notice"}"""
            }
            val extraction = fixture.environment.api.extraction("call-1")
            assertEquals(notice, extraction.safetyNotice)

            val call = CallSummaryDto(
                callId = "call-1",
                parentId = "mom",
                state = "ANALYZED",
                startedAt = Instant.parse("2026-11-05T03:00:00Z"),
                recorded = true,
            )
            val analysis = CallAnalysis.fromBundle(CallAnalysisBundle(call, null, extraction, null), emptyMap())
            assertEquals(notice, analysis.safetyNotice)

            val quiet = CallAnalysis.fromBundle(
                CallAnalysisBundle(call, null, ExtractionDto("call-1", "OK"), null),
                emptyMap(),
            )
            assertNull(quiet.safetyNotice)
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun parentHealthDailyAsksForTheRange() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = { 200 to """{"parentId":"mom","connected":false,"days":[]}""" }
            val dto = fixture.environment.api.parentHealthDaily("mom", from = "2026-10-10", to = "2026-11-08")

            val request = fixture.requests.single()
            assertEquals("/v1/parents/mom/health/daily", request.url.encodedPath)
            assertEquals("2026-10-10", request.url.queryParameter("from"))
            assertEquals("2026-11-08", request.url.queryParameter("to"))
            assertEquals(false, dto.connected)
        } finally {
            fixture.cleanUp()
        }
    }
}
