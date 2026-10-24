package live.collog.app.features.settings.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import live.collog.app.features.settings.models.ManagedFamilyMember
import live.collog.app.features.settings.models.canInviteFamily
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.services.api.FamilyInvitation
import live.collog.app.shared.services.api.FamilySummary
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/**
 * iOS `FamilyMembersSettingsView`. 홈의 시트로 띄우면 [onClose]를 넘기고, 설정 탭 안에서는 null이다.
 */
@Composable
fun FamilyMembersSettingsScreen(onClose: (() -> Unit)? = null) {
    val environment = LocalAppEnvironment.current
    val navigator = LocalNavigationManager.current
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<ManagedFamilyMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showsInvitation by remember { mutableStateOf(false) }
    var selectedInvitation by remember { mutableStateOf<FamilyInvitation?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var serverAllowsInvitations by remember { mutableStateOf(false) }
    var showsInvitationCode by remember { mutableStateOf(false) }
    var families by remember { mutableStateOf<List<FamilySummary>>(emptyList()) }

    val role = environment.session.user?.role
    val isGuest = environment.settings.isGuestMode
    val canInvite = canInviteFamily(isGuest, role, serverAllowsInvitations)

    suspend fun load() {
        isLoading = true
        errorText = null
        serverAllowsInvitations = false
        try {
            if (isGuest) {
                members = environment.family.contacts.map(::ManagedFamilyMember)
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
                members = remote.members.map(::ManagedFamilyMember)
                serverAllowsInvitations = remote.canInvite ?: (environment.session.user?.role == "CHILD")
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

    LaunchedEffect(Unit) { load() }

    Column(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray50)
            .then(if (onClose == null) Modifier.statusBarsPadding() else Modifier),
    ) {
        SettingsDetailHeader("가족 구성원 관리", onBack = onClose ?: { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x5),
        ) {
            if (families.size > 1) {
                FamilyPicker(
                    families = families,
                    selectedId = environment.session.familyId ?: "",
                    enabled = !isLoading,
                    onSelect = { id -> scope.launch { selectFamily(id) } },
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("함께 연결된 가족", style = CollogType.subtitle01, color = CollogColors.gray900)
                Spacer(Modifier.weight(1f))
                Text("${members.size}명", style = CollogType.body03Medium, color = CollogColors.gray700)
            }

            if (isLoading) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    repeat(2) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .background(CollogColors.gray100, RoundedCornerShape(Radius.card)),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    members.forEach { member ->
                        MemberRow(member, canInvite = canInvite, onShowInvitation = { selectedInvitation = it })
                    }
                }
            }

            errorText?.let {
                Text(it, style = CollogType.body03Medium, color = CollogColors.red500)
                SystemTextButton("다시 시도") { scope.launch { load() } }
            }

            if (canInvite) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(CollogColors.gray00, RoundedCornerShape(Radius.btnSmall))
                        .border(1.dp, CollogColors.green200, RoundedCornerShape(Radius.btnSmall))
                        .pressable { showsInvitation = true },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x2, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CollogIcon("plus", size = 16.dp, color = CollogColors.greenDark)
                    Text("가족 초대하기", style = CollogType.body02Semibold, color = CollogColors.greenDark)
                }
            }
            if (!isGuest && role == "PARENT") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .pressable { showsInvitationCode = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("초대 코드 입력", style = CollogType.body02Semibold, color = CollogColors.greenDark)
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
}

@Composable
private fun MemberRow(member: ManagedFamilyMember, canInvite: Boolean, onShowInvitation: (FamilyInvitation) -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(CollogColors.gray00, shape)
            .border(1.dp, CollogColors.gray200, shape)
            .padding(horizontal = Spacing.x4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(member.name, style = CollogType.body01Semibold, color = CollogColors.gray900)
            Text(member.relationTitle, style = CollogType.caption01Medium, color = CollogColors.gray700)
        }
        Text(
            member.statusText,
            style = CollogType.caption01Semibold,
            color = if (member.isConnected) CollogColors.greenDark else CollogColors.gray700,
        )
        val invitation = member.invitation
        if (canInvite && invitation != null && !member.isConnected) {
            Text(
                "초대 보기",
                style = CollogType.body03Medium,
                color = CollogColors.greenDark,
                modifier = Modifier.pressable { onShowInvitation(invitation) },
            )
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
