package live.collog.app.features.timeline.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

/** iOS `WeekNavigatorView`: 좌우 화살표로 주를 넘기는 막대. 새 피그마 05 「주차 이동」: 구분선 없음, 제목 15/700, 기간 12/400. */
@Composable
fun WeekNavigator(
    title: String,
    rangeText: String,
    modifier: Modifier = Modifier,
    canGoForward: Boolean = true,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.x5, vertical = Spacing.x2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton("chevron.left", enabled = true, onClick = onPrevious)

        Spacer(Modifier.weight(1f).widthIn(min = Spacing.x2))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = specText(Pretendard.Bold, 15f))
            Text(rangeText, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        }

        Spacer(Modifier.weight(1f).widthIn(min = Spacing.x2))

        ArrowButton("chevron.right", enabled = canGoForward, onClick = onNext)
    }
}

@Composable
private fun ArrowButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CollogIcon(symbol, size = 23.dp, color = if (enabled) SpecColors.sub else Color(0xFFD5D9DE))
    }
}

/** iOS `TimelineWeekHeader`: 타임라인에서 주마다 붙는 머리말. [rangeText]가 있으면 제목 옆에 적는다. 새 피그마 16/700 + 전폭 구분선. */
@Composable
fun TimelineWeekHeader(title: String, modifier: Modifier = Modifier, rangeText: String? = null) {
    Box(modifier.fillMaxWidth().background(CollogColors.gray50)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.x5, end = Spacing.x5, top = 15.dp, bottom = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(title, Modifier.alignByBaseline(), style = specText(Pretendard.Bold, 16f))
            if (rangeText != null) {
                Text(
                    rangeText,
                    Modifier.alignByBaseline(),
                    style = specText(Pretendard.Regular, 12f, SpecColors.muted),
                )
            }
        }
        SpecDivider(Modifier.align(Alignment.BottomCenter))
    }
}
