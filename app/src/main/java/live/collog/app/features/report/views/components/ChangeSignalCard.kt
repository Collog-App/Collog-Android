package live.collog.app.features.report.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.collog.app.features.report.models.ChangeSignalItem
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText

/** iOS `ChangeSignalCardView`. 새 피그마 05 「카드/변화 신호」(`643:6174`). */
@Composable
fun ChangeSignalCard(signals: List<ChangeSignalItem>, modifier: Modifier = Modifier) {
    Column(modifier.specCard()) {
        Text("변화 신호", style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        Spacer(Modifier.height(18.dp))

        if (signals.isEmpty()) {
            Text(
                "이번 주에는 눈에 띄는 변화가 없었어요.",
                style = specText(Pretendard.Regular, 13f, SpecColors.sub),
            )
        } else {
            signals.forEachIndexed { index, signal ->
                if (index > 0) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(SpecColors.line))
                    Spacer(Modifier.height(11.dp))
                }
                SignalRow(signal)
            }
        }
    }
}

@Composable
private fun SignalRow(signal: ChangeSignalItem) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(signal.metricName, style = specText(Pretendard.Bold, 16f))

            if (signal.isPromoted) {
                Text(
                    "지속",
                    style = specText(Pretendard.Medium, 11f, SpecColors.orange),
                    modifier = Modifier
                        .background(SpecColors.orangeSurface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 5.5.dp),
                )
            }
        }

        Text(signal.summary, style = specText(Pretendard.Regular, 13f, SpecColors.sub))
    }
}
