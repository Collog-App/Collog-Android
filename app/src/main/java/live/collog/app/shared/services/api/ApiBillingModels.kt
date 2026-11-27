package live.collog.app.shared.services.api

import java.time.Instant
import kotlinx.serialization.Serializable

// 결제 1차 약속(`private/billing_contract.md` §2-5): `GET /v1/account/entitlement` 와 `POST …/refresh` 의 본문.

/** 가족 단위 요금제. 화면 판단(잠금·결제 버튼)은 이 서버 값만 쓴다(스토어 SDK 값으로 잠금을 풀지 않는다). */
@Serializable
data class EntitlementDto(
    /** false 면 결제가 꺼진 것: 모두 프리미엄이고 잠금·설정 줄·결제 화면 진입점을 숨긴다. */
    val billingEnabled: Boolean = false,
    /** "PREMIUM" | "FREE" */
    val plan: String = "PREMIUM",
    /** "PAID" | "TRIAL" | null(무료·결제 꺼짐) */
    val source: String? = null,
    @Serializable(with = CollogInstantSerializer::class) val trialEndsAt: Instant? = null,
    @Serializable(with = CollogInstantSerializer::class) val paidUntil: Instant? = null,
    /** 내가 결제한 구독이 유효한가. */
    val paidByMe: Boolean = false,
    /** 가족 중 다른 사람이 결제했으면 그 사람 이름. */
    val payerName: String? = null,
    val analysis: AnalysisUsageDto? = null,
) {
    /** 결제가 켜져 있고 무료: 리포트·타임라인 탭이 잠긴다. */
    val isFreePlan: Boolean get() = billingEnabled && plan == "FREE"
    val isTrial: Boolean get() = source == "TRIAL"
    val isPaid: Boolean get() = source == "PAID"
}

/** 이번 달(한국 시간 달력 월) 가족 분석 사용량. */
@Serializable
data class AnalysisUsageDto(
    /** "YYYY-MM" */
    val month: String,
    val usedSec: Int,
    val limitSec: Int,
    @Serializable(with = CollogInstantSerializer::class) val resetsAt: Instant? = null,
)
