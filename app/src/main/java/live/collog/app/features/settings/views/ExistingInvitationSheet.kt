package live.collog.app.features.settings.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.services.api.FamilyInvitation
import live.collog.app.shared.services.api.InvitationDto
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.shareText

/** iOS `ExistingInvitationSheet`. 아래에서 올라오는 시트까지 이 함수가 그린다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExistingInvitationSheet(invitation: FamilyInvitation, onDismiss: () -> Unit, onUpdated: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var replacement by remember { mutableStateOf<InvitationDto?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun resend() {
        isSubmitting = true
        errorText = null
        scope.launch {
            try {
                replacement = environment.api.resendInvitation(replacement?.invitationId ?: invitation.invitationId)
                onUpdated()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorText = error.message
                onUpdated()
            } finally {
                isSubmitting = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CollogColors.gray50,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            Text("가족 초대", style = CollogType.headline02, color = CollogColors.gray900)
            val current = replacement
            if (current != null || !invitation.isExpired) {
                Text(
                    current?.code ?: invitation.code,
                    style = pretendard(Pretendard.SemiBold, 28f, CollogColors.gray900),
                )
                SystemTextButton("초대 내용 공유") { shareText(context, current?.shareText ?: invitation.shareText) }
            } else {
                Text("초대가 만료됐어요. 새 코드를 만들어 주세요.", style = CollogType.body03Medium, color = CollogColors.gray700)
            }
            SystemTextButton(if (isSubmitting) "만드는 중" else "새 초대 코드 만들기", enabled = !isSubmitting) { resend() }
            errorText?.let { Text(it, style = CollogType.body03Medium, color = CollogColors.red500) }
        }
    }
}
