package live.collog.app.shared.components.stat

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * iOS `HStack(alignment: .firstTextBaseline, spacing: 1) { 값; 단위 }`.
 * 두 글자 크기의 밑줄을 맞추려고 한 줄 글자로 이어 붙인다(1pt 간격은 두지 않는다).
 */
@Composable
fun ValueWithUnit(
    value: String,
    unit: String,
    valueStyle: TextStyle,
    valueColor: Color,
    unitStyle: TextStyle,
    unitColor: Color,
    modifier: Modifier = Modifier,
) {
    val text = buildAnnotatedString {
        append(value)
        withStyle(
            SpanStyle(
                fontSize = unitStyle.fontSize,
                fontWeight = unitStyle.fontWeight,
                color = unitColor,
            ),
        ) { append(unit) }
    }
    Text(text, style = valueStyle, color = valueColor, modifier = modifier)
}
