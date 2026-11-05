package live.collog.app.features.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.features.home.views.components.HomeDetailHeader
import live.collog.app.features.timeline.models.WeeklySpeechHistory
import live.collog.app.shared.components.chart.TrendChart
import live.collog.app.shared.components.chart.TrendPoint
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.ui.designsystem.Axis
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText

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
    val rate = changeRate(trend) ?: return "${trend.metricName}는 $status"
    val direction = if (rate >= 0) "높아졌어요" else "낮아졌어요"
    return "${trend.metricName}는 $status 첫 기록보다 ${fixed1(abs(rate))}% $direction."
}

/**
 * iOS `FamilyHealthOverviewView`: 홈 카드를 눌러 들어가는 가족 건강 상세. 새 피그마 02(`643:6375`).
 * 「최근 6주」는 서버에 주별 집계가 없어 주간 리포트 6개를 받아 주 평균으로 만든다([WeeklySpeechHistory]).
 * 대화 카드·통계 칸은 새 디자인에 없어 뺐다(대화 내용은 리포트 탭에 있다).
 */
@Composable
fun FamilyHealthOverviewScreen(summary: FamilyHealthSummary) {
    val environment = LocalAppEnvironment.current
    val contact = environment.family.selectedContact
    val isGuest = environment.settings.isGuestMode
    var weekly by remember { mutableStateOf(if (isGuest) summary.trend else null) }

    // 체험 모드는 견본 6주를 그대로 쓰고, 실서버는 주간 리포트로 만든다
    LaunchedEffect(contact?.userId, isGuest) {
        if (isGuest) return@LaunchedEffect
        val parentId = contact?.userId ?: return@LaunchedEffect
        weekly = WeeklySpeechHistory.loadTrend(environment.api, parentId)
    }

    val trend = weekly
    val comparable = trend != null && trend.points.size >= 2
    Column(Modifier.fillMaxSize().background(CollogColors.gray50)) {
        HomeDetailHeader("가족 건강")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 19.dp, end = 19.dp, top = 14.dp, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            StatusCard(summary)

            if (trend != null && comparable) ComparisonCard(trend)

            if (trend != null) {
                Column(Modifier.specCard(padding = 19.dp)) {
                    Text("최근 6주", style = specText(Pretendard.Bold, 14f))
                    Spacer(Modifier.height(26.dp))
                    TrendChart(trend, chartHeight = 112.dp, compact = true)
                }
            }

            InsightCard(trend.takeIf { comparable })
        }
    }
}

@Composable
private fun StatusCard(summary: FamilyHealthSummary) {
    Column(Modifier.specCard(padding = 19.dp, background = SpecColors.greenSurface)) {
        Text(summary.memberName, style = specText(Pretendard.Medium, 10.5f, SpecColors.green))
        Spacer(Modifier.height(14.dp))
        Text(summary.headline, style = specText(Pretendard.Bold, 17f))
        Spacer(Modifier.height(12.dp))
        Text(summary.detail, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun ComparisonCard(trend: TrendSeries) {
    Column(Modifier.specCard(padding = 19.dp)) {
        Text("변화 요약", style = specText(Pretendard.Bold, 14f))
        Spacer(Modifier.height(19.dp))

        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
            ComparisonItem("첫 기록", formattedValue(trend.points.firstOrNull()), trend.unit, Modifier.weight(1f))
            SpecDivider(Modifier.padding(horizontal = 14.dp), axis = Axis.Vertical)
            ComparisonItem("최근 기록", formattedValue(trend.latest), trend.unit, Modifier.weight(1f))
            SpecDivider(Modifier.padding(horizontal = 14.dp), axis = Axis.Vertical)
            ComparisonItem("변화", changeRate(trend)?.let(::signedPercent1) ?: "-", null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ComparisonItem(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                value,
                Modifier.alignByBaseline(),
                style = specText(Pretendard.Bold, 14f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (unit != null) {
                Text(unit, Modifier.alignByBaseline(), style = specText(Pretendard.Regular, 11f, SpecColors.sub), maxLines = 1)
            }
        }
    }
}

@Composable
private fun InsightCard(trend: TrendSeries?) {
    Column(Modifier.specCard(padding = 19.dp, background = SpecColors.greenSurface, shadowed = false)) {
        Text("이번 주 해석", style = specText(Pretendard.Bold, 14f))
        Spacer(Modifier.height(11.dp))
        Text(
            if (trend == null) "주간 기록이 더 쌓이면 6주 흐름을 비교해 알려드릴게요." else interpretation(trend),
            style = specText(Pretendard.Regular, 12f, SpecColors.sub),
        )
    }
}

/** Swift `Int(value.rounded())`: .5는 0에서 먼 쪽으로 올린다. */
internal fun roundedInt(value: Double): Int = BigDecimal(value).setScale(0, RoundingMode.HALF_UP).toInt()

private fun formattedValue(point: TrendPoint?): String = if (point == null) "-" else "${roundedInt(point.value)}"
