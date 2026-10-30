package live.collog.app.features.auth.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import live.collog.app.R
import live.collog.app.features.auth.views.components.AuthBlockButton
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.pressable

/**
 * PASS 본인인증(서버 계획 S6). 인증 대행사 계약 전이라 약관 다음에 이 단계로 가지 않는다.
 * 켜려면 대행사 SDK 의 인증 결과를 [PassVerificationScreen]의 onVerify 에 붙인 뒤 true 로 바꾼다.
 */
const val PASS_ENABLED = false

/**
 * 피그마 「최종본」 PASS 본인인증(269:3987), 이미 가입된 계정 알림(269:3997).
 * [onVerify]가 null이면 「인증하기」가 꺼진다(지금은 연결할 인증 수단이 없다).
 */
@Composable
fun PassVerificationScreen(
    onBack: () -> Unit,
    onVerify: (() -> Unit)?,
    showsAlreadyRegistered: Boolean = false,
    onDismissAlreadyRegistered: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(CollogColors.gray00).statusBarsPadding()) {
        SettingsDetailHeader("약관 동의", onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.x5).padding(top = Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x6),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text("콜록 사용을 위해\n본인이 맞는지 확인할게요", style = pretendard(Pretendard.Bold, 24f), color = CollogColors.gray900)
                Text("입력한 정보는 안전하게 저장돼요", style = pretendard(Pretendard.Regular, 16f), color = CollogColors.gray800)
            }
            Image(
                painterResource(R.drawable.img_pass_card),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 160.dp, height = 90.dp),
            )
        }
        AuthBlockButton("인증하기", enabled = onVerify != null) { onVerify?.invoke() }
    }

    if (showsAlreadyRegistered) {
        AlreadyRegisteredDialog(onDismissAlreadyRegistered)
    }
}

/** 피그마 Alert Dialog(269:4008): 흰 둥근 상자, 제목·본문, 검은 「확인」. */
@Composable
private fun AlreadyRegisteredDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = Spacing.x5)
                .fillMaxWidth()
                .background(CollogColors.gray00, RoundedCornerShape(Radius.card))
                .padding(Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text("이미 가입된 계정이 있어요.", style = pretendard(Pretendard.SemiBold, 20f), color = CollogColors.gray900)
                Text("기존 계정으로 로그인 해주세요.", style = pretendard(Pretendard.Medium, 16f), color = CollogColors.gray800)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(CollogColors.gray1000, RoundedCornerShape(Radius.btnMedium))
                    .pressable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("확인", style = pretendard(Pretendard.SemiBold, 14f), color = CollogColors.gray00)
            }
        }
    }
}

/** 피그마 「최종본」 PASS 본인인증 완료(269:4009). */
@Composable
fun PassCompletedScreen(onBack: () -> Unit, onNext: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(CollogColors.gray00).statusBarsPadding()) {
        SettingsDetailHeader("약관 동의", onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.x17))
            Image(painterResource(R.drawable.ic_pass_done), contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(Spacing.x6))
            Text(
                "본인 인증이 완료됐어요!",
                style = pretendard(Pretendard.Bold, 24f),
                color = CollogColors.gray900,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.x2))
            Text(
                "마지막으로 정보 입력만 하면\n콜록을 시작할 수 있어요",
                style = pretendard(Pretendard.Regular, 16f),
                color = CollogColors.gray800,
                textAlign = TextAlign.Center,
            )
        }
        AuthBlockButton("다음으로", enabled = true, onClick = onNext)
    }
}
