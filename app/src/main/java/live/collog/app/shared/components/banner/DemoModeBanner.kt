package live.collog.app.shared.components.banner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/** iOS `DemoModeBanner`. */
@Composable
fun DemoModeBanner(onExit: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CollogColors.orangeLight)
            .padding(horizontal = Spacing.x5, vertical = Spacing.x2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("DEMO", style = CollogType.caption01Semibold, color = CollogColors.gray800)
        Spacer(Modifier.weight(1f))
        Text(
            "로그인",
            style = CollogType.caption01Semibold,
            color = CollogColors.greenDark,
            modifier = Modifier.pressable(onClick = onExit),
        )
    }
}
