package live.collog.app.shared.services.api

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * iOS `APIFormat`. `Date`는 [Instant]로 다룬다(날짜만 있는 문자열은 이 기기 시간대의 자정).
 * 주 번호는 iOS `Calendar(identifier: .gregorian)`와 같이 일요일 시작, 첫 주 최소 1일로 센다.
 */
object ApiFormat {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val shortDateFormatter = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
    private val longDateFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
    private val gregorianWeeks = WeekFields.of(DayOfWeek.SUNDAY, 1)

    /** iOS `isoDate.date(from:)`: `yyyy-MM-dd`. 형식이 다르면 null. */
    fun parseIsoDate(raw: String): Instant? = try {
        LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    /** iOS `isoDate.string(from:)`. */
    fun isoDate(date: Instant): String = DateTimeFormatter.ISO_LOCAL_DATE.format(date.atZone(zone))

    /** iOS `shortDate.string(from:)`: `M/d`. */
    fun shortDate(date: Instant): String = shortDateFormatter.format(date.atZone(zone))

    /** iOS `longDate.string(from:)`: `M월 d일 EEEE`. */
    fun longDate(date: Instant): String = longDateFormatter.format(date.atZone(zone))

    fun duration(seconds: Int): String =
        if (seconds >= 60) "${seconds / 60}분 ${seconds % 60}초" else "${seconds}초"

    fun weekTitle(raw: String): String {
        val date = parseIsoDate(raw) ?: return raw
        val day = date.atZone(zone).toLocalDate()
        return "${day.monthValue}월 ${day.get(gregorianWeeks.weekOfMonth())}주"
    }

    fun shortRange(from: String, to: String): String {
        val start = parseIsoDate(from)
        val end = parseIsoDate(to)
        if (start == null || end == null) return "$from ~ $to"
        return "${shortDate(start)} ~ ${shortDate(end)}"
    }
}
