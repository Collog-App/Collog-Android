package live.collog.app.features.call.views

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.toggleMute
import live.collog.app.features.call.services.toggleSpeaker
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType

// 앱 화면(RootScreen)과 잠금 화면용 통화 화면(CallActivity)이 같이 쓴다.

/** iOS `RootView.callScreen`: 지금 통화를 CallView에 연결한다. */
@Composable
fun ActiveCallScreen(callCenter: CallCenter, call: CallCenter.ActiveCall) {
    key(call.id) {
        CallScreen(
            peerName = call.peerName,
            phase = call.phase,
            questions = call.questions,
            notice = callCenter.incomingAnswerError ?: call.notice,
            isIncoming = call.direction == CallCenter.Direction.Incoming && call.phase == CallPhase.Ringing,
            isAnswering = callCenter.incomingAnswerRequested,
            canAnswer = callCenter.canAnswerIncomingCall,
            onAnswer = { callCenter.answerIncomingCall() },
            recordingEnabled = call.recordingEnabled,
            isMuted = call.isMuted,
            isSpeakerEnabled = call.isSpeakerEnabled,
            onMute = { callCenter.toggleMute() },
            onSpeaker = { callCenter.toggleSpeaker() },
            onEnd = { callCenter.endActiveCall() },
        )
    }
}

/** iOS `.alert("통화를 진행할 수 없어요")`. 마이크가 거부됐으면 앱 설정으로 보낸다. */
@Composable
fun CallErrorAlert(callCenter: CallCenter, onDismissed: () -> Unit = {}) {
    val message = callCenter.callError ?: return
    val context = LocalContext.current
    val dismiss = {
        callCenter.callError = null
        onDismissed()
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("통화를 진행할 수 없어요", style = CollogType.body01Semibold, color = CollogColors.gray900) },
        text = { Text(message, style = CollogType.body02Regular, color = CollogColors.gray800) },
        confirmButton = {
            TextButton(onClick = dismiss) {
                Text("확인", style = CollogType.body02Semibold, color = CollogColors.blue600)
            }
        },
        dismissButton = if (callCenter.needsMicrophoneSettings) {
            {
                TextButton(onClick = {
                    dismiss()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) {
                    Text("설정으로 이동", style = CollogType.body02Semibold, color = CollogColors.blue600)
                }
            }
        } else {
            null
        },
        containerColor = CollogColors.gray00,
    )
}
