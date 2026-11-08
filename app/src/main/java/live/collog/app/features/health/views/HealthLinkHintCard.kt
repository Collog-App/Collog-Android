package live.collog.app.features.health.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

/** 홈의 건강 앱 연결 안내(피그마 없음, 「디자인」 카드 모양). 닫으면 다시 띄우지 않는다(HealthLink 가 기억). */
@Composable
fun HealthLinkHintCard(onConnect: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.specCard(padding = 0.dp).padding(start = 17.dp, end = 6.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "건강 앱을 연결하면 가족이 수면·걸음 변화를 함께 볼 수 있어요",
                style = pretendard(Pretendard.Medium, 15f, SpecColors.ink),
            )
            Text(
                "연결하기",
                style = specText(Pretendard.Bold, 14f, SpecColors.green),
                modifier = Modifier
                    .background(SpecColors.greenSurface, RoundedCornerShape(10.dp))
                    .pressable(onClick = onConnect)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Box(
            Modifier.size(40.dp).semantics { contentDescription = "안내 닫기" }.pressable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = SpecColors.muted, modifier = Modifier.size(18.dp))
        }
    }
}
