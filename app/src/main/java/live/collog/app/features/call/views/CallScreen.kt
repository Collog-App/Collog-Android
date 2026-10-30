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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import live.collog.app.features.call.models.CallDurationFormatter
import live.collog.app.features.call.models.CallPhase
import live.collog.app.shared.components.HealthCrossBadge
import live.collog.app.shared.ui.pressable
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

/** 피그마 「최종본」 통화 화면(269:5141 연결 중, 269:5187 통화 중). 상태·타이머, 오늘의 건강 질문, 음소거·스피커, 종료/수신 버튼. */
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

    Column(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray00)
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
            modifier = Modifier.padding(top = if (notice == null) 47.dp else Spacing.x5),
        )

        if (!isIncoming) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                QuestionList(
                    questions,
                    Modifier.padding(top = 58.dp, bottom = Spacing.x4),
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

/** 피그마 글자는 줄 높이가 글자 크기의 약 1.19배라 한 줄 글자에만 맞춘다. */
private fun singleLine(weight: Pretendard, size: Float): TextStyle =
    pretendard(weight, size).copy(lineHeight = (size * 1.19f).sp)

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
        CollogIcon(symbol, size = 22.dp, color = CollogColors.gray700)
        Text(label, style = pretendard(Pretendard.Regular, 17f), color = CollogColors.gray700)
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
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(21.dp)) {
            Box(Modifier.size(100.dp).background(CollogColors.avatarGradient, CircleShape))
            Text(peerName, style = singleLine(Pretendard.Medium, 30f), color = Color.Black)
        }

        val status = singleLine(Pretendard.Medium, 22f)
        if (isIncoming) {
            Text(if (isAnswering) "전화를 받고 있어요" else "전화가 왔어요", style = status, color = CollogColors.gray700)
        } else if (phase.showsTimer) {
            Text(
                CallDurationFormatter.text(elapsedSeconds),
                style = status.copy(fontFeatureSettings = "tnum"),
                color = CollogColors.gray700,
            )
        } else {
            Text(phase.statusText, style = status, color = CollogColors.gray700)
        }
    }
}

@Composable
private fun QuestionList(questions: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.x6),
        verticalArrangement = Arrangement.spacedBy(Spacing.x4),
    ) {
        if (questions.isNotEmpty()) {
            Text("오늘의 건강 질문", style = singleLine(Pretendard.Medium, 15f), color = Color.Black)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x5)) {
            questions.forEach { question ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .background(CollogColors.gray100, RoundedCornerShape(10.dp))
                        .padding(horizontal = Spacing.x5, vertical = 13.dp),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HealthCrossBadge()
                    Text(
                        question,
                        style = pretendard(Pretendard.Medium, 15f),
                        color = Color.Black,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EndButton(onEnd: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(85.dp)
            .background(CollogColors.red500, CircleShape)
            .pressable(onClick = onEnd)
            .semantics { contentDescription = "통화 종료" },
        contentAlignment = Alignment.Center,
    ) {
        CollogIcon("phone.down.fill", size = 60.dp, color = CollogColors.gray00)
    }
}

@Composable
private fun NoticeView(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CollogType.caption01Medium,
        color = CollogColors.gray700,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(CollogColors.gray100, CircleShape)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x2),
    )
}
