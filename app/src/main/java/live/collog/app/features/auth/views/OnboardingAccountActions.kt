package live.collog.app.features.auth.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.views.AccountSettingsScreen
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable

/**
 * iOS `OnboardingAccountActions`: 계정 관리 시트와 로그아웃.
 * iOS는 부모 뷰의 `.disabled(isSubmitting)`로 막으므로 [enabled]로 받는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingAccountActions(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var showsAccount by remember { mutableStateOf(false) }

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PlainTextAction("계정 관리", enabled = enabled) { showsAccount = true }
        PlainTextAction("로그아웃", enabled = enabled) {
            scope.launch { environment.signOut() }
        }
    }

    if (showsAccount) {
        ModalBottomSheet(
            onDismissRequest = { showsAccount = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CollogColors.gray50,
        ) {
            AccountSettingsScreen(onClose = { showsAccount = false })
        }
    }
}

/** `.pretendardStyle(.semiBold, 14, .gray800)`을 입힌 iOS 글자 버튼. 꺼지면 흐려진다. */
@Composable
internal fun PlainTextAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = pretendardPlain(Pretendard.SemiBold, 14f, CollogColors.gray800),
        modifier = modifier.alpha(if (enabled) 1f else 0.3f).pressable(enabled = enabled, onClick = onClick),
    )
}
