package live.collog.app.shared.components.stat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.unit.dp
import java.util.UUID
import live.collog.app.shared.ui.designsystem.Axis
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

/** iOS `StatTrend`. */
enum class StatTrend {
    Up,
    Down,
    Flat;

    val color: Color get() = CollogColors.gray700
}

/** iOS `StatNote`. */
data class StatNote(val text: String, val trend: StatTrend = StatTrend.Flat)

/** iOS `CallStat`. */
data class CallStat(
    val label: String,
    val value: String,
    val unit: String,
    val note: StatNote?,
) {
    val id: UUID = UUID.randomUUID()

    companion object {
        val samples: List<CallStat> = listOf(
            CallStat("통화 길이", "8", "분", StatNote("평소 12분")),
            CallStat("말씀 속도", "210", "음절/분", StatNote("1.32%", StatTrend.Up)),
        )
    }
}

/** iOS `StatTileLayout`. */
enum class StatTileLayout { Inline, Stacked }

/** iOS `StatTileView`. */
@Composable
fun StatTile(stat: CallStat, modifier: Modifier = Modifier, layout: StatTileLayout = StatTileLayout.Inline) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Text(stat.label, style = CollogType.caption01Medium, color = CollogColors.gray800)

        if (layout == StatTileLayout.Inline) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                StatValue(stat, Modifier.alignBy(FirstBaseline))
                Spacer(Modifier.width(Spacing.x2 + Spacing.x1))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.x2))
                StatNoteParts(stat.note, alignToBaseline = true)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                StatValue(stat, Modifier)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatNoteParts(stat.note, alignToBaseline = false)
                }
            }
        }
    }
}

@Composable
private fun StatValue(stat: CallStat, modifier: Modifier) {
    ValueWithUnit(
        value = stat.value,
        unit = stat.unit,
        valueStyle = pretendard(Pretendard.SemiBold, 24f),
        valueColor = CollogColors.gray900,
        unitStyle = CollogType.body02Medium,
        unitColor = CollogColors.gray800,
        modifier = modifier,
    )
}

@Composable
private fun RowScope.StatNoteParts(note: StatNote?, alignToBaseline: Boolean) {
    if (note == null) return
    if (note.trend != StatTrend.Flat) {
        // iOS 이미지는 밑변이 밑줄이므로 기호 아랫면을 글자 밑줄에 맞춘다
        CollogIcon(
            name = if (note.trend == StatTrend.Up) "arrowtriangle.up.fill" else "arrowtriangle.down.fill",
            size = 9.dp,
            color = note.trend.color,
            modifier = if (alignToBaseline) Modifier.alignBy { it.measuredHeight } else Modifier,
        )
        Spacer(Modifier.width(1.dp))
    }
    Text(
        note.text,
        style = CollogType.caption01Medium,
        color = if (note.trend == StatTrend.Flat) CollogColors.gray700 else note.trend.color,
        modifier = if (alignToBaseline) Modifier.alignBy(FirstBaseline) else Modifier,
    )
}

/** iOS `StatTileRowView`: 통계 칸을 세로 구분선으로 나눠 가로로 놓는다. */
@Composable
fun StatTileRow(
    stats: List<CallStat>,
    modifier: Modifier = Modifier,
    layout: StatTileLayout = StatTileLayout.Inline,
) {
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                DividerLine(Modifier.padding(horizontal = Spacing.x4), axis = Axis.Vertical)
            }
            StatTile(stat, Modifier.weight(1f), layout)
        }
    }
}
