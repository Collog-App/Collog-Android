package live.collog.app.features.report.views.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.collog.app.features.report.models.ConversationGroup
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface

/** iOS `ConversationCardView`. */
@Composable
fun ConversationCard(groups: List<ConversationGroup>, modifier: Modifier = Modifier) {
    Column(modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Text("나눈 이야기", style = CollogType.caption01Medium, color = CollogColors.gray800)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
            groups.forEachIndexed { index, group ->
                if (index > 0) DividerLine()

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.CenterVertically) {
                        CollogIcon(group.symbol, size = 15.dp, color = CollogColors.gray700)
                        Text(group.category, style = CollogType.caption01Semibold, color = CollogColors.gray700)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                        group.items.forEach { item ->
                            Text(item, style = CollogType.body02Medium, color = CollogColors.gray900)
                        }
                    }
                }
            }
        }
    }
}
