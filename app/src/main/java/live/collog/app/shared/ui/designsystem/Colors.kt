package live.collog.app.shared.ui.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** iOS `Color+Extension.swift`와 같은 값. 이름도 그대로 쓴다. */
object CollogColors {
    val gray00 = Color(0xFFFFFFFF)
    val gray50 = Color(0xFFFAFBFC)
    val gray100 = Color(0xFFF7F8F9)
    val gray200 = Color(0xFFF3F4F5)
    val gray300 = Color(0xFFEEEFF1)
    val gray400 = Color(0xFFDCDEE3)
    val gray500 = Color(0xFFD1D3D8)
    val gray600 = Color(0xFFB0B3BA)
    val gray700 = Color(0xFF868B94)
    val gray800 = Color(0xFF555D6D)
    val gray900 = Color(0xFF2A3038)
    val gray1000 = Color(0xFF1A1C20)

    val greenLight = Color(0xFFE9FAF3)
    val greenLightActive = Color(0xFFBCF0D9)
    val green100 = Color(0xFFF2FBF7)
    val green200 = Color(0xFFCBEFE1)
    val green300 = Color(0xFFA4E3CB)
    val green400 = Color(0xFF7DD7B4)
    val green500 = Color(0xFF55CB9E)
    val greenNormal = Color(0xFF26CF85)
    val greenDark = Color(0xFF1D9B64)
    val green700 = Color(0xFF2C8F69)

    val red100 = Color(0xFFFFF7F6)
    val red300 = Color(0xFFFF9790)
    val red400 = Color(0xFFFF685D)
    val red500 = Color(0xFFFF382A)
    val red600 = Color(0xFFF61000)
    val red700 = Color(0xFFC30D00)

    val blue100 = Color(0xFFE5F3FF)
    val blue200 = Color(0xFFB3DBFF)
    val blue500 = Color(0xFF1A94FF)
    val blue600 = Color(0xFF007AE6)
    val blue700 = Color(0xFF005FB3)

    val orangeLight = Color(0xFFFFF4E6)
    val orange600 = Color(0xFFFF5900)

    val disabledForeground = gray500

    val kakaoYellow = Color(0xFFFAE100)
    val kakaoLabel = Color(0xFF21232A)

    val assetPlaceholder = Color(0xFFFF0000)

    /** iOS `LinearGradient.avatar` (topTrailing → bottomLeading). */
    val avatarGradient: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFFFFFBBF), Color(0xFF26CF85)),
        start = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f),
        end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
    )
}
