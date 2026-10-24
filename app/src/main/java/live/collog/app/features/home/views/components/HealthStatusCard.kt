package live.collog.app.features.home.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.shared.components.chart.SparkLine
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

/** Swift `Int(value.rounded())`: .5는 0에서 먼 쪽으로 올린다. */
internal fun roundedInt(value: Double): Int = BigDecimal(value).setScale(0, RoundingMode.HALF_UP).toInt()

/** iOS `HealthStatusCardView`: 홈 맨 위 카드. 누르면 통화 기록 자세히 보기로 간다. */
@Composable
fun HealthStatusCard(
    summary: FamilyHealthSummary,
    modifier: Modifier = Modifier,
    isLoaded: Boolean = true,
    onTap: () -> Unit = {},
) {
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, CollogColors.gray200, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onTap,
            ),
    ) {
        Hero(summary)

        DividerLine(Modifier.padding(horizontal = Spacing.x4))

        Column(
            Modifier
                .fillMaxWidth()
                .background(CollogColors.gray00)
                .padding(Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            if (summary.conversationGroups.isNotEmpty()) {
                Text("나눈 이야기", style = CollogType.caption01Semibold, color = CollogColors.gray700)
                summary.conversationGroups.take(2).forEach { group ->
                    group.items.firstOrNull()?.let { item ->
                        Text(item, style = CollogType.body02Medium, color = CollogColors.gray900)
                    }
                }
            }
            val trend = summary.trend
            if (isLoaded && trend != null) {
                SparkLine(trend.points.map { it.value }, Modifier.fillMaxWidth().height(56.dp), lineWidth = 2.5.dp)
            } else if (!isLoaded) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(CollogColors.gray100, RoundedCornerShape(Radius.btnXsmall)),
                )
            } else if (summary.conversationGroups.isEmpty()) {
                Text(
                    "이번 기간의 통화에서 정리할 대화 항목을 찾지 못했어요.",
                    style = CollogType.body03Medium,
                    color = CollogColors.gray700,
                )
            }
            Text("통화 기록 자세히 보기", style = CollogType.caption01Semibold, color = CollogColors.greenDark)
        }
    }
}

@Composable
private fun Hero(summary: FamilyHealthSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray00)
            .padding(Spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text(summary.headline, style = CollogType.subtitle01, color = CollogColors.gray900)
            Text(summary.detail, style = CollogType.body03Medium, color = CollogColors.gray800)
        }

        val trend = summary.trend
        val latest = trend?.latest
        if (trend != null && latest != null) {
            val within = trend.isWithinNormalRange(latest)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x1), verticalAlignment = Alignment.Bottom) {
                Text(
                    "${roundedInt(latest.value)}",
                    style = pretendard(Pretendard.SemiBold, 24f),
                    color = CollogColors.gray900,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    trend.unit,
                    style = CollogType.body03Medium,
                    color = CollogColors.gray800,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (trend.hasPersonalBaseline) (if (within) "평소 범위" else "평소와 다름") else "비교할 기록 수집 중",
                    style = CollogType.caption01Semibold,
                    color = if (trend.hasPersonalBaseline) {
                        if (within) CollogColors.green700 else CollogColors.orange600
                    } else {
                        CollogColors.gray700
                    },
                    modifier = Modifier
                        .alignByBaseline()
                        .background(CollogColors.gray00.copy(alpha = 0.82f), CircleShape)
                        .padding(horizontal = Spacing.x3, vertical = Spacing.x2),
                )
            }
        }
    }
}
