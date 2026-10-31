package live.collog.app.features.settings.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.views.InvitationAcceptSheet
import live.collog.app.features.settings.models.GuestSamples
import live.collog.app.features.settings.models.ManagedFamilyMember
import live.collog.app.features.settings.models.ProfileTarget
import live.collog.app.features.settings.models.canEnterInvitationCode
import live.collog.app.features.settings.models.canInviteFamily
import live.collog.app.features.settings.models.otherMembers
import live.collog.app.features.settings.views.components.MemberTitle
import live.collog.app.features.settings.views.components.SettingsActionButton
import live.collog.app.features.settings.views.components.SettingsAlert
import live.collog.app.features.settings.views.components.SettingsCallout
import live.collog.app.features.settings.views.components.SettingsCalloutText
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SettingsLink
import live.collog.app.features.settings.views.components.SettingsMutedColor
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.services.api.FamilyInvitation
import live.collog.app.shared.services.api.FamilySummary
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.singleLine
import live.collog.app.shared.ui.pressable

/**
 * 피그마 「최종본」 가족 구성원 관리(277:5984). 홈의 시트로 띄우면 [onClose]를 넘기고, 설정 탭 안에서는 null이다.
 * 역할 구분 없이 누구나 초대하고, 가족이 없으면 첫 초대 때 가족을 만든다.
 */
@Composable
fun FamilyMembersSettingsScreen(onClose: (() -> Unit)? = null) {
    val environment = LocalAppEnvironment.current
    val navigator = LocalNavigationManager.current
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<ManagedFamilyMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var noticeText by remember { mutableStateOf<String?>(null) }
    var serverAllowsInvitations by remember { mutableStateOf(true) }
    var families by remember { mutableStateOf<List<FamilySummary>>(emptyList()) }
    var showsInvitation by remember { mutableStateOf(false) }
    var showsInvitationCode by remember { mutableStateOf(false) }
    var selectedInvitation by remember { mutableStateOf<FamilyInvitation?>(null) }
    var removalCandidate by remember { mutableStateOf<ManagedFamilyMember?>(null) }
    var isRemoving by remember { mutableStateOf(false) }
    // 시트 안에서는 탭 화면 스택이 없으므로 질환 프로필을 이 화면 안에서 바꿔 그린다
    var sheetProfile by remember { mutableStateOf<ProfileTarget?>(null) }

    val isGuest = environment.settings.isGuestMode
    val canInvite = canInviteFamily(isGuest, environment.session.familyId != null, serverAllowsInvitations)
    val canEnterCode = canEnterInvitationCode(isGuest, members)

    suspend fun load() {
        isLoading = true
        errorText = null
        try {
            if (isGuest) {
                members = GuestSamples.members
                return
            }
            val userId = environment.session.user?.id
            try {
                val available = environment.api.families()
                if (environment.session.user?.id != userId) return
                families = available
                val first = available.firstOrNull()
                if (available.none { it.id == environment.session.familyId } && first != null) {
                    environment.session.joinFamily(first.id)
                    environment.family.reset()
                    environment.family.refresh(environment)
                }
                val familyId = environment.session.familyId
                if (familyId == null) {
                    members = emptyList()
                    return
                }
                val remote = environment.api.familyMembers(familyId)
                if (environment.session.user?.id != userId || environment.session.familyId != familyId) return
                members = otherMembers(remote.members, userId)
                serverAllowsInvitations = remote.canInvite ?: true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (environment.session.user?.id != userId) return
                errorText = error.message
            }
        } finally {
            isLoading = false
        }
    }

    suspend fun selectFamily(familyId: String) {
        if (isLoading || families.none { it.id == familyId } || familyId == environment.session.familyId) return
        try {
            environment.session.joinFamily(familyId)
            environment.family.reset()
            members = emptyList()
            load()
            environment.family.refresh(environment)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorText = error.message
        }
    }

    fun remove(member: ManagedFamilyMember) {
        if (isGuest) {
            members = members - member
            Haptics.commit()
            return
        }
        val familyId = environment.session.familyId ?: return
        isRemoving = true
        errorText = null
        scope.launch {
            try {
                environment.api.removeFamilyMember(familyId, member.id)
                Haptics.commit()
                load()
                environment.family.refresh(environment)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorText = error.message
            } finally {
                isRemoving = false
            }
        }
    }

    fun resend(member: ManagedFamilyMember) {
        val invitation = member.invitation ?: return
        if (isGuest) {
            noticeText = "체험 모드에서는 초대를 보내지 않아요."
            return
        }
        selectedInvitation = invitation
    }

    fun editProfile(member: ManagedFamilyMember) {
        val target = member.profileTarget ?: return
        if (onClose == null) navigator.push(SettingsRoute.HealthProfile(target)) else sheetProfile = target
    }

    LaunchedEffect(Unit) { load() }

    val profile = sheetProfile
    if (profile != null) {
        BackHandler { sheetProfile = null }
        HealthProfileSettingsScreen(profile, onBack = { sheetProfile = null }, inSheet = true)
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray00)
            .then(if (onClose == null) Modifier.statusBarsPadding() else Modifier),
    ) {
        SettingsDetailHeader("가족 구성원 관리", onBack = onClose ?: { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5)
                .padding(top = Spacing.x5, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            if (families.size > 1) {
                FamilyPicker(
                    families = families,
                    selectedId = environment.session.familyId ?: "",
                    enabled = !isLoading,
                    onSelect = { id -> scope.launch { selectFamily(id) } },
                )
            }

            if (isLoading) {
                repeat(2) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .background(CollogColors.gray200, RoundedCornerShape(12.dp)),
                    )
                }
            } else if (members.isEmpty() && errorText == null) {
                SettingsCallout {
                    SettingsCalloutText("아직 함께하는 가족이 없어요. 가족을 초대하거나 받은 초대 코드를 입력해 보세요.")
                }
            } else {
                members.forEach { member ->
                    MemberCard(member)
                    MemberActions(
                        member = member,
                        enabled = !isRemoving,
                        onEdit = { editProfile(member) },
                        onResend = { resend(member) },
                        onRemove = { removalCandidate = member },
                    )
                }
            }

            errorText?.let {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Text(it, style = CollogType.body03Medium, color = CollogColors.red500)
                    SystemTextButton("다시 시도") { scope.launch { load() } }
                }
            }
            noticeText?.let { Text(it, style = CollogType.body03Medium, color = CollogColors.gray700) }

            // 체험 모드는 버튼만 보이고 누르면 안내한다
            val showsInvite = canInvite || isGuest
            if (showsInvite || canEnterCode) {
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.x3),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
                ) {
                    if (canEnterCode) {
                        SettingsActionButton(
                            "초대 코드 입력",
                            Modifier.weight(1f),
                            background = CollogColors.gray200,
                            foreground = CollogColors.gray900,
                        ) { showsInvitationCode = true }
                    }
                    if (showsInvite) {
                        SettingsActionButton("가족 초대하기", Modifier.weight(1f)) {
                            if (isGuest) noticeText = "체험 모드에서는 초대를 보내지 않아요." else showsInvitation = true
                        }
                    }
                }
            }
        }
    }

    if (showsInvitationCode) {
        InvitationAcceptSheet(
            onDismiss = { showsInvitationCode = false },
            onAccepted = { scope.launch { load() } },
        )
    }
    if (showsInvitation) {
        FamilyInvitationSheet(
            onDismiss = { showsInvitation = false },
            onCreated = { scope.launch { load() } },
        )
    }
    selectedInvitation?.let { invitation ->
        ExistingInvitationSheet(
            invitation = invitation,
            onDismiss = { selectedInvitation = null },
            onUpdated = { scope.launch { load() } },
        )
    }
    removalCandidate?.let { member ->
        SettingsAlert(
            title = if (member.isConnected) "${member.name}님을 가족에서 삭제할까요?" else "${member.name}님에게 보낸 초대를 삭제할까요?",
            message = if (member.isConnected) {
                "서로 전화하거나 기록을 볼 수 없게 돼요. 다시 함께하려면 새로 초대해야 해요."
            } else {
                "보낸 초대 코드는 더 이상 쓸 수 없어요."
            },
            confirmText = "삭제",
            destructive = true,
            onConfirm = { remove(member) },
            onDismiss = { removalCandidate = null },
        )
    }
}

/**
 * 피그마 구성원 카드(277:6110). 둘째 줄 「드링클리 본점」은 자리 글자라 닉네임이 있을 때만 쓴다.
 * 가족을 만든 사람은 서버가 이름 자리에 닉네임을 주므로, 제목과 같으면 둘째 줄을 비운다.
 */
@Composable
private fun MemberCard(member: ManagedFamilyMember) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray200, RoundedCornerShape(12.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            MemberTitle(member.title)
            val nickname = member.nickname?.takeIf { it != member.name }
            if (nickname != null) {
                Text(nickname, style = singleLine(Pretendard.Medium, 14f), color = CollogColors.gray700)
            } else {
                Spacer(Modifier.height(17.dp))
            }
            Text(member.statusText() ?: "", style = singleLine(Pretendard.Regular, 14f), color = SettingsMutedColor)
        }
        if (member.consentGranted) SettingsLink("동의됨")
    }
}

/** 피그마 Block Btn(277:6192): 연결된 구성원은 「질환 프로필 편집」, 대기 중 초대는 「초대 재발송」, 지울 수 있으면 「구성원 삭제」. */
@Composable
private fun MemberActions(
    member: ManagedFamilyMember,
    enabled: Boolean,
    onEdit: () -> Unit,
    onResend: () -> Unit,
    onRemove: () -> Unit,
) {
    val leading = when {
        member.isConnected -> "질환 프로필 편집" to onEdit
        member.invitation != null -> "초대 재발송" to onResend
        else -> null
    }
    if (leading == null && !member.canRemove) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        if (leading != null) {
            SettingsActionButton(
                leading.first,
                Modifier.weight(1f),
                background = CollogColors.gray200,
                foreground = CollogColors.gray900,
                enabled = enabled,
                onClick = leading.second,
            )
        }
        if (member.canRemove) {
            SettingsActionButton("구성원 삭제", Modifier.weight(1f), background = CollogColors.red500, enabled = enabled, onClick = onRemove)
        }
    }
}

/** iOS `Picker("가족 선택", ...)`(메뉴 형태): 현재 가족 이름을 누르면 목록이 열린다. */
@Composable
private fun FamilyPicker(families: List<FamilySummary>, selectedId: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.3f)) {
        Row(
            Modifier.fillMaxWidth().pressable(enabled = enabled) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("가족 선택", style = CollogType.body02Medium, color = CollogColors.gray900)
            Spacer(Modifier.weight(1f))
            Text(
                families.firstOrNull { it.id == selectedId }?.name ?: "",
                style = CollogType.body02Medium,
                color = CollogColors.blue600,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = CollogColors.gray00) {
            families.forEach { family ->
                DropdownMenuItem(
                    text = { Text(family.name, style = CollogType.body02Medium, color = CollogColors.gray900) },
                    onClick = {
                        expanded = false
                        onSelect(family.id)
                    },
                )
            }
        }
    }
}
