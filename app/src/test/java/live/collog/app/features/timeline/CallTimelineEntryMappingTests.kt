package live.collog.app.features.timeline

import java.time.Instant
import live.collog.app.features.timeline.models.CallAnalysisBundle
import live.collog.app.features.timeline.models.CallTimelineEntry
import live.collog.app.features.timeline.models.fromBundle
import live.collog.app.shared.components.chart.KeywordTone
import live.collog.app.shared.components.stat.StatTrend
import live.collog.app.shared.services.api.AcousticFeatureDto
import live.collog.app.shared.services.api.AcousticFeaturesDto
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.services.api.ExtractionDto
import live.collog.app.shared.services.api.RepeatEventDto
import live.collog.app.shared.services.api.TranscriptDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `CallTimelineEntry+Mapping`. 기대값 중 부동소수점 값은 같은 식을 Swift로 돌려 얻었다. */
class CallTimelineEntryMappingTests {
    private fun call(durationSec: Int? = 867) = CallSummaryDto(
        callId = "call-1",
        parentId = "me",
        state = "ANALYZED",
        timeSlot = null,
        startedAt = Instant.parse("2026-08-14T03:00:00Z"),
        endedAt = null,
        durationSec = durationSec,
        recorded = true,
    )

    private fun feature(metric: String, value: Double?, unit: String = "", status: String = "OK") =
        AcousticFeatureDto(metric = metric, value = value, unit = unit, status = status)

    private fun baseline(metric: String, median: Double?, mad: Double?, status: String = "READY") = BaselineDto(
        metric = metric, timeSlot = "ALL", kind = "ROLLING", status = status,
        sampleCount = 10, requiredCount = 5, median = median, mad = mad,
    )

    private fun bundle(
        features: List<AcousticFeatureDto>? = emptyList(),
        extraction: ExtractionDto? = null,
        transcript: TranscriptDto? = null,
        durationSec: Int? = 867,
    ) = CallAnalysisBundle(
        call = call(durationSec),
        features = features?.let { AcousticFeaturesDto("call-1", it) },
        extraction = extraction,
        transcript = transcript,
    )

    private fun transcript(vararg startMs: Int, count: Int = startMs.size) = TranscriptDto(
        callId = "call-1",
        excluded = false,
        parentSpeechSec = 100,
        repeatEvents = startMs.map { RepeatEventDto(it, it + 500, "REPEAT", "네?") },
        repeatRequestCount = count,
        repeatRequestsPerMinute = 0.5,
    )

    @Test
    fun headerAndDurationUseCallSummary() {
        val entry = CallTimelineEntry.fromBundle(bundle(), emptyMap())
        // 2026-08-14T03:00Z 는 한국 시간 낮 12시라 같은 날이다. 시간대가 달라도 날짜 문자열 형식은 같다.
        assertTrue(entry.dateText.matches(Regex("""\d+월 \d+일 .요일""")))
        assertEquals("14분 27초", entry.durationText)
        assertEquals(1, entry.summaryStats.size)
        assertEquals("통화 길이", entry.summaryStats[0].label)
        assertEquals("14", entry.summaryStats[0].value)
        assertEquals("분", entry.summaryStats[0].unit)
        assertNull(entry.summaryStats[0].note)
    }

    @Test
    fun missingDurationIsZero() {
        val entry = CallTimelineEntry.fromBundle(bundle(durationSec = null), emptyMap())
        assertEquals("0초", entry.durationText)
        assertEquals("0", entry.summaryStats[0].value)
        assertTrue(entry.keywords.isEmpty())
    }

    @Test
    fun speechRateNoteComparesToBaselineMedian() {
        val cases = listOf(
            Triple(215.4, "7.7%", StatTrend.Up) to "215",
            Triple(190.0, "5.0%", StatTrend.Down) to "190",
            Triple(200.4, "0.2%", StatTrend.Flat) to "200",
            Triple(208.5, "4.2%", StatTrend.Up) to "209",
        )
        for ((expected, rounded) in cases) {
            val (value, text, trend) = expected
            val entry = CallTimelineEntry.fromBundle(
                bundle(features = listOf(feature("SPEECH_RATE", value, "음절/분"))),
                mapOf("SPEECH_RATE" to baseline("SPEECH_RATE", 200.0, 10.0)),
            )
            val stat = entry.summaryStats[1]
            assertEquals("말씀 속도", stat.label)
            assertEquals(rounded, stat.value)
            assertEquals("음절/분", stat.unit)
            assertEquals(text, stat.note?.text)
            assertEquals(trend, stat.note?.trend)
        }
    }

    @Test
    fun speechRateWithoutReadyBaselineHasNoNote() {
        val features = listOf(feature("SPEECH_RATE", 210.0, "음절/분"))
        val notReady = CallTimelineEntry.fromBundle(
            bundle(features = features),
            mapOf("SPEECH_RATE" to baseline("SPEECH_RATE", 200.0, 10.0, status = "COLLECTING")),
        )
        val missing = CallTimelineEntry.fromBundle(bundle(features = features), emptyMap())
        val zeroMedian = CallTimelineEntry.fromBundle(
            bundle(features = features),
            mapOf("SPEECH_RATE" to baseline("SPEECH_RATE", 0.0, 10.0)),
        )
        for (entry in listOf(notReady, missing, zeroMedian)) {
            assertEquals("210", entry.summaryStats[1].value)
            assertNull(entry.summaryStats[1].note)
        }
    }

    @Test
    fun unmeasuredFeaturesAreIgnored() {
        val entry = CallTimelineEntry.fromBundle(
            bundle(
                features = listOf(
                    feature("SPEECH_RATE", 210.0, "음절/분", status = "UNMEASURABLE"),
                    feature("COUGH_EVENTS", null, "회"),
                ),
            ),
            emptyMap(),
        )
        assertEquals(1, entry.summaryStats.size)
        assertTrue(entry.counts.isEmpty())
    }

    @Test
    fun coughComesBeforeRepeatRequests() {
        val entry = CallTimelineEntry.fromBundle(
            bundle(
                features = listOf(feature("COUGH_EVENTS", 3.5, "회")),
                transcript = transcript(1000, 2000, count = 4),
            ),
            emptyMap(),
        )
        assertEquals(listOf("기침", "되물으심"), entry.counts.map { it.label })
        // Swift `rounded()`: .5는 0에서 먼 쪽
        assertEquals(listOf("4", "4"), entry.counts.map { it.value })
        assertEquals(listOf("회", "회"), entry.counts.map { it.unit })
    }

    @Test
    fun repeatRequestsAloneStillShowCount() {
        val entry = CallTimelineEntry.fromBundle(bundle(transcript = transcript(count = 0)), emptyMap())
        assertEquals(1, entry.counts.size)
        assertEquals("0", entry.counts[0].value)
    }

    @Test
    fun storyJoinsExtractionLinesOrFallsBack() {
        val joined = CallTimelineEntry.fromBundle(
            bundle(extraction = ExtractionDto("call-1", "OK", symptom = "허리가 아프다", sleep = "잘 주무셨다")),
            emptyMap(),
        )
        assertEquals("허리가 아프다\n잘 주무셨다", joined.story)

        val empty = CallTimelineEntry.fromBundle(bundle(extraction = ExtractionDto("call-1", "OK")), emptyMap())
        assertEquals("이번 통화에서는 기록된 건강 이야기가 없었어요.", empty.story)

        val none = CallTimelineEntry.fromBundle(bundle(extraction = null), emptyMap())
        assertEquals("아직 정리된 대화 항목이 없어요.", none.story)
    }

    @Test
    fun keywordsKeepFirstEightWithPositionsClamped() {
        val starts = intArrayOf(0, 86_700, 173_400, 260_100, 346_800, 433_500, 520_200, 606_900, 693_600, 900_000)
        val entry = CallTimelineEntry.fromBundle(bundle(transcript = transcript(*starts)), emptyMap())
        assertEquals(8, entry.keywords.size)
        assertEquals(listOf("되물음", "되물음"), entry.keywords.mapNotNull { it.label })
        assertTrue(entry.keywords.drop(2).all { it.label == null })
        assertTrue(entry.keywords.all { it.tone == KeywordTone.Caution })
        assertEquals(0.0, entry.keywords[0].position, 1e-12)
        assertEquals(0.1, entry.keywords[1].position, 1e-9)

        val beyond = CallTimelineEntry.fromBundle(bundle(transcript = transcript(2_000_000)), emptyMap())
        assertEquals(1.0, beyond.keywords[0].position, 0.0)
    }

    @Test
    fun noTranscriptMeansNoKeywords() {
        assertTrue(CallTimelineEntry.fromBundle(bundle(), emptyMap()).keywords.isEmpty())
    }

    @Test
    fun gaugesNormalizeAroundBaseline() {
        val baselines = mapOf(
            "PAUSE_RATIO" to baseline("PAUSE_RATIO", 0.2, 0.05),
            "F0_VARIATION" to baseline("F0_VARIATION", 5.0, 1.0),
        )
        val entry = CallTimelineEntry.fromBundle(
            bundle(features = listOf(feature("PAUSE_RATIO", 0.45, "%"), feature("F0_VARIATION", 0.5, "semitone"))),
            baselines,
        )
        assertEquals(listOf("말 사이 쉼", "목소리 높낮이"), entry.gauges.map { it.label })

        val pause = entry.gauges[0]
        assertEquals(0.33333333333333337, pause.normalRange.start, 1e-12)
        assertEquals(0.6666666666666666, pause.normalRange.endInclusive, 1e-12)
        assertEquals(0.8747222222222222, pause.marker, 1e-12)
        assertEquals("평소보다 큼", pause.caption)

        val pitch = entry.gauges[1]
        assertEquals(0.16275, pitch.marker, 1e-12)
        assertEquals("평소보다 작음", pitch.caption)
    }

    @Test
    fun gaugeWithinSpreadIsNormalAndMarkerIsClamped() {
        val within = CallTimelineEntry.fromBundle(
            bundle(features = listOf(feature("PAUSE_RATIO", 0.3, "%"))),
            mapOf("PAUSE_RATIO" to baseline("PAUSE_RATIO", 0.2, 0.05)),
        )
        assertEquals("평소 범위 안", within.gauges[0].caption)
        assertEquals(0.6498888888888888, within.gauges[0].marker, 1e-12)

        val clamped = CallTimelineEntry.fromBundle(
            bundle(features = listOf(feature("F0_VARIATION", 20.0, "semitone"))),
            mapOf("F0_VARIATION" to baseline("F0_VARIATION", 5.0, 1.0)),
        )
        assertEquals(1.0, clamped.gauges[0].marker, 0.0)
    }

    @Test
    fun gaugesNeedMeasuredFeatureAndReadyBaselineWithSpread() {
        val features = listOf(feature("PAUSE_RATIO", 0.3, "%"))
        val cases = listOf(
            emptyMap(),
            mapOf("PAUSE_RATIO" to baseline("PAUSE_RATIO", 0.2, 0.05, status = "COLLECTING")),
            mapOf("PAUSE_RATIO" to baseline("PAUSE_RATIO", 0.2, 0.0)),
            mapOf("PAUSE_RATIO" to baseline("PAUSE_RATIO", 0.2, null)),
        )
        for (baselines in cases) {
            assertTrue(CallTimelineEntry.fromBundle(bundle(features = features), baselines).gauges.isEmpty())
        }
        val unmeasured = bundle(features = listOf(feature("PAUSE_RATIO", 0.3, "%", status = "UNMEASURABLE")))
        val ready = mapOf("PAUSE_RATIO" to baseline("PAUSE_RATIO", 0.2, 0.05))
        assertTrue(CallTimelineEntry.fromBundle(unmeasured, ready).gauges.isEmpty())
    }
}
