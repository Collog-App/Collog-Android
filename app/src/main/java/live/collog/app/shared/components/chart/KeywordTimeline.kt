package live.collog.app.shared.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import java.util.UUID
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing

/** iOS `KeywordTone`. */
enum class KeywordTone {
    Neutral,
    Positive,
    Caution,
    Concern;

    val tickColor: Color
        get() = when (this) {
            Neutral -> CollogColors.gray500
            Positive -> CollogColors.greenNormal
            Caution -> CollogColors.orange600
            Concern -> CollogColors.red400
        }

    val chipForeground: Color get() = if (this == Concern) CollogColors.red400 else CollogColors.gray800

    val chipBackground: Color get() = if (this == Concern) CollogColors.red100 else CollogColors.gray100
}

/** iOS `KeywordMark`. [position]은 0~1 비율. */
data class KeywordMark(val position: Double, val tone: KeywordTone, val label: String?) {
    val id: UUID = UUID.randomUUID()
}

/** iOS `KeywordTimelineView`: 띠 위에 키워드 표시와 말풍선 이름표를 놓는다. */
@Composable
fun KeywordTimeline(marks: List<KeywordMark>, modifier: Modifier = Modifier) {
    val topLabels = marks.filter { it.label != null && it.tone != KeywordTone.Concern }
    val bottomLabels = marks.filter { it.label != null && it.tone == KeywordTone.Concern }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        if (topLabels.isNotEmpty()) ChipRow(topLabels, pointerBelow = true)

        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawRoundRect(CollogColors.gray100, size = size, cornerRadius = CornerRadius(2.dp.toPx()))
            val tickWidth = 3.dp.toPx()
            marks.forEach { mark ->
                val x = (size.width * mark.position.toFloat()).coerceAtLeast(0f).coerceAtMost(maxOf(size.width - tickWidth, 0f))
                drawRoundRect(
                    mark.tone.tickColor,
                    Offset(x, 0f),
                    Size(tickWidth, size.height),
                    CornerRadius(1.dp.toPx()),
                )
            }
        }

        if (bottomLabels.isNotEmpty()) ChipRow(bottomLabels, pointerBelow = false)
    }
}

@Composable
private fun ChipRow(items: List<KeywordMark>, pointerBelow: Boolean) {
    Box(Modifier.fillMaxWidth().height(26.dp)) {
        items.forEach { mark ->
            Column(
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(Constraints())
                    val width = constraints.maxWidth
                    // iOS는 글자 수로 이름표 폭을 어림해 양 끝에서 밀어 넣는다
                    val estimatedHalfWidth = ((mark.label?.codePointCount(0, mark.label.length) ?: 0) * 6 + 24).dp.toPx() / 2
                    val center = (width * mark.position.toFloat())
                        .coerceAtLeast(estimatedHalfWidth)
                        .coerceAtMost(maxOf(width - estimatedHalfWidth, estimatedHalfWidth))
                    layout(width, constraints.maxHeight) {
                        placeable.place(
                            (center - placeable.width / 2f).toInt(),
                            (constraints.maxHeight - placeable.height) / 2,
                        )
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pointerBelow) Chip(mark)
                Pointer(mark.tone.chipBackground, pointsDown = pointerBelow)
                if (!pointerBelow) Chip(mark)
            }
        }
    }
}

@Composable
private fun Chip(mark: KeywordMark) {
    Text(
        mark.label.orEmpty(),
        style = CollogType.caption01Semibold,
        color = mark.tone.chipForeground,
        modifier = Modifier
            .background(mark.tone.chipBackground, RoundedCornerShape(Radius.chip))
            .padding(horizontal = Spacing.x3, vertical = Spacing.x1),
    )
}

/** iOS `Triangle`(9x4). 위쪽 변이 넓고 아래로 뾰족하며, 아래 이름표는 180도 뒤집는다. */
@Composable
private fun Pointer(color: Color, pointsDown: Boolean) {
    Canvas(Modifier.size(9.dp, 4.dp).graphicsLayer { rotationZ = if (pointsDown) 0f else 180f }) {
        val path = Path().apply {
            moveTo(size.width / 2, size.height)
            lineTo(0f, 0f)
            lineTo(size.width, 0f)
            close()
        }
        drawPath(path, color)
    }
}
