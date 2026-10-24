package live.collog.app.features.call.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

/** iOS `CallRecordingIndicator`. 녹음 중이 아니어도 자리는 차지하고 보이지 않을 뿐이다. */
@Composable
fun CallRecordingIndicator(isRecording: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(top = Spacing.x3)
            .alpha(if (isRecording) 1f else 0f)
            .then(
                if (isRecording) {
                    Modifier.semantics { contentDescription = "건강 기록을 위해 통화 음성을 녹음 중이에요" }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            )
            .background(CollogColors.red500, CircleShape)
            .padding(horizontal = Spacing.x4, vertical = Spacing.x2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollogIcon("record.circle.fill", size = 18.dp, color = CollogColors.gray00)
        Text("통화 녹음 중", style = pretendard(Pretendard.SemiBold, 15f), color = CollogColors.gray00)
    }
}
