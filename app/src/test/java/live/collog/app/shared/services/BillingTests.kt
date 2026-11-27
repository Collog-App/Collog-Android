package live.collog.app.shared.services

import android.app.Activity
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.shared.services.api.AnalysisUsageDto
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.EntitlementDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 결제 1차 약속(`private/billing_contract.md` §2-5·§3)의 앱 쪽: 서버 요금제 해석, 잠금 판단, 결제 화면, 구매·복원. */
class BillingTests {
    private class FakeStore(override val isAvailable: Boolean = true) : PurchaseStore {
        val identified = mutableListOf<String>()
        var price: StorePrice? = StorePrice("₩13,000", 13_000_000_000, "KRW")
        var priceRequests = 0
        var outcome = PurchaseOutcome.Purchased
        var failure: Exception? = null
        var purchases = 0
        var restores = 0
        var restoreHasPremium = true

        override suspend fun identify(userId: String) {
            identified += userId
        }

        override suspend fun monthlyPrice(userId: String): StorePrice? {
            priceRequests += 1
            return price
        }

        override suspend fun purchase(activity: Activity, userId: String): PurchaseOutcome {
            purchases += 1
            failure?.let { throw it }
            return outcome
        }

        override suspend fun restore(userId: String): Boolean {
            restores += 1
            failure?.let { throw it }
            return restoreHasPremium
        }
    }

    private fun withFixture(store: PurchaseStore = FakeStore(), body: suspend (TestEnvironment, Billing) -> Unit) = runTest {
        val billing = Billing(store)
        val fixture = TestEnvironment(billing = billing)
        try {
            body(fixture, billing)
        } finally {
            fixture.cleanUp()
        }
    }

    private val trial =
        """{"billingEnabled":true,"plan":"PREMIUM","source":"TRIAL","trialEndsAt":"2026-12-15T00:00:00+00:00",
        "paidUntil":null,"paidByMe":false,"payerName":null,
        "analysis":{"month":"2026-12","usedSec":1260,"limitSec":15000,"resetsAt":"2027-01-01T00:00:00+09:00"}}"""
    private val free =
        """{"billingEnabled":true,"plan":"FREE","source":null,"trialEndsAt":"2026-11-20T00:00:00+00:00",
        "paidUntil":null,"paidByMe":false,"payerName":null,
        "analysis":{"month":"2026-11","usedSec":720,"limitSec":2400,"resetsAt":"2026-12-01T00:00:00+09:00"}}"""
    private val disabled =
        """{"billingEnabled":false,"plan":"PREMIUM","source":null,"trialEndsAt":null,"paidUntil":null,
        "paidByMe":false,"payerName":null,
        "analysis":{"month":"2026-11","usedSec":600,"limitSec":15000,"resetsAt":"2026-12-01T00:00:00+09:00"}}"""
    private val paidByMe =
        """{"billingEnabled":true,"plan":"PREMIUM","source":"PAID","trialEndsAt":null,
        "paidUntil":"2026-12-27T03:00:00+00:00","paidByMe":true,"willRenew":true,"payerName":null,
        "analysis":{"month":"2026-11","usedSec":0,"limitSec":15000,"resetsAt":"2026-12-01T00:00:00+09:00"}}"""
    private val paidByMom =
        """{"billingEnabled":true,"plan":"PREMIUM","source":"PAID","trialEndsAt":null,
        "paidUntil":"2026-12-27T03:00:00+00:00","paidByMe":false,"payerName":"엄마",
        "analysis":{"month":"2026-11","usedSec":0,"limitSec":15000,"resetsAt":"2026-12-01T00:00:00+09:00"}}"""

    private fun decode(json: String): EntitlementDto = CollogJson.decodeFromString(EntitlementDto.serializer(), json)

    /** 단위 테스트의 android.jar 는 몸통이 비어 있어(기본값 반환) 실제 화면 없이 Activity 를 만들 수 있다. */
    private val activity = Activity()

    @Test
    fun entitlementIsReadInTheContractShape() {
        val entitlement = decode(trial)
        assertTrue(entitlement.billingEnabled)
        assertEquals("PREMIUM", entitlement.plan)
        assertTrue(entitlement.isTrial)
        assertFalse(entitlement.isFreePlan)
        assertEquals(Instant.parse("2026-12-15T00:00:00Z"), entitlement.trialEndsAt)
        assertEquals(AnalysisUsageDto("2026-12", 1260, 15000, Instant.parse("2026-12-31T15:00:00Z")), entitlement.analysis)
        assertTrue(decode(free).isFreePlan)
        // 결제가 꺼져 있으면 무료일 수 없다(모두 프리미엄)
        assertFalse(decode(disabled).isFreePlan)
    }

    @Test
    fun lockAndEntryPointsFollowTheServerPlanOnly() = withFixture { fixture, billing ->
        // 아직 모르면(서버에 API 가 없어도) 아무것도 막지 않고 진입점도 없다
        assertFalse(billing.isLocked)
        assertFalse(billing.isEnabled)

        var body = free
        fixture.respond = { 200 to body }
        billing.load(fixture.environment)
        assertTrue(billing.isLocked)
        assertTrue(billing.isEnabled)

        body = trial
        billing.load(fixture.environment)
        assertFalse(billing.isLocked)
        assertTrue(billing.isEnabled)

        body = disabled
        billing.load(fixture.environment)
        assertFalse(billing.isLocked)
        assertFalse(billing.isEnabled)
    }

    @Test
    fun loadAsksTheEntitlementAndKeepsTheLastValueOnFailure() = withFixture { fixture, billing ->
        fixture.respond = { 200 to free }
        billing.load(fixture.environment)
        val request = fixture.requests.single()
        assertEquals("/v1/account/entitlement", request.url.encodedPath)
        assertEquals("GET", request.method)

        fixture.respond = { 503 to """{"code":"SERVICE_UNAVAILABLE","message":"잠시 후 다시 시도해주세요"}""" }
        billing.load(fixture.environment)
        assertTrue(billing.isLocked)
    }

    @Test
    fun guestUsesNeitherServerNorStore() {
        val store = FakeStore()
        withFixture(store) { fixture, billing ->
            fixture.environment.settings.isGuestMode = true
            billing.identify(fixture.environment)
            billing.load(fixture.environment)
            billing.purchase(activity, fixture.environment)

            assertTrue(fixture.requests.isEmpty())
            assertTrue(store.identified.isEmpty())
            assertEquals(0, store.purchases)
            assertNull(billing.entitlement)
        }
    }

    @Test
    fun accountChangeForgetsThePreviousPlan() = withFixture { fixture, billing ->
        fixture.respond = { 200 to free }
        billing.load(fixture.environment)
        billing.presentPaywall()
        assertTrue(billing.isLocked)

        fixture.environment.session.signOut()
        assertNull(billing.entitlement)
        assertFalse(billing.isLocked)
        assertFalse(billing.isPaywallPresented)
    }

    @Test
    fun storeIsTurnedOnWithTheServerUserIdOnlyAfterTheServerEnablesBilling() {
        val store = FakeStore()
        withFixture(store) { fixture, billing ->
            var body = disabled
            fixture.respond = { 200 to body }
            // 결제가 꺼져 있으면 키가 있어도 RevenueCat 에 사용자 ID 를 보내지 않는다
            billing.identify(fixture.environment)
            billing.load(fixture.environment)
            billing.preparePaywall(fixture.environment)
            billing.purchase(activity, fixture.environment)
            billing.restore(fixture.environment)
            assertTrue(store.identified.isEmpty())
            assertEquals(0, store.priceRequests)
            assertEquals(0, store.purchases)
            assertEquals(0, store.restores)

            body = trial
            billing.load(fixture.environment)
            assertEquals(listOf("me"), store.identified)
        }
        val noKey = FakeStore(isAvailable = false)
        withFixture(noKey) { fixture, billing ->
            fixture.respond = { 200 to trial }
            billing.load(fixture.environment)
            billing.identify(fixture.environment)
            assertTrue(noKey.identified.isEmpty())
        }
    }

    @Test
    fun purchaseAsksTheServerToRereadTheSubscription() = withFixture { fixture, billing ->
        fixture.respond = { request ->
            if (request.url.encodedPath == "/v1/account/entitlement/refresh") 200 to paidByMe else 200 to free
        }
        billing.load(fixture.environment)
        fixture.requests.clear()

        billing.purchase(activity, fixture.environment)

        val request = fixture.requests.single()
        assertEquals("/v1/account/entitlement/refresh", request.url.encodedPath)
        assertEquals("POST", request.method)
        assertTrue(billing.entitlement!!.paidByMe)
        assertFalse(billing.isLocked)
        assertNull(billing.message)
        assertFalse(billing.isWorking)
    }

    @Test
    fun purchaseIsNotShownAsFailedWhenTheServerCannotConfirmItYet() = withFixture { fixture, billing ->
        var current = free
        fixture.respond = { request ->
            if (request.url.encodedPath == "/v1/account/entitlement/refresh") {
                502 to """{"code":"UPSTREAM_ERROR","message":"RevenueCat 확인에 실패했어요"}"""
            } else {
                200 to current
            }
        }
        billing.load(fixture.environment)
        fixture.paths.clear()

        billing.purchase(activity, fixture.environment)

        // 다시 한 번 읽고, 아직 유료로 안 보이면 실패 대신 반영이 늦다는 안내
        assertEquals(listOf("/v1/account/entitlement/refresh", "/v1/account/entitlement"), fixture.paths.toList())
        assertNull(billing.message)
        assertEquals("결제는 완료됐어요. 반영까지 잠시 걸릴 수 있어요.", billing.notice)

        // 그사이 웹훅으로 반영됐으면 안내 없이 바뀐 요금제
        current = paidByMe
        billing.purchase(activity, fixture.environment)
        assertNull(billing.notice)
        assertTrue(billing.entitlement!!.paidByMe)
    }

    @Test
    fun purchaseConfirmedButNotYetPaidSaysItIsBeingApplied() = withFixture { fixture, billing ->
        // refresh 가 200 이어도 아직 유료로 안 보이면(웹훅 전) 반영 중 안내
        fixture.respond = { 200 to free }
        billing.load(fixture.environment)
        billing.purchase(activity, fixture.environment)
        assertEquals(Billing.PURCHASE_PENDING, billing.notice)
        assertNull(billing.message)
    }

    @Test
    fun pendingPaymentAndAlreadyOwnedHaveTheirOwnWordsAndRereadThePlan() {
        val store = FakeStore()
        withFixture(store) { fixture, billing ->
            fixture.respond = { 200 to free }
            billing.load(fixture.environment)

            fixture.paths.clear()
            store.outcome = PurchaseOutcome.PaymentPending
            billing.purchase(activity, fixture.environment)
            assertEquals("결제 승인을 기다리고 있어요. 승인되면 자동으로 반영돼요.", billing.notice)
            assertNull(billing.message)
            assertEquals(listOf("/v1/account/entitlement"), fixture.paths.toList())

            fixture.paths.clear()
            store.outcome = PurchaseOutcome.AlreadyOwned
            billing.purchase(activity, fixture.environment)
            assertEquals("이미 구매한 구독이 있어요. 구매 복원을 눌러 주세요.", billing.message)
            assertNull(billing.notice)
            assertEquals(listOf("/v1/account/entitlement"), fixture.paths.toList())
        }
    }

    @Test
    fun restoreWithoutAnActivePremiumSaysThereIsNothingToRestore() {
        val store = FakeStore().apply { restoreHasPremium = false }
        withFixture(store) { fixture, billing ->
            fixture.respond = { 200 to free }
            billing.load(fixture.environment)
            fixture.paths.clear()

            billing.restore(fixture.environment)

            assertEquals("복원할 구매가 없어요.", billing.notice)
            assertNull(billing.message)
            assertTrue(fixture.paths.isEmpty())
            assertTrue(billing.isLocked)
        }
    }

    @Test
    fun aSlowPlanReadStartedEarlierDoesNotOverwriteTheRefreshedPlan() = withFixture { fixture, billing ->
        fixture.respond = { 200 to free }
        billing.load(fixture.environment)

        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.respond = { request ->
            if (request.url.encodedPath == "/v1/account/entitlement") {
                received.countDown()
                release.await(5, TimeUnit.SECONDS)
                200 to free
            } else {
                200 to paidByMe
            }
        }
        // 앱으로 돌아올 때의 GET 이 늦게 오는 사이 구매가 끝나 refresh 가 유료를 받는다
        val slowRead = CoroutineScope(Dispatchers.Default).launch { billing.load(fixture.environment) }
        assertTrue(received.await(5, TimeUnit.SECONDS))
        billing.purchase(activity, fixture.environment)
        assertTrue(billing.entitlement!!.paidByMe)

        release.countDown()
        slowRead.join()
        assertTrue(billing.entitlement!!.paidByMe)
        assertFalse(billing.isLocked)
    }

    @Test
    fun paywallShowsLoadingFromTheStartAndAccountChangeClearsIt() = withFixture { fixture, billing ->
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.respond = {
            received.countDown()
            release.await(5, TimeUnit.SECONDS)
            200 to free
        }
        val preparing = CoroutineScope(Dispatchers.Default).launch { billing.preparePaywall(fixture.environment) }
        assertTrue(received.await(5, TimeUnit.SECONDS))
        // 요금제를 읽는 동안부터 로딩(「준비하고 있어요」가 먼저 비치지 않게)
        assertTrue(BillingText.paywall(billing.entitlement, billing.isStoreAvailable, billing.price, billing.isLoadingPrice).isLoading)

        fixture.environment.session.signOut()
        assertFalse(billing.isLoadingPrice)
        release.countDown()
        preparing.join()
        assertFalse(billing.isLoadingPrice)
        assertNull(billing.entitlement)
        assertNull(billing.price)
    }

    @Test
    fun cancelledPurchaseLeavesTheScreenAsItWas() {
        val store = FakeStore().apply { outcome = PurchaseOutcome.Cancelled }
        withFixture(store) { fixture, billing ->
            fixture.respond = { 200 to free }
            billing.load(fixture.environment)
            fixture.requests.clear()
            fixture.respond = { fail("취소하면 서버에 묻지 않는다"); error("unreachable") }
            billing.purchase(activity, fixture.environment)
            assertEquals(1, store.purchases)
            assertNull(billing.message)
            assertTrue(fixture.requests.isEmpty())
        }
    }

    @Test
    fun failedPurchaseShowsTheContractMessage() {
        val store = FakeStore().apply { failure = IllegalStateException("store") }
        withFixture(store) { fixture, billing ->
            fixture.respond = { 200 to free }
            billing.load(fixture.environment)
            fixture.paths.clear()
            billing.purchase(activity, fixture.environment)
            assertEquals("결제를 마치지 못했어요. 잠시 뒤 다시 시도해 주세요.", billing.message)
            // 결제가 됐을 수도 있어 요금제를 한 번 더 읽는다(refresh 는 부르지 않는다)
            assertEquals(listOf("/v1/account/entitlement"), fixture.paths.toList())
            assertFalse(billing.isWorking)
        }
    }

    @Test
    fun restoreRefreshesAndHasItsOwnFailureMessage() {
        val store = FakeStore()
        withFixture(store) { fixture, billing ->
            fixture.respond = { 200 to paidByMe }
            billing.load(fixture.environment)
            fixture.paths.clear()
            billing.restore(fixture.environment)
            assertEquals(listOf("/v1/account/entitlement/refresh"), fixture.paths.toList())
            assertTrue(billing.entitlement!!.paidByMe)

            store.failure = IllegalStateException("store")
            billing.restore(fixture.environment)
            assertEquals(Billing.RESTORE_FAILED, billing.message)
        }
    }

    @Test
    fun withoutTheKeyThePaywallOnlySaysItIsPreparing() {
        withFixture(PurchaseStore.Unavailable) { fixture, billing ->
            fixture.respond = { 200 to trial }
            billing.preparePaywall(fixture.environment)
            billing.purchase(activity, fixture.environment)
            // 요금제만 읽고 구매는 시도하지 않는다
            assertEquals(listOf("/v1/account/entitlement"), fixture.paths.toList())

            val content = BillingText.paywall(billing.entitlement, billing.isStoreAvailable, billing.price, billing.isLoadingPrice)
            assertTrue(content.showsPreparing)
            assertFalse(content.showsPurchase)
            assertFalse(content.showsRestore)
            assertNull(content.price)
        }
    }

    @Test
    fun paywallLoadsThePriceOnce() {
        val store = FakeStore()
        withFixture(store) { fixture, billing ->
            fixture.respond = { 200 to free }
            billing.preparePaywall(fixture.environment)
            billing.preparePaywall(fixture.environment)
            // 원화는 스토어 문자열(「₩13,000」) 대신 「13,000원」
            assertEquals("13,000원", billing.price)
            assertEquals(1, store.priceRequests)
        }
    }

    @Test
    fun familyPaidBySomeoneElseHidesThePurchaseButton() {
        val content = BillingText.paywall(decode(paidByMom), storeAvailable = true, price = "13,000원", isLoadingPrice = false)
        assertEquals("엄마님 덕분에 가족 모두 프리미엄을 쓰고 있어요", content.status)
        assertFalse(content.showsPurchase)
        assertFalse(content.showsPreparing)
        assertNull(content.price)
    }

    @Test
    fun myOwnSubscriptionShowsTheNextBillingDay() {
        val content = BillingText.paywall(decode(paidByMe), storeAvailable = true, price = "13,000원", isLoadingPrice = false)
        assertEquals("프리미엄 이용 중 · 다음 결제 12월 27일", content.status)
        assertFalse(content.showsPurchase)
        assertNull(content.price)
        assertTrue(content.showsRestore)

        // 해지해 둔 구독은 다음 결제 대신 끝나는 날
        val cancelled = decode(paidByMe.replace("\"willRenew\":true", "\"willRenew\":false"))
        assertEquals(
            "프리미엄 이용 중 · 12월 27일까지",
            BillingText.paywall(cancelled, storeAvailable = true, price = "13,000원", isLoadingPrice = false).status,
        )
    }

    @Test
    fun paidFamilyWithoutAPayerNameStillHidesThePurchaseButton() {
        val unnamed = decode(paidByMom.replace("\"payerName\":\"엄마\"", "\"payerName\":null"))
        val content = BillingText.paywall(unnamed, storeAvailable = true, price = "13,000원", isLoadingPrice = false)
        assertEquals("가족 모두 프리미엄을 쓰고 있어요", content.status)
        assertFalse(content.showsPurchase)
    }

    @Test
    fun wonPricesAreWrittenInKoreanAndOtherCurrenciesAsTheStoreSays() {
        assertEquals("13,000원", BillingText.price(StorePrice("₩13,000", 13_000_000_000, "KRW")))
        assertEquals("$9.99", BillingText.price(StorePrice("$9.99", 9_990_000, "USD")))
    }

    @Test
    fun trialOffersThePurchaseWithTheStorePrice() {
        val entitlement = decode(trial)
        val ready = BillingText.paywall(entitlement, storeAvailable = true, price = "13,000원", isLoadingPrice = false)
        assertEquals("무료 체험 중 · 12월 15일까지", ready.status)
        assertEquals("월 13,000원", ready.price)
        assertTrue(ready.showsPurchase)
        assertFalse(ready.showsPreparing)

        val loading = BillingText.paywall(entitlement, storeAvailable = true, price = null, isLoadingPrice = true)
        assertTrue(loading.isLoading)
        assertFalse(loading.showsPreparing)
        assertFalse(loading.showsPurchase)

        // 상품을 못 받았으면 살 수 없다
        val missing = BillingText.paywall(entitlement, storeAvailable = true, price = null, isLoadingPrice = false)
        assertTrue(missing.showsPreparing)
        assertFalse(missing.showsPurchase)
    }

    @Test
    fun settingsRowShowsPlanAndThisMonthsAnalysis() {
        assertEquals("프리미엄 · 무료 체험 12월 15일까지", BillingText.plan(decode(trial)))
        assertEquals("무료", BillingText.plan(decode(free)))
        assertEquals("프리미엄", BillingText.plan(decode(paidByMe)))
        assertEquals("21분 / 250분", BillingText.usage(decode(trial).analysis!!))
        assertEquals("12분 / 40분", BillingText.usage(decode(free).analysis!!))
    }

    @Test
    fun quotaMessageDependsOnThePlan() {
        assertEquals("이번 달 무료 분석 시간(40분)을 다 써서 이 통화는 분석하지 않았어요.", BillingText.quotaMessage(isFreePlan = true))
        assertEquals("이번 달 분석 시간을 다 썼어요. 다음 달 1일에 다시 채워져요.", BillingText.quotaMessage(isFreePlan = false))
    }

    @Test
    fun premiumRequiredIsNotSharingTurnedOff() = withFixture { fixture, _ ->
        // 서버가 코드를 빠뜨려도(기본값 REQUEST_FAILED) 402 는 프리미엄 잠금이다
        fixture.respond = { 402 to """{"code":"REQUEST_FAILED","message":"주간 리포트는 프리미엄에서 볼 수 있어요."}""" }
        val premium = try {
            fixture.environment.api.report("mom")
            null
        } catch (e: ApiError) {
            e
        }
        assertTrue(premium!!.isPremiumRequired)

        fixture.respond = { 403 to """{"code":"SCOPE_DENIED","message":"열람 권한이 없는 항목입니다"}""" }
        val forbidden = try {
            fixture.environment.api.report("mom")
            null
        } catch (e: ApiError) {
            e
        }
        assertFalse(forbidden!!.isPremiumRequired)
        // 403 공유 판정(accessCheck)은 402 를 공유 꺼짐으로 보지 않는다
        fixture.respond = { 402 to """{"code":"PREMIUM_REQUIRED","message":"주간 리포트는 프리미엄에서 볼 수 있어요."}""" }
        assertNull(ApiError.accessCheck { fixture.environment.api.report("mom") })
    }
}
