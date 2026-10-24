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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.IconSize
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/** iOS `WeekNavigatorView`: 좌우 화살표로 주를 넘기는 막대. */
@Composable
fun WeekNavigator(
    title: String,
    rangeText: String,
    modifier: Modifier = Modifier,
    canGoForward: Boolean = true,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.x5, vertical = Spacing.x2)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.x2),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowButton("chevron.left", enabled = true, onClick = onPrevious)

            Spacer(Modifier.weight(1f).widthIn(min = Spacing.x2))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = CollogType.body01Semibold, color = CollogColors.gray900)
                Text(rangeText, style = CollogType.body03Medium, color = CollogColors.gray800)
            }

            Spacer(Modifier.weight(1f).widthIn(min = Spacing.x2))

            ArrowButton("chevron.right", enabled = canGoForward, onClick = onNext)
        }
        DividerLine(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ArrowButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CollogIcon(symbol, size = IconSize.medium, color = if (enabled) CollogColors.gray800 else CollogColors.gray400)
    }
}

/** iOS `TimelineWeekHeader`: 타임라인에서 주마다 붙는 머리말. [rangeText]가 있으면 제목 옆에 적는다. */
@Composable
fun TimelineWeekHeader(title: String, modifier: Modifier = Modifier, rangeText: String? = null) {
    Box(modifier.fillMaxWidth().background(CollogColors.gray50)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
            verticalAlignment = Alignment.Top,
        ) {
            Text(title, Modifier.alignByBaseline(), style = CollogType.body01Semibold, color = CollogColors.gray900)
            if (rangeText != null) {
                Text(
                    rangeText,
                    Modifier.alignByBaseline(),
                    style = CollogType.body03Medium,
                    color = CollogColors.gray700,
                )
            }
        }
        DividerLine(Modifier.align(Alignment.BottomCenter))
    }
}
