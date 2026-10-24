package live.collog.app.features.settings.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.CollogToggle
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.IconSize
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable

/** iOS `SettingsSection`: 작은 제목과 흰 둥근 상자. */
@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text(
            title,
            style = CollogType.caption01Medium,
            color = CollogColors.gray800,
            modifier = Modifier.padding(horizontal = Spacing.x1),
        )
        Column(Modifier.fillMaxWidth().background(CollogColors.gray00, RoundedCornerShape(Radius.card))) {
            content()
        }
    }
}

/** iOS `SettingsNavigationRow`. */
@Composable
fun SettingsNavigationRow(label: String, value: String? = null, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.x4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CollogType.body02Medium, color = CollogColors.gray900)
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(value, style = CollogType.caption01Medium, color = CollogColors.gray700)
        }
        CollogIcon("chevron.right", size = IconSize.small, color = CollogColors.gray600)
    }
}

/** iOS `SettingsToggleRow`. */
@Composable
fun SettingsToggleRow(label: String, isOn: Boolean, onIsOnChange: (Boolean) -> Unit, caption: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = CollogType.body02Medium, color = CollogColors.gray900)
            if (caption != null) {
                Text(caption, style = CollogType.caption01Medium, color = CollogColors.gray700)
            }
        }
        CollogToggle(checked = isOn, onCheckedChange = onIsOnChange)
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

/** iOS `SettingsValueRow`(AccountSettingsView.swift): 왼쪽 이름, 오른쪽 값. */
@Composable
fun SettingsValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Spacing.x4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CollogType.body02Medium, color = CollogColors.gray800)
        Spacer(Modifier.weight(1f))
        Text(value, style = CollogType.body02Medium, color = CollogColors.gray900, textAlign = TextAlign.End)
    }
}

/** iOS `HomeDetailHeader`(홈 포팅 전이라 설정 안에 둔 같은 모양): 뒤로 버튼과 가운데 제목. */
@Composable
fun SettingsDetailHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(CollogColors.gray50)
            .padding(horizontal = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).semantics { contentDescription = "뒤로" }.pressable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            CollogIcon("chevron.left", size = 20.dp, color = CollogColors.gray900)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = CollogType.body01Semibold, color = CollogColors.gray900)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(40.dp))
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
