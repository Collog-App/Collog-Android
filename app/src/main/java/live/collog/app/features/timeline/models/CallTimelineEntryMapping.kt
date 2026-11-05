package live.collog.app.features.timeline.models

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import live.collog.app.features.report.models.roundHalfAwayFromZero
import live.collog.app.shared.components.chart.KeywordMark
import live.collog.app.shared.components.chart.KeywordTone
import live.collog.app.shared.components.chart.RangeGauge
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.components.stat.StatNote
import live.collog.app.shared.services.api.AcousticFeatureDto
import live.collog.app.shared.services.api.AcousticFeaturesDto
import live.collog.app.shared.services.api.ApiFormat
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.services.api.ExtractionDto
import live.collog.app.shared.services.api.MetricLabel
import live.collog.app.shared.services.api.TranscriptDto

/** iOS `CallAnalysisBundle`: 통화 하나의 분석 결과 묶음. 못 받은 항목은 null. */
data class CallAnalysisBundle(
    val call: CallSummaryDto,
    val features: AcousticFeaturesDto?,
    val extraction: ExtractionDto?,
    val transcript: TranscriptDto?,
)

/** iOS `CallTimelineEntry.init(bundle:baselines:)`. */
fun CallTimelineEntry.Companion.fromBundle(
    bundle: CallAnalysisBundle,
    baselines: Map<String, BaselineDto>,
    usualCallMinutes: Int? = null,
): CallTimelineEntry {
    val features = bundle.features?.features ?: emptyList()
    val durationSec = bundle.call.durationSec ?: 0

    val speechRate = features.firstOrNull { it.metric == "SPEECH_RATE" && it.isMeasured }
    val cough = features.firstOrNull { it.metric == "COUGH_EVENTS" && it.isMeasured }

    // 새 피그마 「평소 N분」: 최근 통화들의 평균(3건 미만이면 호출하는 쪽이 null 을 준다)
    val stats = mutableListOf(
        CallStat("통화 길이", "${(max(durationSec, 0) + 59) / 60}", "분", usualCallMinutes?.let { StatNote("평소 ${it}분") }),
    )
    val speechValue = speechRate?.value
    if (speechRate != null && speechValue != null) {
        stats += CallStat(
            "말씀 속도",
            "${roundHalfAwayFromZero(speechValue)}",
            speechRate.unit,
            note(baselines["SPEECH_RATE"]),
        )
    }

    val counts = mutableListOf<CallStat>()
    bundle.transcript?.repeatRequestCount?.let {
        counts += CallStat("되물으심", "$it", "회", null)
    }
    val coughValue = cough?.value
    if (cough != null && coughValue != null) {
        counts.add(0, CallStat("기침", "${roundHalfAwayFromZero(coughValue)}", cough.unit, null))
    }

    return CallTimelineEntry(
        dateText = ApiFormat.longDate(bundle.call.startedAt),
        durationText = ApiFormat.duration(durationSec),
        summaryStats = stats,
        story = story(bundle.extraction),
        keywords = keywords(bundle.extraction, bundle.transcript, durationSec),
        gauges = gauges(features, baselines),
        counts = counts,
        callId = bundle.call.callId,
    )
}

/** 새 피그마 「평소 208」: 준비된 기준선의 중앙값. 기준선이 없으면 비교 글자를 뺀다. */
private fun note(baseline: BaselineDto?): StatNote? {
    val median = baseline?.median ?: return null
    if (!baseline.isReady || median == 0.0) return null
    return StatNote("평소 ${roundHalfAwayFromZero(median)}")
}

private fun story(extraction: ExtractionDto?): String {
    if (extraction == null) return "아직 정리된 대화 항목이 없어요."
    val lines = listOfNotNull(extraction.symptom, extraction.medication, extraction.activity, extraction.sleep)
    return if (lines.isEmpty()) "이번 통화에서는 기록된 건강 이야기가 없었어요." else lines.joinToString("\n")
}

private val factLabels = mapOf("symptom" to "증상", "medication" to "복약", "activity" to "활동", "sleep" to "수면")

/**
 * 키워드 줄: 범주 이름(증상·복약·활동·수면)은 추출 결과의 근거 구간 시작 위치에, 되물음은 그 지점에 눈금으로 그린다.
 * 서버는 「혈압약」 같은 낱말 키워드나 이상 판정을 주지 않으므로 범주만 쓰고 색으로 판정하지 않는다.
 */
private fun keywords(extraction: ExtractionDto?, transcript: TranscriptDto?, durationSec: Int): List<KeywordMark> {
    if (transcript == null || durationSec <= 0) return emptyList()
    val total = (durationSec * 1000).toDouble()
    val segmentStarts = transcript.segments.associate { it.segmentId to it.startMs }

    val facts = extraction?.facts.orEmpty().mapNotNull { fact ->
        val label = factLabels[fact.category] ?: return@mapNotNull null
        val start = fact.evidenceSegmentIds.firstNotNullOfOrNull { segmentStarts[it] } ?: return@mapNotNull null
        KeywordMark(min(max(start.toDouble() / total, 0.0), 1.0), KeywordTone.Neutral, label)
    }
    val repeats = transcript.repeatEvents.take(8).map { event ->
        KeywordMark(min(max(event.startMs.toDouble() / total, 0.0), 1.0), KeywordTone.Caution, null)
    }
    return (facts + repeats).sortedBy { it.position }
}

private fun gauges(features: List<AcousticFeatureDto>, baselines: Map<String, BaselineDto>): List<RangeGauge> =
    listOf("PAUSE_RATIO", "F0_VARIATION").mapNotNull { metric ->
        val feature = features.firstOrNull { it.metric == metric && it.isMeasured } ?: return@mapNotNull null
        val value = feature.value ?: return@mapNotNull null
        val baseline = baselines[metric] ?: return@mapNotNull null
        if (!baseline.isReady) return@mapNotNull null
        val median = baseline.median ?: return@mapNotNull null
        val mad = baseline.mad ?: return@mapNotNull null
        if (mad <= 0) return@mapNotNull null

        val spread = mad * 1.5 / 0.6745
        val domain = spread * 3
        val normalized = { input: Double -> min(max((input - (median - domain)) / (domain * 2), 0.0), 1.0) }
        val withinNormal = abs(value - median) <= spread

        RangeGauge(
            label = MetricLabel.korean(metric),
            normalRange = normalized(median - spread)..normalized(median + spread),
            marker = normalized(value),
            caption = if (withinNormal) "평소 범위 안" else if (value > median) "평소보다 큼" else "평소보다 작음",
        )
    }
