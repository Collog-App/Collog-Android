package live.collog.app.features.timeline.models

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import live.collog.app.features.health.models.HealthDailyCalculator
import live.collog.app.features.health.models.LifeRecord
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
    /** 가족이 리포트·타임라인 공유를 꺼 둬 서버가 막았다(403). 오류가 아니라 빈 상태로 보인다. */
    val reportNotShared: Boolean = false,
    val timelineNotShared: Boolean = false,
    /** 결제가 켜져 있고 보는 사람의 가족이 무료라 서버가 리포트를 막았다(402). 403(공유 꺼짐)과 따로 잠금 화면을 보인다. */
    val reportPremiumRequired: Boolean = false,
    /** 리포트 「생활 기록」. 못 받았거나(서버 미지원·공유 막힘) 타임라인 탭이면 null 이라 숨긴다. */
    val lifeRecord: LifeRecord? = null,
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

        /** 리포트 「생활 기록」의 오늘. 건강 요약 날짜는 서버와 같은 Asia/Seoul 이다. 테스트가 고정할 때 바꾼다. */
        internal var healthToday: () -> LocalDate = { LocalDate.now(HealthDailyCalculator.zone) }

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
