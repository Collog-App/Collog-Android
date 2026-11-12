package live.collog.app.features.auth.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable

/** 동의서 전문(서버 동의 문서 v5)을 정리한 페이지. 화면에는 받는 곳만 두고 나머지는 여기로 보낸다. */
internal const val CONSENT_DETAILS_URL = "https://privacy.collog.live/consent.html"
private const val PRIVACY_POLICY_URL = "https://privacy.collog.live"

private val Recipients = listOf(
    "Deepgram" to "통화 음성을 보내 대화를 글로 바꿔요",
    "Google Gemini" to "대화 글(건강 이야기 포함)을 보내 건강 기록을 만들어요",
    "ElevenLabs" to "오늘의 질문 문장을 보내 질문 목소리를 만들어요",
)

/**
 * iOS `ThirdPartyAIDisclosureView`. 외부 AI 서비스마다 무엇을 왜 보내는지 이름으로 밝힌다.
 * App Store 1.1 (26) 반려(5.1.2(i), 받는 곳을 보내기 전에 밝히지 않음) 뒤 두 앱의 동의 화면을 같은 구조로 맞췄다.
 * [showsPolicyLink]가 false 면 처리방침 링크를 따로 두는 화면이다.
 */
@Composable
fun ThirdPartyAiDisclosure(modifier: Modifier = Modifier, showsPolicyLink: Boolean = true) {
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        Text("외부 AI로 보내는 정보", style = CollogType.body02Semibold, color = CollogColors.gray900)
        Recipients.forEach { (name, detail) ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                Text(name, style = CollogType.body03Medium, color = CollogColors.gray900)
                Text(detail, style = CollogType.caption01Medium, color = CollogColors.gray700)
            }
        }
        Text(
            "이름·전화번호는 보내지 않지만 대화 중에 말한 내용은 들어갈 수 있어요. 해외 서버에서 처리될 수 있어요.",
            style = CollogType.caption01Medium,
            color = CollogColors.gray700,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x4)) {
            LinkText("자세히 보기 ›") { uriHandler.openUri(CONSENT_DETAILS_URL) }
            if (showsPolicyLink) LinkText("개인정보처리방침 보기 ›") { uriHandler.openUri(PRIVACY_POLICY_URL) }
        }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = pretendardPlain(Pretendard.SemiBold, 13f, CollogColors.gray800),
        modifier = Modifier.pressable(onClick = onClick),
    )
}
