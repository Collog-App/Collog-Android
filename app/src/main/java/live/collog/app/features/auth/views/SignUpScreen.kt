package live.collog.app.features.auth.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.R
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.viewmodels.Gender
import live.collog.app.features.auth.viewmodels.NicknameStatus
import live.collog.app.features.auth.viewmodels.SignUpViewModel
import live.collog.app.features.auth.views.components.AuthBlockButton
import live.collog.app.features.auth.views.components.InfoCallout
import live.collog.app.features.settings.views.components.SettingsBullet
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.pressable

/**
 * 피그마 「최종본」 회원가입(269:3930, 닉네임 겹침 269:3888, 다 채움 269:3902).
 * 네 번째 칸은 피그마에 「생년월일」이 한 번 더 있으나 초대 코드 칸이다(선택, 숫자 6자리).
 * 닉네임은 입력이 0.4초 멈추면 서버에 겹치는지 묻는다.
 */
@Composable
fun SignUpScreen(onBack: () -> Unit, onCompleted: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val viewModel = remember {
        SignUpViewModel().apply { environment.session.user?.let(::prefill) }
    }
    var isGenderOpen by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.nickname) {
        if (viewModel.nicknameStatus != NicknameStatus.Checking) return@LaunchedEffect
        delay(400)
        viewModel.checkNickname(environment)
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(CollogColors.gray00).statusBarsPadding().imePadding()) {
        SettingsDetailHeader("회원가입", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5)
                .padding(top = Spacing.x5, bottom = Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            Field("닉네임", footer = nicknameFooter(viewModel.nicknameStatus)) {
                TextInput(
                    value = viewModel.nickname,
                    onValueChange = viewModel::updateNickname,
                    placeholder = "닉네임을 입력해주세요.",
                    isError = viewModel.nicknameStatus in setOf(NicknameStatus.Taken, NicknameStatus.Invalid),
                    enabled = !viewModel.isSubmitting,
                )
            }
            Field("성별") {
                GenderPicker(
                    selection = viewModel.gender,
                    isOpen = isGenderOpen,
                    enabled = !viewModel.isSubmitting,
                    onToggleOpen = {
                        focus.clearFocus()
                        isGenderOpen = !isGenderOpen
                    },
                    onSelect = {
                        viewModel.gender = it
                        isGenderOpen = false
                        Haptics.focus()
                    },
                )
            }
            Field("생년월일", footer = viewModel.birthDateError) {
                TextInput(
                    value = viewModel.birthDigits,
                    onValueChange = viewModel::updateBirthDigits,
                    placeholder = "생년월일을 입력해주세요.",
                    isError = viewModel.birthDateError != null,
                    enabled = !viewModel.isSubmitting,
                    keyboardType = KeyboardType.Number,
                    visualTransformation = BirthDateTransformation,
                )
            }
            Field("초대코드", required = false, footer = viewModel.inviteCodeError) {
                TextInput(
                    value = viewModel.inviteCode,
                    onValueChange = viewModel::updateInviteCode,
                    placeholder = "초대코드를 입력해주세요.",
                    isError = viewModel.inviteCodeError != null,
                    enabled = !viewModel.isSubmitting,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                )
            }
            if (viewModel.inviteError != null) {
                Text(
                    "코드를 지우고 시작하면 가입 후 설정에서 입력할 수 있어요.",
                    style = CollogType.caption01Medium,
                    color = CollogColors.gray700,
                )
            }
            InfoCallout {
                SettingsBullet("가족에게 받은 초대코드를 입력하면 그 가족과 바로 연결돼요.")
                SettingsBullet("초대코드가 없으면 비워 두세요. 가입 후 설정에서도 입력할 수 있어요.")
            }
            viewModel.errorMessage?.let { Text(it, style = CollogType.body03Medium, color = CollogColors.red500) }
        }

        AuthBlockButton(if (viewModel.isSubmitting) "저장 중" else "콜록 시작하기", enabled = viewModel.canSubmit) {
            focus.clearFocus()
            scope.launch {
                if (viewModel.submit(environment)) onCompleted()
            }
        }
    }
}

private fun nicknameFooter(status: NicknameStatus): String? = when (status) {
    NicknameStatus.Taken -> "이미 존재하는 닉네임이에요."
    NicknameStatus.Invalid -> "닉네임은 공백 없이 한글·영문·숫자 2~10자로 입력해주세요."
    else -> null
}

/** 피그마 Feild(269:3936): 이름(필수면 빨간 표시, 아니면 「선택」), 입력칸, 오류 줄을 4dp 간격으로. */
@Composable
private fun Field(label: String, required: Boolean = true, footer: String? = null, input: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        Row(Modifier.height(20.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.x1), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = pretendard(Pretendard.SemiBold, 14f), color = CollogColors.gray800)
            if (required) {
                Image(painterResource(R.drawable.ic_required_mark), contentDescription = "필수", modifier = Modifier.size(8.dp))
            } else {
                Text("선택", style = pretendard(Pretendard.Regular, 12f), color = CollogColors.gray700)
            }
        }
        input()
        if (footer != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x1), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_input_error), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(footer, style = pretendard(Pretendard.Medium, 12f), color = CollogColors.red600)
            }
        }
    }
}

/** 피그마 Input(269:3936): 56dp 흰 상자, 테두리 gray400, 모서리 10. 오류면 붉은 바탕·테두리. 글자가 있으면 지우기 단추. */
@Composable
private fun InputBox(isError: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Radius.listItem)
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (isError) CollogColors.red100 else CollogColors.gray00, shape)
            .border(1.dp, if (isError) CollogColors.red700 else CollogColors.gray400, shape)
            .padding(horizontal = Spacing.x5),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = pretendard(Pretendard.Medium, 16f, CollogColors.gray900),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType, imeAction = imeAction),
        visualTransformation = visualTransformation,
        decorationBox = { field ->
            InputBox(isError) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = pretendard(Pretendard.Medium, 16f), color = CollogColors.gray600, maxLines = 1)
                        }
                        field()
                    }
                    if (value.isNotEmpty() && enabled) ClearButton { onValueChange("") }
                }
            }
        },
    )
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    Image(
        painterResource(R.drawable.ic_input_clear),
        contentDescription = null,
        modifier = Modifier.size(24.dp).semantics { contentDescription = "지우기" }.pressable(onClick = onClick),
    )
}

/** 성별 칸: 누르면 아래에 남성·여성 목록이 열린다(설정 질환 고르기와 같은 모양). */
@Composable
private fun GenderPicker(
    selection: Gender?,
    isOpen: Boolean,
    enabled: Boolean,
    onToggleOpen: () -> Unit,
    onSelect: (Gender?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        InputBox(isError = false, modifier = Modifier.pressable(enabled = enabled, onClick = onToggleOpen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selection?.title ?: "성별을 체크해주세요.",
                    style = pretendard(Pretendard.Medium, 16f),
                    color = if (selection == null) CollogColors.gray600 else CollogColors.gray900,
                    modifier = Modifier.weight(1f),
                )
                if (selection != null && enabled) ClearButton { onSelect(null) }
            }
        }
        if (isOpen) {
            val shape = RoundedCornerShape(Radius.listItem)
            Column(Modifier.fillMaxWidth().background(CollogColors.gray00, shape).border(1.dp, CollogColors.gray400, shape)) {
                Gender.entries.forEach { gender ->
                    val isSelected = gender == selection
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .semantics { selected = isSelected }
                            .pressable(enabled = enabled) { onSelect(gender) }
                            .padding(horizontal = Spacing.x5),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(gender.title, style = pretendard(Pretendard.Medium, 16f), color = CollogColors.gray900, modifier = Modifier.weight(1f))
                        if (isSelected) CollogIcon("checkmark", size = 20.dp, color = CollogColors.greenNormal)
                    }
                }
            }
        }
    }
}

/** 숫자 8자리를 YYYY-MM-DD 로 보인다. */
private object BirthDateTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val shown = buildString {
            digits.forEachIndexed { index, char ->
                if (index == 4 || index == 6) append('-')
                append(char)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset + (if (offset > 4) 1 else 0) + (if (offset > 6) 1 else 0)

            override fun transformedToOriginal(offset: Int): Int =
                (offset - (if (offset > 4) 1 else 0) - (if (offset > 7) 1 else 0)).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown), mapping)
    }
}
