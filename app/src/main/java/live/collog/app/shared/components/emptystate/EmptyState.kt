package live.collog.app.shared.components.emptystate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import live.collog.app.shared.services.BillingText
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.LocalViewerAccent
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/** iOS `EmptyStateView`. [actionTitle]과 [action]이 모두 있을 때만 버튼을 그린다. */
@Composable
fun EmptyState(
    symbol: String,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionTitle: String? = null,
    action: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x5, vertical = Spacing.x8),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CollogIcon(symbol, size = 34.dp, color = CollogColors.gray500)

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.x1),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = CollogType.body01Semibold, color = CollogColors.gray800, textAlign = TextAlign.Center)
            if (message != null) {
                Text(message, style = CollogType.body03Medium, color = CollogColors.gray700, textAlign = TextAlign.Center)
            }
        }

        if (actionTitle != null && action != null) {
            // 내 기록을 볼 때는 주황(iOS `viewerAccent`)
            val accent = LocalViewerAccent.current
            Box(
                Modifier
                    .padding(top = Spacing.x1)
                    .pressable(onClick = action)
                    .height(40.dp)
                    .background(accent.light, CircleShape)
                    .padding(horizontal = Spacing.x4),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionTitle, style = CollogType.body02Semibold, color = accent.text)
            }
        }
    }
}

/** 가족이 「가족 공유 데이터 범위」에서 공유를 꺼 둔 항목(서버 403). 다시 시도해도 같으므로 버튼 없이 안내만(홈·리포트·타임라인). */
@Composable
fun NotSharedEmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        symbol = "info.circle",
        title = "이 가족이 공유하지 않은 항목이에요",
        modifier = modifier,
        message = "가족이 설정의 「가족 공유 데이터 범위」에서 공유를 켜면 볼 수 있어요.",
    )
}

/** 결제가 켜져 있고 가족이 무료라 리포트·타임라인 탭이 잠겼다(서버 402 도 같은 화면). 버튼은 결제 화면을 연다. */
@Composable
fun PremiumLockedState(onLearnMore: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        symbol = "lock.fill",
        title = BillingText.LOCKED_TITLE,
        modifier = modifier,
        message = BillingText.LOCKED_MESSAGE,
        actionTitle = BillingText.LEARN_MORE,
        action = onLearnMore,
    )
}
