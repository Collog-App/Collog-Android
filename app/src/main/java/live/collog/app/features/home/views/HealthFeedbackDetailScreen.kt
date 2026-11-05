package live.collog.app.features.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import live.collog.app.features.home.models.ConversationFollowUp
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.views.components.HomeDetailHeader
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText

/**
 * iOS `HealthFeedbackDetailView`: 건강 피드백. 새 피그마 03(`643:6418`)의 주황 카드 + 「함께 확인해보세요」 체크 3줄 아래에,
 * 실제 대화 항목별 「다음에 물어볼 질문」 카드를 이어 붙인다(대조표 11: 고정 문구만으로는 가족이 얻는 정보가 없다).
 * [lastCallText]는 「3일 전」처럼 마지막 통화 날짜 말(없으면 그 알약을 뺀다).
 */
@Composable
fun HealthFeedbackDetailScreen(feedback: HealthFeedback, lastCallText: String? = null) {
    Column(Modifier.fillMaxSize().background(CollogColors.gray50)) {
        HomeDetailHeader("건강 피드백")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 19.dp, end = 19.dp, top = 12.dp, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Hero(lastCallText)
            Checklist()
            feedback.followUps.forEach { FollowUpCard(it) }
        }
    }
}

@Composable
private fun Hero(lastCallText: String?) {
    Column(Modifier.specCard(padding = 21.dp, background = SpecColors.heroOrange, shadowed = false)) {
        Text("최근 생활 변화 살펴보기", style = specText(Pretendard.Bold, 17f))
        Spacer(Modifier.height(10.dp))
        Text(
            "최근 통화 기록을 가족과 함께 살펴보세요.",
            style = specText(Pretendard.Regular, 14f, SpecColors.muted),
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("최근 기록")
            lastCallText?.let { Pill("$it 통화") }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = specText(Pretendard.Medium, 10.5f, SpecColors.sub),
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

@Composable
private fun Checklist() {
    Column(Modifier.specCard(padding = 21.dp)) {
        Text("함께 확인해보세요", style = specText(Pretendard.Bold, 16f))
        Spacer(Modifier.height(15.dp))
        Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
            listOf(
                "최근 생활에서 달라진 점이 있는지 물어보세요",
                "복약과 수면 상태를 편하게 이야기해보세요",
                "걱정이 이어지면 의료진과 상담해보세요",
            ).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(17.dp).background(SpecColors.green, CircleShape), contentAlignment = Alignment.Center) {
                        CollogIcon("checkmark", size = 11.dp, color = Color.White)
                    }
                    Text(line, style = specText(Pretendard.Regular, 14f, SpecColors.sub))
                }
            }
        }
    }
}

@Composable
private fun FollowUpCard(followUp: ConversationFollowUp) {
    Column(Modifier.specCard(padding = 21.dp)) {
        Text(followUp.category, style = specText(Pretendard.Bold, 14f))
        Spacer(Modifier.height(8.dp))
        Text(followUp.summary, style = specText(Pretendard.Regular, 13f, SpecColors.sub))
        Spacer(Modifier.height(14.dp))
        Text("다음에 물어볼 질문", style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        Spacer(Modifier.height(4.dp))
        Text(followUp.question, style = specText(Pretendard.Medium, 14f))
    }
}
