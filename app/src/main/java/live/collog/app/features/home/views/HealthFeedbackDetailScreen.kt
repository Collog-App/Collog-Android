package live.collog.app.features.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import live.collog.app.features.home.models.ConversationFollowUp
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.views.components.HomeDetailHeader
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface

/** iOS `HealthFeedbackDetailView`: 다음 통화에서 물어볼 대화 내용 목록. */
@Composable
fun HealthFeedbackDetailScreen(feedback: HealthFeedback) {
    Column(Modifier.fillMaxSize().background(CollogColors.gray50)) {
        HomeDetailHeader(feedback.title)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            Hero(feedback)
            if (feedback.followUps.isEmpty()) {
                Text(
                    "이번 기간에 정리된 대화 내용이 없어요. 다음에는 요즘 일상을 편하게 이야기해보세요.",
                    style = CollogType.body03Medium,
                    color = CollogColors.gray800,
                    modifier = Modifier.cardSurface(padding = Spacing.x5),
                )
            } else {
                feedback.followUps.forEach { FollowUpCard(it) }
            }
        }
    }
}

@Composable
private fun Hero(feedback: HealthFeedback) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.orangeLight.copy(alpha = 0.58f), RoundedCornerShape(Radius.card))
            .padding(Spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Spacing.x4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text(feedback.headline, style = CollogType.subtitle01, color = CollogColors.gray900)
            Text(
                "통화 내용을 요약한 기록이에요. 실제 말씀과 다를 수 있으니 다음 대화에서 확인해보세요.",
                style = CollogType.body03Medium,
                color = CollogColors.gray800,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            feedback.tags.forEach { tag ->
                Text(
                    tag,
                    style = CollogType.caption01Semibold,
                    color = CollogColors.gray800,
                    modifier = Modifier
                        .background(CollogColors.gray00.copy(alpha = 0.86f), CircleShape)
                        .padding(horizontal = Spacing.x3, vertical = Spacing.x2),
                )
            }
        }
    }
}

@Composable
private fun FollowUpCard(followUp: ConversationFollowUp) {
    Column(Modifier.cardSurface(padding = Spacing.x5), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Text(followUp.category, style = CollogType.body01Semibold, color = CollogColors.gray900)
        Text(followUp.summary, style = CollogType.body03Medium, color = CollogColors.gray800)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("다음에 물어볼 질문", style = CollogType.caption01Semibold, color = CollogColors.gray800)
            Text(followUp.question, style = CollogType.body02Medium, color = CollogColors.gray900)
        }
    }
}
