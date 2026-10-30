package live.collog.app.features.auth.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable

/**
 * iOS `InvitationAcceptSheet`. 아래에서 올라오는 시트까지 이 함수가 그린다.
 * `@Environment(\.dismiss)`는 [onDismiss]로 바꿨다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationAcceptSheet(onDismiss: () -> Unit, onAccepted: () -> Unit = {}) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CollogColors.gray50,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.x5)) {
            // iOS 툴바의 cancellationAction
            Text(
                "취소",
                style = pretendardPlain(Pretendard.Medium, 16f, CollogColors.blue600),
                modifier = Modifier.pressable(onClick = onDismiss),
            )
        }
        InvitationAcceptScreen(
            onCompleted = {
                onDismiss()
                onAccepted()
            },
        )
    }
}
