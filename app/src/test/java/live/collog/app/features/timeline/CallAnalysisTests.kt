package live.collog.app.features.timeline

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.timeline.models.CallAnalysis
import live.collog.app.features.timeline.models.CallAnalysisBundle
import live.collog.app.features.timeline.models.CallAnalysisState
import live.collog.app.features.timeline.models.fromBundle
import live.collog.app.features.timeline.viewmodels.CallAnalysisViewModel
import live.collog.app.shared.services.api.AcousticFeatureDto
import live.collog.app.shared.services.api.AcousticFeaturesDto
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.services.api.ExtractionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 피그마 「최종본」 통화 분석(269:5235). iOS에는 없는 화면이라 옮길 테스트가 없다. */
class CallAnalysisTests {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-11-05T03:00:00Z") // 한국 시간 11월 5일 낮 12시

    private fun call(startedAt: Instant = now) = CallSummaryDto(
        callId = "call-1",
        parentId = "mom",
        state = "ANALYZED",
        startedAt = startedAt,
        durationSec = 600,
        recorded = true,
    )

    private fun cough(value: Double?, status: String = "OK") =
        AcousticFeatureDto(metric = "COUGH_EVENTS", value = value, unit = "회", status = status)

    private fun speech(value: Double?, status: String = "OK") =
        AcousticFeatureDto(metric = "SPEECH_RATE", value = value, unit = "음절/분", status = status)

    private fun extraction(
        symptom: String? = null,
        medication: String? = null,
        activity: String? = null,
        sleep: String? = null,
    ) = ExtractionDto("call-1", "OK", symptom, medication, activity, sleep)

    private fun analysis(
        features: List<AcousticFeatureDto>? = emptyList(),
        extraction: ExtractionDto? = extraction(),
        baselines: Map<String, BaselineDto> = emptyMap(),
        startedAt: Instant = now,
    ) = CallAnalysis.fromBundle(
        CallAnalysisBundle(
            call = call(startedAt),
            features = features?.let { AcousticFeaturesDto("call-1", it) },
            extraction = extraction,
            transcript = null,
        ),
        baselines,
        now = now,
        zone = zone,
    )

    @Test
    fun coughLineUsesMeasuredCount() {
        assertEquals("기침을 총 5번 하셨어요", analysis(listOf(cough(5.0))).acoustic)
        // 타임라인 카드와 같이 반올림한다
        assertEquals("기침을 총 3번 하셨어요", analysis(listOf(cough(2.5))).acoustic)
        assertEquals("이번 통화에서는 기침이 없었어요", analysis(listOf(cough(0.0))).acoustic)
        assertEquals(
            "기침을 총 5번 하셨어요\n말씀 속도는 분당 210음절이었어요",
            analysis(listOf(speech(209.6), cough(5.0))).acoustic,
        )
    }

    @Test
    fun acousticShowsOnlyMeasuredMetrics() {
        // 서버는 기침 감지기 검증 전까지 값 없이 UNMEASURABLE 로 보낸다 → 기침 줄을 빼고 잰 값만
        assertEquals(
            "말씀 속도는 분당 210음절이었어요",
            analysis(listOf(cough(null, status = "UNMEASURABLE"), speech(210.0))).acoustic,
        )
        assertEquals(
            "이번 통화에서는 계산된 음향 지표가 없어요",
            analysis(listOf(cough(null, status = "UNMEASURABLE"), speech(null, status = "UNMEASURABLE"))).acoustic,
        )
        assertEquals("이번 통화에서는 계산된 음향 지표가 없어요", analysis(emptyList()).acoustic)
        assertEquals("이번 통화에서는 계산된 음향 지표가 없어요", analysis(features = null).acoustic)
    }

    @Test
    fun conversationUsesTimelineStory() {
        val item = analysis(extraction = extraction(medication = "혈압약을 아침에 못 챙기셨어요", sleep = "잠을 깊게 못 주무셨어요"))
        assertEquals("혈압약을 아침에 못 챙기셨어요\n잠을 깊게 못 주무셨어요", item.conversation)
        assertEquals("이번 통화에서는 기록된 건강 이야기가 없었어요.", analysis(extraction = extraction()).conversation)
        assertEquals("아직 정리된 대화 항목이 없어요.", analysis(extraction = null).conversation)
    }

    @Test
    fun recommendationAsksTheFirstExtractedTopicsQuestion() {
        assertEquals(
            "다음 통화에서 \"최근 통화에서 나눈 약 이야기에서 더 알려주실 내용이 있어요?\" 하고 물어보세요.",
            analysis(extraction = extraction(medication = "혈압약", sleep = "잠")).recommendation,
        )
        // 빈 항목은 건너뛴다
        assertEquals(
            "다음 통화에서 \"최근 통화에서 나눈 수면 이야기는 요즘 어때요?\" 하고 물어보세요.",
            analysis(extraction = extraction(symptom = "  ", sleep = "잠")).recommendation,
        )
        assertEquals(
            "다음 통화에서는 요즘 일상을 편하게 이야기해보세요.",
            analysis(extraction = extraction()).recommendation,
        )
        assertEquals("다음 통화에서는 요즘 일상을 편하게 이야기해보세요.", analysis(extraction = null).recommendation)
    }

    @Test
    fun baselineUsesTimelineGaugesOrCollectingNotice() {
        assertEquals(
            "아직 평소 범위를 모으는 중이에요. 통화가 쌓이면 변화를 보여드릴게요.",
            analysis(listOf(AcousticFeatureDto("PAUSE_RATIO", 0.2, "%", "OK"))).baseline,
        )

        val ready = BaselineDto(
            metric = "PAUSE_RATIO", timeSlot = "ALL", kind = "ROLLING", status = "READY",
            sampleCount = 10, requiredCount = 5, median = 0.2, mad = 0.05,
        )
        val compared = analysis(
            features = listOf(AcousticFeatureDto("PAUSE_RATIO", 0.21, "%", "OK")),
            baselines = mapOf("PAUSE_RATIO" to ready),
        )
        assertEquals("말 사이 쉼: 평소 범위 안", compared.baseline)
    }

    @Test
    fun dayTextCountsCalendarDays() {
        assertEquals("오늘 통화", analysis(startedAt = Instant.parse("2026-11-04T15:30:00Z")).dayText)
        assertEquals("어제 통화", analysis(startedAt = Instant.parse("2026-11-04T14:30:00Z")).dayText)
        assertEquals("5일 전 통화", analysis(startedAt = Instant.parse("2026-10-31T03:00:00Z")).dayText)
    }

    @Test
    fun subjectNameComesFromFamilyThenMe() {
        val item = analysis()
        val mom = FamilyContact("m1", "mom", "김영희", "MOTHER", "")
        val me = ApiUser(id = "mom", role = "PARENT", name = "나")
        assertEquals("김영희", item.subjectName(listOf(mom), me))
        assertEquals("나", item.subjectName(emptyList(), me))
        assertEquals("가족", item.subjectName(emptyList(), me.copy(id = "other")))
        // 다른 화면처럼 닉네임이 있으면 닉네임
        assertEquals("Emul1", item.subjectName(emptyList(), me.copy(nickname = "Emul1")))
    }

    private fun withFixture(body: suspend (TestEnvironment) -> Unit) = runTest {
        val fixture = TestEnvironment()
        try {
            body(fixture)
        } finally {
            fixture.cleanUp()
        }
    }

    private fun callJson(state: String) =
        """{"callId":"c1","parentId":"mom","childId":"me","state":"$state","startedAt":"2026-11-05T03:00:00Z","durationSec":600,"recorded":true}"""

    private fun serve(fixture: TestEnvironment, state: String, extractionStatus: Int = 200) {
        fixture.respond = { request ->
            when (request.url.encodedPath) {
                "/v1/calls/c1" -> 200 to callJson(state)
                "/v1/calls/c1/extraction" ->
                    if (extractionStatus == 200) {
                        200 to """{"callId":"c1","parseStatus":"OK","sleep":"잠을 깊게 못 주무셨어요","facts":[]}"""
                    } else {
                        extractionStatus to """{"code":"NOT_FOUND","message":"추출 결과가 아직 없습니다"}"""
                    }
                "/v1/calls/c1/acoustic-features" ->
                    200 to """{"callId":"c1","audioSource":"DEVICE_RAW","features":[{"metric":"COUGH_EVENTS","value":5.0,"unit":"회","status":"OK"}]}"""
                "/v1/parents/mom/baseline" -> 200 to """{"baselines":[]}"""
                else -> 404 to """{"code":"NOT_FOUND","message":"통화를 찾을 수 없습니다"}"""
            }
        }
    }

    @Test
    fun analyzedCallLoadsExtractionAndAcousticFeatures() = withFixture { fixture ->
        serve(fixture, "ANALYZED")
        val model = CallAnalysisViewModel("c1")
        model.load(fixture.environment)

        val state = model.state as CallAnalysisState.Ready
        assertEquals("잠을 깊게 못 주무셨어요", state.analysis.conversation)
        assertEquals("기침을 총 5번 하셨어요", state.analysis.acoustic)
        assertEquals("mom", state.analysis.subjectId)
        assertTrue("/v1/parents/mom/baseline" in fixture.paths)
    }

    @Test
    fun processingCallIsPendingWithoutLoadingResults() = withFixture { fixture ->
        for (state in listOf("ENDED", "PROCESSING")) {
            serve(fixture, state)
            fixture.paths.clear()
            val model = CallAnalysisViewModel("c1")
            model.load(fixture.environment)

            assertEquals(CallAnalysisState.Pending, model.state)
            assertEquals(listOf("/v1/calls/c1"), fixture.paths.toList())
        }
    }

    @Test
    fun missingExtractionMeansNotReadyYet() = withFixture { fixture ->
        serve(fixture, "ANALYZED", extractionStatus = 404)
        val model = CallAnalysisViewModel("c1")
        model.load(fixture.environment)
        assertEquals(CallAnalysisState.Pending, model.state)
    }

    @Test
    fun excludedAndFailedCallsAreUnavailable() = withFixture { fixture ->
        serve(fixture, "ANALYSIS_EXCLUDED")
        val excluded = CallAnalysisViewModel("c1")
        excluded.load(fixture.environment)
        assertEquals("분석 대상에서 제외된 통화예요.", (excluded.state as CallAnalysisState.Unavailable).title)

        serve(fixture, "ANALYSIS_FAILED")
        val failed = CallAnalysisViewModel("c1")
        failed.load(fixture.environment)
        assertEquals("통화를 분석하지 못했어요.", (failed.state as CallAnalysisState.Unavailable).title)
        assertFalse(fixture.paths.any { it.endsWith("/extraction") })
    }

    @Test
    fun otherServerErrorsShowTheMessage() = withFixture { fixture ->
        serve(fixture, "ANALYZED", extractionStatus = 500)
        val model = CallAnalysisViewModel("c1")
        model.load(fixture.environment)
        assertEquals(CallAnalysisState.Error("추출 결과가 아직 없습니다"), model.state)

        fixture.respond = { 404 to """{"code":"NOT_FOUND","message":"통화를 찾을 수 없습니다"}""" }
        model.load(fixture.environment)
        assertEquals(CallAnalysisState.Error("통화를 찾을 수 없습니다"), model.state)
    }
}
