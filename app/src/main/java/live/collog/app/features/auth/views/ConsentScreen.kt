package live.collog.app.features.auth.views

import androidx.compose.runtime.Composable
import live.collog.app.shared.ui.PendingPort

// 포팅 전 자리. iOS `ConsentView(onAgreed:showsAccountActions:)`로 통째로 바꾼다.
@Composable
fun ConsentScreen(onAgreed: () -> Unit, showsAccountActions: Boolean = true) {
    PendingPort("동의")
}
