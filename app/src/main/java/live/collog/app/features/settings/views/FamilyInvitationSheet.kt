package live.collog.app.features.settings.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.models.InvitationRelations
import live.collog.app.features.settings.models.createFamilyInvitation
import live.collog.app.features.settings.models.relationTitle
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable
import live.collog.app.shared.ui.shareText

/** iOS `FamilyInvitationSheet`. 아래에서 올라오는 시트까지 이 함수가 그린다. 가족이 없으면 먼저 만든다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyInvitationSheet(onDismiss: () -> Unit, onCreated: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("MOTHER") }
    var invitationCode by remember { mutableStateOf<String?>(null) }
    var shareTextValue by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var createdFamilyId by remember { mutableStateOf<String?>(null) }

    val canCreate = name.isNotEmpty() && !isSubmitting

    // 새로 만든 가족은 시트를 닫을 때 세션에 넣는다. 넣는 순간 앱이 홈으로 돌아가 코드를 보여 줄 수 없다.
    fun close() {
        val familyId = createdFamilyId
        onDismiss()
        if (familyId != null && environment.session.familyId == null) {
            runCatching { environment.session.joinFamily(familyId) }
        }
    }

    fun createInvitation() {
        isSubmitting = true
        errorText = null
        scope.launch {
            try {
                if (environment.settings.isGuestMode) return@launch
                val hadFamily = environment.session.familyId != null
                val created = createFamilyInvitation(environment, name, relation)
                invitationCode = created.invitation.code
                shareTextValue = created.invitation.shareText
                if (hadFamily) onCreated() else createdFamilyId = created.familyId
                Haptics.commit()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorText = error.message
            } finally {
                isSubmitting = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = rememberModalBottomSheetState(),
        containerColor = CollogColors.gray50,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5, vertical = Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x5),
        ) {
            // iOS 툴바의 topBarTrailing "완료"
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    "완료",
                    style = CollogType.body02Semibold,
                    color = CollogColors.greenDark,
                    modifier = Modifier.pressable(onClick = ::close),
                )
            }

            Text("초대할 가족", style = CollogType.headline02, color = CollogColors.gray900)

            val code = invitationCode
            val share = shareTextValue
            if (code != null && share != null) {
                Column(Modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
                    Text("초대 코드", style = CollogType.body03Medium, color = CollogColors.gray700)
                    // iOS `.textSelection(.enabled)`는 옮기지 않았다
                    Text(code, style = pretendard(Pretendard.SemiBold, 28f, CollogColors.gray900))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(CollogColors.greenNormal, RoundedCornerShape(Radius.btnSmall))
                            .pressable { shareText(context, share) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("초대 내용 공유", style = CollogType.body01Semibold, color = CollogColors.gray00)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = pretendardPlain(Pretendard.Medium, 16f),
                        decorationBox = { field ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(CollogColors.gray00, RoundedCornerShape(Radius.btnSmall))
                                    .padding(horizontal = Spacing.x4),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (name.isEmpty()) {
                                    Text("이름", style = pretendardPlain(Pretendard.Medium, 16f, CollogColors.gray600))
                                }
                                field()
                            }
                        },
                    )

                    // 관계 일곱 가지는 한 줄 분할 선택에 들어가지 않아 줄바꿈되는 알약 버튼으로 고른다
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                    ) {
                        InvitationRelations.forEach { option ->
                            val selected = option == relation
                            Box(
                                Modifier
                                    .height(40.dp)
                                    .background(if (selected) CollogColors.greenNormal else CollogColors.gray00, CircleShape)
                                    .border(1.dp, if (selected) CollogColors.greenNormal else CollogColors.gray400, CircleShape)
                                    .semantics { this.selected = selected }
                                    .pressable { relation = option }
                                    .padding(horizontal = Spacing.x4),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    relationTitle(option) ?: "",
                                    style = CollogType.body02Semibold,
                                    color = if (selected) CollogColors.gray00 else CollogColors.gray800,
                                )
                            }
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(
                                if (canCreate) CollogColors.greenNormal else CollogColors.gray500,
                                RoundedCornerShape(Radius.btnSmall),
                            )
                            .pressable(enabled = canCreate) { createInvitation() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isSubmitting) "만드는 중" else "초대 만들기",
                            style = CollogType.body01Semibold,
                            color = CollogColors.gray00,
                        )
                    }
                }
            }

            errorText?.let {
                Text(it, style = CollogType.body03Medium, color = CollogColors.red500)
            }
        }
    }
}
