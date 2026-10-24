package live.collog.app.features.settings.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.views.InvitationAcceptSheet
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.settings.models.canSaveRole
import live.collog.app.features.settings.models.deleteAccountAndSignOut
import live.collog.app.features.settings.models.roleText
import live.collog.app.features.settings.models.showsInvitationCodeRow
import live.collog.app.features.settings.models.updateAccountRole
import live.collog.app.features.settings.views.components.SettingsAlert
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SettingsNavigationRow
import live.collog.app.features.settings.views.components.SettingsSection
import live.collog.app.features.settings.views.components.SettingsValueRow
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.models.UserRoleOption
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.SegmentedPicker
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface

/**
 * iOS `AccountSettingsView`. 시트로 띄우면 [onClose]를 넘기고, 탭 안에서 밀어 넣으면 null이다.
 * Apple 재인증(`AppleDeletionAuthorization`)은 없다. Google·전화 계정은 바로 삭제된다.
 */
@Composable
fun AccountSettingsScreen(onClose: (() -> Unit)? = null) {
    val environment = LocalAppEnvironment.current
    val callCenter = LocalCallCenter.current
    val navigator = LocalNavigationManager.current
    val scope = rememberCoroutineScope()
    val user = environment.session.user

    var selectedRole by remember { mutableStateOf(UserRoleOption.from(user?.role) ?: UserRoleOption.Child) }
    var isSubmitting by remember { mutableStateOf(false) }
    var confirmsRoleChange by remember { mutableStateOf(false) }
    var confirmsDeletion by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showsInvitationCode by remember { mutableStateOf(false) }

    val displayName = user?.name ?: "데모 사용자"
    val role = roleText(user?.role)
    val locked = isSubmitting || callCenter.hasCallInProgress

    fun updateRole() {
        if (isSubmitting || callCenter.hasCallInProgress) return
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                updateAccountRole(environment, selectedRole)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message
            } finally {
                isSubmitting = false
            }
        }
    }

    fun deleteAccount() {
        if (isSubmitting || callCenter.hasCallInProgress) return
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                deleteAccountAndSignOut(environment)
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
            .then(if (onClose == null) Modifier.statusBarsPadding() else Modifier),
    ) {
        SettingsDetailHeader("계정 정보", onBack = onClose ?: { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x5),
        ) {
            Column(Modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text(displayName, style = CollogType.subtitle01, color = CollogColors.gray900)
                Text(role, style = CollogType.body03Medium, color = CollogColors.gray700)
            }

            SettingsSection("기본 정보") {
                SettingsValueRow("이름", displayName)
                DividerLine()
                user?.phone?.let { phone ->
                    SettingsValueRow("전화번호", phone)
                    DividerLine()
                }
                SettingsValueRow("역할", role)
            }

            SettingsSection("가족") {
                SettingsValueRow("등록된 가족", "${environment.family.contacts.size}명")
                if (showsInvitationCodeRow(environment.session.isAuthenticated, user?.role)) {
                    DividerLine()
                    SettingsNavigationRow("초대 코드 입력", enabled = !locked) { showsInvitationCode = true }
                }
            }

            if (environment.session.isAuthenticated) {
                SettingsSection("역할 변경") {
                    Column(
                        Modifier.fillMaxWidth().padding(Spacing.x4).alpha(if (locked) 0.3f else 1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
                    ) {
                        SegmentedPicker(
                            options = UserRoleOption.entries,
                            selection = selectedRole,
                            onSelect = { selectedRole = it },
                            label = { it.title },
                            enabled = !locked,
                        )
                        Text(
                            "부모는 본인의 건강 기록을, 자녀는 가족의 건강 기록을 확인해요.",
                            style = CollogType.caption01Medium,
                            color = CollogColors.gray700,
                        )
                        SystemTextButton(
                            "역할 저장",
                            enabled = !locked && canSaveRole(selectedRole, user?.role),
                        ) { confirmsRoleChange = true }
                    }
                }

                if (callCenter.hasCallInProgress) {
                    Text(
                        "통화를 마친 뒤 역할 변경과 계정 삭제를 할 수 있어요.",
                        style = CollogType.caption01Medium,
                        color = CollogColors.gray700,
                    )
                }
                errorMessage?.let {
                    Text(it, style = CollogType.caption01Medium, color = CollogColors.red500)
                }
                if (isSubmitting) CollogProgress()

                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SystemTextButton("계정 삭제", enabled = !locked, destructive = true) { confirmsDeletion = true }
                }
            }
        }
    }

    if (showsInvitationCode) {
        InvitationAcceptSheet(onDismiss = { showsInvitationCode = false })
    }
    if (confirmsRoleChange) {
        SettingsAlert(
            title = "역할을 ${selectedRole.title}(으)로 변경할까요?",
            message = "가족은 유지돼요. 부모로 변경하면 건강 정보 동의와 프로필 설정이 필요할 수 있어요.",
            confirmText = "변경",
            onConfirm = ::updateRole,
            onDismiss = { confirmsRoleChange = false },
        )
    }
    if (confirmsDeletion) {
        SettingsAlert(
            title = "계정을 삭제할까요?",
            message = "계정과 관련 통화 기록, 녹음, 건강 정보가 삭제돼요. 삭제 후에는 복구할 수 없어요.",
            confirmText = "삭제",
            destructive = true,
            onConfirm = ::deleteAccount,
            onDismiss = { confirmsDeletion = false },
        )
    }
}
