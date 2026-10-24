package live.collog.app.features.auth.views

import androidx.compose.runtime.Composable
import live.collog.app.shared.ui.PendingPort

// 포팅 전 자리. iOS `InvitationAcceptSheet`로 통째로 바꾼다. 시트를 닫는 것은 [onDismiss]로 알린다.
@Composable
fun InvitationAcceptSheet(onDismiss: () -> Unit, onAccepted: () -> Unit = {}) {
    PendingPort("초대 코드 입력")
}
