package live.collog.app.features.settings.views

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.models.HealthSelection
import live.collog.app.features.settings.models.ProfileTarget
import live.collog.app.features.settings.models.canSaveHealthProfile
import live.collog.app.features.settings.models.parentTargets
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.shared.models.HealthCondition
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.pressable

private const val GUEST_CONDITIONS_KEY = "settings.guestHealthConditions"

/** iOS `HealthProfileSettingsView`. 저장하면 이전 화면으로 돌아간다. */
@Composable
fun HealthProfileSettingsScreen() {
    val environment = LocalAppEnvironment.current
    val navigator = LocalNavigationManager.current
    val scope = rememberCoroutineScope()
    // iOS `@AppStorage("settings.guestHealthConditions")`: 체험 모드에서만 쓰는 값
    val guestPrefs = LocalContext.current.getSharedPreferences("collog.settings", Context.MODE_PRIVATE)

    var selection by remember { mutableStateOf(HealthSelection()) }
    var targets by remember { mutableStateOf<List<ProfileTarget>>(emptyList()) }
    var target by remember { mutableStateOf<ProfileTarget?>(null) }
    var profileLoaded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val isGuest = environment.settings.isGuestMode
    val canSave = canSaveHealthProfile(profileLoaded, selection, isSaving, isLoading)

    suspend fun loadProfile(candidate: ProfileTarget) {
        isLoading = true
        profileLoaded = false
        errorText = null
        target = candidate
        selection = HealthSelection()
        try {
            val profile = environment.api.profile(candidate.id)
            selection = HealthSelection.fromProfile(profile.conditions, profile.hasCompletedSetup)
            profileLoaded = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorText = error.message
        } finally {
            isLoading = false
        }
    }

    suspend fun load() {
        if (isGuest) {
            selection = HealthSelection.fromGuestString(guestPrefs.getString(GUEST_CONDITIONS_KEY, "HYPERTENSION") ?: "")
            profileLoaded = true
            isLoading = false
            return
        }
        val user = environment.session.user
        if (user == null) {
            errorText = "로그인이 필요해요."
            isLoading = false
            return
        }
        if (user.role == "PARENT") {
            val own = ProfileTarget(user.id, user.name)
            targets = listOf(own)
            loadProfile(own)
            return
        }
        val familyId = environment.session.familyId
        if (familyId == null) {
            errorText = "가족을 먼저 초대해주세요."
            isLoading = false
            return
        }
        try {
            targets = parentTargets(environment.api.members(familyId))
            if (targets.isEmpty()) errorText = "등록된 부모님이 없어요. 가족 초대를 먼저 완료해주세요."
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorText = error.message
        } finally {
            isLoading = false
        }
    }

    fun save() {
        if (!canSave) return
        isSaving = true
        errorText = null
        val values = selection.values
        if (isGuest) {
            guestPrefs.edit().putString(GUEST_CONDITIONS_KEY, values.joinToString(",")).apply()
            Haptics.commit()
            isSaving = false
            navigator.pop()
            return
        }
        val parentId = target?.id
        if (parentId == null) {
            errorText = "건강 프로필을 수정할 사람을 선택해주세요."
            isSaving = false
            return
        }
        scope.launch {
            try {
                environment.api.updateProfile(parentId, values)
                Haptics.commit()
                navigator.pop()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorText = error.message
            } finally {
                isSaving = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(CollogColors.gray50).statusBarsPadding()) {
        SettingsDetailHeader("건강 프로필", onBack = { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x5),
        ) {
            Column(Modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text("건강 질문을 맞춤 준비해드려요", style = CollogType.subtitle01, color = CollogColors.gray900)
                Text(
                    "선택한 항목은 오늘의 질문과 통화 요약을 준비할 때 참고해요.",
                    style = CollogType.body03Medium,
                    color = CollogColors.gray700,
                )
            }

            if (!isGuest) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Text("건강 프로필을 수정할 사람", style = CollogType.body02Medium, color = CollogColors.gray900)
                    targets.forEach { candidate ->
                        val isTarget = target?.id == candidate.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(CollogColors.gray00, RoundedCornerShape(Radius.btnSmall))
                                .alpha(if (isLoading || isSaving) 0.3f else 1f)
                                .semantics { selected = isTarget }
                                .pressable(enabled = !isLoading && !isSaving) { scope.launch { loadProfile(candidate) } }
                                .padding(Spacing.x4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(candidate.name, style = CollogType.body01Medium, color = CollogColors.gray900)
                            Spacer(Modifier.weight(1f))
                            if (isTarget) CollogIcon("checkmark", size = 20.dp, color = CollogColors.blue600)
                        }
                    }
                    val current = target
                    if (current != null) {
                        Text("${current.name}님의 건강 정보를 수정해요.", style = CollogType.body03Medium, color = CollogColors.gray700)
                    } else if (targets.isNotEmpty()) {
                        Text("이름을 선택한 뒤 건강 정보를 입력해주세요.", style = CollogType.body03Medium, color = CollogColors.gray700)
                    }
                }
            }

            val rowsEnabled = profileLoaded && !isLoading && !isSaving
            Column(
                Modifier.alpha(if (rowsEnabled) 1f else 0.3f),
                verticalArrangement = Arrangement.spacedBy(Spacing.x2),
            ) {
                (HealthCondition.entries + listOf(null)).forEach { condition ->
                    ConditionRow(
                        title = condition?.title ?: "해당 없음",
                        isSelected = selection.isSelected(condition),
                        enabled = rowsEnabled,
                    ) {
                        errorText = null
                        selection = selection.toggled(condition)
                        Haptics.focus()
                    }
                }
            }

            errorText?.let {
                Text(it, style = CollogType.body03Medium, color = CollogColors.red500)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        if (canSave) CollogColors.greenNormal else CollogColors.gray500,
                        RoundedCornerShape(Radius.btnSmall),
                    )
                    .pressable(enabled = canSave) { save() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isSaving) "저장 중" else "저장", style = CollogType.body01Semibold, color = CollogColors.gray00)
            }
        }
    }
}

@Composable
private fun ConditionRow(title: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(CollogColors.gray00, shape)
            .border(1.dp, if (isSelected) CollogColors.green300 else CollogColors.gray200, shape)
            .semantics { selected = isSelected }
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.x4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = CollogType.body02Medium, color = CollogColors.gray900)
        Spacer(Modifier.weight(1f))
        if (isSelected) CollogIcon("checkmark", size = 16.dp, color = CollogColors.greenDark)
    }
}
