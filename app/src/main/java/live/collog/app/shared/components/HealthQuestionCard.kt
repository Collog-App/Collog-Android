package live.collog.app.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

/** 피그마 「최종본」 건강 질문 카드(통화 화면 269:5141, 홈 269:4951). 통화 화면과 홈이 같이 쓴다. */
@Composable
fun HealthQuestionCard(question: String, modifier: Modifier = Modifier) {
    Row(
        modifier
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
