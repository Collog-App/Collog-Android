package live.collog.app.features.call.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import live.collog.app.features.call.models.CallDurationFormatter
import live.collog.app.features.call.models.CallPhase
import live.collog.app.shared.ui.pressable
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing

/** 새 피그마 「디자인」 13 통화_연결중·14 통화_통화중(어두운 화면). 이니셜 아바타, 이름, 상태·타이머, 질문, 종료/수신 버튼. 음소거·스피커는 명세 밖 예외. */
@Composable
fun CallScreen(
    peerName: String,
    phase: CallPhase,
    questions: List<String>,
    notice: String? = null,
    isIncoming: Boolean = false,
    isAnswering: Boolean = false,
    canAnswer: Boolean = false,
    onAnswer: (() -> Unit)? = null,
    recordingEnabled: Boolean = false,
    isMuted: Boolean = false,
    isSpeakerEnabled: Boolean = false,
    onMute: (() -> Unit)? = null,
    onSpeaker: (() -> Unit)? = null,
    onEnd: () -> Unit,
) {
    var connectedAt by remember { mutableStateOf<Long?>(null) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }

    // phase가 처음부터 active여도 잡히도록 initial 실행
    LaunchedEffect(phase) {
        if (phase == CallPhase.Active && connectedAt == null) connectedAt = System.currentTimeMillis()
    }
    // iOS의 1초 Timer: 틱마다 경과 시간을 갱신한다
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            connectedAt?.let { elapsedMillis = System.currentTimeMillis() - it }
        }
    }

    // 어두운 통화 화면이라 상태 표시줄 글자를 밝게 바꾸고, 화면을 떠나면 되돌린다
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CallBackgroundTop, CallBackgroundBottom)))) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 새 피그마 13·14: 아바타 윗선이 상태 표시줄 아래 71dp(프레임 y=125)
            Header(
                peerName = peerName,
                phase = phase,
                isIncoming = isIncoming,
                isAnswering = isAnswering,
                elapsedSeconds = elapsedMillis / 1000.0,
                modifier = Modifier.padding(top = if (notice == null) 71.dp else 111.dp),
            )

            if (!isIncoming) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    QuestionList(questions, Modifier.padding(top = 42.dp, bottom = Spacing.x4))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.x4))

            val bottomInset = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            if (isIncoming && onAnswer != null) {
                IncomingCallActionsView(
                    canAnswer = canAnswer,
                    isAnswering = isAnswering,
                    onAnswer = onAnswer,
                    onDecline = onEnd,
                    modifier = bottomInset.padding(bottom = 40.dp),
                )
            } else {
                AudioControls(isMuted, isSpeakerEnabled, onMute, onSpeaker)
                EndButton(onEnd, bottomInset.padding(bottom = 40.dp))
            }
        }

        // 녹음 표시와 안내는 위에 겹쳐 놓아 명세의 아바타 자리를 밀지 않는다
        Column(
            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallRecordingIndicator(isRecording = recordingEnabled && phase == CallPhase.Active)
            if (notice != null) NoticeView(notice, Modifier.padding(top = Spacing.x2))
        }
    }
}

/**
 * 명세(13·14)에 없는 음소거·스피커. 질문 목록과 종료 버튼 사이 빈 자리(종료 버튼 바로 위)에
 * 작은 원형 버튼 둘로 둬서 명세 배치를 건드리지 않는다.
 */
@Composable
private fun AudioControls(
    isMuted: Boolean,
    isSpeakerEnabled: Boolean,
    onMute: (() -> Unit)?,
    onSpeaker: (() -> Unit)?,
) {
    Row(
        Modifier.padding(bottom = Spacing.x6),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x5),
    ) {
        if (onMute != null) {
            AudioButton(
                label = if (isMuted) "음소거 해제" else "음소거",
                symbol = if (isMuted) "mic.slash.fill" else "mic.fill",
                active = isMuted,
                onClick = onMute,
            )
        }
        if (onSpeaker != null) {
            AudioButton(
                label = "스피커",
                symbol = if (isSpeakerEnabled) "speaker.wave.3.fill" else "speaker.fill",
                active = isSpeakerEnabled,
                onClick = onSpeaker,
            )
        }
    }
}

@Composable
private fun AudioButton(label: String, symbol: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(if (active) Color.White else CallSurface, CircleShape)
            .pressable(onClick = onClick)
            .semantics { contentDescription = "$label, ${if (active) "켜짐" else "꺼짐"}" },
        contentAlignment = Alignment.Center,
    ) {
        CollogIcon(symbol, size = 20.dp, color = if (active) Color(0xFF1E2024) else Color.White)
    }
}

/** 새 피그마 13·14 위쪽: 아바타 120.7(`#2a2c30`)+이니셜 28/400, 이름 20/700, 상태 16/400 `#cfd2d6`. */
@Composable
private fun Header(
    peerName: String,
    phase: CallPhase,
    isIncoming: Boolean,
    isAnswering: Boolean,
    elapsedSeconds: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(121.dp).background(CallAvatar, CircleShape), contentAlignment = Alignment.Center) {
            Text(
                peerName.trim().take(1),
                style = specText(Pretendard.Regular, 28f, Color.White),
            )
        }
        Text(
            peerName,
            style = specText(Pretendard.Bold, 20f, Color.White),
            maxLines = 1,
            modifier = Modifier.padding(top = 17.dp),
        )

        val status = specText(Pretendard.Regular, 16f, CallStatusText)
        val statusModifier = Modifier.padding(top = 13.dp)
        if (isIncoming) {
            Text(if (isAnswering) "전화를 받고 있어요" else "전화가 왔어요", style = status, modifier = statusModifier)
        } else if (phase.showsTimer) {
            Text(
                CallDurationFormatter.text(elapsedSeconds),
                style = status.copy(fontFeatureSettings = "tnum"),
                modifier = statusModifier,
            )
        } else {
            Text(phase.statusText, style = status, modifier = statusModifier)
        }
    }
}

/** 새 피그마 13: 「이 질문으로 시작해보세요」 18/400 `#8a9099` + 어두운 말풍선(`#26282c`, 번호 `#59d68a`, 글자 흰색). */
@Composable
private fun QuestionList(questions: List<String>, modifier: Modifier = Modifier) {
    if (questions.isEmpty()) return
    Column(
        modifier.fillMaxWidth().padding(horizontal = 41.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("이 질문으로 시작해보세요", style = specText(Pretendard.Regular, 18f, SpecColors.muted))
        Column(Modifier.padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
            questions.take(3).forEachIndexed { index, question ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(CallSurfaceQuestion, RoundedCornerShape(15.dp))
                        .heightIn(min = 42.dp)
                        .padding(horizontal = 19.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%02d".format(index + 1), style = specText(Pretendard.Bold, 14f, Color(0xFF59D68A)))
                    Text(question, Modifier.weight(1f), style = specText(Pretendard.Medium, 15.4f, Color.White))
                }
            }
        }
    }
}

/** 새 피그마 「통화 종료 버튼」: 72.7 빨강 `#ff0000` 원, 흰 수화기. */
@Composable
private fun EndButton(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(73.dp)
            .background(Color(0xFFFF0000), CircleShape)
            .pressable(onClick = onEnd)
            .semantics { contentDescription = "통화 종료" },
        contentAlignment = Alignment.Center,
    ) {
        CollogIcon("phone.down.fill", size = 36.dp, color = Color.White)
    }
}

@Composable
private fun NoticeView(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CollogType.caption01Medium,
        color = CallStatusText,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(CallSurface, CircleShape)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x2),
    )
}

private val CallBackgroundTop = Color(0xFF1E2024)
private val CallBackgroundBottom = Color(0xFF07080A)
private val CallAvatar = Color(0xFF2A2C30)
private val CallSurface = Color(0xFF2A2C30)
private val CallSurfaceQuestion = Color(0xFF26282C)
private val CallStatusText = Color(0xFFCFD2D6)
