package live.collog.app.features.report.models

import java.math.BigDecimal
import java.math.RoundingMode
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.components.stat.StatNote
import live.collog.app.shared.services.api.CallSummaryDto

private fun analyzedSeconds(calls: List<CallSummaryDto>): Int =
    calls.filter { it.isAnalyzed }.sumOf { it.durationSec ?: 0 }

private fun minutes(seconds: Int): Int = BigDecimal(seconds / 60.0).setScale(0, RoundingMode.HALF_UP).toInt()

/**
 * 새 피그마 05·07의 통화 요약(분석된 통화·총 통화 시간·분당 되물음)을 통화 목록으로 계산해 넣는다.
 * 서버 리포트에는 총 통화 시간과 지난주 값이 없어서 앱이 이번 주·지난주 통화 목록에서 만든다.
 * - [previous]가 null(못 받음)이거나 지난주 분석 통화가 없으면 「지난주」 비교 글자는 뺀다.
 * - 분당 되물음은 분석된 통화가 1분 이상일 때만 계산한다. 아니면 그 줄을 비운다(화면이 숨긴다).
 */
fun WeeklyReport.withCallStats(calls: List<CallSummaryDto>, previous: List<CallSummaryDto>?): WeeklyReport {
    val countStat = summaryStats.firstOrNull() ?: return this
    val seconds = analyzedSeconds(calls)
    val previousCalls = previous?.filter { it.isAnalyzed }.orEmpty()
    val previousSeconds = analyzedSeconds(previous.orEmpty())

    val stats = mutableListOf(
        CallStat(
            countStat.label, countStat.value, countStat.unit,
            if (previousCalls.isEmpty()) null else StatNote("지난주 ${previousCalls.size}건"),
        ),
    )
    if (seconds > 0) {
        stats += CallStat(
            "총 통화 시간", "${minutes(seconds)}", "분",
            if (previousSeconds > 0) StatNote("지난주 ${minutes(previousSeconds)}분") else null,
        )
    }

    val repeatCount = repeatObservation.countText.toIntOrNull() ?: 0
    val perMinute = if (seconds >= 60) "분당 ${formatOneDecimal(repeatCount / (seconds / 60.0))}회" else ""

    return copy(
        summaryStats = stats,
        repeatObservation = repeatObservation.copy(perMinuteText = perMinute),
    )
}
