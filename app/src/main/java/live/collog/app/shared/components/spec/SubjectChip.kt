package live.collog.app.shared.components.spec

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specText

/** 새 피그마 「보호자 선택 칩」(`어머니 ⌄`): 80x29, 둥글기 8, 글자 14/500. 누르는 동작은 호출하는 쪽이 [modifier] 로 붙인다. */
@Composable
fun SubjectChip(label: String, modifier: Modifier = Modifier, showsChevron: Boolean = true) {
    Row(
        modifier
            .background(SpecColors.chip, RoundedCornerShape(8.dp))
            .padding(start = 16.dp, end = if (showsChevron) 8.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = specText(Pretendard.Medium, 14f), maxLines = 1)
        if (showsChevron) CollogIcon("chevron.down", size = 15.dp, color = SpecColors.sub)
    }
}
