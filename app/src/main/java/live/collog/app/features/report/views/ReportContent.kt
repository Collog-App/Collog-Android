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
import live.collog.app.shared.components.stat.StatTileRow
import live.collog.app.shared.components.stat.ValueWithUnit
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.IconSize
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.designsystem.pretendard

/** iOS `ReportContentView`: 주간 리포트 본문. [isLoaded]가 false면 음향 추세 자리에 회색 막대를 둔다. */
@Composable
fun ReportContent(report: WeeklyReport, modifier: Modifier = Modifier, isLoaded: Boolean = true) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        if (report.state == ReportState.BaselineCollecting) {
            NoticeBanner("아직 평소 범위를 모으는 중이에요. " + "통화가 쌓이면 변화를 보여드릴게요.")
        }

        StatTileRow(report.summaryStats, Modifier.cardSurface())

        ChangeSignalCard(report.changeSignals)

        Column(Modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
            Text("음향 추세", style = CollogType.caption01Medium, color = CollogColors.gray800)

            val trend = report.acousticTrend
            if (isLoaded && trend != null) {
                TrendChart(trend)
            } else if (isLoaded) {
                Text("아직 음향 분석 데이터가 없어요", style = CollogType.body02Medium, color = CollogColors.gray700)
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
                    style = CollogType.body02Semibold,
                    color = CollogColors.gray900,
                    modifier = Modifier.padding(start = Spacing.x1),
                )

                report.metricTrends.forEach { MetricTrendRow(it) }
            }
        }

        ConversationCard(report.conversationGroups)

        RepeatCard(report)
    }
}

@Composable
private fun RepeatCard(report: WeeklyReport) {
    Column(Modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Text("되물으심", style = CollogType.caption01Medium, color = CollogColors.gray800)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            ValueWithUnit(
                value = report.repeatObservation.countText,
                unit = "회",
                valueStyle = pretendard(Pretendard.SemiBold, 24f),
                valueColor = CollogColors.gray900,
                unitStyle = CollogType.body02Medium,
                unitColor = CollogColors.gray800,
                modifier = Modifier.alignByBaseline(),
            )

            Spacer(Modifier.width(Spacing.x1))
            Spacer(Modifier.weight(1f))

            Text(
                report.repeatObservation.perMinuteText,
                style = CollogType.caption01Medium,
                color = CollogColors.gray700,
                modifier = Modifier.alignByBaseline(),
            )
        }

        Text(report.repeatObservation.caption, style = CollogType.caption01Medium, color = CollogColors.gray700)
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
