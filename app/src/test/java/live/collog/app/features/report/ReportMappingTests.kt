package live.collog.app.features.report

import live.collog.app.features.report.models.ReportState
import live.collog.app.features.report.models.WeeklyReport
import live.collog.app.features.report.models.fromDto
import live.collog.app.features.report.models.signedPercent
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.services.api.AcousticTrendDto
import live.collog.app.shared.services.api.AcousticTrendPointDto
import live.collog.app.shared.services.api.ComparisonDto
import live.collog.app.shared.services.api.RepeatObservationDto
import live.collog.app.shared.services.api.ReportDto
import live.collog.app.shared.services.api.SignalDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `ReportMappingTests`. */
class ReportMappingTests {
    @Test
    fun noAnalyzedCallsHideReportContent() {
        for (state in listOf("EMPTY", "READY", "BASELINE_COLLECTING")) {
            val report = WeeklyReport.fromDto(makeReport(count = 0, state = state), TrendSeries.speechRateSample)

            assertEquals(state, ReportState.Empty, report.state)
            assertTrue(state, report.summaryStats.isEmpty())
            assertTrue(state, report.conversationGroups.isEmpty())
            assertTrue(state, report.metricTrends.isEmpty())
            assertNull(state, report.acousticTrend)
        }
    }

    @Test
    fun analyzedCallsPreserveZeroMeasurements() {
        val report = WeeklyReport.fromDto(makeReport(count = 1, state = "READY"), null)

        assertEquals(ReportState.Ready, report.state)
        assertEquals("1", report.summaryStats.first().value)
        assertEquals("0", report.repeatObservation.countText)
        assertEquals(listOf(0.0, 0.0), report.metricTrends.first().values)
        assertEquals("0", report.metricTrends.first().value)
    }

    @Test
    fun analyzedCallsKeepBaselineCollectionState() {
        val report = WeeklyReport.fromDto(makeReport(count = 1, state = "BASELINE_COLLECTING"), null)

        assertEquals(ReportState.BaselineCollecting, report.state)
        assertFalse(report.summaryStats.isEmpty())
    }

    // 아래는 iOS 테스트에 없는 보강: Swift `String(format: "평소 대비 %+.0f%%")` 결과를 swift 6.4에서 직접 돌려 고정했다.
    @Test
    fun signedPercentMatchesSwiftFormat() {
        val expected = mapOf(
            2.5 to "+2", 3.5 to "+4", -2.5 to "-2", -0.4 to "-0", 0.5 to "+0",
            12.0 to "+12", -1.6 to "-2", 7.45 to "+7",
        )
        expected.forEach { (value, text) -> assertEquals("$value", text, signedPercent(value)) }
    }

    @Test
    fun signalSummaryFallsBackToComparisonThenDefault() {
        fun signal(summary: String? = null, acute: String? = null, rolling: ComparisonDto? = null, anchor: ComparisonDto? = null) =
            SignalDto("s", "SPEECH_RATE", "MORNING", 2, true, false, summary, acute, anchor, rolling)

        val rolling = ComparisonDto(deltaPct = -12.5, direction = "DOWN", robustZ = 1.0, significant = true)
        val anchor = ComparisonDto(deltaPct = 4.0, direction = "UP", robustZ = 1.0, significant = true)
        val dto = makeReport(count = 1, state = "READY").copy(
            promotedSignals = listOf(signal(summary = "문장"), signal(acute = "급변"), signal(rolling = rolling, anchor = anchor)),
            acuteSignals = listOf(signal(anchor = anchor), signal()),
        )

        val summaries = WeeklyReport.fromDto(dto, null).changeSignals.map { it.summary }

        assertEquals(listOf("문장", "급변", "평소 대비 -12%", "평소 대비 +4%", "변화를 관찰하고 있어요"), summaries)
    }

    private fun makeReport(count: Int, state: String) = ReportDto(
        parentId = "parent", period = "WEEK", from = "2026-09-07", to = "2026-09-13",
        state = state, emptyMessage = null, disclaimer = "", advisory = null,
        promotedSignals = emptyList(), acuteSignals = emptyList(), conversationItems = mapOf("activity" to listOf("산책")),
        repeatObservation = RepeatObservationDto(count = 0, callsWithRepeat = 0, label = ""),
        acousticTrends = listOf(
            AcousticTrendDto(
                "COUGH_EVENTS",
                listOf(
                    AcousticTrendPointDto("2026-09-12", 0.0, "회"),
                    AcousticTrendPointDto("2026-09-13", 0.0, "회"),
                ),
            ),
        ),
        recentAcousticHistory = null, analyzedCallCount = count,
        containsDemoData = false, demoDataNotice = null,
    )
}
