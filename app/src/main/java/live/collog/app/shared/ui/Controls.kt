package live.collog.app.shared.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType

/**
 * iOS `.buttonStyle(.plain)` 버튼: 물결 효과 없이 누르는 동안 흐려진다.
 * 화면 버튼은 기본적으로 이것을 쓴다(Material ripple을 쓰지 않는다).
 */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    graphicsLayer { alpha = if (pressed) 0.5f else 1f }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** iOS `Picker(...).pickerStyle(.segmented)`와 비슷한 모양. */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selection: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val index = options.indexOf(selection).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(CollogColors.gray200, RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(segmentWidth * index, label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(1.dp, RoundedCornerShape(7.dp))
                .background(CollogColors.gray00, RoundedCornerShape(7.dp)),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEach { option ->
                Box(
                    Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = CollogType.body03Medium,
                        color = if (enabled) CollogColors.gray900 else CollogColors.gray600,
                    )
                }
            }
        }
    }
}

/** iOS `Toggle`. 켜짐 색은 greenNormal. */
@Composable
fun CollogToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = CollogColors.greenNormal,
            checkedThumbColor = CollogColors.gray00,
            uncheckedTrackColor = CollogColors.gray300,
            uncheckedThumbColor = CollogColors.gray00,
            uncheckedBorderColor = CollogColors.gray300,
        ),
    )
}

/** iOS `ProgressView()`. */
@Composable
fun CollogProgress(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier.width(24.dp).height(24.dp), color = CollogColors.gray600, strokeWidth = 2.5.dp)
}

/** iOS `ShareLink(item: text)`: 시스템 공유 창을 연다. */
fun shareText(context: Context, text: String, title: String? = null) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
