package live.collog.app.features.timeline

import java.time.LocalDate
import live.collog.app.features.timeline.models.CallTimelineEntry
import live.collog.app.features.timeline.models.TimelineWeekPage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `TimelineWeekPage`에는 전용 테스트가 없다. 아래 기대값은 이 맥에서 Swift로 같은 식을 돌려 얻은 출력이다. */
class TimelineWeekPageTests {
    private val original = TimelineWeekPage.today

    @After
    fun restoreClock() {
        TimelineWeekPage.today = original
    }

    // 오늘|offset|anchor|start|end|title|rangeText|timelineRangeText|견본 통화 날짜
    private val iosOutput = """
        2026-10-24|0|2026-10-24|2026-10-19|2026-10-25|10월 4주|10/19 ~ 10/25|19일부터 25일|10월 20일 화요일
        2026-10-24|-1|2026-10-17|2026-10-12|2026-10-18|10월 3주|10/12 ~ 10/18|12일부터 18일|10월 14일 수요일
        2026-10-24|-2|2026-10-10|2026-10-05|2026-10-11|10월 2주|10/5 ~ 10/11|5일부터 11일|10월 8일 목요일
        2026-10-24|-3|2026-10-03|2026-09-28|2026-10-04|9월 5주|9/28 ~ 10/4|28일부터 4일|10월 2일 금요일
        2026-10-24|-5|2026-09-19|2026-09-14|2026-09-20|9월 3주|9/14 ~ 9/20|14일부터 20일|9월 16일 수요일
        2026-10-24|-25|2026-05-02|2026-04-27|2026-05-03|4월 5주|4/27 ~ 5/3|27일부터 3일|4월 29일 수요일
        2026-11-02|0|2026-11-02|2026-11-02|2026-11-08|11월 2주|11/2 ~ 11/8|2일부터 8일|11월 3일 화요일
        2026-11-02|-1|2026-10-26|2026-10-26|2026-11-01|10월 5주|10/26 ~ 11/1|26일부터 1일|10월 28일 수요일
        2026-11-02|-2|2026-10-19|2026-10-19|2026-10-25|10월 4주|10/19 ~ 10/25|19일부터 25일|10월 22일 목요일
        2026-11-02|-3|2026-10-12|2026-10-12|2026-10-18|10월 3주|10/12 ~ 10/18|12일부터 18일|10월 16일 금요일
        2026-11-02|-5|2026-09-28|2026-09-28|2026-10-04|9월 5주|9/28 ~ 10/4|28일부터 4일|9월 30일 수요일
        2026-11-02|-25|2026-05-11|2026-05-11|2026-05-17|5월 3주|5/11 ~ 5/17|11일부터 17일|5월 13일 수요일
        2026-03-01|0|2026-03-01|2026-02-23|2026-03-01|2월 5주|2/23 ~ 3/1|23일부터 1일|2월 24일 화요일
        2026-03-01|-1|2026-02-22|2026-02-16|2026-02-22|2월 4주|2/16 ~ 2/22|16일부터 22일|2월 18일 수요일
        2026-03-01|-2|2026-02-15|2026-02-09|2026-02-15|2월 3주|2/9 ~ 2/15|9일부터 15일|2월 12일 목요일
        2026-03-01|-3|2026-02-08|2026-02-02|2026-02-08|2월 2주|2/2 ~ 2/8|2일부터 8일|2월 6일 금요일
        2026-03-01|-5|2026-01-25|2026-01-19|2026-01-25|1월 4주|1/19 ~ 1/25|19일부터 25일|1월 21일 수요일
        2026-03-01|-25|2025-09-07|2025-09-01|2025-09-07|9월 1주|9/1 ~ 9/7|1일부터 7일|9월 3일 수요일
        2026-12-31|0|2026-12-31|2026-12-28|2027-01-03|12월 5주|12/28 ~ 1/3|28일부터 3일|12월 29일 화요일
        2026-12-31|-1|2026-12-24|2026-12-21|2026-12-27|12월 4주|12/21 ~ 12/27|21일부터 27일|12월 23일 수요일
        2026-12-31|-2|2026-12-17|2026-12-14|2026-12-20|12월 3주|12/14 ~ 12/20|14일부터 20일|12월 17일 목요일
        2026-12-31|-3|2026-12-10|2026-12-07|2026-12-13|12월 2주|12/7 ~ 12/13|7일부터 13일|12월 11일 금요일
        2026-12-31|-5|2026-11-26|2026-11-23|2026-11-29|11월 5주|11/23 ~ 11/29|23일부터 29일|11월 25일 수요일
        2026-12-31|-25|2026-07-09|2026-07-06|2026-07-12|7월 2주|7/6 ~ 7/12|6일부터 12일|7월 8일 수요일
        2027-01-03|0|2027-01-03|2026-12-28|2027-01-03|12월 5주|12/28 ~ 1/3|28일부터 3일|12월 29일 화요일
        2027-01-03|-1|2026-12-27|2026-12-21|2026-12-27|12월 4주|12/21 ~ 12/27|21일부터 27일|12월 23일 수요일
        2027-01-03|-2|2026-12-20|2026-12-14|2026-12-20|12월 3주|12/14 ~ 12/20|14일부터 20일|12월 17일 목요일
        2027-01-03|-3|2026-12-13|2026-12-07|2026-12-13|12월 2주|12/7 ~ 12/13|7일부터 13일|12월 11일 금요일
        2027-01-03|-5|2026-11-29|2026-11-23|2026-11-29|11월 5주|11/23 ~ 11/29|23일부터 29일|11월 25일 수요일
        2027-01-03|-25|2026-07-12|2026-07-06|2026-07-12|7월 2주|7/6 ~ 7/12|6일부터 12일|7월 8일 수요일
        2026-02-28|0|2026-02-28|2026-02-23|2026-03-01|2월 5주|2/23 ~ 3/1|23일부터 1일|2월 24일 화요일
        2026-02-28|-1|2026-02-21|2026-02-16|2026-02-22|2월 4주|2/16 ~ 2/22|16일부터 22일|2월 18일 수요일
        2026-02-28|-2|2026-02-14|2026-02-09|2026-02-15|2월 3주|2/9 ~ 2/15|9일부터 15일|2월 12일 목요일
        2026-02-28|-3|2026-02-07|2026-02-02|2026-02-08|2월 2주|2/2 ~ 2/8|2일부터 8일|2월 6일 금요일
        2026-02-28|-5|2026-01-24|2026-01-19|2026-01-25|1월 4주|1/19 ~ 1/25|19일부터 25일|1월 21일 수요일
        2026-02-28|-25|2025-09-06|2025-09-01|2025-09-07|9월 1주|9/1 ~ 9/7|1일부터 7일|9월 3일 수요일
    """.trimIndent().lines()

    @Test
    fun weekBoundsAndTitlesMatchIos() {
        assertEquals(36, iosOutput.size)
        for (line in iosOutput) {
            val parts = line.split("|")
            val (today, offset, anchor) = parts
            val start = parts[3]
            val end = parts[4]
            val title = parts[5]
            val range = parts[6]
            val timelineRange = parts[7]
            TimelineWeekPage.today = { LocalDate.parse(today) }
            val offsetValue = offset.toInt()
            val bounds = TimelineWeekPage.bounds(offsetValue)
            val page = TimelineWeekPage(offsetValue)

            assertEquals(line, anchor, bounds.anchor.toString())
            assertEquals(line, start, bounds.start.toString())
            assertEquals(line, end, bounds.end.toString())
            assertEquals(line, title, page.title)
            assertEquals(line, range, page.rangeText)
            assertEquals(line, timelineRange, page.timelineRangeText)
        }
    }

    @Test
    fun sampleEntryDateMatchesIos() {
        for (line in iosOutput) {
            val parts = line.split("|")
            TimelineWeekPage.today = { LocalDate.parse(parts[0]) }
            assertEquals(line, parts[8], CallTimelineEntry.sample(parts[1].toInt()).dateText)
        }
    }

    @Test
    fun weekStartsOnMondayAndSpansSevenDays() {
        TimelineWeekPage.today = { LocalDate.parse("2026-10-24") }
        for (offset in -25..0) {
            val bounds = TimelineWeekPage.bounds(offset)
            assertEquals(java.time.DayOfWeek.MONDAY, bounds.start.dayOfWeek)
            assertEquals(bounds.start.plusDays(6), bounds.end)
            assertTrue(!bounds.anchor.isBefore(bounds.start) && !bounds.anchor.isAfter(bounds.end))
        }
    }

    @Test
    fun monthAndWeekTitleParts() {
        TimelineWeekPage.today = { LocalDate.parse("2026-10-24") }
        val page = TimelineWeekPage(-3)
        assertEquals(9, page.month)
        assertEquals("5주", page.weekTitle)
        assertEquals("9월 5주", page.title)
        assertEquals(-3, page.id)
    }
}
