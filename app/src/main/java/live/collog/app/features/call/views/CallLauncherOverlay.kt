package live.collog.app.features.call.views

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import live.collog.app.features.call.viewmodels.CallLauncherModel
import live.collog.app.shared.ui.PendingPort

// 포팅 전 자리: 인자는 유지하고 iOS `CallLauncherOverlay`로 통째로 바꾼다.
@Composable
fun CallLauncherOverlay(
    model: CallLauncherModel,
    anchorInset: Dp,
    notice: String?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    PendingPort("가족 선택")
}
