package live.collog.app.features.report.views.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID
import live.collog.app.shared.components.chart.SparkBar
import live.collog.app.shared.components.chart.SparkLine
import live.collog.app.shared.components.stat.ValueWithUnit
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText

/** iOS `MetricTrend`. */
data class MetricTrend(
    val label: String,
    val value: String,
    val unit: String,
    val values: List<Double>,
    val shape: Shape,
) {
    enum class Shape { Bar, Line }

    val id: UUID = UUID.randomUUID()
}

/** iOS `MetricTrendRowView`. 새 피그마 06·07 「카드/지난 흐름 말씀 속도」: 값 20/700, 오른쪽 스파크라인 105x56. */
@Composable
fun MetricTrendRow(trend: MetricTrend, modifier: Modifier = Modifier) {
    Row(
        modifier.specCard(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trend.label, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
            ValueWithUnit(
                value = trend.value,
                unit = trend.unit,
                valueStyle = specText(Pretendard.Bold, 20f),
                valueColor = SpecColors.ink,
                unitStyle = specText(Pretendard.Regular, 12f),
                unitColor = SpecColors.sub,
            )
        }

        Spacer(Modifier.width(Spacing.x3))
        Spacer(Modifier.weight(1f))

        val sparkModifier = Modifier.size(width = 97.dp, height = 40.dp)
        when (trend.shape) {
            MetricTrend.Shape.Bar -> SparkBar(trend.values, sparkModifier)
            MetricTrend.Shape.Line -> SparkLine(trend.values, sparkModifier)
        }
    }
}
