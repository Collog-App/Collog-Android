package live.collog.app.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import live.collog.app.R
import live.collog.app.shared.ui.designsystem.CollogColors

/** 피그마 「최종본」 질문 카드 앞의 초록 원 + 십자(24dp). */
@Composable
fun HealthCrossBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(24.dp).background(CollogColors.greenNormal, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_health_cross),
            contentDescription = null,
            tint = CollogColors.orangeLight,
            modifier = Modifier.size(14.dp),
        )
    }
}
