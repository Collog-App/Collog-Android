package live.collog.app.shared.services.api

import java.time.Instant
import kotlinx.serialization.Serializable

// iOS `APIReportModels.swift`.

@Serializable
data class ConsentDocument(
    val version: String,
    val fullText: String,
    val collectedItems: List<String>,
    val purpose: String,
    val retentionPeriod: String,
    val rawAudioPolicy: String,
    val requiredItems: List<String>,
)

object ConsentItemLabel {
    fun korean(code: String): String = when (code) {
        "SENSITIVE_HEALTH_COLLECTION" -> "건강 민감정보 수집·이용"
        "VOICE_FEATURE_EXTRACTION" -> "음성 특징값 추출"
        "CALL_RECORDING" -> "통화 녹음과 분석"
        "REPORT_SHARING_WITH_CHILD" -> "자녀와 리포트 공유"
        "THIRD_PARTY_AI_PROCESSING" -> "Deepgram, Google Gemini, ElevenLabs에 데이터 전송"
        else -> code
    }
}

@Serializable
data class ConsentRecordDto(
    val consentId: String,
    val userId: String,
    val documentVersion: String,
    val status: String,
    val agreedItems: List<String>,
    val isCurrent: Boolean,
    val currentDocumentVersion: String,
) {
    val isGranted: Boolean get() = status == "GRANTED"
}

@Serializable
data class ProfileDto(
    val parentId: String,
    val conditions: List<String>,
    val isCompleted: Boolean? = null,
    @Serializable(with = CollogInstantSerializer::class) val updatedAt: Instant? = null,
) {
    val hasCompletedSetup: Boolean get() = isCompleted ?: (updatedAt != null || conditions.isNotEmpty())
}

@Serializable
data class InvitationDto(
    val invitationId: String,
    val code: String,
    val shareText: String,
    val status: String,
)

@Serializable
data class CallSummaryDto(
    val callId: String,
    val parentId: String,
    val state: String,
    val timeSlot: String? = null,
    @Serializable(with = CollogInstantSerializer::class) val startedAt: Instant,
    @Serializable(with = CollogInstantSerializer::class) val endedAt: Instant? = null,
    val durationSec: Int? = null,
    val recorded: Boolean,
    val parentSpeechSec: Int? = null,
) {
    val id: String get() = callId
    val isAnalyzed: Boolean get() = state == "ANALYZED"
}

@Serializable
data class CallsResponse(val calls: List<CallSummaryDto>)

@Serializable
data class ComparisonDto(
    val deltaPct: Double,
    val direction: String,
    val robustZ: Double,
    val significant: Boolean,
)

@Serializable
data class SignalDto(
    val signalId: String,
    val metric: String,
    val timeSlot: String,
    val consecutiveWeeks: Int,
    val promoted: Boolean,
    val acute: Boolean,
    val summaryText: String? = null,
    val acuteText: String? = null,
    val vsAnchor: ComparisonDto? = null,
    val vsRolling: ComparisonDto? = null,
)

@Serializable
data class RepeatObservationDto(
    val count: Int,
    val callsWithRepeat: Int,
    val label: String,
)

@Serializable
data class AcousticTrendPointDto(
    val date: String,
    val value: Double? = null,
    val unit: String? = null,
)

@Serializable
data class AcousticTrendDto(
    val metric: String,
    val points: List<AcousticTrendPointDto>,
)

@Serializable
data class ReportDto(
    val parentId: String,
    val period: String,
    val from: String,
    val to: String,
    val state: String,
    val emptyMessage: String? = null,
    val disclaimer: String,
    val advisory: String? = null,
    val promotedSignals: List<SignalDto>,
    val acuteSignals: List<SignalDto>,
    val conversationItems: Map<String, List<String>>,
    val repeatObservation: RepeatObservationDto,
    val acousticTrends: List<AcousticTrendDto>,
    val recentAcousticHistory: List<AcousticTrendDto>? = null,
    val analyzedCallCount: Int,
    val containsDemoData: Boolean? = null,
    val demoDataNotice: String? = null,
)

@Serializable
data class AcousticFeatureDto(
    val metric: String,
    val value: Double? = null,
    val unit: String,
    val status: String,
    val unmeasurableReason: String? = null,
) {
    val isMeasured: Boolean get() = status == "OK" && value != null
}

@Serializable
data class AcousticFeaturesDto(
    val callId: String,
    val features: List<AcousticFeatureDto>,
)

@Serializable
data class ExtractionDto(
    val callId: String,
    val parseStatus: String,
    val symptom: String? = null,
    val medication: String? = null,
    val activity: String? = null,
    val sleep: String? = null,
)

@Serializable
data class RepeatEventDto(
    val startMs: Int,
    val endMs: Int,
    val category: String,
    val matchedText: String,
)

@Serializable
data class TranscriptDto(
    val callId: String,
    val excluded: Boolean,
    val parentSpeechSec: Int,
    val repeatEvents: List<RepeatEventDto>,
    val repeatRequestCount: Int,
    val repeatRequestsPerMinute: Double,
)

@Serializable
data class BaselineDto(
    val metric: String,
    val timeSlot: String,
    val kind: String,
    val status: String,
    val sampleCount: Int,
    val requiredCount: Int,
    val median: Double? = null,
    val mad: Double? = null,
) {
    val isReady: Boolean get() = status == "READY" && median != null && (mad ?: 0.0) > 0
}

@Serializable
data class BaselinesResponse(val baselines: List<BaselineDto>)

object MetricLabel {
    fun korean(metric: String): String = when (metric) {
        "SPEECH_RATE" -> "말씀 속도"
        "PAUSE_RATIO" -> "말 사이 쉼"
        "F0_VARIATION" -> "목소리 높낮이"
        "COUGH_EVENTS" -> "기침"
        else -> metric
    }

    fun unit(metric: String): String = when (metric) {
        "SPEECH_RATE" -> "음절/분"
        "PAUSE_RATIO" -> "%"
        "F0_VARIATION" -> "semitone"
        "COUGH_EVENTS" -> "회"
        else -> ""
    }
}
