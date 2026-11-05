package live.collog.app.shared.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import java.util.UUID
import live.collog.app.shared.ui.designsystem.Axis
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.specText

/** iOS `RangeGauge`. [normalRange]와 [marker]는 0~1 비율. */
data class RangeGauge(
    val label: String,
    val normalRange: ClosedFloatingPointRange<Double>,
    val marker: Double,
    val caption: String,
) {
    val id: UUID = UUID.randomUUID()
}

private fun clamped(value: Double): Double = value.coerceIn(0.0, 1.0)

/** iOS `RangeGaugeView`. 새 피그마 08 「게이지 트랙」(높이 6)·「현재 값 마커」(3x15). */
@Composable
fun RangeGaugeTile(gauge: RangeGauge, modifier: Modifier = Modifier) {
    val rangeWidth = clamped(gauge.normalRange.endInclusive) - clamped(gauge.normalRange.start)

    Column(modifier.fillMaxWidth()) {
        Text(gauge.label, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        Spacer(Modifier.height(14.dp))

        Canvas(Modifier.fillMaxWidth().height(15.dp)) {
            val width = size.width
            val trackHeight = 6.dp.toPx()
            val trackTop = (size.height - trackHeight) / 2
            val capsule = CornerRadius(trackHeight / 2)

            drawRoundRect(SpecColors.chip, Offset(0f, trackTop), Size(width, trackHeight), capsule)
            drawRoundRect(
                SpecColors.gaugeRange,
                Offset((width * clamped(gauge.normalRange.start)).toFloat(), trackTop),
                Size(maxOf(0.0, width * rangeWidth).toFloat(), trackHeight),
                capsule,
            )
            drawRoundRect(
                SpecColors.green,
                Offset((width * clamped(gauge.marker)).toFloat() - 1.5.dp.toPx(), 0f),
                Size(3.dp.toPx(), 15.dp.toPx()),
                CornerRadius(1.5.dp.toPx()),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(gauge.caption, style = specText(Pretendard.Regular, 11f, SpecColors.muted))
    }
}

/** iOS `RangeGaugeRowView`. */
@Composable
fun RangeGaugeRow(gauges: List<RangeGauge>, modifier: Modifier = Modifier) {
    Row(modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        gauges.forEachIndexed { index, gauge ->
            if (index > 0) {
                SpecDivider(Modifier.padding(horizontal = Spacing.x4), axis = Axis.Vertical)
            }
            RangeGaugeTile(gauge, Modifier.weight(1f))
        }
    }
}
