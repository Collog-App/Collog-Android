package live.collog.app.shared.components.spec

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.pressable

/** 새 피그마 04 「토글(켜짐)」: 트랙 63x29(둥글기 12, `#63cf8a` + 1dp `#4fbf79`), 흰 노브 24. 꺼짐은 회색 트랙. */
@Composable
fun SpecToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val knobX by animateDpAsState(if (checked) 36.dp else 3.dp, label = "specToggleKnob")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(width = 63.dp, height = 29.dp)
            .background(if (checked) SpecColors.chartGreen else SpecColors.rail, shape)
            .border(1.dp, if (checked) Color(0xFF4FBF79) else SpecColors.line, shape)
            .semantics {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
            }
            .pressable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = knobX - 1.dp).size(24.dp).background(Color.White, CircleShape))
    }
}
