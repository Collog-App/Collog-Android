package live.collog.app.features.call.views

import androidx.compose.runtime.Composable
import live.collog.app.features.call.models.CallPhase
import live.collog.app.shared.ui.PendingPort

// 포팅 전 자리: 인자는 유지하고 iOS `CallView`로 통째로 바꾼다.
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
    PendingPort("통화 · $peerName")
}
