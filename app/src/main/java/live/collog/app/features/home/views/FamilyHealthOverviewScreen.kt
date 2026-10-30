package live.collog.app.features.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.features.home.views.components.HomeDetailHeader
import live.collog.app.features.report.views.components.ConversationCard
import live.collog.app.shared.components.chart.TrendChart
import live.collog.app.shared.components.chart.TrendPoint
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.stat.StatTileRow
import live.collog.app.shared.ui.designsystem.Axis
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface

/** Swift `String(format: "%.1f")`: 정확한 이진 값을 소수 한 자리로, 딱 중간이면 짝수 쪽으로. */
internal fun fixed1(value: Double): String =
    BigDecimal(value).setScale(1, RoundingMode.HALF_EVEN).toPlainString()

/** Swift `String(format: "%+.1f%%")`: 음수가 0.0으로 반올림돼도 `-0.0%`. */
internal fun signedPercent1(value: Double): String {
    val negative = value < 0 || (value == 0.0 && 1 / value < 0)
    return (if (negative) "-" else "+") + fixed1(Math.abs(value)) + "%"
}

/** 첫 기록 대비 최근 기록의 변화율(%). 첫 값이 0이거나 점이 없으면 null. */
internal fun changeRate(trend: TrendSeries?): Double? {
    val first = trend?.points?.firstOrNull()?.value ?: return null
    val latest = trend.latest?.value ?: return null
    if (first == 0.0) return null
    return (latest - first) / abs(first) * 100
}

/** iOS `FamilyHealthOverviewView.interpretation`. */
internal fun interpretation(trend: TrendSeries?): String {
    val latest = trend?.latest
        ?: return "음성 변화를 비교할 기록이 부족해요. 대화 내용은 먼저 확인할 수 있어요."
    if (!trend.hasPersonalBaseline) {
        return "측정값을 모으고 있어요. 아직 평소와의 차이를 판단하기 어려워요."
    }
    val status = if (trend.isWithinNormalRange(latest)) "평소 범위 안에 있어요." else "평소 범위를 벗어난 값이 확인됐어요."
    val rate = changeRate(trend) ?: return "${trend.metricName}은 $status"
    val direction = if (rate >= 0) "높아졌어요" else "낮아졌어요"
    return "${trend.metricName}은 $status 첫 기록보다 ${fixed1(abs(rate))}% $direction."
}

/** iOS `FamilyHealthOverviewView`: 홈 카드를 눌러 들어가는 통화 기록 상세. */
@Composable
fun FamilyHealthOverviewScreen(summary: FamilyHealthSummary) {
    val trend = summary.trend
    Column(Modifier.fillMaxSize().background(CollogColors.gray50)) {
        HomeDetailHeader("통화 기록")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            StatusCard(summary)
            if (summary.conversationGroups.isNotEmpty()) {
                ConversationCard(summary.conversationGroups)
            }
            if (trend != null) {
                ComparisonCard(trend)

                Column(Modifier.cardSurface(padding = Spacing.x5), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
                    Text(summary.periodText, style = CollogType.body01Semibold, color = CollogColors.gray900)
                    TrendChart(trend)
                }
            }

            InsightCard(trend)

            StatTileRow(summary.stats, Modifier.cardSurface(padding = Spacing.x5))
        }
    }
}

@Composable
private fun StatusCard(summary: FamilyHealthSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.green100, RoundedCornerShape(Radius.card))
            .padding(Spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Text(summary.memberName, style = CollogType.caption01Semibold, color = CollogColors.green700)
        Text(summary.headline, style = CollogType.subtitle01, color = CollogColors.gray900)
        Text(summary.detail, style = CollogType.body03Medium, color = CollogColors.gray800)
    }
}

@Composable
private fun ComparisonCard(trend: TrendSeries) {
    Column(Modifier.cardSurface(padding = Spacing.x5), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Text("변화 요약", style = CollogType.body01Semibold, color = CollogColors.gray900)

        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            ComparisonItem("첫 기록", formattedValue(trend.points.firstOrNull(), trend), Modifier.weight(1f))
            DividerLine(Modifier.padding(horizontal = Spacing.x3), axis = Axis.Vertical)
            ComparisonItem("최근 기록", formattedValue(trend.latest, trend), Modifier.weight(1f))
            DividerLine(Modifier.padding(horizontal = Spacing.x3), axis = Axis.Vertical)
            ComparisonItem("변화", changeRate(trend)?.let(::signedPercent1) ?: "-", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ComparisonItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text(label, style = CollogType.caption01Medium, color = CollogColors.gray700)
        Text(
            value,
            style = CollogType.body01Semibold,
            color = CollogColors.gray900,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun InsightCard(trend: TrendSeries?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.green100, RoundedCornerShape(Radius.card))
            .padding(Spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Text("이번 주 해석", style = CollogType.body01Semibold, color = CollogColors.gray900)
        Text(interpretation(trend), style = CollogType.body03Medium, color = CollogColors.gray800)
    }
}

/** Swift `Int(value.rounded())`: .5는 0에서 먼 쪽으로 올린다. */
internal fun roundedInt(value: Double): Int = BigDecimal(value).setScale(0, RoundingMode.HALF_UP).toInt()

private fun formattedValue(point: TrendPoint?, trend: TrendSeries): String =
    if (point == null) "-" else "${roundedInt(point.value)}${trend.unit}"
