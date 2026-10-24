package live.collog.app.features.auth.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.pressable

private data class OnboardingFeature(val id: String, val symbol: String, val title: String, val detail: String)

private val features = listOf(
    OnboardingFeature("call", "phone.fill", "가족 통화", "어머니, 아버지와 바로 연결"),
    OnboardingFeature("questions", "questionmark.bubble.fill", "오늘의 질문", "다음 대화를 편하게 준비"),
    OnboardingFeature("report", "chart.line.uptrend.xyaxis", "주간 리포트", "통화에서 찾은 변화를 확인"),
    OnboardingFeature("timeline", "clock.arrow.circlepath", "타임라인", "지난 기록을 주차별로 모아보기"),
)

/** iOS `OnboardingView`. 메시 그라디언트는 비스듬한 선형 그라디언트로 근사한다. */
@Composable
fun OnboardingScreen(onCompleted: () -> Unit) {
    Box(Modifier.fillMaxSize().background(onboardingBackground)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.x5, end = Spacing.x5, top = Spacing.x5, bottom = Spacing.x4),
                verticalArrangement = Arrangement.spacedBy(Spacing.x6),
            ) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "가족의 목소리와 건강을\n함께 기억하세요",
                    style = pretendard(Pretendard.SemiBold, 26f, CollogColors.gray900),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                    features.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                            pair.forEach { FeatureCard(it, Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .padding(start = Spacing.x5, end = Spacing.x5, top = Spacing.x3, bottom = Spacing.x5)
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(CollogColors.greenNormal, CircleShape)
                    .pressable(onClick = onCompleted),
                contentAlignment = Alignment.Center,
            ) {
                Text("시작하기", style = CollogType.body01Semibold, color = CollogColors.gray00)
            }
        }
    }
}

// MeshGradient(3x3)의 모서리·중앙 색을 이어 만든 근사. 위에 gray00 18%를 덧씌운다.
private val onboardingBackground = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFAFBFC),
        Color(0xFFEAF5EF),
        Color(0xFFDDF3E8),
        Color(0xFFE8F4EE),
        Color(0xFFF7F8F9),
    ).map { CollogColors.gray00.copy(alpha = 0.18f).compositeOver(it) },
)

@Composable
private fun FeatureCard(feature: OnboardingFeature, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier
            .heightIn(min = 156.dp)
            .background(CollogColors.gray00.copy(alpha = 0.52f), shape)
            .border(1.dp, CollogColors.gray00.copy(alpha = 0.78f), shape)
            .padding(Spacing.x4),
    ) {
        CollogIcon(feature.symbol, size = 28.dp, color = CollogColors.greenDark)
        Spacer(Modifier.weight(1f).heightIn(min = Spacing.x5))
        Text(feature.title, style = CollogType.body02Semibold, color = CollogColors.gray900)
        Text(feature.detail, style = CollogType.caption01Regular, color = CollogColors.gray700)
    }
}
