package live.collog.app.features.health.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import live.collog.app.features.health.models.LifeRecord
import live.collog.app.features.health.models.LifeRecordDay
import live.collog.app.features.health.models.LifeRecordLatest
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

private val BarAreaHeight = 64.dp

/**
 * 리포트 「생활 기록」(피그마 없음, 「디자인」 카드 모양): 최근 7일 수면·걸음 막대와 최근 심박·혈압·혈당.
 * 연결 안 된 사람이면 안내만, 내 리포트면 [onConnect]로 연결 흐름을 연다.
 */
@Composable
fun LifeRecordCard(record: LifeRecord, onConnect: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.specCard()) {
        Row(Modifier.fillMaxWidth()) {
            Text("생활 기록", Modifier.weight(1f), style = specText(Pretendard.Regular, 12f, SpecColors.muted))
            if (record.connected) Text(record.rangeText, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        }
        Spacer(Modifier.height(18.dp))

        if (!record.connected) {
            Text("아직 건강 앱이 연결되지 않았어요", style = specText(Pretendard.Medium, 14f, SpecColors.sub))
            if (onConnect != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "연결하기",
                    style = specText(Pretendard.Bold, 14f, SpecColors.green),
                    modifier = Modifier
                        .background(SpecColors.greenSurface, RoundedCornerShape(10.dp))
                        .pressable(onClick = onConnect)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            return@Column
        }

        DayBars("수면 시간", record.sleepAverageText?.let { "평균 $it" }, record.sleep) { LifeRecord.sleepText(it.toInt()) }
        Spacer(Modifier.height(22.dp))
        DayBars("걸음", record.stepsAverageText?.let { "평균 $it" }, record.steps) { "${it.toInt()}걸음" }

        Spacer(Modifier.height(20.dp))
        SpecDivider()
        Spacer(Modifier.height(10.dp))
        LatestRow("안정 시 심박", record.restingHeartRate)
        LatestRow("혈압", record.bloodPressure)
        LatestRow("혈당", record.bloodGlucose)

        Spacer(Modifier.height(14.dp))
        record.lastSyncedText?.let {
            Text("마지막 동기화 $it", style = specText(Pretendard.Regular, 12f, SpecColors.muted))
            Spacer(Modifier.height(6.dp))
        }
        Text(record.disclaimer, style = specText(Pretendard.Regular, 11f, SpecColors.muted))
    }
}

/** 7일 막대. 기록이 없는 날은 바닥에 옅은 자리만 둔다. */
@Composable
private fun DayBars(title: String, summary: String?, days: List<LifeRecordDay>, valueText: (Double) -> String) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, Modifier.weight(1f), style = specText(Pretendard.Bold, 15f))
            Text(summary ?: "기록이 없어요", style = specText(Pretendard.Medium, 13f, SpecColors.sub))
        }
        Spacer(Modifier.height(14.dp))
        val maximum = days.mapNotNull { it.value }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                val value = day.value
                Column(
                    Modifier
                        .weight(1f)
                        .clearAndSetSemantics {
                            contentDescription = "${day.label} ${value?.let(valueText) ?: "기록 없음"}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(BarAreaHeight), contentAlignment = Alignment.BottomCenter) {
                        val height = if (value == null) 4.dp else maxOf(BarAreaHeight * (value / maximum).toFloat(), 4.dp)
                        Box(
                            Modifier
                                .width(14.dp)
                                .height(height)
                                .background(if (value == null) SpecColors.track else SpecColors.chartGreen, RoundedCornerShape(4.dp)),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(day.label, style = specText(Pretendard.Regular, 11f, SpecColors.muted))
                }
            }
        }
    }
}

@Composable
private fun LatestRow(label: String, latest: LifeRecordLatest?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, Modifier.weight(1f), style = specText(Pretendard.Medium, 14f, SpecColors.sub))
        if (latest == null) {
            Text("기록 없음", style = specText(Pretendard.Regular, 13f, SpecColors.muted))
        } else {
            Text(latest.value, style = specText(Pretendard.Bold, 16f))
            Text(latest.unit, style = specText(Pretendard.Regular, 12f, SpecColors.sub))
            Spacer(Modifier.width(6.dp))
            Text(latest.dateText, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        }
    }
}
