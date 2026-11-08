package live.collog.app.features.health.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText

/**
 * 응급 안내(2026-11-08 가드레일): 서버가 고정 문구로 남긴 안내를 경고색 카드로 보인다. 아이콘 없이 문구만.
 * 리포트 맨 위와 그 통화의 분석 화면 위에 둔다. [caption]은 어느 통화인지(「11월 7일 통화」).
 */
@Composable
fun SafetyNoticeCard(text: String, modifier: Modifier = Modifier, caption: String? = null) {
    Column(
        modifier.specCard(
            padding = 16.dp,
            background = SpecColors.concernSurface,
            shadowed = false,
            borderColor = SpecColors.concern.copy(alpha = 0.45f),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = pretendard(Pretendard.SemiBold, 16f, CollogColors.red700))
        if (caption != null) Text(caption, style = specText(Pretendard.Regular, 12f, SpecColors.sub))
    }
}
