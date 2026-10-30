package live.collog.app.features.call.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.pressable
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing

/** iOS `IncomingCallActionsView`. 수신 화면의 거절·받기 버튼. */
@Composable
fun IncomingCallActionsView(
    canAnswer: Boolean,
    isAnswering: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x8, Alignment.CenterHorizontally),
    ) {
        ActionButton("거절", "phone.down.fill", CollogColors.red500, !isAnswering, onDecline)
        ActionButton(
            title = if (isAnswering) "받는 중" else "받기",
            symbol = "phone.fill",
            color = CollogColors.greenNormal,
            enabled = canAnswer,
            onClick = onAnswer,
            modifier = Modifier.alpha(if (canAnswer || isAnswering) 1f else 0.45f),
        )
    }
}

@Composable
private fun ActionButton(
    title: String,
    symbol: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .pressable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = if (title == "거절") "수신 전화 거절" else "수신 전화 받기" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
    ) {
        Box(Modifier.size(72.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            CollogIcon(symbol, size = 30.dp, color = CollogColors.gray00)
        }
        Text(title, style = CollogType.body03Medium, color = CollogColors.gray700)
    }
}
