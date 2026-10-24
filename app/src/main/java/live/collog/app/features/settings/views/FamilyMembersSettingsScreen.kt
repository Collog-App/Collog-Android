package live.collog.app.features.settings.views

import androidx.compose.runtime.Composable
import live.collog.app.shared.ui.PendingPort

// 포팅 전 자리. iOS `FamilyMembersSettingsView`로 통째로 바꾼다. 시트로 띄운 곳은 [onClose]를 넘긴다.
@Composable
fun FamilyMembersSettingsScreen(onClose: (() -> Unit)? = null) {
    PendingPort("가족 구성원")
}
