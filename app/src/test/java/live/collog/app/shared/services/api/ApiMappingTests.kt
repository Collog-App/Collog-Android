package live.collog.app.shared.services.api

import java.time.Instant
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.chart.from
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ApiFormat`·`TrendSeries` 변환 보강 테스트(iOS에는 없다).
 * 기대값 중 날짜 문자열은 Swift `Calendar(identifier: .gregorian)`/`DateFormatter(ko_KR)`를 swift 6.4에서 직접 돌려 얻었다.
 */
class ApiMappingTests {
    @Test
    fun weekTitleCountsSundayStartWeeksLikeSwiftGregorianCalendar() {
        // 2026-09-07(월)=2주, 09-01(화)=1주, 08-31(월)=6주, 02-28(토)=4주, 11-29(일)=5주, 2027-01-01=1주
        assertEquals("9월 2주", ApiFormat.weekTitle("2026-09-07"))
        assertEquals("9월 1주", ApiFormat.weekTitle("2026-09-01"))
        assertEquals("10월 1주", ApiFormat.weekTitle("2026-10-01"))
        assertEquals("8월 6주", ApiFormat.weekTitle("2026-08-31"))
        assertEquals("2월 4주", ApiFormat.weekTitle("2026-02-28"))
        assertEquals("11월 5주", ApiFormat.weekTitle("2026-11-29"))
        assertEquals("1월 1주", ApiFormat.weekTitle("2027-01-01"))
    }

    @Test
    fun weekTitleReturnsRawWhenNotADate() {
        assertEquals("not-a-date", ApiFormat.weekTitle("not-a-date"))
    }

    @Test
    fun koreanDateFormats() {
        val date = ApiFormat.parseIsoDate("2026-08-14")!!
        assertEquals("8/14", ApiFormat.shortDate(date))
        assertEquals("8월 14일 금요일", ApiFormat.longDate(date))
        assertEquals("2026-08-14", ApiFormat.isoDate(date))
        assertEquals("9/7 ~ 9/13", ApiFormat.shortRange("2026-09-07", "2026-09-13"))
        assertEquals("a ~ b", ApiFormat.shortRange("a", "b"))
    }

    @Test
    fun durationFormat() {
        assertEquals("45초", ApiFormat.duration(45))
        assertEquals("1분 0초", ApiFormat.duration(60))
        assertEquals("14분 27초", ApiFormat.duration(867))
    }

    @Test
    fun trendNeedsTwoMeasuredPoints() {
        val one = AcousticTrendDto("SPEECH_RATE", listOf(AcousticTrendPointDto("2026-09-12", 200.0), AcousticTrendPointDto("2026-09-13", null)))
        assertNull(TrendSeries.from(one, null))
    }

    @Test
    fun trendWithoutBaselineDerivesRangeFromValues() {
        val trend = AcousticTrendDto(
            "SPEECH_RATE",
            listOf(AcousticTrendPointDto("2026-09-12", 200.0), AcousticTrendPointDto("2026-09-13", 210.0)),
        )
        val series = TrendSeries.from(trend, null)!!

        assertEquals("말씀 속도", series.metricName)
        assertEquals("음절/분", series.unit)
        assertFalse(series.hasPersonalBaseline)
        // 여백 = max(10 * 0.2, 0.5) = 2
        assertEquals(202.0, series.normalRange.start, 1e-9)
        assertEquals(208.0, series.normalRange.endInclusive, 1e-9)
        assertEquals(listOf("9/12", "9/13"), series.points.map { it.label })
    }

    @Test
    fun trendWithReadyBaselineUsesMedianAndMad() {
        val trend = AcousticTrendDto(
            "PAUSE_RATIO",
            listOf(AcousticTrendPointDto("2026-09-12", 10.0), AcousticTrendPointDto("2026-09-13", 12.0)),
        )
        val baseline = BaselineDto("PAUSE_RATIO", "MORNING", "ROLLING", "READY", 10, 5, median = 11.0, mad = 0.6745)
        val series = TrendSeries.from(trend, baseline)!!

        assertTrue(series.hasPersonalBaseline)
        assertEquals(11.0 - 1.5, series.normalRange.start, 1e-9)
        assertEquals(11.0 + 1.5, series.normalRange.endInclusive, 1e-9)
        assertEquals(11.0, series.median, 1e-9)
    }

    @Test
    fun nearestPicksClosestPointByTime() {
        val series = TrendSeries.speechRateSample
        val target = series.points[2].date.plusSeconds(60)
        assertEquals(series.points[2], series.nearest(target))
        assertEquals(series.points.last(), series.nearest(Instant.now().plusSeconds(10 * 86_400)))
    }
}
