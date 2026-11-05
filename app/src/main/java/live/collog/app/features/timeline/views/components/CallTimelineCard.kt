package live.collog.app.features.timeline.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import live.collog.app.features.timeline.models.CallTimelineEntry
import live.collog.app.shared.components.chart.KeywordTimeline
import live.collog.app.shared.components.chart.RangeGaugeRow
import live.collog.app.shared.components.spec.SpecNotePlacement
import live.collog.app.shared.components.spec.SpecStatRow
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText

/** iOS `CallTimelineDateHeader`: "8월 14일 금요일"에서 월을 떼고 "14일 금요일"만 보여 준다. 새 피그마 15/700. */
@Composable
fun CallTimelineDateHeader(entry: CallTimelineEntry, modifier: Modifier = Modifier) {
    val monthEnd = entry.dateText.indexOf("월 ")
    val compact = if (monthEnd < 0) entry.dateText else entry.dateText.substring(monthEnd + 2)
    Text(compact, modifier.fillMaxWidth(), style = specText(Pretendard.Bold, 15f))
}

/** iOS `CallTimelineRail`: 카드 왼쪽의 짧은 막대(13x3)와 세로선(`#e4e7ea`). 부모 높이를 채운다. */
@Composable
fun CallTimelineRail(isLast: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(
            Modifier
                .padding(top = 9.dp)
                .width(13.dp)
                .height(3.dp)
                .background(SpecColors.node, RoundedCornerShape(2.dp)),
        )
        Spacer(
            Modifier
                .width(1.dp)
                .weight(1f)
                .background(if (isLast) SpecColors.rail.copy(alpha = 0.7f) else SpecColors.rail),
        )
    }
}

/** iOS `CallTimelineCardView`: 통화 하나의 요약 카드. 새 피그마 08 「카드/8월 18일」(`643:6482`). */
@Composable
fun CallTimelineCard(entry: CallTimelineEntry, modifier: Modifier = Modifier) {
    Column(modifier.specCard(padding = 20.dp)) {
        SpecStatRow(entry.summaryStats, notePlacement = SpecNotePlacement.Inline)

        Spacer(Modifier.height(14.dp))
        SpecDivider()
        Spacer(Modifier.height(10.dp))

        Text(entry.story, style = specText(Pretendard.Regular, 14f).copy(lineHeight = 24.sp))

        if (entry.keywords.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            KeywordTimeline(entry.keywords)
        }

        if (entry.gauges.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SpecDivider()
            Spacer(Modifier.height(17.dp))
            RangeGaugeRow(entry.gauges)
        }

        if (entry.counts.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SpecDivider()
            Spacer(Modifier.height(15.dp))
            CountRow(entry.counts)
        }
    }
}

@Composable
private fun CountRow(counts: List<CallStat>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        counts.forEach { stat ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stat.label,
                    Modifier.alignByBaseline(),
                    style = specText(Pretendard.Regular, 12f, SpecColors.muted),
                )
                Text(
                    "${stat.value}${stat.unit}",
                    Modifier.alignByBaseline(),
                    style = specText(Pretendard.Bold, 15f),
                )
            }

            if (stat.id != counts.last().id) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
