package live.collog.app.features.timeline.models

import java.time.Instant
import java.time.ZoneId
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.viewmodels.HomeViewModel
import live.collog.app.features.report.models.roundHalfAwayFromZero
import live.collog.app.shared.services.api.AcousticFeaturesDto
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.ExtractionDto

/**
 * 피그마 「최종본」 통화 분석(269:5235)에 보이는 통화 하나의 분석. iOS에는 없는 화면이다.
 * 대화 항목과 기준선 비교는 타임라인 카드([CallTimelineEntry.fromBundle])와 같은 값을 쓴다.
 */
data class CallAnalysis(
    /** 분석 대상(`calls.parentId`). 이름은 화면이 가족 목록에서 찾는다([subjectName]). */
    val subjectId: String,
    /** "오늘 통화", "어제 통화", "3일 전 통화". */
    val dayText: String,
    val conversation: String,
    val baseline: String,
    val acoustic: String,
    val recommendation: String,
    /** 응급 안내(11-08 가드레일). 서버가 고정 문구로 남겼을 때만. */
    val safetyNotice: String? = null,
) {
    /** 분석 대상 이름: 가족 목록에서 찾고, 내가 분석 대상이면 내 닉네임(없으면 계정 이름). 못 찾으면 "가족". */
    fun subjectName(contacts: List<FamilyContact>, me: ApiUser?): String =
        contacts.firstOrNull { it.userId == subjectId }?.name
            ?: me?.takeIf { it.id == subjectId }?.let { it.nickname?.takeIf(String::isNotBlank) ?: it.name }
            ?: "가족"

    companion object
}

/** 통화 분석 화면의 상태. */
sealed interface CallAnalysisState {
    data object Loading : CallAnalysisState

    /** 통화가 끝나지 않았거나 분석 중이다(결과가 아직 없어 404인 때도). */
    data object Pending : CallAnalysisState

    /** 분석에서 빠졌거나(ANALYSIS_EXCLUDED) 분석하지 못한(ANALYSIS_FAILED) 통화. */
    data class Unavailable(val title: String, val message: String?, val safetyNotice: String? = null) : CallAnalysisState

    data class Error(val message: String) : CallAnalysisState

    data class Ready(val analysis: CallAnalysis) : CallAnalysisState
}

/** 피그마 「최종본」 통화 분석(269:5235)의 내용. [now]·[zone]은 "오늘 통화" 계산에 쓴다. */
fun CallAnalysis.Companion.fromBundle(
    bundle: CallAnalysisBundle,
    baselines: Map<String, BaselineDto>,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): CallAnalysis {
    val entry = CallTimelineEntry.fromBundle(bundle, baselines)
    return CallAnalysis(
        subjectId = bundle.call.parentId,
        dayText = "${HomeViewModel.lastCallDayText(bundle.call.startedAt, now, zone)} 통화",
        conversation = entry.story,
        // 리포트의 기준선 수집 중 안내(ReportContent)와 같은 문구
        baseline = entry.gauges.joinToString("\n") { "${it.label}: ${it.caption}" }
            .ifEmpty { "아직 평소 범위를 모으는 중이에요. 통화가 쌓이면 변화를 보여드릴게요." },
        acoustic = acoustic(bundle.features),
        recommendation = recommendation(bundle.extraction),
        safetyNotice = bundle.extraction?.safetyNotice?.takeIf { it.isNotBlank() },
    )
}

/** 잰 값만 적는다(타임라인 카드처럼). 기침 감지기가 꺼진 서버에서는 기침 줄이 없다. */
private fun acoustic(features: AcousticFeaturesDto?): String {
    val measured = features?.features.orEmpty().filter { it.isMeasured }
    val lines = buildList {
        measured.firstOrNull { it.metric == "COUGH_EVENTS" }?.value?.let { cough ->
            val count = roundHalfAwayFromZero(cough)
            add(if (count == 0L) "이번 통화에서는 기침이 없었어요" else "기침을 총 ${count}번 하셨어요")
        }
        measured.firstOrNull { it.metric == "SPEECH_RATE" }?.value?.let { rate ->
            add("말씀 속도는 분당 ${roundHalfAwayFromZero(rate)}음절이었어요")
        }
    }
    return lines.joinToString("\n").ifEmpty { "이번 통화에서는 계산된 음향 지표가 없어요" }
}

/** 홈 「다음 통화에서 물어보기」([HealthFeedback.topics])처럼 처음 나온 대화 항목의 질문을 권한다. */
private fun recommendation(extraction: ExtractionDto?): String {
    val question = HealthFeedback.topics
        .firstOrNull { (key, _, _) -> !extraction?.item(key).isNullOrBlank() }
        ?.third
        ?: return "다음 통화에서는 요즘 일상을 편하게 이야기해보세요."
    return "다음 통화에서 \"$question\" 하고 물어보세요."
}

private fun ExtractionDto.item(key: String): String? = when (key) {
    "symptom" -> symptom
    "medication" -> medication
    "activity" -> activity
    "sleep" -> sleep
    else -> null
}
