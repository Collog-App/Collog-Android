package live.collog.app.shared.services

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore

/**
 * RevenueCat Android SDK 11.4.3. appUserID 는 로그인한 서버 사용자 ID(UUID)다.
 * 처음 쓸 때 그 ID 로 configure 하고, 이미 configure 됐는데 계정이 바뀌었으면 logIn 한다. logOut 은 부르지 않는다(익명 ID 가 생김).
 * [apiKey]가 비어 있으면 아무것도 하지 않는다.
 */
class RevenueCatStore(context: Context, private val apiKey: String) : PurchaseStore {
    private val context = context.applicationContext

    override val isAvailable: Boolean get() = apiKey.isNotBlank()

    override suspend fun identify(userId: String) {
        if (!isAvailable) return
        if (!Purchases.isConfigured) {
            Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).appUserID(userId).build())
        } else if (Purchases.sharedInstance.appUserID != userId) {
            Purchases.sharedInstance.awaitLogIn(userId)
        }
    }

    override suspend fun monthlyPrice(userId: String): String? = monthlyPackage(userId)?.product?.price?.formatted

    override suspend fun purchase(activity: Activity, userId: String): PurchaseOutcome {
        val monthly = monthlyPackage(userId) ?: throw IllegalStateException("구독 상품이 없어요")
        return try {
            Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, monthly).build())
            PurchaseOutcome.Purchased
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseOutcome.Cancelled else throw e
        }
    }

    override suspend fun restore(userId: String) {
        identify(userId)
        Purchases.sharedInstance.awaitRestore()
    }

    /** offering `default`(없으면 대시보드의 current)의 `$rc_monthly` 패키지. 구매 전에 사용자를 다시 맞춘다. */
    private suspend fun monthlyPackage(userId: String): Package? {
        identify(userId)
        if (!Purchases.isConfigured) return null
        val offerings = Purchases.sharedInstance.awaitOfferings()
        val offering = offerings.getOffering(OFFERING_ID) ?: offerings.current ?: return null
        return offering.monthly
    }

    private companion object {
        const val OFFERING_ID = "default"
    }
}
