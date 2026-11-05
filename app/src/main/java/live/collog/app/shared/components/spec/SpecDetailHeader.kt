package live.collog.app.shared.components.spec

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

/** 새 피그마 16~18 위쪽 막대: 왼쪽 뒤로 꺾쇠(19dp 안쪽), 가운데 제목 13/500. 높이 66dp(상태 표시줄 아래). */
@Composable
fun SpecDetailHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .size(44.dp)
                .semantics { contentDescription = "뒤로" }
                .pressable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            CollogIcon("chevron.left", size = 22.dp, color = SpecColors.ink)
        }
        Text(title, style = specText(Pretendard.Medium, 13f), maxLines = 1)
    }
}
