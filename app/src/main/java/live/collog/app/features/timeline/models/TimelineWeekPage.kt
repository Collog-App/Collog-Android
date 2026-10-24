package live.collog.app.features.timeline.models

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import live.collog.app.features.report.models.WeeklyReport
import live.collog.app.shared.services.api.ApiFormat
import live.collog.app.shared.services.api.CallSummaryDto

/** iOS `TimelineWeekPage.bounds`의 튜플. */
data class WeekBounds(val anchor: LocalDate, val start: LocalDate, val end: LocalDate)

/** iOS `TimelineWeekPage`: 오늘에서 [offset]주 떨어진 한 주(월요일 시작)의 리포트와 통화 기록. */
data class TimelineWeekPage(
    val offset: Int,
    val entries: List<CallTimelineEntry> = emptyList(),
    val calls: List<CallSummaryDto> = emptyList(),
    val report: WeeklyReport = WeeklyReport.empty,
    val isLoaded: Boolean = false,
    val reportError: String? = null,
    val timelineError: String? = null,
) {
    val id: Int get() = offset

    private val startDate: LocalDate get() = bounds(offset).start

    val month: Int get() = startDate.monthValue

    val weekTitle: String get() = "${startDate.get(weekOfMonth)}주"

    val title: String get() = "${month}월 $weekTitle"

    val rangeText: String
        get() {
            val bounds = bounds(offset)
            return ApiFormat.shortRange(isoDate.format(bounds.start), isoDate.format(bounds.end))
        }

    val timelineRangeText: String
        get() {
            val bounds = bounds(offset)
            return "${bounds.start.dayOfMonth}일부터 ${bounds.end.dayOfMonth}일"
        }

    companion object {
        /** 테스트가 "오늘"을 고정할 때 바꾼다. */
        internal var today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) }

        internal val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

        // iOS `Calendar(identifier: .gregorian)` + `firstWeekday = 2`: 월요일 시작, 첫 주 최소 1일
        private val weekOfMonth = WeekFields.of(DayOfWeek.MONDAY, 1).weekOfMonth()

        internal fun instant(date: LocalDate): Instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant()

        fun bounds(offset: Int): WeekBounds {
            val anchor = today().plusWeeks(offset.toLong())
            val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return WeekBounds(anchor, start, start.plusDays(6))
        }
    }
}
