package live.collog.app.features.call.views

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.delay
import live.collog.app.features.call.viewmodels.CallLauncherModel
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing

private const val INNER_RADIUS = 34f
private const val OUTER_RADIUS = 178f
private const val FAN_SIZE = 356f

/** iOS `CallLauncherOverlay`. 전화 버튼 위로 펼쳐지는 가족 선택 부채꼴과 오늘의 질문 칩. */
@Composable
fun CallLauncherOverlay(
    model: CallLauncherModel,
    anchorInset: Dp,
    notice: String?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, responseSpring(0.32f, 0.82f)) }

    Box(Modifier.fillMaxSize().alpha(appear.value.coerceIn(0f, 1f))) {
        // 흐림 효과 대신 거의 불투명한 흰 막
        Box(
            Modifier
                .fillMaxSize()
                .background(CollogColors.gray00.copy(alpha = 0.92f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )

        AmbientGradient()

        if (notice != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(top = Spacing.x4),
                contentAlignment = Alignment.TopCenter,
            ) { Hint(notice) }
        }

        // 전화 버튼 중심을 기준점으로 삼는 크기 0 상자
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = anchorInset + navigationInset)
                .size(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.Center, unbounded = true)
                    .size(600.dp)
                    .background(
                        Brush.radialGradient(
                            0f to CollogColors.greenNormal.copy(alpha = 0.14f),
                            1f to CollogColors.greenNormal.copy(alpha = 0f),
                        ),
                        CircleShape,
                    ),
            )
            QuestionStack(model)
            TargetFan(model, appear.value, onSelect)
        }
    }
}

@Composable
private fun AmbientGradient() {
    val transition = rememberInfiniteTransition(label = "ambient")
    val phase by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ambientPhase",
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationZ = -5f + 12f * phase
                val s = 0.96f + 0.10f * phase
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        Glow(360.dp, CollogColors.greenNormal.copy(alpha = 0.14f), (-120).dp, 160.dp)
        Glow(300.dp, CollogColors.greenLightActive.copy(alpha = 0.24f), 140.dp, (-100).dp)
    }
}

/** iOS의 블러 처리된 원을 방사형 그라데이션으로 대신한다. */
@Composable
private fun Glow(size: Dp, color: Color, x: Dp, y: Dp) {
    Box(
        Modifier
            .offset(x, y)
            .wrapContentSize(Alignment.Center, unbounded = true)
            .size(size)
            .background(Brush.radialGradient(0f to color, 1f to color.copy(alpha = 0f)), CircleShape),
    )
}

@Composable
private fun QuestionStack(model: CallLauncherModel) {
    Column(
        Modifier
            .wrapContentSize(Alignment.Center, unbounded = true)
            .width(300.dp)
            .offset(y = -(model.arcRadius + 245).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (model.targets.isEmpty()) {
            Hint("설정에서 통화할 가족을 등록해주세요")
        } else if (model.questions.isEmpty()) {
            Hint("오늘의 질문이 아직 없어요")
        } else {
            Text(
                "이 질문으로 시작해보세요",
                style = CollogType.caption01Medium,
                color = CollogColors.gray700,
                modifier = Modifier.padding(start = Spacing.x2),
            )
            Column(
                Modifier.padding(top = Spacing.x4),
                verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                model.questions.take(3).forEachIndexed { index, question ->
                    QuestionChip(question, index)
                }
            }
        }
    }
}

@Composable
private fun QuestionChip(text: String, index: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        shown = true
    }
    val progress by animateFloatAsState(if (shown) 1f else 0f, responseSpring(0.34f, 0.78f), label = "chip")

    val transition = rememberInfiniteTransition(label = "chipFloat")
    val float by transition.animateFloat(
        -3f, 3f,
        infiniteRepeatable(tween(2700 + index * 320, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "chipFloatValue",
    )

    Row(
        Modifier
            .graphicsLayer {
                alpha = progress.coerceIn(0f, 1f)
                translationY = (1f - progress) * 40.dp.toPx() + float.dp.toPx()
            }
            .shadow(
                6.dp, CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            )
            .background(CollogColors.gray00, CircleShape)
            .padding(horizontal = Spacing.x3, vertical = Spacing.x2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(index + 1), style = CollogType.caption01Semibold, color = CollogColors.green700)
        Text(
            text,
            style = CollogType.body03Medium,
            color = CollogColors.gray900,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = CollogType.body03Medium,
        color = CollogColors.gray800,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .background(CollogColors.gray00, CircleShape)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x3),
    )
}

@Composable
private fun TargetFan(model: CallLauncherModel, appear: Float, onSelect: (Int) -> Unit) {
    val fanAlpha by animateFloatAsState(if (model.mode == CallLauncherModel.Mode.Dragging) 1f else 0.78f, label = "fanAlpha")
    val density = LocalDensity.current
    val isSticky = model.mode == CallLauncherModel.Mode.Sticky

    Box(
        Modifier
            .wrapContentSize(Alignment.Center, unbounded = true)
            .size(FAN_SIZE.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 부채꼴 영역: 모양이 다각형이라 눌린 위치를 각도·반지름으로 직접 판정한다
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 0.82f + 0.18f * appear
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    alpha = fanAlpha * appear.coerceIn(0f, 1f)
                }
                .pointerInput(isSticky, model.targets.size) {
                    if (!isSticky) return@pointerInput
                    detectTapGestures { position ->
                        val dx = position.x / density.density - FAN_SIZE / 2
                        val dy = position.y / density.density - FAN_SIZE / 2
                        val radius = hypot(dx, dy)
                        if (radius < INNER_RADIUS || radius > OUTER_RADIUS) return@detectTapGestures
                        val angle = Math.toDegrees(atan2(dy, dx).toDouble())
                        val index = model.targets.indices.firstOrNull { angle in model.angleRange(it) }
                        if (index != null) onSelect(index)
                    }
                },
        ) {
            model.targets.indices.forEach { index ->
                val range = model.angleRange(index)
                FanSectorView(range.start, range.endInclusive, model.focusedIndex == index)
            }
        }

        model.targets.forEachIndexed { index, contact ->
            TargetBubble(model, contact, index, appear, isSticky, onSelect)
        }
    }
}

@Composable
private fun FanSectorView(start: Double, end: Double, isFocused: Boolean) {
    val scale by animateFloatAsState(if (isFocused) 1.025f else 1f, responseSpring(0.2f, 0.88f), label = "sectorScale")
    Canvas(Modifier.fillMaxSize()) {
        val path = sectorPath(center, INNER_RADIUS * density, OUTER_RADIUS * density, start, end)
        drawScale(scale, pivot = Offset(size.width / 2, size.height)) {
            drawPath(
                path,
                if (isFocused) CollogColors.greenLightActive.copy(alpha = 0.28f) else CollogColors.gray00.copy(alpha = 0.16f),
            )
            drawPath(
                path,
                CollogColors.gray00.copy(alpha = if (isFocused) 0.42f else 0.28f),
                style = Stroke(0.75.dp.toPx()),
            )
        }
    }
}

private fun sectorPath(center: Offset, inner: Float, outer: Float, start: Double, end: Double): Path {
    val steps = 32
    fun point(radius: Float, angle: Double): Offset {
        val r = Math.toRadians(angle)
        return Offset(center.x + (cos(r) * radius).toFloat(), center.y + (sin(r) * radius).toFloat())
    }
    return Path().apply {
        for (step in 0..steps) {
            val p = point(outer, start + (end - start) * step / steps)
            if (step == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        for (step in steps downTo 0) {
            val p = point(inner, start + (end - start) * step / steps)
            lineTo(p.x, p.y)
        }
        close()
    }
}

@Composable
private fun TargetBubble(
    model: CallLauncherModel,
    contact: FamilyContact,
    index: Int,
    appear: Float,
    isSticky: Boolean,
    onSelect: (Int) -> Unit,
) {
    val isFocused = model.focusedIndex == index
    val focusScale by animateFloatAsState(if (isFocused) 1.06f else 1f, responseSpring(0.2f, 0.88f), label = "bubbleScale")
    val offset = model.offset(index)

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        shown = true
    }
    val progress by animateFloatAsState(if (shown) 1f else 0f, responseSpring(0.3f, 0.8f), label = "bubbleAppear")

    Column(
        Modifier
            .offset(offset.x.dp, offset.y.dp)
            .graphicsLayer {
                // 중심에서 퍼져 나오는 모양
                translationX = -offset.x.dp.toPx() * (1f - progress)
                translationY = -offset.y.dp.toPx() * (1f - progress)
                alpha = (progress * appear).coerceIn(0f, 1f)
            }
            .semantics { contentDescription = "${contact.name}에게 전화" }
            .then(
                if (isSticky) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(index) }
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.x1),
    ) {
        val glow = Color.Black.copy(alpha = if (isFocused) 0.12f else 0.07f)
        Box(
            Modifier
                .size(56.dp)
                .scale(focusScale)
                .shadow(if (isFocused) 6.dp else 4.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .background(CollogColors.avatarGradient, CircleShape)
                .then(
                    if (isFocused) {
                        Modifier.border(1.5.dp, CollogColors.greenNormal.copy(alpha = 0.55f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )
        Text(
            contact.name,
            style = CollogType.caption01Semibold,
            color = if (isFocused) CollogColors.gray900 else CollogColors.gray800,
        )
    }
}
