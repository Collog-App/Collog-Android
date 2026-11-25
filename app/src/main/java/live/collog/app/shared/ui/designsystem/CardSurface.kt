package live.collog.app.shared.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** iOS `cardSurface()`: 흰 배경, 둥근 모서리, 안쪽 여백, 가로 꽉 채움. 내 기록을 볼 때([ViewerAccent.Mine])는 주황 테두리가 붙는다. */
fun Modifier.cardSurface(padding: Dp = Spacing.x4, cornerRadius: Dp = Radius.card): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val accentBorder = LocalViewerAccent.current.cardBorder
    fillMaxWidth()
        .background(CollogColors.gray00, shape)
        .then(if (accentBorder != null) Modifier.border(1.5.dp, accentBorder, shape) else Modifier)
        .padding(padding)
}

enum class Axis { Horizontal, Vertical }

@Composable
fun DividerLine(modifier: Modifier = Modifier, axis: Axis = Axis.Horizontal) {
    val sized = when (axis) {
        Axis.Horizontal -> modifier.fillMaxWidth().height(1.dp)
        Axis.Vertical -> modifier.fillMaxHeight().width(1.dp)
    }
    Box(sized.background(CollogColors.gray100))
}
