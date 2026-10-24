package live.collog.app.features.home.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/** iOS `HomeDetailHeader`: 뒤로 버튼과 가운데 제목. 뒤로는 현재 탭의 화면 스택에서 한 장 빼낸다. */
@Composable
fun HomeDetailHeader(title: String, modifier: Modifier = Modifier) {
    val navigation = LocalNavigationManager.current
    Row(
        modifier
            .fillMaxWidth()
            .background(CollogColors.gray50)
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .semantics { contentDescription = "뒤로" }
                .pressable { navigation.pop() },
            contentAlignment = Alignment.Center,
        ) {
            CollogIcon("chevron.left", size = 20.dp, color = CollogColors.gray900)
        }
        FlexibleSpace()
        Text(title, style = CollogType.body01Semibold, color = CollogColors.gray900)
        FlexibleSpace()
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun RowScope.FlexibleSpace() {
    Spacer(Modifier.weight(1f))
}
