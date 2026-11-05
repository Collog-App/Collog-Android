package live.collog.app.features.report.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import live.collog.app.features.report.models.ConversationGroup
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText

/** iOS `ConversationCardView`. 새 피그마 07 「카드/나눈 이야기」(`643:6277`). */
@Composable
fun ConversationCard(groups: List<ConversationGroup>, modifier: Modifier = Modifier) {
    Column(modifier.specCard()) {
        Text("나눈 이야기", style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        Spacer(Modifier.height(16.dp))

        groups.forEachIndexed { index, group ->
            if (index > 0) {
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(SpecColors.line))
                Spacer(Modifier.height(13.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CollogIcon(group.symbol, size = 15.dp, color = Color(0xFF7B828B))
                Text(group.category, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.items.forEach { Text(it, style = specText(Pretendard.Medium, 14f)) }
            }
        }
    }
}
