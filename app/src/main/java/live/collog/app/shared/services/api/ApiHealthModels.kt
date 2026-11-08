package live.collog.app.shared.services.api

import kotlinx.serialization.Serializable

// 건강 앱 연결(2026-11-08 서버·안드로이드 공통 약속). iOS 에는 아직 없다.

/** 서버가 내려주는 별도 선택 동의 문서. 앱은 그대로 보여 준다. */
@Serializable
data class HealthLinkDocument(
    val version: String,
    val title: String,
    val items: List<String> = emptyList(),
)

/** `GET/POST /v1/health/link`. 시각·날짜는 화면에 쓰는 것만 글자로 받는다(형식이 달라도 응답 전체가 깨지지 않게). */
@Serializable
data class HealthLinkStatusDto(
    val connected: Boolean = false,
    val consentVersion: String? = null,
    val connectedAt: String? = null,
    val document: HealthLinkDocument? = null,
    /** "YYYY-MM-DD" */
    val lastSyncedDay: String? = null,
)

@Serializable
data class HealthLinkBody(val version: String, val platform: String)

/** 하루 요약 한 줄. 그날 기록이 없는 값은 null(`CollogJson` 이 null 칸은 보내지 않는다). */
@Serializable
data class HealthDayDto(
    /** Asia/Seoul 기준 "YYYY-MM-DD" */
    val date: String,
    val sleepMinutes: Int? = null,
    val steps: Int? = null,
    val restingHeartRate: Int? = null,
    val bloodPressureSystolic: Int? = null,
    val bloodPressureDiastolic: Int? = null,
    val bloodGlucoseMgDl: Double? = null,
) {
    val hasAnyValue: Boolean
        get() = sleepMinutes != null || steps != null || restingHeartRate != null ||
            bloodPressureSystolic != null || bloodPressureDiastolic != null || bloodGlucoseMgDl != null
}

@Serializable
data class HealthDailyUploadBody(val platform: String, val days: List<HealthDayDto>)

@Serializable
data class HealthDailyUploadResult(val saved: Int = 0, val lastSyncedDay: String? = null)

/** `GET /v1/parents/{parentId}/health/daily`. 연결 안 됐으면 [days]가 비어 있다. */
@Serializable
data class ParentHealthDailyDto(
    val parentId: String = "",
    val connected: Boolean = false,
    val platform: String? = null,
    val lastSyncedDay: String? = null,
    val days: List<HealthDayDto> = emptyList(),
    val disclaimer: String? = null,
)

/** 리포트 `safetyNotices` 한 줄: 통화 중 응급 표현이 있었을 때 서버가 고정 문구로 남긴다. */
@Serializable
data class SafetyNoticeDto(
    val callId: String = "",
    val observedAt: String? = null,
    val text: String = "",
)
