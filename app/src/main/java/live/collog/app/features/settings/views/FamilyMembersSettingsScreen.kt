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
import live.collog.app.features.settings.views.components.SettingsActionButton
import live.collog.app.features.settings.views.components.SettingsAlert
import live.collog.app.features.settings.views.components.SystemTextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import live.collog.app.features.settings.models.relationTitle
import live.collog.app.shared.components.spec.SpecDetailHeader
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.services.api.FamilyInvitation
import live.collog.app.shared.services.api.FamilySummary
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/**
 * 새 피그마 17 가족 구성원 관리(카드 탭 → 상세 시트). 옛 「최종본」 277:5984 의 동작을 유지한다. 홈의 시트로 띄우면 [onClose]를 넘기고, 설정 탭 안에서는 null이다.
 * 역할 구분 없이 누구나 초대하고, 가족이 없으면 첫 초대 때 가족을 만든다.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var detailMemberId by remember { mutableStateOf<String?>(null) }
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
                val current = environment.session.familyId
                // 구성원에서 삭제되어 지금 가족이 목록에 없으면 남은 가족으로 옮기거나 가족 없음으로 되돌린다
                if (available.none { it.id == current } && (first != null || current != null)) {
                    if (first != null) environment.session.joinFamily(first.id) else environment.session.leaveFamily()
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
            .background(SpecColors.page)
            .then(if (onClose == null) Modifier.statusBarsPadding() else Modifier),
    ) {
        SpecDetailHeader("가족 구성원 관리", onBack = onClose ?: { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 19.dp)
                .padding(top = 9.dp, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            if (families.size > 1) {
                FamilyPicker(
                    families = families,
                    selectedId = environment.session.familyId ?: "",
                    enabled = !isLoading,
                    onSelect = { id -> scope.launch { selectFamily(id) } },
                )
            }

            // 새 피그마 17: 「함께 연결된 가족」 17/700 + 오른쪽 「N명」 10/400
            if (!isLoading && members.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(start = 0.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("함께 연결된 가족", Modifier.weight(1f), style = specText(Pretendard.Bold, 17f))
                    Text("${members.size}명", style = specText(Pretendard.Regular, 10f, SpecColors.muted))
                }
            }

            if (isLoading) {
                repeat(2) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(SpecColors.chip, RoundedCornerShape(16.dp)),
                    )
                }
            } else if (members.isEmpty() && errorText == null) {
                Text(
                    "아직 함께하는 가족이 없어요. 가족을 초대하거나 받은 초대 코드를 입력해 보세요.",
                    style = specText(Pretendard.Regular, 14f, SpecColors.muted),
                )
            } else {
                members.forEach { member ->
                    MemberCard(member, onClick = { detailMemberId = member.id })
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
            if (showsInvite) {
                InviteButton {
                    if (isGuest) noticeText = "체험 모드에서는 초대를 보내지 않아요." else showsInvitation = true
                }
            }
            // 명세에 없지만 받은 초대 코드를 넣는 길은 여기뿐이라 점선 버튼 아래 글자 링크로 남긴다
            if (canEnterCode) {
                Text(
                    "받은 초대 코드 입력",
                    Modifier.align(Alignment.CenterHorizontally).pressable { showsInvitationCode = true }.padding(Spacing.x2),
                    style = specText(Pretendard.Medium, 13f, SpecColors.sub),
                )
            }
        }
    }

    // 카드를 누르면 열리는 구성원 상세: 질환 프로필 편집·초대 재발송·삭제
    val detailMember = members.firstOrNull { it.id == detailMemberId }
    if (detailMember != null) {
        ModalBottomSheet(
            onDismissRequest = { detailMemberId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SpecColors.page,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 19.dp).padding(bottom = Spacing.x8),
                verticalArrangement = Arrangement.spacedBy(Spacing.x3),
            ) {
                MemberCard(detailMember, onClick = null)
                detailMember.statusText()?.let {
                    Text(it, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
                }
                MemberActions(
                    member = detailMember,
                    enabled = !isRemoving,
                    onEdit = {
                        detailMemberId = null
                        editProfile(detailMember)
                    },
                    onResend = {
                        detailMemberId = null
                        resend(detailMember)
                    },
                    onRemove = {
                        detailMemberId = null
                        removalCandidate = detailMember
                    },
                )
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
 * 새 피그마 17 구성원 카드: 이름 13/700, 관계 12/400 muted, 오른쪽 「통화 가능」 14/700 초록.
 * 아직 수락하지 않은 초대는 「통화 가능」 대신 상태 글자를 muted 로 보인다. [onClick]이 있으면 누르면 상세가 열린다.
 */
@Composable
private fun MemberCard(member: ManagedFamilyMember, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .specCard(padding = 0.dp, radius = 16.dp)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 17.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(member.name, style = specText(Pretendard.Bold, 13f), maxLines = 1)
            relationTitle(member.relation)?.let {
                Text(it, style = specText(Pretendard.Regular, 12f, SpecColors.muted), maxLines = 1)
            }
        }
        if (member.isConnected) {
            Text("통화 가능", style = specText(Pretendard.Bold, 14f, SpecColors.green))
        } else {
            Text(
                if (member.invitation?.isExpired == true) "초대 만료" else "초대 대기 중",
                style = specText(Pretendard.Medium, 13f, SpecColors.muted),
            )
        }
    }
}

/** 새 피그마 17 「가족 초대하기」: 흰 점선풍 초록 테두리 버튼(`#bfe8cf`), 「+」 14/400 · 글자 14/700 초록, 높이 53. */
@Composable
private fun InviteButton(onClick: () -> Unit) {
    Row(
        Modifier
            .specCard(padding = 0.dp, radius = 14.dp, borderColor = SpecColors.greenOutline)
            .height(53.dp)
            .pressable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+", style = specText(Pretendard.Regular, 14f, SpecColors.green))
        Spacer(Modifier.width(24.dp))
        Text("가족 초대하기", style = specText(Pretendard.Bold, 14f, SpecColors.green))
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
