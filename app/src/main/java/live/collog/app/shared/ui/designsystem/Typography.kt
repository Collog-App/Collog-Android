package live.collog.app.shared.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import live.collog.app.R

enum class Pretendard(val weight: FontWeight) {
    Bold(FontWeight.Bold),
    SemiBold(FontWeight.SemiBold),
    Medium(FontWeight.Medium),
    Regular(FontWeight.Normal),
}

val PretendardFamily = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_regular, FontWeight.Normal),
)

/**
 * iOS `Typography`: 자간 -2.5%, 줄 높이 1.45배.
 * iOS는 글꼴 기본 줄 높이(1.2배)에 lineSpacing을 더하므로 결과 줄 높이가 1.45배다.
 */
object Typography {
    const val LETTER_SPACING_RATIO = -0.025f
    const val LINE_HEIGHT_RATIO = 1.45f
}

/** `Text(...).pretendard(weight, size)`와 같은 스타일. 색이 null이면 상위 색을 따른다. */
fun pretendard(weight: Pretendard, size: Float, color: Color = Color.Unspecified): TextStyle =
    TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = weight.weight,
        fontSize = size.sp,
        letterSpacing = Typography.LETTER_SPACING_RATIO.em,
        lineHeight = (size * Typography.LINE_HEIGHT_RATIO).sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
        color = color,
    )

/** `pretendardStyle(weight, size)`: 입력창처럼 줄 간격을 더하지 않는 곳에 쓴다. */
fun pretendardPlain(weight: Pretendard, size: Float, color: Color = CollogColors.gray900): TextStyle =
    TextStyle(
        fontFamily = PretendardFamily,
        fontWeight = weight.weight,
        fontSize = size.sp,
        letterSpacing = Typography.LETTER_SPACING_RATIO.em,
        color = color,
    )

/** iOS `Text` 확장(headline_02 등)과 같은 이름·크기. */
object CollogType {
    val headline02 = pretendard(Pretendard.Bold, 24f)
    val subtitle01 = pretendard(Pretendard.SemiBold, 20f)
    val subtitle02 = pretendard(Pretendard.SemiBold, 18f)
    val body01Semibold = pretendard(Pretendard.SemiBold, 16f)
    val body01Medium = pretendard(Pretendard.Medium, 16f)
    val body01Regular = pretendard(Pretendard.Regular, 16f)
    val body02Semibold = pretendard(Pretendard.SemiBold, 14f)
    val body02Medium = pretendard(Pretendard.Medium, 14f)
    val body02Regular = pretendard(Pretendard.Regular, 14f)
    val body03Medium = pretendard(Pretendard.Medium, 13f)
    val caption01Semibold = pretendard(Pretendard.SemiBold, 12f)
    val caption01Medium = pretendard(Pretendard.Medium, 12f)
    val caption01Regular = pretendard(Pretendard.Regular, 12f)
    val caption02Semibold = pretendard(Pretendard.SemiBold, 10f)
    val caption02Medium = pretendard(Pretendard.Medium, 10f)
    val caption02Regular = pretendard(Pretendard.Regular, 10f)
}
