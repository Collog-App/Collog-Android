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
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.designsystem.pretendard

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

/** iOS `MetricTrendRowView`. */
@Composable
fun MetricTrendRow(trend: MetricTrend, modifier: Modifier = Modifier) {
    Row(
        modifier.cardSurface(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text(trend.label, style = CollogType.caption01Medium, color = CollogColors.gray800)
            ValueWithUnit(
                value = trend.value,
                unit = trend.unit,
                valueStyle = pretendard(Pretendard.SemiBold, 24f),
                valueColor = CollogColors.gray900,
                unitStyle = CollogType.body02Medium,
                unitColor = CollogColors.gray800,
            )
        }

        Spacer(Modifier.width(Spacing.x3))
        Spacer(Modifier.weight(1f))

        val sparkModifier = Modifier.size(width = 96.dp, height = 40.dp)
        when (trend.shape) {
            MetricTrend.Shape.Bar -> SparkBar(trend.values, sparkModifier)
            MetricTrend.Shape.Line -> SparkLine(trend.values, sparkModifier)
        }
    }
}
