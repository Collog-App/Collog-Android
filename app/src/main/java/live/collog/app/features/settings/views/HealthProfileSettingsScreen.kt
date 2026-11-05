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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import live.collog.app.shared.components.spec.SpecDetailHeader
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
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
import live.collog.app.features.settings.models.canSaveHealthProfile
import live.collog.app.features.settings.models.ownProfileTarget
import live.collog.app.features.settings.views.components.SettingsActionButton
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SystemTextButton
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

/** 새 피그마 16 의 질환 5개: 서버 `Condition` 값(`schemas.py`)과 화면 글자. 이 순서로 보인다. */
internal val SpecHealthConditions: List<Pair<String, String>> = listOf(
    "DIABETES" to "당뇨",
    "HYPERTENSION" to "고혈압",
    "DYSLIPIDEMIA" to "이상지질혈증",
    "ASTHMA" to "천식",
    "OBESITY" to "비만",
)

/**
 * 새 피그마 16 「설정 · 나의 건강 프로필」: 설명 카드 + 질환 5개(여러 개 선택) + 저장.
 * 자유 서술 칸은 화면에 없지만, 서버에 이미 있는 값(`concerns`)은 읽어 둔 그대로 다시 보낸다.
 * 이 화면에 없는 예전 값(건망증·호흡기 질환·비염)도 지우지 않고 그대로 돌려보낸다.
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

    // 서버가 가진 질환 값 전부(이 화면에 없는 값 포함)와 읽어 둔 걱정되는 부분
    var stored by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var concerns by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        isLoading = true
        errorText = null
        try {
            if (isGuest) {
                val (conditions, note) = GuestSamples.profile(target?.id)
                stored = (guestPrefs.getString(GUEST_CONDITIONS_KEY + guestSuffix, conditions) ?: "")
                    .split(",").filter { it.isNotBlank() }
                concerns = guestPrefs.getString(GUEST_CONCERNS_KEY + guestSuffix, note) ?: ""
            } else {
                val id = subject?.id
                if (id == null) {
                    errorText = "로그인이 필요해요."
                    return
                }
                val profile = environment.api.profile(id)
                stored = profile.conditions
                concerns = profile.concerns.orEmpty()
            }
            picked = stored.filter { code -> SpecHealthConditions.any { it.first == code } }.toSet()
            profileLoaded = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorText = error.message
        } finally {
            isLoading = false
        }
    }

    fun save() {
        if (!profileLoaded || isSaving || isLoading) return
        isSaving = true
        errorText = null
        // 이 화면이 모르는 값은 그대로 두고, 아는 5개만 고른 대로 바꾼다
        val values = (stored.filter { code -> SpecHealthConditions.none { it.first == code } } + picked).distinct().sorted()
        val note = concerns.trim()
        scope.launch {
            try {
                if (isGuest) {
                    guestPrefs.edit()
                        .putString(GUEST_CONDITIONS_KEY + guestSuffix, values.joinToString(","))
                        .putString(GUEST_CONCERNS_KEY + guestSuffix, note)
                        .apply()
                } else {
                    val id = subject?.id ?: throw ApiError.Unauthenticated
                    environment.api.updateProfile(id, values, note)
                }
                Haptics.commit()
                onBack()
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

    Column(
        Modifier
            .fillMaxSize()
            .background(SpecColors.page)
            .then(if (inSheet) Modifier else Modifier.statusBarsPadding()),
    ) {
        SpecDetailHeader(if (target == null) "나의 건강 프로필" else "가족 건강 프로필", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 19.dp)
                .padding(top = 9.dp, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier.specCard(padding = 0.dp, radius = 16.dp).padding(horizontal = 17.dp, vertical = 17.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (target == null) "건강 질문을 맞춤 준비해드려요" else "${subject?.title.orEmpty()} 건강 프로필",
                    style = specText(Pretendard.Bold, 18f),
                )
                Text(
                    "선택한 항목은 오늘의 질문과 통화 요약을 준비할 때 참고해요.",
                    style = specText(Pretendard.Regular, 12f, SpecColors.muted),
                )
            }

            Spacer(Modifier.height(2.dp))
            SpecHealthConditions.forEach { (code, title) ->
                ConditionOption(
                    title = title,
                    isSelected = code in picked,
                    enabled = profileLoaded && !isSaving,
                ) {
                    picked = if (code in picked) picked - code else picked + code
                    Haptics.focus()
                }
            }

            Spacer(Modifier.height(2.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .alpha(if (profileLoaded && !isSaving) 1f else 0.4f)
                    .background(SpecColors.toggleOn, RoundedCornerShape(14.dp))
                    .pressable(enabled = profileLoaded && !isSaving, onClick = ::save),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CollogProgress()
                } else {
                    Text(if (isSaving) "저장 중" else "저장", style = specText(Pretendard.Bold, 18f, Color.White))
                }
            }

            errorText?.let {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Text(it, style = CollogType.body03Medium, color = CollogColors.red500)
                    if (!profileLoaded) SystemTextButton("다시 시도", enabled = !isLoading) { scope.launch { load() } }
                }
            }
        }
    }
}

/** 새 피그마 「선택지」: 흰 카드(둥글기 14), 고르면 연초록 바탕 `#f2faf5` + 초록 테두리 `#9addb4` + 오른쪽 초록 체크. 글자 12.5/500. */
@Composable
private fun ConditionOption(title: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .specCard(
                padding = 0.dp,
                background = if (isSelected) SpecColors.greenSelected else Color.White,
                shadowed = !isSelected,
                radius = 14.dp,
                borderColor = if (isSelected) SpecColors.greenBorder else SpecColors.cardBorder,
            )
            .height(53.dp)
            .semantics { selected = isSelected }
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = specText(Pretendard.Medium, 12.5f))
        if (isSelected) CollogIcon("checkmark", size = 17.dp, color = SpecColors.green)
    }
}

private fun conditionDescription(condition: HealthCondition): String =
    "수집 정보: ${condition.collected}\n활용: ${condition.usage}"

/**
 * 피그마 질환 선택(269:3917): 질환 고르기 + 추가로 걱정되는 부분. 고른 질환의 설명 상자(277:5137)는 그 아래에 보인다.
 * 가입의 나의 질환 단계도 이 편집기를 [title]·[saveLabel]만 바꿔 쓴다.
 */
@Composable
internal fun HealthProfileEditor(
    initialSelection: HealthSelection,
    initialConcerns: String,
    onCancel: () -> Unit,
    onSave: suspend (HealthSelection, String) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    title: String = "질환 프로필 편집",
    saveLabel: String = "저장",
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
        SettingsDetailHeader(title, onBack = onCancel)

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
            SettingsActionButton(if (isSaving) "저장 중" else saveLabel, Modifier.fillMaxWidth(), enabled = canSave, onClick = ::save)
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
