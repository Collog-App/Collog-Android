package live.collog.app.features.billing.views

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.views.components.SettingsActionButton
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.services.BillingText
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

private val BodyStyle = pretendard(Pretendard.Medium, 17f, CollogColors.gray800)

/**
 * 결제 화면(약속 §3, 직접 만든 화면 — RevenueCat Paywalls UI 안 씀). 설정 「구독 관리」·잠금 화면·분량 초과 안내의
 * 「프리미엄 알아보기」가 [live.collog.app.shared.services.Billing.presentPaywall]로 연다. 탭 화면(MainTabs)에 하나만 둔다.
 * 버튼·상태 줄은 서버 요금제로 고르고([BillingText.paywall]), 가격만 스토어 상품에서 받는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallSheetHost() {
    val environment = LocalAppEnvironment.current
    val billing = environment.billing
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    // 시트가 닫혀도 구매 뒤 서버 갱신은 끝까지 돌게 시트 밖 범위를 쓴다
    val scope = rememberCoroutineScope()
    if (!billing.isPaywallPresented) return

    LaunchedEffect(Unit) { billing.preparePaywall(environment) }
    val content = BillingText.paywall(billing.entitlement, billing.isStoreAvailable, billing.price, billing.isLoadingPrice)

    ModalBottomSheet(
        onDismissRequest = { billing.dismissPaywall() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CollogColors.gray50,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5)
                .padding(top = Spacing.x2, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            Text(BillingText.TITLE, style = CollogType.headline02, color = CollogColors.gray900)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                BillingText.BENEFITS.forEach { benefit ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.CenterVertically) {
                        CollogIcon("checkmark", size = 20.dp, color = CollogColors.greenNormal)
                        Text(benefit, style = BodyStyle, modifier = Modifier.weight(1f))
                    }
                }
            }

            content.status?.let { Text(it, style = CollogType.body01Semibold, color = CollogColors.greenDark) }

            if (content.isLoading) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CollogProgress() }
            }
            if (content.showsPreparing) {
                Text(BillingText.PREPARING, style = CollogType.body01Medium, color = CollogColors.gray700)
            }
            content.price?.let { price ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                    Text(price, style = pretendard(Pretendard.Bold, 20f, CollogColors.gray900))
                    Text(BillingText.RENEWAL, style = CollogType.body02Regular, color = CollogColors.gray700)
                }
            }
            if (content.showsPurchase) {
                SettingsActionButton(
                    BillingText.PURCHASE,
                    Modifier.fillMaxWidth().padding(top = Spacing.x2),
                    enabled = !billing.isWorking && activity != null,
                ) {
                    activity?.let { scope.launch { billing.purchase(it, environment) } }
                }
            }
            billing.message?.let { Text(it, style = CollogType.body02Medium, color = CollogColors.red500) }

            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.x2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.x3),
            ) {
                if (content.showsRestore) {
                    SystemTextButton(BillingText.RESTORE, enabled = !billing.isWorking) {
                        scope.launch { billing.restore(environment) }
                    }
                }
                SystemTextButton("구독 관리·해지") { uriHandler.openUri(BillingText.MANAGE_URL) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x5)) {
                    SystemTextButton("이용약관") { uriHandler.openUri(BillingText.TERMS_URL) }
                    SystemTextButton("개인정보처리방침") { uriHandler.openUri(BillingText.PRIVACY_URL) }
                }
            }
        }
    }
}
