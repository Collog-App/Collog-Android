package live.collog.app.features.report.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import live.collog.app.features.report.models.ReportState
import live.collog.app.features.report.models.WeeklyReport
import live.collog.app.features.report.views.components.ChangeSignalCard
import live.collog.app.features.report.views.components.ConversationCard
import live.collog.app.features.report.views.components.MetricTrendRow
import live.collog.app.shared.components.chart.TrendChart
import live.collog.app.shared.components.spec.SpecStatRow
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.IconSize
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText

/** iOS `ReportContentView`: 주간 리포트 본문. [isLoaded]가 false면 음향 추세 자리에 회색 막대를 둔다. */
@Composable
fun ReportContent(report: WeeklyReport, modifier: Modifier = Modifier, isLoaded: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        if (report.state == ReportState.BaselineCollecting) {
            NoticeBanner("아직 평소 범위를 모으는 중이에요. " + "통화가 쌓이면 변화를 보여드릴게요.")
        }

        SpecStatRow(report.summaryStats, Modifier.specCard(padding = 15.dp))

        ChangeSignalCard(report.changeSignals)

        Column(Modifier.specCard()) {
            Text("음향 추세", style = specText(Pretendard.Regular, 12f, SpecColors.muted))
            Spacer(Modifier.height(24.dp))

            val trend = report.acousticTrend
            if (isLoaded && trend != null) {
                TrendChart(trend, chartHeight = 112.dp)
            } else if (isLoaded) {
                Text("아직 음향 분석 데이터가 없어요", style = specText(Pretendard.Medium, 13f, SpecColors.sub))
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .background(CollogColors.gray100, RoundedCornerShape(Radius.btnXsmall)),
                )
            }
        }

        if (report.metricTrends.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text(
                    "지난 흐름",
                    style = specText(Pretendard.Bold, 14f),
                    modifier = Modifier.padding(start = Spacing.x1),
                )

                report.metricTrends.forEach { MetricTrendRow(it) }
            }
        }

        ConversationCard(report.conversationGroups)

        RepeatCard(report)
    }
}

/** 새 피그마 07 「카드/되물으심 상세」. 분당 횟수는 계산할 수 있을 때만(`perMinuteText` 가 비면 숨김). */
@Composable
private fun RepeatCard(report: WeeklyReport) {
    Column(Modifier.specCard()) {
        Text("되물으심", style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth()) {
            Text(report.repeatObservation.countText, Modifier.alignByBaseline(), style = specText(Pretendard.Bold, 24f))
            Spacer(Modifier.width(1.dp))
            Text("회", Modifier.alignByBaseline(), style = specText(Pretendard.Regular, 14f, SpecColors.sub))
            Spacer(Modifier.weight(1f))
            if (report.repeatObservation.perMinuteText.isNotEmpty()) {
                Text(
                    report.repeatObservation.perMinuteText,
                    Modifier.alignByBaseline(),
                    style = specText(Pretendard.Regular, 12f, SpecColors.muted),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(report.repeatObservation.caption, style = specText(Pretendard.Regular, 14f, SpecColors.muted))
    }
}

@Composable
private fun NoticeBanner(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray100, RoundedCornerShape(Radius.btnXsmall))
            .padding(Spacing.x3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.Top,
    ) {
        CollogIcon("info.circle", size = IconSize.small, color = CollogColors.gray700)
        Text(text, style = CollogType.caption01Medium, color = CollogColors.gray800, modifier = Modifier.weight(1f))
    }
}
