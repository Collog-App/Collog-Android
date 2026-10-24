package live.collog.app.shared.components.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import live.collog.app.shared.ui.designsystem.CollogColors

/** iOS `SparkBarView`: 막대가 아래에서 차례로 솟는다. 마지막 [highlightCount]개만 초록. */
@Composable
fun SparkBar(
    values: List<Double>,
    modifier: Modifier = Modifier,
    highlightCount: Int = 3,
    barWidth: Dp = 10.dp,
    spacing: Dp = 4.dp,
) {
    val maximum = maxOf(values.maxOrNull() ?: 1.0, 0.0001)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { index, value ->
            var revealed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(index * 40L)
                revealed = true
            }
            // SwiftUI spring(response: 0.5, dampingFraction: 0.8): 강성 (2π/0.5)^2
            val scale by animateFloatAsState(
                targetValue = if (revealed) maxOf((value / maximum).toFloat(), 0.08f) else 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 157.9f),
                label = "sparkBar",
            )
            val color = if (index >= values.size - highlightCount) CollogColors.greenNormal else CollogColors.gray300
            Box(
                Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
    }
}

/** iOS `SparkLineView`: 선이 왼쪽에서 오른쪽으로 0.7초 동안 그려진다. */
@Composable
fun SparkLine(values: List<Double>, modifier: Modifier = Modifier, lineWidth: Dp = 2.dp) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(700, easing = EaseOut)) }

    Canvas(modifier) {
        if (values.size <= 1) return@Canvas
        val minimum = values.min()
        val maximum = values.max()
        val span = maxOf(maximum - minimum, 0.0001)
        val step = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(index * step, size.height - ((value - minimum) / span).toFloat() * size.height)
        }

        val full = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val measure = PathMeasure().apply { setPath(full, false) }
        val trimmed = Path()
        measure.getSegment(0f, measure.length * progress.value, trimmed, true)
        drawPath(
            trimmed,
            CollogColors.greenNormal,
            style = Stroke(lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
