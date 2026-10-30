package live.collog.app.features.auth.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable

/** iOS `InvitationAcceptView`. 설정의 초대 코드 입력 시트([InvitationAcceptSheet]) 안에서 쓴다. */
@Composable
fun InvitationAcceptScreen(onCompleted: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canSubmit = code.length == 6 && code.all { it.isDigit() } && !isSubmitting

    fun accept() {
        isSubmitting = true
        errorMessage = null
        val userId = environment.session.user?.id
        scope.launch {
            try {
                val accepted = environment.api.acceptInvitation(code.trim())
                if (environment.session.user?.id != userId) return@launch
                environment.session.joinFamily(accepted.familyId)
                environment.family.refresh(environment)
                onCompleted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message
            } finally {
                isSubmitting = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray50)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.x5, vertical = Spacing.x8),
        verticalArrangement = Arrangement.spacedBy(Spacing.x6),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("가족 초대 코드를 입력해주세요", style = CollogType.headline02, color = CollogColors.gray900)
            Text("가족에게 받은 코드로 가족에 참여할 수 있어요", style = CollogType.body02Medium, color = CollogColors.gray800)
        }

        BasicTextField(
            value = code,
            onValueChange = { code = it },
            singleLine = true,
            textStyle = pretendardPlain(Pretendard.Medium, 16f),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Number,
            ),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(CollogColors.gray00, RoundedCornerShape(Radius.btnSmall))
                        .padding(horizontal = Spacing.x4),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (code.isEmpty()) {
                        Text("초대 코드", style = pretendardPlain(Pretendard.Medium, 16f, CollogColors.gray600))
                    }
                    field()
                }
            },
        )

        errorMessage?.let {
            Text(it, style = CollogType.caption01Medium, color = CollogColors.red500)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(CollogColors.greenNormal, RoundedCornerShape(Radius.btnSmall))
                .pressable(enabled = canSubmit) { accept() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isSubmitting) "확인 중" else "가족 참여하기",
                style = CollogType.body01Semibold,
                color = CollogColors.gray00,
            )
        }
    }
}
