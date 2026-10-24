package live.collog.app.shared.components.tabbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.collog.app.features.call.viewmodels.CallLauncherModel
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.shared.navigation.MainTab
import live.collog.app.shared.ui.designsystem.CollogColors

// 포팅 전 자리: 인자와 상수는 유지하고 iOS `BottomNavBarView`로 통째로 바꾼다.
object BottomNavBar {
    val barHeight = 64.dp
    val buttonLift = 14.dp
    val anchorInset = barHeight / 2 + buttonLift
}

@Composable
fun BottomNavBar(
    selection: MainTab,
    onSelectionChange: (MainTab) -> Unit,
    launcher: CallLauncherModel,
    onLaunch: (FamilyContact) -> Unit,
    onReselect: (MainTab) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().height(BottomNavBar.barHeight).background(CollogColors.gray00)) {
        MainTab.entries.forEach { tab ->
            Text(
                tab.title,
                modifier = Modifier.weight(1f).clickable {
                    if (tab == selection) onReselect(tab) else onSelectionChange(tab)
                },
            )
        }
    }
}
