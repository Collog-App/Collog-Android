package live.collog.app.shared.services

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import live.collog.app.config.AppEnvironment
import live.collog.app.shared.services.api.AnalysisUsageDto
import live.collog.app.shared.services.api.EntitlementDto

/** 스토어 결제(RevenueCat SDK)와 맞닿는 부분. 테스트는 가짜를 넣는다. */
interface PurchaseStore {
    /** 공개 SDK 키가 있어 스토어 결제를 쓸 수 있는가. 없으면 결제 화면은 「결제를 준비하고 있어요」만 보인다. */
    val isAvailable: Boolean

    /** 로그인한 서버 사용자 ID 로 SDK 를 맞춘다: 처음이면 configure, 다른 ID 면 logIn. logOut 은 부르지 않는다. */
    suspend fun identify(userId: String)

    /** offering `default` 의 월 상품 가격. 상품이 없으면 null. */
    suspend fun monthlyPrice(userId: String): StorePrice?

    /** 월 상품을 산다. 취소·승인 대기·이미 구매함은 [PurchaseOutcome]으로, 그 밖의 실패는 던진다. */
    suspend fun purchase(activity: Activity, userId: String): PurchaseOutcome

    /** 이 스토어 계정의 구매를 지금 사용자에게 다시 붙인다. 붙은 뒤 `premium` 이 켜져 있으면 true. 실패하면 던진다. */
    suspend fun restore(userId: String): Boolean

    /** 공개 SDK 키 없이 만든 빌드(CI 등). SDK 를 켜지 않는다. */
    object Unavailable : PurchaseStore {
        override val isAvailable: Boolean = false
        override suspend fun identify(userId: String) = Unit
        override suspend fun monthlyPrice(userId: String): StorePrice? = null
        override suspend fun purchase(activity: Activity, userId: String): PurchaseOutcome =
            throw IllegalStateException("결제를 쓸 수 없는 빌드예요")
        override suspend fun restore(userId: String): Boolean = throw IllegalStateException("결제를 쓸 수 없는 빌드예요")
    }
}

enum class PurchaseOutcome {
    Purchased,
    Cancelled,

    /** 결제 승인을 기다린다(보류 결제 수단). 승인되면 웹훅으로 반영된다. */
    PaymentPending,

    /** 이 스토어 계정에 이미 유효한 구독이 있다(다른 앱 사용자에게 붙었을 수 있다). */
    AlreadyOwned,
}

/** 스토어 상품 가격. [formatted]는 스토어가 만든 문자열(Play 원화는 「₩13,000」). */
data class StorePrice(val formatted: String, val amountMicros: Long, val currencyCode: String)

/**
 * 결제 1차(`private/billing_contract.md` §3): 가족 요금제(서버 값), 결제 화면 상태, 구매·복원.
 * 잠금·버튼 판단은 서버 `GET /v1/account/entitlement` 값만 쓴다(가족 결제·체험은 서버만 안다).
 * SDK 는 서버가 결제를 켰다고(`billingEnabled`) 답했을 때만 켠다: 꺼져 있으면 키가 있는 빌드라도 RevenueCat 에
 * 사용자 ID 를 보내지 않는다. 앱 전체에서 하나다(AppEnvironment). 둘러보기에서는 서버도 SDK 도 쓰지 않는다.
 */
class Billing(private val store: PurchaseStore = PurchaseStore.Unavailable) {
    /** 지금 계정의 요금제. 아직 모르거나 서버에 API 가 없으면 null(결제 꺼짐과 같게 아무것도 막지 않는다). */
    var entitlement by mutableStateOf<EntitlementDto?>(null)
        private set
    var isPaywallPresented by mutableStateOf(false)
        private set

    /** 결제 화면의 월 가격(「13,000원」). null 이면 아직 모르거나 상품이 없다. */
    var price by mutableStateOf<String?>(null)
        private set
    var isLoadingPrice by mutableStateOf(false)
        private set

    /** 구매·복원 중. 버튼을 막는다. */
    var isWorking by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** 오류가 아닌 안내(결제는 됐는데 서버 반영이 늦을 때). */
    var notice by mutableStateOf<String?>(null)
        private set

    private var generation = UUID.randomUUID()

    /** 요금제 요청(GET·refresh) 순번. 지금 값보다 나중에 출발한 요청의 결과만 적용한다(느린 GET 이 방금 받은 유료를 덮지 않게). */
    private var requestSequence = 0L
    private var appliedSequence = 0L

    val isStoreAvailable: Boolean get() = store.isAvailable

    /** 결제가 켜져 있다: 설정 「구독 관리」 줄과 결제 화면 진입점을 보인다. */
    val isEnabled: Boolean get() = entitlement?.billingEnabled == true

    /** 결제가 켜져 있고 가족이 무료: 리포트·타임라인 탭이 잠긴다. */
    val isLocked: Boolean get() = entitlement?.isFreePlan == true

    /** 계정이 바뀌거나 로그아웃했다. 진행 중이던 요청의 결과는 버린다. */
    fun reset() {
        generation = UUID.randomUUID()
        entitlement = null
        price = null
        isLoadingPrice = false
        message = null
        notice = null
        isPaywallPresented = false
    }

    /** 결제가 켜져 있으면 SDK 를 지금 계정으로 맞춘다(처음이면 configure). 실패해도 구매할 때 다시 맞춘다. */
    suspend fun identify(environment: AppEnvironment) {
        if (!store.isAvailable || !isEnabled) return
        val userId = userId(environment) ?: return
        try {
            store.identify(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "RevenueCat 사용자를 맞추지 못했어요", e)
        }
    }

    /** 앱 시작·앞으로 올 때·결제 화면을 열 때. 실패하면 아는 값을 그대로 둔다. 결제가 켜져 있으면 SDK 도 맞춘다. */
    suspend fun load(environment: AppEnvironment) {
        val userId = userId(environment) ?: return
        val generation = generation
        val sequence = ++requestSequence
        val loaded = try {
            environment.api.entitlement()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        if (apply(loaded, sequence, generation, userId, environment)) identify(environment)
    }

    /** 같은 계정이고 지금 값보다 나중에 출발한 요청이면 적용한다. */
    private fun apply(loaded: EntitlementDto, sequence: Long, generation: UUID, userId: String, environment: AppEnvironment): Boolean {
        if (generation != this.generation || environment.session.user?.id != userId) return false
        if (sequence <= appliedSequence) return false
        appliedSequence = sequence
        entitlement = loaded
        return true
    }

    fun presentPaywall() {
        message = null
        notice = null
        isPaywallPresented = true
    }

    fun dismissPaywall() {
        isPaywallPresented = false
        message = null
        notice = null
    }

    /**
     * 결제 화면이 열릴 때: 요금제를 다시 읽고(켜져 있으면 SDK 도 켠다), 스토어 가격을 아직 모르면 받는다.
     * 요금제를 읽는 동안부터 로딩으로 보인다(그 전에 「준비하고 있어요」가 잠깐 비치지 않게).
     */
    suspend fun preparePaywall(environment: AppEnvironment) {
        if (isLoadingPrice) return
        val generation = generation
        isLoadingPrice = true
        try {
            load(environment)
            if (!store.isAvailable || !isEnabled || price != null) return
            val userId = userId(environment) ?: return
            val loaded = store.monthlyPrice(userId)
            if (generation == this.generation) price = loaded?.let(BillingText::price)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "구독 상품을 불러오지 못했어요", e)
        } finally {
            // 그사이 계정이 바뀌었으면 reset 이 이미 내렸고, 새 계정의 로딩을 건드리지 않는다
            if (generation == this.generation) isLoadingPrice = false
        }
    }

    /** 「프리미엄 시작하기」. 사용자가 취소하면 아무 안내 없이 화면을 그대로 둔다. */
    suspend fun purchase(activity: Activity, environment: AppEnvironment) {
        runStoreAction(environment, PURCHASE_FAILED) { userId, generation ->
            when (store.purchase(activity, userId)) {
                PurchaseOutcome.Purchased -> refreshAfterStore(environment, userId, generation)
                PurchaseOutcome.Cancelled -> Unit
                PurchaseOutcome.PaymentPending -> {
                    if (generation == this.generation) notice = PAYMENT_PENDING
                    load(environment)
                }
                PurchaseOutcome.AlreadyOwned -> {
                    if (generation == this.generation) message = ALREADY_OWNED
                    load(environment)
                }
            }
        }
    }

    /** 「구매 복원」. 붙은 구독이 없으면 그렇다고만 알린다(잠금 판단은 서버 값만). */
    suspend fun restore(environment: AppEnvironment) {
        runStoreAction(environment, RESTORE_FAILED) { userId, generation ->
            if (store.restore(userId)) {
                refreshAfterStore(environment, userId, generation)
            } else if (generation == this.generation) {
                notice = NOTHING_TO_RESTORE
            }
        }
    }

    /** 구매·복원을 하나씩. 스토어가 실패를 던지면 문구를 보이고, 결제가 됐을 수도 있어 요금제를 한 번 더 읽는다. */
    private suspend fun runStoreAction(
        environment: AppEnvironment,
        failure: String,
        action: suspend (userId: String, generation: UUID) -> Unit,
    ) {
        if (isWorking || !store.isAvailable || !isEnabled) return
        val userId = userId(environment) ?: return
        val generation = generation
        isWorking = true
        message = null
        notice = null
        try {
            try {
                action(userId, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "스토어 동작 실패", e)
                if (generation == this.generation) message = failure
                load(environment)
            }
        } finally {
            isWorking = false
        }
    }

    /**
     * 구매·복원 직후 `POST …/refresh`. 서버가 RevenueCat 확인에 실패했거나(502 `UPSTREAM_ERROR`) 연결이 끊겼으면
     * 결제 실패로 보이지 않게 `GET` 으로 한 번 더 읽는다. 어느 쪽이든 아직 유료로 안 보이면 반영이 늦다고만 알린다(웹훅이 곧 반영한다).
     */
    private suspend fun refreshAfterStore(environment: AppEnvironment, userId: String, generation: UUID) {
        val sequence = ++requestSequence
        val refreshed = try {
            environment.api.refreshEntitlement()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (generation != this.generation || environment.session.user?.id != userId) return
        if (refreshed != null) apply(refreshed, sequence, generation, userId, environment) else load(environment)
        if (generation == this.generation && entitlement?.isPaid != true) notice = PURCHASE_PENDING
    }

    private fun userId(environment: AppEnvironment): String? {
        if (environment.settings.isGuestMode || !environment.session.isAuthenticated) return null
        return environment.session.user?.id
    }

    companion object {
        private const val TAG = "CollogBilling"
        const val PURCHASE_FAILED = "결제를 마치지 못했어요. 잠시 뒤 다시 시도해 주세요."
        const val RESTORE_FAILED = "구매를 복원하지 못했어요. 잠시 뒤 다시 시도해 주세요."
        const val PURCHASE_PENDING = "결제는 완료됐어요. 반영까지 잠시 걸릴 수 있어요."
        const val PAYMENT_PENDING = "결제 승인을 기다리고 있어요. 승인되면 자동으로 반영돼요."
        const val ALREADY_OWNED = "이미 구매한 구독이 있어요. 구매 복원을 눌러 주세요."
        const val NOTHING_TO_RESTORE = "복원할 구매가 없어요."
    }
}

/** 결제 화면에 보일 것(약속 §3 「결제 화면」). */
data class PaywallContent(
    /** 체험·결제 상태 줄. 없으면 null. */
    val status: String?,
    /** 가격 줄(「월 …」). 구매 버튼이 있을 때만. */
    val price: String?,
    val showsPurchase: Boolean,
    /** 「결제를 준비하고 있어요」: 공개 키가 없거나 상품을 못 받았다. */
    val showsPreparing: Boolean,
    val showsRestore: Boolean,
    val isLoading: Boolean,
)

/** 결제 화면·설정 줄·분석 안내 문구. 날짜는 서버 기준인 한국 시간으로 적는다. */
object BillingText {
    const val TITLE = "콜록 프리미엄"
    val BENEFITS = listOf(
        "주간·월간 리포트로 부모님 변화를 한눈에",
        "증상 타임라인으로 지난 통화를 날짜별로",
        "통화 분석 월 250분 (무료는 40분)",
    )
    const val RENEWAL = "매월 자동으로 결제되고, 언제든 해지할 수 있어요."
    const val PURCHASE = "프리미엄 시작하기"
    const val RESTORE = "구매 복원"
    const val PREPARING = "결제를 준비하고 있어요"
    const val LEARN_MORE = "프리미엄 알아보기"

    const val TERMS_URL = "https://privacy.collog.live/terms.html"
    const val PRIVACY_URL = "https://privacy.collog.live/"
    const val MANAGE_URL = "https://play.google.com/store/account/subscriptions?sku=collog_premium&package=live.collog.app"

    const val LOCKED_TITLE = "주간 리포트와 타임라인은 프리미엄에서 볼 수 있어요"
    const val LOCKED_MESSAGE = "통화가 쌓이면 부모님의 변화를 한 주, 한 달 단위로 모아 보여드려요."

    const val QUOTA_FREE = "이번 달 무료 분석 시간(40분)을 다 써서 이 통화는 분석하지 않았어요."
    const val QUOTA_PREMIUM = "이번 달 분석 시간을 다 썼어요. 다음 달 1일에 다시 채워져요."

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val dayFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

    fun day(instant: Instant): String = dayFormatter.format(instant.atZone(zone))

    /** 가격 줄에 쓰는 가격: 원화면 「13,000원」, 다른 통화면 스토어 문자열 그대로. */
    fun price(price: StorePrice): String =
        if (price.currencyCode == "KRW") String.format(Locale.US, "%,d원", price.amountMicros / 1_000_000) else price.formatted

    /** 분량을 다 써서 분석하지 않은 통화의 안내. 무료면 「프리미엄 알아보기」 버튼을 같이 둔다. */
    fun quotaMessage(isFreePlan: Boolean): String = if (isFreePlan) QUOTA_FREE else QUOTA_PREMIUM

    /** 설정 「구독 관리」 줄의 지금 요금제. */
    fun plan(entitlement: EntitlementDto): String {
        val trialEndsAt = entitlement.trialEndsAt
        return when {
            entitlement.plan == "FREE" -> "무료"
            entitlement.isTrial && trialEndsAt != null -> "프리미엄 · 무료 체험 ${day(trialEndsAt)}까지"
            else -> "프리미엄"
        }
    }

    /** 이번 달 분석 「12분 / 40분」. */
    fun usage(analysis: AnalysisUsageDto): String = "${analysis.usedSec / 60}분 / ${analysis.limitSec / 60}분"

    fun paywall(
        entitlement: EntitlementDto?,
        storeAvailable: Boolean,
        price: String?,
        isLoadingPrice: Boolean,
    ): PaywallContent {
        val payerName = entitlement?.payerName?.takeIf { it.isNotBlank() }
        // 이미 프리미엄을 결제로 쓰고 있으면 구매 버튼을 두지 않는다(가족 안 중복 결제 방지)
        val paidUntil = entitlement?.paidUntil
        val paidStatus = when {
            entitlement == null -> null
            // 해지해 둔 구독(willRenew 가 true 가 아님)은 다음 결제 대신 끝나는 날
            entitlement.paidByMe -> "프리미엄 이용 중" + when {
                paidUntil == null -> ""
                entitlement.willRenew == true -> " · 다음 결제 ${day(paidUntil)}"
                else -> " · ${day(paidUntil)}까지"
            }
            payerName != null -> "${payerName}님 덕분에 가족 모두 프리미엄을 쓰고 있어요"
            entitlement.isPaid -> "가족 모두 프리미엄을 쓰고 있어요"
            else -> null
        }
        if (paidStatus != null) {
            return PaywallContent(paidStatus, null, showsPurchase = false, showsPreparing = false, showsRestore = storeAvailable, isLoading = false)
        }
        val trialEndsAt = entitlement?.trialEndsAt
        val status = if (entitlement?.isTrial == true && trialEndsAt != null) "무료 체험 중 · ${day(trialEndsAt)}까지" else null
        val loading = storeAvailable && price == null && isLoadingPrice
        val purchasable = storeAvailable && price != null
        return PaywallContent(
            status = status,
            price = if (purchasable) "월 $price" else null,
            showsPurchase = purchasable,
            showsPreparing = !purchasable && !loading,
            showsRestore = storeAvailable,
            isLoading = loading,
        )
    }
}
