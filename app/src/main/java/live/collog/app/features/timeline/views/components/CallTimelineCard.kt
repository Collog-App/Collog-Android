package live.collog.app.features.timeline.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import live.collog.app.features.timeline.models.CallTimelineEntry
import live.collog.app.shared.components.chart.KeywordTimeline
import live.collog.app.shared.components.chart.RangeGaugeRow
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.ui.designsystem.Axis
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing

/** iOS `CallTimelineDateHeader`: "8월 14일 금요일"에서 월을 떼고 "14일 금요일"만 보여 준다. */
@Composable
fun CallTimelineDateHeader(entry: CallTimelineEntry, modifier: Modifier = Modifier) {
    val monthEnd = entry.dateText.indexOf("월 ")
    val compact = if (monthEnd < 0) entry.dateText else entry.dateText.substring(monthEnd + 2)
    Text(
        compact,
        modifier.fillMaxWidth(),
        style = CollogType.body01Semibold,
        color = CollogColors.gray900,
    )
}

/** iOS `CallTimelineRail`: 카드 왼쪽의 짧은 막대와 세로선. 부모 높이를 채운다. */
@Composable
fun CallTimelineRail(isLast: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.width(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(
            Modifier
                .padding(top = 9.dp)
                .width(12.dp)
                .height(3.dp)
                .background(CollogColors.gray800, RoundedCornerShape(1.5.dp)),
        )
        Spacer(
            Modifier
                .width(1.dp)
                .weight(1f)
                .background(if (isLast) CollogColors.gray300.copy(alpha = 0.7f) else CollogColors.gray300),
        )
    }
}

/** iOS `CallTimelineCardView`: 통화 하나의 요약 카드. */
@Composable
fun CallTimelineCard(entry: CallTimelineEntry, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.btnSmall)
    Column(
        modifier
            .fillMaxWidth()
            .background(CollogColors.gray00, shape)
            .border(1.dp, CollogColors.gray200, shape)
            .padding(Spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Spacing.x4),
    ) {
        MetricRow(entry.summaryStats)

        DividerLine()

        Text(entry.story, style = CollogType.body02Medium, color = CollogColors.gray900)

        if (entry.keywords.isNotEmpty()) {
            KeywordTimeline(entry.keywords, Modifier.padding(top = Spacing.x1))
        }

        if (entry.gauges.isNotEmpty()) {
            DividerLine()
            RangeGaugeRow(entry.gauges)
        }

        if (entry.counts.isNotEmpty()) {
            DividerLine()
            CountRow(entry.counts)
        }
    }
}

@Composable
private fun MetricRow(stats: List<CallStat>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                DividerLine(Modifier.padding(horizontal = Spacing.x4), axis = Axis.Vertical)
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text(stat.label, style = CollogType.caption01Medium, color = CollogColors.gray700)

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                    Text(
                        stat.value,
                        Modifier.alignByBaseline(),
                        style = CollogType.subtitle01,
                        color = CollogColors.gray900,
                    )
                    Text(
                        stat.unit,
                        Modifier.alignByBaseline(),
                        style = CollogType.caption01Medium,
                        color = CollogColors.gray800,
                    )
                    stat.note?.let {
                        Text(
                            it.text,
                            Modifier.alignByBaseline(),
                            style = CollogType.caption01Medium,
                            color = CollogColors.gray700,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountRow(counts: List<CallStat>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        counts.forEach { stat ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                Text(
                    stat.label,
                    Modifier.alignByBaseline(),
                    style = CollogType.caption01Medium,
                    color = CollogColors.gray700,
                )
                Text(
                    "${stat.value}${stat.unit}",
                    Modifier.alignByBaseline(),
                    style = CollogType.body02Semibold,
                    color = CollogColors.gray900,
                )
            }

            if (stat.id != counts.last().id) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
