package live.collog.app.features.report

import java.time.Instant
import live.collog.app.features.report.models.RepeatObservation
import live.collog.app.features.report.models.WeeklyReport
import live.collog.app.features.report.models.withCallStats
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.services.api.CallSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 새 피그마 05·07: 총 통화 시간·지난주 비교·분당 되물음은 통화 목록으로 앱에서 계산한다. */
class ReportCallStatsTests {
    private fun call(seconds: Int?, state: String = "ANALYZED") = CallSummaryDto(
        callId = "c$seconds$state", parentId = "p", state = state,
        startedAt = Instant.parse("2026-11-05T03:00:00Z"), durationSec = seconds, recorded = true,
    )

    private val report = WeeklyReport.sample.copy(
        summaryStats = listOf(CallStat("분석된 통화", "3", "건", null)),
        repeatObservation = RepeatObservation("3", "", "대화 중 다시 물어보신 횟수예요"),
    )

    @Test
    fun totalsAndPreviousWeekComeFromAnalyzedCalls() {
        val result = report.withCallStats(
            calls = listOf(call(600), call(660), call(660), call(300, "ENDED")),
            previous = listOf(call(840), call(840), call(null, "ENDED")),
        )
        assertEquals(listOf("분석된 통화", "총 통화 시간"), result.summaryStats.map { it.label })
        assertEquals("32", result.summaryStats[1].value)
        assertEquals("분", result.summaryStats[1].unit)
        assertEquals("지난주 2건", result.summaryStats[0].note?.text)
        assertEquals("지난주 28분", result.summaryStats[1].note?.text)
        // 3회 / 31.67분
        assertEquals("분당 0.1회", result.repeatObservation.perMinuteText)
    }

    @Test
    fun noPreviousWeekMeansNoComparisonText() {
        val none = report.withCallStats(listOf(call(600)), previous = null)
        assertNull(none.summaryStats[0].note)
        assertNull(none.summaryStats[1].note)
        val empty = report.withCallStats(listOf(call(600)), previous = emptyList())
        assertNull(empty.summaryStats[0].note)
    }

    @Test
    fun shortOrMissingCallTimeHidesPerMinuteAndTotal() {
        val short = report.withCallStats(listOf(call(null), call(40)), previous = emptyList())
        assertEquals("", short.repeatObservation.perMinuteText)
        val missing = report.withCallStats(listOf(call(null)), previous = emptyList())
        assertEquals(listOf("분석된 통화"), missing.summaryStats.map { it.label })
        assertEquals("", missing.repeatObservation.perMinuteText)
    }
}
