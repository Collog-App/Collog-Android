package live.collog.app.features.auth.views

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import live.collog.app.R
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

private class IntroTile(@DrawableRes val icon: Int?, val title: String, val body: String)

private val tiles = listOf(
    // 피그마 「카드/가족 통화」(643:6926)에는 아이콘이 없다
    IntroTile(null, "가족 통화", "어머니, 아버지와 바로 연결"),
    IntroTile(R.drawable.ic_onboarding_question, "오늘의 질문", "다음 대화를 편하게 준비"),
    IntroTile(R.drawable.ic_onboarding_report, "주간 리포트", "통화에서 찾은 변화를 확인"),
    IntroTile(R.drawable.ic_onboarding_timeline, "타임라인", "지난 기록을 주차별로 모아보기"),
)

/**
 * 새 피그마 「디자인」 19 온보딩 · 시작(643:6923). 로그인 앞 첫 장이다.
 * 「시작하기」는 기존 로그인 화면([OnboardingScreen])으로 간다.
 */
@Composable
fun IntroScreen(onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    0f to Color.White,
                    0.5f to Color(0xFFE8F4EC),
                    1f to Color(0xFFF7F8FA),
                    start = Offset.Zero,
                    end = Offset(1000f, 2200f),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 19.dp),
    ) {
        Spacer(Modifier.height(67.dp))
        Text("가족의 목소리와 건강을\n함께 기억하세요", style = specText(Pretendard.Bold, 25f).copy(lineHeight = 36.sp))
        Spacer(Modifier.height(37.dp))
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    row.forEach { IntroTileCard(it, Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .height(53.dp)
                .pressable(onClick = onStart)
                .background(SpecColors.toggleOn, RoundedCornerShape(27.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("시작하기", style = specText(Pretendard.Bold, 16f, Color.White))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun IntroTileCard(tile: IntroTile, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .height(156.dp)
            .shadow(8.dp, shape, ambientColor = Color(0xFF33804D).copy(alpha = 0.05f), spotColor = Color(0xFF33804D).copy(alpha = 0.05f))
            .background(Color.White.copy(alpha = 0.62f), shape)
            .border(1.2.dp, Color.White, shape),
    ) {
        tile.icon?.let {
            Image(painterResource(it), contentDescription = null, modifier = Modifier.padding(start = 18.dp, top = 19.dp).size(28.dp))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 17.dp)) {
            Text(tile.title, style = specText(Pretendard.Bold, 14f))
            Spacer(Modifier.height(8.dp))
            Text(tile.body, style = specText(Pretendard.Regular, 11f, SpecColors.muted))
        }
    }
}
