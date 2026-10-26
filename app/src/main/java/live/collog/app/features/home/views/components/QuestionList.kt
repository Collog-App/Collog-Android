package live.collog.app.features.home.views.components

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import live.collog.app.shared.models.PreviewQuestion
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.pressable

/** iOS `QuestionListView`: 오늘의 질문 카드. 누르면 새 질문을 만든다. */
@Composable
fun QuestionList(
    questions: List<PreviewQuestion>,
    modifier: Modifier = Modifier,
    isGenerating: Boolean = false,
    onTap: () -> Unit = {},
) {
    Column(
        modifier
            .pressable(enabled = !isGenerating, onClick = onTap)
            .cardSurface(),
        verticalArrangement = Arrangement.spacedBy(Spacing.x5),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                Text("오늘의 질문", style = CollogType.body01Semibold, color = CollogColors.gray900)
                Text("탭해서 새로운 질문을 생성해보세요", style = CollogType.caption01Medium, color = CollogColors.gray700)
            }

            if (isGenerating) {
                CircularProgressIndicator(Modifier.size(16.dp), color = CollogColors.gray700, strokeWidth = 2.dp)
            } else {
                CollogIcon("arrow.clockwise", size = 16.dp, color = CollogColors.gray600)
            }
        }

        FloatingQuestions(displayedQuestions(questions))
    }
}

/** 같은 문장은 한 번만, 앞에서 세 개. */
private fun displayedQuestions(questions: List<PreviewQuestion>): List<PreviewQuestion> =
    questions.distinctBy { it.text }.take(3)

@Composable
private fun FloatingQuestions(questions: List<PreviewQuestion>) {
    // 시스템 애니메이션을 끄면(iOS 동작 줄이기) 떠다니지 않는다
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var frameNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        // iOS `TimelineView(.animation(minimumInterval: 1 / 30))`처럼 초당 30번까지만 다시 그린다
        var last = 0L
        while (true) {
            withFrameNanos {
                if (it - last >= 33_000_000L) {
                    frameNanos = it
                    last = it
                }
            }
        }
    }

    Box(Modifier.fillMaxWidth().height(144.dp), contentAlignment = Alignment.Center) {
        questions.forEachIndexed { index, question ->
            QuestionPill(
                text = question.text,
                index = index,
                modifier = Modifier.graphicsLayer {
                    val elapsed = frameNanos / 1_000_000_000.0
                    val (driftX, driftY) = if (reduceMotion) 0.0 to 0.0 else drift(elapsed, index)
                    translationX = (horizontalOffset(index) + driftX.toFloat()) * density
                    translationY = (verticalOffset(index) + driftY.toFloat()) * density
                    rotationZ = rotation(index)
                },
            )
        }
    }
}

@Composable
private fun QuestionPill(text: String, index: Int, modifier: Modifier = Modifier) {
    val shadowColor = Color.Black.copy(alpha = 0.035f)
    Row(
        modifier
            .height(44.dp)
            .shadow(4.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
            .background(pillColor(index), CircleShape)
            .border(1.dp, if (index == 1) CollogColors.green200 else CollogColors.gray200, CircleShape)
            .padding(horizontal = Spacing.x4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text((index + 1).toString().padStart(2, '0'), style = CollogType.caption01Semibold, color = CollogColors.green700)
        Text(
            text,
            style = CollogType.body03Medium,
            color = CollogColors.gray900,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun pillColor(index: Int): Color = when (index) {
    1 -> CollogColors.greenLight
    2 -> CollogColors.green100
    else -> CollogColors.gray00
}

private fun horizontalOffset(index: Int): Float = when (index) {
    1 -> 14f
    2 -> -8f
    else -> -12f
}

private fun verticalOffset(index: Int): Float = (index - 1) * 44f

private fun rotation(index: Int): Float = when (index) {
    1 -> 1.0f
    2 -> -0.8f
    else -> -1.4f
}

/** iOS `driftOffset(at:index:)`: 느리게 흔들리는 (가로, 세로) 이동량(pt). */
private fun drift(elapsed: Double, index: Int): Pair<Double, Double> {
    val speed = 0.72 + index * 0.09
    val phase = index * 1.8
    return sin(elapsed * speed * 0.7 + phase) * 1.5 to sin(elapsed * speed + phase) * 2.5
}
