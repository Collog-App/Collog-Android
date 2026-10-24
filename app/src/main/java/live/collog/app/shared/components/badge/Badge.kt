package live.collog.app.shared.components.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing

/** iOS `BadgeView`. */
@Composable
fun Badge(
    label: String,
    modifier: Modifier = Modifier,
    foregroundColor: Color = CollogColors.blue600,
    backgroundColor: Color = CollogColors.blue100,
) {
    Box(
        modifier
            .height(26.dp)
            .background(backgroundColor, RoundedCornerShape(Radius.badgeMedium))
            .padding(horizontal = Spacing.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CollogType.caption01Semibold, color = foregroundColor)
    }
}
