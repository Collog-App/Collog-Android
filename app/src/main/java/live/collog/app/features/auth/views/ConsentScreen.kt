package live.collog.app.features.auth.views

import androidx.compose.runtime.Composable
import live.collog.app.shared.ui.PendingPort

// 포팅 전 자리. 이 파일은 해당 화면을 옮길 때 통째로 바꾼다.
@Composable
fun ConsentScreen(onCompleted: () -> Unit) {
    PendingPort("동의")
}
