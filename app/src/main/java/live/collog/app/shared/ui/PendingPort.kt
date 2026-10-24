package live.collog.app.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType

/** 아직 옮기지 않은 화면 자리. 포팅이 끝나면 쓰는 곳이 없어야 한다. */
@Composable
fun PendingPort(name: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(CollogColors.gray50), contentAlignment = Alignment.Center) {
        Text("$name 준비 중", style = CollogType.body01Medium, color = CollogColors.gray700)
    }
}
