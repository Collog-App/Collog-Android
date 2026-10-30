package live.collog.app.features.settings.views.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import live.collog.app.R
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.designsystem.singleLine
import live.collog.app.shared.ui.pressable

// 피그마 「최종본」 설정(275:5311~278:6731)에만 나오는 색
internal val SettingsSectionTitleColor = Color(0xFF353C49)
internal val SettingsMutedColor = Color(0xFF71788C)
private val ListLabelColor = Color(0xFF222222)
private val ListArrowColor = Color(0xFFB1B9BE)
private val ListDividerColor = Color(0xFFE9EBED)

/** 피그마 Top Navigator Bar: 흰 64dp, 아래 1dp 선, 왼쪽 뒤로 버튼, 가운데 제목. [onBack]이 null이면 뒤로 버튼이 없다. */
@Composable
fun SettingsDetailHeader(title: String, onBack: (() -> Unit)?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(CollogColors.gray00)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(CollogColors.gray200, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            .padding(horizontal = Spacing.x5),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .semantics { contentDescription = "뒤로" }
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_left),
                    contentDescription = null,
                    tint = CollogColors.gray900,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(title, style = singleLine(Pretendard.SemiBold, 18f), color = CollogColors.gray900)
    }
}

/** 피그마 MySettingListItem(275:5322): 52dp 흰 줄, 17pt 글자, 오른쪽 꺾쇠, 아래 옅은 선. */
@Composable
fun SettingsNavigationRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressable(onClick = onClick)
            .drawBehind {
                val y = size.height - 0.25.dp.toPx()
                drawLine(ListDividerColor, Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
            }
            .padding(start = Spacing.x5, end = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = singleLine(Pretendard.Regular, 17f), color = ListLabelColor, modifier = Modifier.weight(1f))
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ListArrowColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 피그마 설정 소제목(277:5831): 18pt, 아래 내용과 13dp(가족 공유·질환 프로필 화면은 20dp). */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    spacing: Dp = 13.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
        Text(title, style = singleLine(Pretendard.SemiBold, 18f), color = SettingsSectionTitleColor)
        content()
    }
}

/** 피그마 Call Out(277:5833): 회색 둥근 상자, 안쪽 14dp. */
@Composable
fun SettingsCallout(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(CollogColors.gray200, RoundedCornerShape(Radius.listItem))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** 피그마 Call Out 안의 14pt 글자. */
@Composable
fun SettingsCalloutText(text: String, modifier: Modifier = Modifier, color: Color = CollogColors.gray800) {
    Text(text, style = CollogType.body02Regular, color = color, modifier = modifier)
}

/** 피그마 Call Out 안의 글머리표 줄(277:5897): 글자 첫 줄 가운데에 4dp 점. */
@Composable
fun SettingsBullet(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(22.dp).height(20.dp).padding(start = 9.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.size(4.dp).background(CollogColors.gray800, CircleShape))
        }
        SettingsCalloutText(text, Modifier.weight(1f))
    }
}

/** 피그마 Call Out 안의 밑줄 글자(277:5911 「비밀번호 변경」, 277:5844 「동의됨」). */
@Composable
fun SettingsLink(text: String, modifier: Modifier = Modifier, color: Color = CollogColors.gray800, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = CollogType.body02Regular.copy(textDecoration = TextDecoration.Underline),
        color = color,
        modifier = if (onClick != null) modifier.pressable(onClick = onClick) else modifier,
    )
}

/** 피그마 계정 정보 기본 정보(277:5840): 이름과 값 두 줄. */
@Composable
fun SettingsValueRow(label: String, value: String) {
    Text("$label\n$value", style = CollogType.body02Regular, color = CollogColors.gray800, modifier = Modifier.fillMaxWidth())
}

/** 피그마 알림 설정 줄(277:5866): 흰 둥근 53dp, 16pt 회색 글자, 오른쪽 작은 스위치. */
@Composable
fun SettingsToggleRow(label: String, isOn: Boolean, onIsOnChange: (Boolean) -> Unit, caption: String? = null, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 53.dp)
            .background(CollogColors.gray00, RoundedCornerShape(Radius.listItem))
            .padding(horizontal = 15.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(label, style = singleLine(Pretendard.SemiBold, 16f), color = SettingsMutedColor)
            if (caption != null) {
                Text(caption, style = singleLine(Pretendard.Regular, 13f), color = CollogColors.gray700)
            }
        }
        SettingsSwitch(isOn, onIsOnChange, label = label, enabled = enabled)
    }
}

/** 피그마 스위치(277:5870): 40x21 초록 알약, 16dp 흰 원. 꺼지면 회색. */
@Composable
fun SettingsSwitch(isOn: Boolean, onIsOnChange: (Boolean) -> Unit, label: String, enabled: Boolean = true) {
    val knob by animateDpAsState(if (isOn) 21.dp else 3.dp, label = "knob")
    Box(
        Modifier
            .width(40.dp)
            .height(21.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(if (isOn) CollogColors.greenNormal else CollogColors.gray400, CircleShape)
            .semantics {
                role = Role.Switch
                contentDescription = label
                stateDescription = if (isOn) "켜짐" else "꺼짐"
            }
            .pressable(enabled = enabled) { onIsOnChange(!isOn) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = knob).size(16.dp).background(CollogColors.gray00, CircleShape))
    }
}

/** 피그마 icon_gray_badge(277:6115)와 이름 줄. */
@Composable
fun MemberTitle(title: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_member_badge), contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(3.dp))
        Text(title, style = singleLine(Pretendard.SemiBold, 16f), color = CollogColors.gray900)
        if (trailing != null) {
            Spacer(Modifier.width(14.dp))
            Text(trailing, style = singleLine(Pretendard.SemiBold, 16f), color = CollogColors.gray900)
        }
    }
}

/** 피그마 Action Btn(277:6192): 56dp 둥근 버튼. */
@Composable
fun SettingsActionButton(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = CollogColors.greenNormal,
    foreground: Color = CollogColors.gray00,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(background, RoundedCornerShape(Radius.btnLarge))
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = pretendard(Pretendard.SemiBold, 16f), color = foreground, maxLines = 1)
    }
}

/** iOS `SettingsFieldRow`: 레이블과 URL 입력칸. 지금 화면에서는 쓰지 않는다. */
@Composable
fun SettingsFieldRow(label: String, text: String, onTextChange: (String) -> Unit, placeholder: String = "") {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.x4, vertical = Spacing.x3),
        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
    ) {
        Text(label, style = CollogType.body02Medium, color = CollogColors.gray900)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = pretendardPlain(Pretendard.Regular, 14f),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
            ),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(CollogColors.gray100, RoundedCornerShape(Radius.btnXsmall))
                        .padding(horizontal = Spacing.x3),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) {
                        Text(placeholder, style = pretendardPlain(Pretendard.Regular, 14f, CollogColors.gray600))
                    }
                    field()
                }
            },
        )
    }
}

/** iOS의 기본 `Button("...")`: 시스템 파랑 글자, 꺼지면 회색. [destructive]면 빨강. */
@Composable
fun SystemTextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> CollogColors.gray600
        destructive -> CollogColors.red500
        else -> CollogColors.blue600
    }
    Text(
        text,
        style = pretendardPlain(Pretendard.Medium, 16f, color),
        modifier = modifier.pressable(enabled = enabled, onClick = onClick),
    )
}

/** iOS `.alert(title, isPresented:)`: 취소와 확인 두 버튼. */
@Composable
fun SettingsAlert(
    title: String,
    message: String,
    confirmText: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CollogColors.gray00,
        title = { Text(title, style = CollogType.body01Semibold, color = CollogColors.gray900) },
        text = { Text(message, style = CollogType.body03Medium, color = CollogColors.gray800) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", style = pretendardPlain(Pretendard.Medium, 16f, CollogColors.blue600))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) {
                Text(
                    confirmText,
                    style = pretendardPlain(
                        Pretendard.SemiBold,
                        16f,
                        if (destructive) CollogColors.red500 else CollogColors.blue600,
                    ),
                )
            }
        },
    )
}
