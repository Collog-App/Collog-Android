package live.collog.app.features.settings.views

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.R
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.models.CONCERNS_LIMIT
import live.collog.app.features.settings.models.GuestSamples
import live.collog.app.features.settings.models.HealthSelection
import live.collog.app.features.settings.models.ProfileTarget
import live.collog.app.features.settings.models.ageText
import live.collog.app.features.settings.models.canSaveHealthProfile
import live.collog.app.features.settings.models.ownProfileTarget
import live.collog.app.features.settings.views.components.MemberTitle
import live.collog.app.features.settings.views.components.SettingsActionButton
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SettingsSection
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.components.tabbar.BottomNavBar
import live.collog.app.shared.models.HealthCondition
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable

private const val GUEST_CONDITIONS_KEY = "settings.guestHealthConditions"
private const val GUEST_CONCERNS_KEY = "settings.guestHealthConcerns"

/**
 * 피그마 「최종본」 질환 프로필 요약(278:6645, 비었을 때 278:6731)과 편집(269:3917 모양).
 * [target]이 null이면 나의 건강 프로필, 아니면 그 가족 구성원의 것. [inSheet]면 위쪽 상태 막대 여백을 두지 않는다.
 */
@Composable
internal fun HealthProfileSettingsScreen(target: ProfileTarget?, onBack: () -> Unit, inSheet: Boolean = false) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    // 체험 모드에서만 쓰는 값. 나의 것은 iOS `@AppStorage("settings.guestHealthConditions")`와 같은 키다
    val guestPrefs = LocalContext.current.getSharedPreferences("collog.settings", Context.MODE_PRIVATE)
    val isGuest = environment.settings.isGuestMode
    val subject = target ?: (if (isGuest) GuestSamples.user else environment.session.user)?.let(::ownProfileTarget)
    val guestSuffix = target?.let { ".${it.id}" } ?: ""

    var selection by remember { mutableStateOf(HealthSelection()) }
    var concerns by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isEditing by remember { mutableStateOf(false) }

    suspend fun load() {
        isLoading = true
        errorText = null
        try {
            if (isGuest) {
                val (conditions, note) = GuestSamples.profile(target?.id)
                selection = HealthSelection.fromGuestString(guestPrefs.getString(GUEST_CONDITIONS_KEY + guestSuffix, conditions) ?: "")
                concerns = guestPrefs.getString(GUEST_CONCERNS_KEY + guestSuffix, note) ?: ""
                profileLoaded = true
                return
            }
            val id = subject?.id
            if (id == null) {
                errorText = "로그인이 필요해요."
                return
            }
            val profile = environment.api.profile(id)
            selection = HealthSelection.fromProfile(profile.conditions, profile.hasCompletedSetup)
            concerns = profile.concerns.orEmpty()
            profileLoaded = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorText = error.message
        } finally {
            isLoading = false
        }
    }

    suspend fun save(next: HealthSelection, note: String) {
        val trimmed = note.trim()
        if (isGuest) {
            guestPrefs.edit()
                .putString(GUEST_CONDITIONS_KEY + guestSuffix, next.values.joinToString(","))
                .putString(GUEST_CONCERNS_KEY + guestSuffix, trimmed)
                .apply()
            selection = next
            concerns = trimmed
        } else {
            val id = subject?.id ?: throw ApiError.Unauthenticated
            val saved = environment.api.updateProfile(id, next.values, trimmed)
            selection = HealthSelection.fromProfile(saved.conditions, hasCompletedSetup = true)
            concerns = saved.concerns.orEmpty()
        }
        isEditing = false
    }

    LaunchedEffect(Unit) { load() }
    BackHandler(enabled = isEditing) { isEditing = false }

    val screen = Modifier
        .fillMaxSize()
        .background(CollogColors.gray00)
        .then(if (inSheet) Modifier else Modifier.statusBarsPadding())
    if (isEditing) {
        HealthProfileEditor(
            initialSelection = selection,
            initialConcerns = concerns,
            onCancel = { isEditing = false },
            onSave = ::save,
            modifier = screen,
            // 탭 안에서는 아래 막대의 전화 버튼이 막대 위로 튀어나온 만큼 버튼을 올린다
            bottomInset = if (inSheet) 0.dp else BottomNavBar.topPadding,
        )
        return
    }

    Column(screen) {
        SettingsDetailHeader(if (target == null) "나의 건강 프로필" else "가족 건강 프로필", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5)
                .padding(top = Spacing.x5, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            SettingsSection("질환 프로필", spacing = Spacing.x5) {
                ProfileSummaryCard(
                    title = subject?.title ?: "",
                    age = ageText(subject?.birthDate),
                    selection = selection,
                    concerns = concerns,
                    isLoading = isLoading,
                )
            }

            SettingsActionButton(
                "질환 프로필 편집",
                Modifier.fillMaxWidth(),
                enabled = profileLoaded && !isLoading,
            ) { isEditing = true }

            errorText?.let {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Text(it, style = CollogType.body03Medium, color = CollogColors.red500)
                    SystemTextButton("다시 시도", enabled = !isLoading) { scope.launch { load() } }
                }
            }
        }
    }
}

/** 피그마 질환 프로필 카드(278:7153). */
@Composable
private fun ProfileSummaryCard(title: String, age: String?, selection: HealthSelection, concerns: String, isLoading: Boolean) {
    val conditions = selection.ordered
    val isEmpty = conditions.isEmpty() && concerns.isBlank()
    Column(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray200, RoundedCornerShape(Radius.listItem))
            .padding(start = 11.dp, end = 11.dp, top = 19.dp, bottom = 27.dp),
        verticalArrangement = Arrangement.spacedBy(if (isEmpty) Spacing.x5 else 30.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
            MemberTitle(title, trailing = age)
            if (!isEmpty && !isLoading) {
                Text("질환이나 걱정되는 부분을 관리해 보아요", style = CollogType.body02Regular, color = CollogColors.gray800)
            }
        }
        when {
            isLoading -> CollogProgress()
            isEmpty -> Text(
                if (selection.hasNoConditions) "선택한 질환이 없어요" else "아직 등록한 질환이 없어요\n질환 프로필을 편집해 보세요!",
                style = pretendard(Pretendard.Regular, 16f).copy(lineHeight = 22.4.sp),
                color = CollogColors.gray800,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                conditions.forEach { ConditionSummaryRow(it) }
                if (concerns.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(CollogColors.green100, RoundedCornerShape(Radius.listItem))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.x1),
                    ) {
                        Text("추가로 걱정되는 부분", style = CollogType.body02Semibold, color = CollogColors.gray800)
                        Text(concerns, style = CollogType.body02Regular, color = CollogColors.gray800)
                    }
                }
            }
        }
    }
}

/** 피그마 질환 줄(278:7069). 누르면 수집 정보·활용(277:5137)을 펼친다. */
@Composable
private fun ConditionSummaryRow(condition: HealthCondition) {
    var expanded by rememberSaveable(condition.rawValue) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.green100, RoundedCornerShape(Radius.listItem))
            .pressable { expanded = !expanded }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_condition_badge),
                contentDescription = null,
                tint = CollogColors.green700,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(15.dp))
            Text(
                condition.title,
                style = pretendard(Pretendard.SemiBold, 16f).copy(lineHeight = 22.4.sp),
                color = CollogColors.gray800,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = if (expanded) "설명 접기" else "설명 보기",
                tint = CollogColors.green700,
                modifier = Modifier.size(24.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            Text(
                conditionDescription(condition),
                style = CollogType.body02Regular,
                color = CollogColors.gray800,
                modifier = Modifier.padding(start = 39.dp),
            )
        }
    }
}

private fun conditionDescription(condition: HealthCondition): String =
    "수집 정보: ${condition.collected}\n활용: ${condition.usage}"

/** 피그마 질환 선택(269:3917): 질환 고르기 + 추가로 걱정되는 부분. 고른 질환의 설명 상자(277:5137)는 그 아래에 보인다. */
@Composable
private fun HealthProfileEditor(
    initialSelection: HealthSelection,
    initialConcerns: String,
    onCancel: () -> Unit,
    onSave: suspend (HealthSelection, String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(initialSelection) }
    var concerns by remember { mutableStateOf(initialConcerns) }
    var isPickerOpen by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val canSave = canSaveHealthProfile(profileLoaded = true, selection = selection, isSaving = isSaving, isLoading = false)

    fun save() {
        if (!canSave) return
        isSaving = true
        errorText = null
        scope.launch {
            try {
                onSave(selection, concerns)
                Haptics.commit()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorText = error.message
            } finally {
                isSaving = false
            }
        }
    }

    Column(modifier.imePadding()) {
        SettingsDetailHeader("질환 프로필 편집", onBack = onCancel)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5)
                .padding(top = Spacing.x5, bottom = Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                FieldLabel("앓고 있는 질환을 선택해주세요.") {
                    Text("*", style = pretendard(Pretendard.SemiBold, 14f), color = CollogColors.red600)
                }
                ConditionPicker(
                    selection = selection,
                    isOpen = isPickerOpen,
                    enabled = !isSaving,
                    onToggleOpen = { isPickerOpen = !isPickerOpen },
                    onToggle = { condition ->
                        errorText = null
                        selection = selection.toggled(condition)
                        Haptics.focus()
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                FieldLabel("추가적으로 걱정되는 부분을 작성해주세요.") {
                    Text("선택", style = pretendard(Pretendard.Regular, 12f), color = CollogColors.gray700)
                }
                BasicTextField(
                    value = concerns,
                    onValueChange = { concerns = it.take(CONCERNS_LIMIT) },
                    enabled = !isSaving,
                    textStyle = pretendardPlain(Pretendard.Medium, 16f, CollogColors.gray900),
                    decorationBox = { field ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 95.dp)
                                .background(CollogColors.gray00, RoundedCornerShape(Radius.listItem))
                                .border(1.dp, CollogColors.gray400, RoundedCornerShape(Radius.listItem))
                                .padding(horizontal = Spacing.x5, vertical = Spacing.x3),
                        ) {
                            if (concerns.isEmpty()) {
                                Text("내용을 작성해주세요.", style = pretendardPlain(Pretendard.Medium, 16f, CollogColors.gray600))
                            }
                            field()
                        }
                    },
                )
                Text(
                    "${concerns.length}/$CONCERNS_LIMIT",
                    style = pretendard(Pretendard.Regular, 12f),
                    color = CollogColors.gray700,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 피그마 12의 안내 상자 자리(269:3925)에 고른 질환 설명(277:5137)을 둔다
            selection.ordered.forEach { ConditionDescription(it) }

            errorText?.let { Text(it, style = CollogType.body03Medium, color = CollogColors.red500) }
        }

        // 피그마 Block Btn(269:3928): 아래에 붙은 56dp 버튼
        Box(
            Modifier
                .fillMaxWidth()
                .background(CollogColors.gray00)
                .padding(horizontal = Spacing.x5)
                .padding(top = Spacing.x2, bottom = Spacing.x2 + bottomInset),
        ) {
            SettingsActionButton(if (isSaving) "저장 중" else "저장", Modifier.fillMaxWidth(), enabled = canSave, onClick = ::save)
        }
    }
}

/** 피그마 Feild Header(269:3923): 14pt 이름과 뒤에 붙는 표시(필수 *, 선택). */
@Composable
private fun FieldLabel(text: String, mark: @Composable () -> Unit) {
    Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.x1), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = pretendard(Pretendard.SemiBold, 14f), color = CollogColors.gray800)
        mark()
    }
}

/** 피그마 Input(269:3923): 고른 질환을 쉼표로 보이고, 누르면 아래에 고를 수 있는 목록이 열린다(여러 개 선택). */
@Composable
private fun ConditionPicker(
    selection: HealthSelection,
    isOpen: Boolean,
    enabled: Boolean,
    onToggleOpen: () -> Unit,
    onToggle: (HealthCondition?) -> Unit,
) {
    val shape = RoundedCornerShape(Radius.listItem)
    Column(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray00, shape)
            .border(1.dp, CollogColors.gray400, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .pressable(enabled = enabled, onClick = onToggleOpen)
                .padding(horizontal = Spacing.x5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val summary = selection.summary
            Text(
                summary ?: "예 : 건망증, 비염",
                style = pretendard(Pretendard.Medium, 16f),
                color = if (summary == null) CollogColors.gray600 else CollogColors.gray900,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = if (isOpen) "목록 닫기" else "목록 열기",
                tint = CollogColors.gray800,
                modifier = Modifier.size(24.dp).rotate(if (isOpen) 180f else 0f),
            )
        }
        if (isOpen) {
            (HealthCondition.entries + listOf(null)).forEach { condition ->
                val isSelected = selection.isSelected(condition)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { selected = isSelected }
                        .pressable(enabled = enabled) { onToggle(condition) }
                        .padding(horizontal = Spacing.x5),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        condition?.title ?: "해당 없음",
                        style = pretendard(Pretendard.Medium, 16f),
                        color = CollogColors.gray900,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) CollogIcon("checkmark", size = 20.dp, color = CollogColors.greenNormal)
                }
            }
        }
    }
}

/** 피그마 질환 설명 상자(277:5156): 파란 바탕, 수집 정보와 활용. */
@Composable
private fun ConditionDescription(condition: HealthCondition) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.blue100, RoundedCornerShape(Radius.listItem))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x1),
    ) {
        Icon(
            painterResource(R.drawable.ic_condition_badge),
            contentDescription = null,
            tint = CollogColors.blue700,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(condition.title, style = pretendard(Pretendard.SemiBold, 14f), color = CollogColors.blue700)
            Text(conditionDescription(condition), style = CollogType.body02Regular, color = CollogColors.blue700)
        }
    }
}
