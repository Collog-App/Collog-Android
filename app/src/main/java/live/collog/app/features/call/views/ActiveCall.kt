package live.collog.app.features.call.views

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalContext
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.incomingCallShown
import live.collog.app.features.call.services.toggleMute
import live.collog.app.features.call.services.toggleSpeaker
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType

// 앱 화면(RootScreen)과 잠금 화면용 통화 화면(CallActivity)이 같이 쓴다.

/** iOS `RootView.callScreen`: 지금 통화를 CallView에 연결한다. */
@Composable
fun ActiveCallScreen(callCenter: CallCenter, call: CallCenter.ActiveCall) {
    key(call.id) {
        val isIncoming = call.direction == CallCenter.Direction.Incoming && call.phase == CallPhase.Ringing
        // 이용 기록: 앱을 보고 있으면 이 화면이 울림 화면이다(알림이 못 떴어도)
        if (isIncoming) LaunchedEffect(Unit) { callCenter.incomingCallShown(call.id) }
        CallScreen(
            peerName = call.peerName,
            phase = call.phase,
            connectedAtMillis = call.connectedAtMillis,
            questions = call.questions,
            notice = callCenter.incomingAnswerError ?: call.notice,
            isIncoming = isIncoming,
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

/**
 * 통화 종료 화면(CallPhase.Ended)과 통화 뒤 한 줄 질문(이용 기록 약속). 답하거나 닫을 때까지 머문다.
 * 통화는 이미 정리돼 마이크·통화 서비스·시스템 통화는 남아 있지 않다. 새 통화가 오면 CallCenter 가 질문을 내린다.
 */
@Composable
fun EndedCallScreen(callCenter: CallCenter, question: CallCenter.PostCallQuestion) {
    key(question.callId) {
        CallScreen(
            peerName = question.peerName,
            phase = CallPhase.Ended,
            questions = question.questions,
            onPostCallAnswer = callCenter::answerPostCallQuestion,
            onPostCallClose = callCenter::dismissPostCallQuestion,
            onEnd = callCenter::dismissPostCallQuestion,
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
