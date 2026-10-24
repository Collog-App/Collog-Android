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
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
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
            Box(
                Modifier
                    .padding(top = Spacing.x1)
                    .pressable(onClick = action)
                    .height(40.dp)
                    .background(CollogColors.greenLight, CircleShape)
                    .padding(horizontal = Spacing.x4),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionTitle, style = CollogType.body02Semibold, color = CollogColors.greenDark)
            }
        }
    }
}
