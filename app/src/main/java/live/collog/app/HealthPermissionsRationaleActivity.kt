package live.collog.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import live.collog.app.features.settings.views.components.SettingsActionButton
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.pressable

/**
 * Health Connect 권한 화면의 「개인정보처리방침」 링크가 여는 화면(매니페스트의 rationale 액티비티·Android 14 별칭).
 * 서버에 닿지 못해도 보이도록 건강 앱 연결 동의 문서(health-link-2026-11-08.v1)를 그대로 담고, 처리방침 전문으로 잇는다.
 */
class HealthPermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val body = pretendard(Pretendard.Medium, 17f, CollogColors.gray800)
            Column(
                Modifier
                    .fillMaxSize()
                    .background(CollogColors.gray50)
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.x5, vertical = Spacing.x6),
                verticalArrangement = Arrangement.spacedBy(Spacing.x4),
            ) {
                Text("건강 앱 연결 (선택)", style = CollogType.headline02, color = CollogColors.gray900)
                Items.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("•", style = body, modifier = Modifier.width(18.dp))
                        Text(item, style = body, modifier = Modifier.weight(1f))
                    }
                }
                Text(
                    "개인정보처리방침 전문 보기",
                    style = pretendard(Pretendard.Medium, 16f, CollogColors.gray800).copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.padding(top = Spacing.x2).pressable {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
                    },
                )
                SettingsActionButton("닫기", Modifier.fillMaxWidth().padding(top = Spacing.x4)) { finish() }
            }
        }
    }

    private companion object {
        const val PRIVACY_URL = "https://privacy.collog.live"

        val Items = listOf(
            "휴대폰 건강 앱(Health Connect·Apple 건강)에 기록된 수면 시간, 걸음 수, 심박, 혈압, 혈당을 하루 단위로 요약해 콜록 서버로 보냅니다. 분 단위 원본 기록은 보내지 않습니다.",
            "요약은 가족 건강 리포트에 생활 변화를 보여 주는 데만 씁니다.",
            "리포트 공유를 허락한 가족 구성원에게만 보입니다.",
            "연결을 끊거나 탈퇴하면 서버에 저장된 요약을 바로 지웁니다.",
            "동의하지 않아도 콜록의 다른 기능은 그대로 쓸 수 있습니다.",
            "이 정보는 의료 진단이 아니며 건강 변화 참고용입니다.",
        )
    }
}
