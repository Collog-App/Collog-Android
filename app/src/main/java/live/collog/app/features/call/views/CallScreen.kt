package live.collog.app.features.call.views

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import live.collog.app.features.call.models.CallDurationFormatter
import live.collog.app.features.call.models.CallPhase
import live.collog.app.shared.ui.pressable
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

/** iOS `CallView`. 전체 화면 통화 UI: 상태·타이머, 오늘의 질문, 음소거·스피커, 종료/수신 버튼. */
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

    // iOS preferredColorScheme(.dark): 상태 표시줄 아이콘을 밝게
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF171A20), Color(0xFF07080A))))
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CallRecordingIndicator(isRecording = recordingEnabled && phase == CallPhase.Active)
        if (notice != null) {
            NoticeView(notice, Modifier.padding(top = Spacing.x3))
        }

        Header(
            peerName = peerName,
            phase = phase,
            isIncoming = isIncoming,
            isAnswering = isAnswering,
            elapsedSeconds = elapsedMillis / 1000.0,
            modifier = Modifier.padding(top = if (notice == null) Spacing.x8 else Spacing.x5),
        )

        if (!isIncoming) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                QuestionList(
                    questions,
                    Modifier.padding(top = Spacing.x8, bottom = Spacing.x4),
                )
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
                modifier = bottomInset.padding(bottom = Spacing.x8),
            )
        } else {
            AudioControls(isMuted, isSpeakerEnabled, onMute, onSpeaker)
            EndButton(onEnd, bottomInset.padding(bottom = Spacing.x8))
        }
    }
}

@Composable
private fun AudioControls(
    isMuted: Boolean,
    isSpeakerEnabled: Boolean,
    onMute: (() -> Unit)?,
    onSpeaker: (() -> Unit)?,
) {
    Row(
        Modifier.padding(bottom = Spacing.x5),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x8),
    ) {
        if (onMute != null) {
            AudioButton(
                label = if (isMuted) "음소거 해제" else "음소거",
                symbol = if (isMuted) "mic.slash.fill" else "mic.fill",
                state = if (isMuted) "켜짐" else "꺼짐",
                onClick = onMute,
            )
        }
        if (onSpeaker != null) {
            AudioButton(
                label = "스피커",
                symbol = if (isSpeakerEnabled) "speaker.wave.3.fill" else "speaker.fill",
                state = if (isSpeakerEnabled) "켜짐" else "꺼짐",
                onClick = onSpeaker,
            )
        }
    }
}

@Composable
private fun AudioButton(label: String, symbol: String, state: String, onClick: () -> Unit) {
    Row(
        Modifier
            .pressable(onClick = onClick)
            .semantics { contentDescription = "$label, $state" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollogIcon(symbol, size = 22.dp, color = CollogColors.gray00)
        Text(label, style = pretendard(Pretendard.Regular, 17f), color = CollogColors.gray00)
    }
}

@Composable
private fun Header(
    peerName: String,
    phase: CallPhase,
    isIncoming: Boolean,
    isAnswering: Boolean,
    elapsedSeconds: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        Box(
            Modifier.size(120.dp).background(CollogColors.gray00.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(peerName.take(1), style = pretendard(Pretendard.SemiBold, 40f), color = CollogColors.gray00)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text(peerName, style = CollogType.headline02, color = CollogColors.gray00)

            val status = pretendard(Pretendard.Medium, 16f)
            if (isIncoming) {
                Text(if (isAnswering) "전화를 받고 있어요" else "전화가 왔어요", style = status, color = CollogColors.gray400)
            } else if (phase.showsTimer) {
                Text(
                    CallDurationFormatter.text(elapsedSeconds),
                    style = status.copy(fontFeatureSettings = "tnum"),
                    color = CollogColors.gray400,
                )
            } else {
                Text(phase.statusText, style = status, color = CollogColors.gray400)
            }
        }
    }
}

@Composable
private fun QuestionList(questions: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.x5),
        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
    ) {
        if (questions.isNotEmpty()) {
            Text(
                "오늘의 질문",
                style = CollogType.caption01Medium,
                color = CollogColors.gray500,
                modifier = Modifier.padding(horizontal = Spacing.x1),
            )
        }

        questions.forEachIndexed { index, question ->
            val shape = RoundedCornerShape(Radius.btnSmall)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CollogColors.gray00.copy(alpha = 0.08f), shape)
                    .border(1.dp, CollogColors.gray00.copy(alpha = 0.06f), shape)
                    .padding(Spacing.x3),
                horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%02d".format(index + 1), style = CollogType.caption01Semibold, color = CollogColors.green400)
                Text(
                    question,
                    style = CollogType.body02Medium,
                    color = CollogColors.gray00,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EndButton(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(72.dp)
            .background(CollogColors.red500, CircleShape)
            .pressable(onClick = onEnd)
            .semantics { contentDescription = "통화 종료" },
        contentAlignment = Alignment.Center,
    ) {
        CollogIcon("phone.down.fill", size = 30.dp, color = CollogColors.gray00)
    }
}

@Composable
private fun NoticeView(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CollogType.caption01Medium,
        color = CollogColors.gray400,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(CollogColors.gray00.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x2),
    )
}
