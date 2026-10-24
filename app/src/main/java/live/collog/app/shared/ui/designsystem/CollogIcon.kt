package live.collog.app.shared.ui.designsystem

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS 코드가 쓰는 SF Symbol 이름을 Material 아이콘으로 바꾼다.
 * iOS 화면 코드를 옮길 때 `Icon(name: "phone.fill")`을 `CollogIcon("phone.fill")`로 그대로 쓰면 된다.
 */
fun sfSymbol(name: String): ImageVector = when (name) {
    "arrow.clockwise" -> Icons.Filled.Refresh
    "arrowtriangle.down.fill" -> Icons.Filled.ArrowDropDown
    "arrowtriangle.up.fill" -> Icons.Filled.ArrowDropUp
    "bell" -> Icons.Outlined.Notifications
    "bubble.left.and.text.bubble.right.fill" -> Icons.Filled.Forum
    "chart.line.uptrend.xyaxis" -> Icons.AutoMirrored.Filled.TrendingUp
    "chart.xyaxis.line" -> Icons.AutoMirrored.Filled.ShowChart
    "checkmark" -> Icons.Filled.Check
    "chevron.down" -> Icons.Filled.ExpandMore
    "chevron.left" -> Icons.Filled.ChevronLeft
    "chevron.right" -> Icons.Filled.ChevronRight
    "chevron.up" -> Icons.Filled.ExpandLess
    "clock.arrow.circlepath" -> Icons.Outlined.History
    "doc.text.fill" -> Icons.Filled.Description
    "doc.text.magnifyingglass" -> Icons.Filled.FindInPage
    "exclamationmark.circle" -> Icons.Outlined.ErrorOutline
    "figure.walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
    "fork.knife" -> Icons.Outlined.Restaurant
    "gearshape.fill" -> Icons.Filled.Settings
    "house.fill" -> Icons.Filled.Home
    "info.circle" -> Icons.Outlined.Info
    "mic.fill" -> Icons.Filled.Mic
    "mic.slash.fill" -> Icons.Filled.MicOff
    "moon" -> Icons.Outlined.Bedtime
    "phone.badge.waveform" -> Icons.Filled.PhoneInTalk
    "phone.down.fill" -> Icons.Filled.CallEnd
    "phone.fill" -> Icons.Filled.Phone
    "pills" -> Icons.Outlined.Medication
    "plus" -> Icons.Filled.Add
    "questionmark.bubble.fill" -> Icons.Filled.QuestionAnswer
    "record.circle.fill" -> Icons.Filled.FiberManualRecord
    "speaker.fill" -> Icons.AutoMirrored.Filled.VolumeDown
    "speaker.wave.3.fill" -> Icons.AutoMirrored.Filled.VolumeUp
    "stethoscope" -> Icons.Outlined.MedicalServices
    "text.bubble" -> Icons.Outlined.ChatBubbleOutline
    else -> {
        Log.w("Collog", "SF Symbol 대응 없음: $name")
        Icons.AutoMirrored.Filled.Chat
    }
}

/** iOS `Icon`: 상자 크기 `size` 안에 82% 크기의 기호. */
@Composable
fun CollogIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = IconSize.medium,
    color: Color = CollogColors.gray800,
) {
    Box(modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Icon(sfSymbol(name), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.82f))
    }
}

@Composable
fun NotificationDot(modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).background(CollogColors.red500, CircleShape))
}
