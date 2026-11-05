package live.collog.app.shared.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 2026-11 새 피그마 「디자인」 페이지(402x874 프레임) 값. 브릿지 json 으로 읽은 수치 그대로다.
 * 이 파일의 이름은 `Spec*` 으로 두어 10-30 「최종본」 기반 토큰(`CollogColors` 등)과 섞이지 않게 한다.
 */
object SpecColors {
    val ink = Color(0xFF2D3139)
    val sub = Color(0xFF4A5058)
    val muted = Color(0xFF8A9099)
    val line = Color(0xFFEEF0F2)
    val cardBorder = Color(0xFFEFF1F3)
    val chip = Color(0xFFF1F3F5)
    val green = Color(0xFF3E9A62)
    val chartGreen = Color(0xFF63CF8A)
    val gaugeRange = Color(0xFF9CE1B7)
    val greenSurface = Color(0xFFF0F8F3)
    val orange = Color(0xFFF2711C)
    val orangeSurface = Color(0xFFFFF1E8)
    val concern = Color(0xFFE5533C)
    val concernSurface = Color(0xFFFFEFEA)
    val concernTick = Color(0xFFFF6B50)
    val track = Color(0xFFF4F5F7)
    val tag = Color(0xFFF0F1F3)
    val tickNeutral = Color(0xFFD5D8DC)
    val rail = Color(0xFFE4E7EA)
    val node = Color(0xFF6B7078)
    val heroOrange = Color(0xFFFBF6EE)
}

/** 새 피그마 글자: 줄 높이 글자 크기의 약 1.19배, 자간 0. */
fun specText(weight: Pretendard, size: Float, color: Color = SpecColors.ink): TextStyle =
    pretendard(weight, size, color).copy(letterSpacing = 0.sp, lineHeight = (size * 1.19f).sp)

/** 새 피그마 카드: 흰 바탕, 둥글기 18, 1dp 테두리, 아주 옅은 그림자(0,2,12 / 3%). */
fun Modifier.specCard(padding: Dp = 15.dp, background: Color = Color.White, shadowed: Boolean = true): Modifier {
    val shape = RoundedCornerShape(18.dp)
    return fillMaxWidth()
        .then(
            if (shadowed) {
                Modifier.shadow(
                    6.dp, shape,
                    ambientColor = Color.Black.copy(alpha = 0.03f),
                    spotColor = Color.Black.copy(alpha = 0.03f),
                )
            } else {
                Modifier
            },
        )
        .background(background, shape)
        .border(1.dp, SpecColors.cardBorder, shape)
        .padding(padding)
}

/** 새 피그마 구분선: 1dp `#eef0f2`. */
@Composable
fun SpecDivider(modifier: Modifier = Modifier, axis: Axis = Axis.Horizontal) {
    val sized = when (axis) {
        Axis.Horizontal -> modifier.fillMaxWidth().height(1.dp)
        Axis.Vertical -> modifier.fillMaxHeight().width(1.dp)
    }
    Box(sized.background(SpecColors.line))
}
