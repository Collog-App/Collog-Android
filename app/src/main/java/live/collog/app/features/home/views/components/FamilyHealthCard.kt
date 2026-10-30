package live.collog.app.features.home.views.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import live.collog.app.R
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.shared.components.HealthCrossBadge
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.chart.smoothPath
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/**
 * 피그마 「최종본」 홈 「우리 가족 건강」 카드(269:4909): 고른 가족 이름표, 요약, 말씀 속도 선, 「가족 모두 보기」, 건강 피드백.
 * 피그마의 지역 줄(「경기도 과천시」)은 받는 값이 없어 그리지 않는다.
 */
@Composable
fun FamilyHealthCard(
    summary: FamilyHealthSummary,
    feedback: HealthFeedback?,
    lastCallText: String?,
    contacts: List<FamilyContact>,
    onSelect: (FamilyContact) -> Unit,
    onShowFamily: () -> Unit,
    onShowFeedback: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray00, RoundedCornerShape(16.dp))
            .padding(Spacing.x4),
        verticalArrangement = Arrangement.spacedBy(Spacing.x4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x1), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_family_health), contentDescription = null, modifier = Modifier.size(24.dp))
                Text("우리 가족 건강", style = CollogType.subtitle01, color = CollogColors.gray1000)
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                        MemberBadge(summary.memberName, contacts, onSelect)
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                            Text(summary.periodText, style = CollogType.body02Medium, color = CollogColors.gray800)
                            Text(summary.headline, style = CollogType.body01Semibold, color = CollogColors.gray900)
                            Text(summary.detail, style = CollogType.body02Semibold, color = CollogColors.gray600)
                        }
                    }
                    summary.trend?.let { TrendLine(it) }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pressable(onClick = onShowFamily),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x1, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("가족 모두 보기", style = CollogType.body02Semibold, color = CollogColors.gray700)
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = CollogColors.gray700,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        feedback?.let { FeedbackBox(it, lastCallText, onShowFeedback) }
    }
}

/** 피그마 Badge(269:4916). 가족이 둘 이상이면 눌러서 볼 가족을 바꾼다. */
@Composable
fun MemberBadge(name: String, contacts: List<FamilyContact>, onSelect: (FamilyContact) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .height(26.dp)
                .pressable(enabled = contacts.size > 1) { expanded = true }
                .background(CollogColors.blue100, RoundedCornerShape(6.dp))
                .padding(horizontal = Spacing.x2),
            contentAlignment = Alignment.Center,
        ) {
            Text(name, style = CollogType.caption01Semibold, color = CollogColors.blue600)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = CollogColors.gray00,
        ) {
            contacts.forEach { contact ->
                DropdownMenuItem(
                    text = { Text(contact.name, style = CollogType.body02Medium, color = CollogColors.gray900) },
                    onClick = {
                        expanded = false
                        onSelect(contact)
                    },
                )
            }
        }
    }
}

/** 피그마 그래프(269:4925): 회색 평소 선, 초록 추세선, 끝 점, 처음·마지막 이름표. */
@Composable
private fun TrendLine(series: TrendSeries) {
    val first = series.points.firstOrNull() ?: return
    val last = series.points.last()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        Canvas(Modifier.fillMaxWidth().height(73.dp)) {
            val points = series.points
            if (points.size < 2) return@Canvas
            val values = points.map { it.value } + if (series.hasPersonalBaseline) listOf(series.median) else emptyList()
            val minimum = values.min()
            val span = maxOf(values.max() - minimum, 0.0001)
            val dotRadius = 11.dp.toPx()
            val top = dotRadius + 1.dp.toPx()
            val bottom = size.height - top
            // 끝 점이 잘리지 않게 오른쪽을 비운다(피그마 318 중 282)
            val right = size.width - 36.dp.toPx()
            fun y(value: Double): Float = bottom - ((value - minimum) / span).toFloat() * (bottom - top)

            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (series.hasPersonalBaseline) {
                val medianY = y(series.median)
                drawLine(
                    CollogColors.gray500,
                    Offset(0f, medianY),
                    Offset(size.width, medianY),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
            val offsets = points.mapIndexed { index, point -> Offset(right * index / points.lastIndex, y(point.value)) }
            drawPath(smoothPath(offsets), CollogColors.greenNormal, style = stroke)
            drawCircle(CollogColors.gray300, dotRadius, offsets.last())
            drawCircle(CollogColors.greenNormal, 5.dp.toPx(), offsets.last())
        }
        Row(Modifier.fillMaxWidth()) {
            Text(first.label, style = CollogType.caption02Regular, color = CollogColors.gray600)
            Spacer(Modifier.weight(1f))
            Text(last.label, style = CollogType.caption02Regular, color = CollogColors.gray600)
        }
    }
}

/** 피그마 건강 피드백 상자(269:4934). 누르면 다음 통화에서 물어볼 내용으로 간다. */
@Composable
private fun FeedbackBox(feedback: HealthFeedback, lastCallText: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .background(CollogColors.gray100, RoundedCornerShape(Spacing.x3))
            .padding(Spacing.x4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
    ) {
        HealthCrossBadge()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("건강 피드백", style = CollogType.body01Semibold, color = CollogColors.gray800, modifier = Modifier.weight(1f))
                Icon(
                    painterResource(R.drawable.ic_more_vertical),
                    contentDescription = null,
                    tint = CollogColors.gray800,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(feedback.headline, style = CollogType.body01Semibold, color = CollogColors.gray900)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                Text("최근 기록", style = CollogType.body02Semibold, color = CollogColors.gray600)
                lastCallText?.let { Text("$it 통화", style = CollogType.body02Semibold, color = CollogColors.gray600) }
            }
        }
    }
}
