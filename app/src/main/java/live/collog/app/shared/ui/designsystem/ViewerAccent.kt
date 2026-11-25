package live.collog.app.shared.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * iOS `ViewerAccent`: 리포트·타임라인에서 지금 누구의 기록을 보는지에 따라 바뀌는 색 세트.
 * 가족(기본) = 브랜드 그린 그대로, 「나」의 기록 = 주황. 색은 보조 신호이고 이름·태그·제목 문구가 실제 구분을 맡는다.
 * 탭 바·전화 버튼은 누구를 보든 그린이라 iOS 의 `fab`·`tabLabel`·`tabBorder` 는 옮기지 않았다.
 */
@Immutable
data class ViewerAccent(
    /** 링·아바타 채움 같은 큰 요소 */
    val accent: Color,
    /** 버튼 글자·태그 배경처럼 흰색 또는 옅은 배경 위에 얹는 진한 톤 */
    val text: Color,
    /** 칩·버튼 배경 */
    val light: Color,
    val border: Color,
    val background: Color,
    /** 카드 테두리. 가족(기본)은 카드가 원래 쓰던 테두리 그대로 */
    val cardBorder: Color?,
) {
    companion object {
        /** 가족을 볼 때(기본). 기존 앱 색 그대로 */
        val Family = ViewerAccent(
            accent = CollogColors.greenNormal,
            text = CollogColors.greenDark,
            light = CollogColors.greenLight,
            border = CollogColors.greenLightActive,
            background = CollogColors.gray50,
            cardBorder = null,
        )

        /** 내 기록을 볼 때 */
        val Mine = ViewerAccent(
            accent = CollogColors.selfAccent,
            text = CollogColors.selfAccentDark,
            light = CollogColors.selfAccentLight,
            border = CollogColors.selfAccentBorder,
            background = CollogColors.selfScreenBackground,
            cardBorder = CollogColors.selfAccentBorder,
        )
    }
}

/** iOS `EnvironmentValues.viewerAccent`. 리포트·타임라인 화면이 넣어 주고, 그 밖에서는 가족 색이다. */
val LocalViewerAccent = compositionLocalOf { ViewerAccent.Family }
