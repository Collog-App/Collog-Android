package live.collog.app.shared.components.chip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.IconSize
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/** iOS `FilterChipView`: 누르면 [onTap]. */
@Composable
fun FilterChip(label: String, modifier: Modifier = Modifier, onTap: () -> Unit = {}) {
    FilterChipLabel(label, modifier.pressable(onClick = onTap))
}

/** iOS `FilterChipLabelView`: 누를 수 없는 칩 모양. */
@Composable
fun FilterChipLabel(label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.chip)
    Row(
        modifier
            .background(CollogColors.gray200, shape)
            .border(1.dp, CollogColors.gray300, shape)
            .padding(horizontal = Spacing.x3, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CollogType.body02Medium, color = CollogColors.gray800)
        CollogIcon("chevron.down", size = IconSize.small, color = CollogColors.gray700)
    }
}
