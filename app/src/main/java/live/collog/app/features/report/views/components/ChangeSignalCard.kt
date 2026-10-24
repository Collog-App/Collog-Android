package live.collog.app.features.report.views.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import live.collog.app.features.report.models.ChangeSignalItem
import live.collog.app.shared.components.badge.Badge
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface

/** iOS `ChangeSignalCardView`. */
@Composable
fun ChangeSignalCard(signals: List<ChangeSignalItem>, modifier: Modifier = Modifier) {
    Column(modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Text("변화 신호", style = CollogType.caption01Medium, color = CollogColors.gray800)

        if (signals.isEmpty()) {
            Text("이번 주에는 눈에 띄는 변화가 없었어요.", style = CollogType.body02Medium, color = CollogColors.gray800)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                signals.forEachIndexed { index, signal ->
                    if (index > 0) DividerLine()
                    SignalRow(signal)
                }
            }
        }
    }
}

@Composable
private fun SignalRow(signal: ChangeSignalItem) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.CenterVertically) {
            Text(signal.metricName, style = CollogType.body01Semibold, color = CollogColors.gray900)

            if (signal.isPromoted) {
                Badge(
                    label = "지속",
                    foregroundColor = CollogColors.orange600,
                    backgroundColor = CollogColors.orangeLight,
                )
            }
        }

        Text(signal.summary, style = CollogType.body02Medium, color = CollogColors.gray800)
    }
}
