package live.collog.app.shared.components.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import java.util.UUID
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specText

/** iOS `KeywordTone`. */
enum class KeywordTone {
    Neutral,
    Positive,
    Caution,
    Concern;

    val tickColor: Color
        get() = when (this) {
            Neutral -> SpecColors.tickNeutral
            Positive -> SpecColors.chartGreen
            Caution -> SpecColors.orange
            Concern -> SpecColors.concernTick
        }

    val chipForeground: Color get() = if (this == Concern) SpecColors.concern else SpecColors.sub

    val chipBackground: Color get() = if (this == Concern) SpecColors.concernSurface else SpecColors.tag
}

/** iOS `KeywordMark`. [position]은 0~1 비율. */
data class KeywordMark(val position: Double, val tone: KeywordTone, val label: String?) {
    val id: UUID = UUID.randomUUID()
}

/** iOS `KeywordTimelineView`: 띠 위에 키워드 표시와 이름표를 놓는다. 새 피그마 08 「태그 트랙」(높이 15, 눈금 2) 모양. */
@Composable
fun KeywordTimeline(marks: List<KeywordMark>, modifier: Modifier = Modifier) {
    val topLabels = marks.filter { it.label != null && it.tone != KeywordTone.Concern }
    val bottomLabels = marks.filter { it.label != null && it.tone == KeywordTone.Concern }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (topLabels.isNotEmpty()) ChipRow(topLabels, pointerBelow = true)

        Canvas(Modifier.fillMaxWidth().height(15.dp)) {
            drawRoundRect(SpecColors.track, size = size, cornerRadius = CornerRadius(2.dp.toPx()))
            val tickWidth = 2.dp.toPx()
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
    Box(Modifier.fillMaxWidth().height(if (pointerBelow) 21.dp else 22.dp)) {
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
                Chip(mark)
            }
        }
    }
}

@Composable
private fun Chip(mark: KeywordMark) {
    val concern = mark.tone == KeywordTone.Concern
    Text(
        mark.label.orEmpty(),
        style = specText(Pretendard.Medium, 12f, mark.tone.chipForeground),
        modifier = Modifier
            .background(mark.tone.chipBackground, RoundedCornerShape(if (concern) 13.dp else 12.dp))
            .padding(horizontal = if (concern) 20.dp else 12.dp, vertical = if (concern) 4.dp else 3.5.dp),
    )
}
