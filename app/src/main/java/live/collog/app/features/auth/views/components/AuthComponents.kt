package live.collog.app.features.auth.views.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import live.collog.app.R
import live.collog.app.features.settings.views.components.SettingsCallout
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.pressable

/** 피그마 Block Btn(269:3942): 아래에 붙은 흰 72dp 막대와 56dp 버튼. 꺼지면 옅은 회색 바탕에 회색 글자. */
@Composable
internal fun AuthBlockButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray00)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.x5, vertical = Spacing.x2),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(if (enabled) CollogColors.greenNormal else CollogColors.gray200, RoundedCornerShape(Radius.btnLarge))
                .pressable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = pretendard(Pretendard.SemiBold, 16f),
                color = if (enabled) CollogColors.gray00 else CollogColors.gray500,
                maxLines = 1,
            )
        }
    }
}

/** 피그마 Check Box(269:3950): 24dp, 모서리 4. 켜지면 검은 바탕에 흰 체크. */
@Composable
internal fun AuthCheckBox(checked: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(24.dp)
            .background(if (checked) CollogColors.gray1000 else CollogColors.gray00, shape)
            .then(if (checked) Modifier else Modifier.border(1.dp, CollogColors.gray500, shape)),
    ) {
        if (checked) {
            Icon(painterResource(R.drawable.ic_checkbox_check), contentDescription = null, tint = CollogColors.gray00)
        }
    }
}

/** 피그마 Call Out(269:3940): 회색 상자 왼쪽에 안내 말풍선 아이콘. */
@Composable
internal fun InfoCallout(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SettingsCallout(modifier) { InfoIconRow(content) }
}

/** Call Out 안의 한 줄: 24dp 안내 아이콘, 4dp 띄우고 내용. */
@Composable
internal fun InfoIconRow(content: @Composable ColumnScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        Image(painterResource(R.drawable.ic_info_bubble), contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), content = content)
    }
}
