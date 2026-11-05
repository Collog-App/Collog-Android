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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.designsystem.Spacing

private const val INNER_RADIUS = 34f
private const val OUTER_RADIUS = 177f
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
        // 새 피그마 10~12: 거의 불투명한 `#f2f3f3` 막 + 아래 420dp 초록 글로우(투명 `#cfebd9` → 95% `#c4e8d2`)
        Box(
            Modifier
                .fillMaxSize()
                .background(SpecColors.launcherScrim)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xFFCFEBD9).copy(alpha = 0f),
                        1f to Color(0xFFC4E8D2).copy(alpha = 0.95f),
                    ),
                ),
        )

        if (notice != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(top = Spacing.x4),
                contentAlignment = Alignment.TopCenter,
            ) { Hint(notice) }
        }

        // 질문 묶음: 위 제목 18dp 부터 전화 버튼 중심 위 316dp 까지(피그마 10: 제목 맨 위가 버튼 중심 위 530dp)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = anchorInset + navigationInset + 316.dp)
                .padding(horizontal = 42.dp),
        ) { QuestionStack(model) }

        // 전화 버튼 중심을 기준점으로 삼는 크기 0 상자
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = anchorInset + navigationInset)
                .size(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            TargetFan(model, appear.value, onSelect)
        }
    }
}

/** 새 피그마 10 「안내 문구」(18/400 muted) 아래 32dp 간격으로 질문 말풍선 최대 3개(간격 17dp). 높이는 3개 기준 고정해 위가 흔들리지 않게 한다. */
@Composable
private fun QuestionStack(model: CallLauncherModel) {
    Column(
        Modifier.fillMaxWidth().height(214.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (model.targets.isEmpty()) {
            Spacer(Modifier.height(60.dp))
            Hint("설정에서 통화할 가족을 등록해주세요")
        } else if (model.questions.isEmpty()) {
            Spacer(Modifier.height(60.dp))
            Hint("오늘의 질문이 아직 없어요")
        } else {
            Text("이 질문으로 시작해보세요", style = specText(Pretendard.Regular, 18f, SpecColors.muted))
            Column(
                Modifier.padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp),
            ) {
                model.questions.take(3).forEachIndexed { index, question ->
                    QuestionChip(question, index)
                }
            }
        }
    }
}

/** 새 피그마 「질문 말풍선」: 흰 바탕 둥글기 15.4, 번호 14/700 초록, 질문 15.4/500, 그림자(0,2.8 / 반경 11 / 8%). */
@Composable
private fun QuestionChip(text: String, index: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        shown = true
    }
    val progress by animateFloatAsState(if (shown) 1f else 0f, responseSpring(0.34f, 0.78f), label = "chip")
    val shape = RoundedCornerShape(15.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.coerceIn(0f, 1f)
                translationY = (1f - progress) * 40.dp.toPx()
            }
            .shadow(
                6.dp, shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f),
            )
            .background(Color.White, shape)
            .heightIn(min = 42.dp)
            .padding(horizontal = 19.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(index + 1), style = specText(Pretendard.Bold, 14f, SpecColors.green))
        Text(
            text,
            style = specText(Pretendard.Medium, 15.4f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
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
    val density = LocalDensity.current
    val isSticky = model.mode == CallLauncherModel.Mode.Sticky

    Box(
        Modifier
            .wrapContentSize(Alignment.Center, unbounded = true)
            .size(FAN_SIZE.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 반원 다이얼: 눌린 위치를 각도·반지름으로 직접 판정한다
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 0.82f + 0.18f * appear
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    alpha = appear.coerceIn(0f, 1f)
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
            Dial(model)
        }

        model.targets.forEachIndexed { index, contact ->
            TargetBubble(model, contact, index, appear, isSticky, onSelect)
        }
    }
}

/**
 * 새 피그마 「다이얼 반원」: 반지름 177, 흰 30% 면 + 1.2dp 흰 테두리(아래 평평한 선 포함), 칸 사이 1.1dp 흰 구분선.
 * 11·12(어머니·아버지 선택)는 면이 한 겹 더 겹쳐 더 하얗게 보이므로, 눌려 고른 칸에 흰 30% 를 한 겹 더 깐다.
 */
@Composable
private fun Dial(model: CallLauncherModel) {
    val count = model.targets.size
    val focused = model.focusedIndex
    Canvas(Modifier.fillMaxSize()) {
        val radius = OUTER_RADIUS * density
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        val white = Color.White
        drawArc(white.copy(alpha = 0.3f), 180f, 180f, true, topLeft, arcSize)
        if (focused != null && focused in 0 until count) {
            val range = model.angleRange(focused)
            drawPath(sectorPath(center, 0f, radius, range.start, range.endInclusive), white.copy(alpha = 0.3f))
        }
        drawArc(white, 180f, 180f, true, topLeft, arcSize, style = Stroke(1.2.dp.toPx()))
        for (i in 1 until count) {
            val r = Math.toRadians(-180.0 + 180.0 * i / count)
            drawLine(
                white,
                center,
                Offset(center.x + (cos(r) * radius).toFloat(), center.y + (sin(r) * radius).toFloat()),
                1.1.dp.toPx(),
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val glow = Color(0xFF4DB273).copy(alpha = 0.25f)
        Box(
            Modifier
                .size(55.dp)
                .scale(focusScale)
                .shadow(4.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .background(
                    Brush.linearGradient(listOf(Color(0xFFE1F3A6), Color(0xFF7ED3A0))),
                    CircleShape,
                ),
        )
        Text(contact.name, style = specText(Pretendard.Medium, 14f, Color(0xFF59606A)), maxLines = 1)
    }
}
