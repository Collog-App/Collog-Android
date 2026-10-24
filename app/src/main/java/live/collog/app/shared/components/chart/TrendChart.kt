package live.collog.app.shared.components.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale
import live.collog.app.shared.components.stat.ValueWithUnit
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

// iOS Swift Charts 설정값: X축 양 끝 여백 8, 위아래 여백 Spacing.x2
private val PlotPaddingX = 8.dp
private val PlotPaddingY = Spacing.x2
private val AxisLabelHeight = 22.dp
private val READOUT_FORMAT = DecimalFormat("0", DecimalFormatSymbols(Locale.ROOT))

/**
 * iOS `TrendChartView`: 주간 추세선. 짚으면 가장 가까운 점이 선택되고, 처음에는 왼쪽에서 오른쪽으로 펼쳐진다.
 * Swift Charts가 없어 Canvas로 직접 그린다(곡선은 Catmull-Rom).
 */
@Composable
fun TrendChart(
    series: TrendSeries,
    modifier: Modifier = Modifier,
    showsReadout: Boolean = true,
    chartHeight: Dp = 132.dp,
) {
    var selectedDate by remember { mutableStateOf<Instant?>(null) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reveal.value == 0f) reveal.animateTo(1f, tween(900, easing = EaseOut))
    }

    val selected = selectedDate
    val focusedPoint = if (selected == null) series.latest else series.nearest(selected)
    val yDomain = remember(series) { yDomain(series) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        if (showsReadout) Readout(series, focusedPoint)

        Box(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .drawWithContent {
                    clipRect(right = size.width * reveal.value) {
                        this@drawWithContent.drawContent()
                    }
                }
                .pointerInput(series) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        selectedDate = dateAt(series, down.position.x, size.width.toFloat(), PlotPaddingX.toPx())
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || !change.pressed) break
                            selectedDate = dateAt(series, change.position.x, size.width.toFloat(), PlotPaddingX.toPx())
                        }
                        selectedDate = null
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawChart(series, yDomain, focusedPoint) }
            AxisLabels(series)
        }
    }
}

@Composable
private fun Readout(series: TrendSeries, focusedPoint: TrendPoint?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text(
            focusedPoint?.label ?: series.metricName,
            style = CollogType.caption01Medium,
            color = CollogColors.gray800,
            modifier = Modifier.alignBy(FirstBaseline),
        )
        Spacer(Modifier.weight(1f))
        if (focusedPoint != null) {
            ValueWithUnit(
                value = READOUT_FORMAT.format(focusedPoint.value),
                unit = series.unit,
                valueStyle = pretendard(Pretendard.SemiBold, 20f),
                valueColor = CollogColors.gray900,
                unitStyle = CollogType.caption01Medium,
                unitColor = CollogColors.gray800,
                modifier = Modifier.alignBy(FirstBaseline),
            )

            if (series.hasPersonalBaseline) {
                val within = series.isWithinNormalRange(focusedPoint)
                Text(
                    if (within) "평소 범위" else "평소와 다름",
                    style = CollogType.caption01Medium,
                    color = if (within) CollogColors.greenDark else CollogColors.orange600,
                    modifier = Modifier.alignBy(FirstBaseline),
                )
            } else {
                Text(
                    "비교할 기록 수집 중",
                    style = CollogType.caption01Medium,
                    color = CollogColors.gray700,
                    modifier = Modifier.alignBy(FirstBaseline),
                )
            }
        }
    }
}

/** X축에는 처음과 마지막 날짜의 이름표만 둔다(처음은 왼쪽 위 기준, 마지막은 오른쪽 위 기준). */
@Composable
private fun AxisLabels(series: TrendSeries) {
    val first = series.points.firstOrNull() ?: return
    val last = series.points.last()
    val labelStyle = CollogType.caption01Medium
    Box(Modifier.fillMaxSize()) {
        if (first.date == last.date) {
            Text(
                series.nearest(first.date)?.label.orEmpty(),
                style = labelStyle,
                color = CollogColors.gray700,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            Text(
                series.nearest(first.date)?.label.orEmpty(),
                style = labelStyle,
                color = CollogColors.gray700,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = PlotPaddingX),
            )
            Text(
                series.nearest(last.date)?.label.orEmpty(),
                style = labelStyle,
                color = CollogColors.gray700,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = PlotPaddingX),
            )
        }
    }
}

private fun yDomain(series: TrendSeries): ClosedFloatingPointRange<Double> {
    val values = series.points.map { it.value } + listOf(series.normalRange.start, series.normalRange.endInclusive)
    val minimum = values.min()
    val maximum = values.max()
    val padding = maxOf((maximum - minimum) * 0.25, 1.0)
    return (minimum - padding)..(maximum + padding)
}

/** 짚은 가로 위치를 날짜로 되돌린다. 날짜가 하나뿐이면 그 날짜. */
private fun dateAt(series: TrendSeries, x: Float, width: Float, paddingX: Float): Instant? {
    if (series.points.isEmpty()) return null
    val times = series.points.map { it.date.toEpochMilli() }
    val start = times.min()
    val end = times.max()
    if (start == end) return Instant.ofEpochMilli(start)
    val ratio = ((x - paddingX) / (width - 2 * paddingX)).toDouble()
    return Instant.ofEpochMilli(start + (ratio * (end - start)).toLong())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChart(
    series: TrendSeries,
    yDomain: ClosedFloatingPointRange<Double>,
    focusedPoint: TrendPoint?,
) {
    val points = series.points
    if (points.isEmpty()) return

    val paddingX = PlotPaddingX.toPx()
    val plotTop = PlotPaddingY.toPx()
    val plotBottom = size.height - AxisLabelHeight.toPx() - PlotPaddingY.toPx()
    val times = points.map { it.date.toEpochMilli() }
    val start = times.min()
    val end = times.max()

    fun x(point: TrendPoint): Float =
        if (start == end) size.width / 2
        else paddingX + (point.date.toEpochMilli() - start).toFloat() / (end - start) * (size.width - 2 * paddingX)

    fun y(value: Double): Float =
        plotBottom - ((value - yDomain.start) / (yDomain.endInclusive - yDomain.start)).toFloat() * (plotBottom - plotTop)

    if (series.hasPersonalBaseline) {
        val medianY = y(series.median)
        drawLine(CollogColors.gray300, Offset(0f, medianY), Offset(size.width, medianY), strokeWidth = 1.dp.toPx())
    }

    val offsets = points.map { Offset(x(it), y(it.value)) }
    val curve = smoothPath(offsets)

    val area = Path().apply {
        addPath(curve)
        lineTo(offsets.last().x, plotBottom)
        lineTo(offsets.first().x, plotBottom)
        close()
    }
    drawPath(
        area,
        Brush.verticalGradient(
            listOf(CollogColors.greenNormal.copy(alpha = 0.18f), CollogColors.greenNormal.copy(alpha = 0.01f)),
            startY = offsets.minOf { it.y },
            endY = plotBottom,
        ),
    )
    drawPath(
        curve,
        CollogColors.greenNormal,
        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    if (focusedPoint != null) {
        val side = 9.dp.toPx()
        drawRoundRect(
            CollogColors.greenDark,
            Offset(x(focusedPoint) - side / 2, y(focusedPoint.value) - side / 2),
            Size(side, side),
            CornerRadius(2.dp.toPx()),
        )
    }
}

/** Catmull-Rom 곡선을 3차 베지어로 바꿔 점들을 잇는다. */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points[maxOf(i - 1, 0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[minOf(i + 2, points.size - 1)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
            p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
            p2.x, p2.y,
        )
    }
    return path
}
