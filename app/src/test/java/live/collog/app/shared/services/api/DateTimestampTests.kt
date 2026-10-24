package live.collog.app.shared.services.api

import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `DateTimestampTests` (`Date.fromCollogTimestamp` → `CollogTimestamp.parse`). */
class DateTimestampTests {
    private fun seconds(timestamp: String): Double {
        val parsed = CollogTimestamp.parse(timestamp)
        assertNotNull("파싱 실패: $timestamp", parsed)
        return parsed!!.epochSecond + parsed.nano / 1e9
    }

    @Test
    fun wholeSecondsPreserveTimeZone() {
        for (timestamp in listOf(
            "1970-01-01T00:00:00Z",
            "1970-01-01T00:00:00",
            "1970-01-01T09:00:00+09:00",
            "1969-12-31T19:00:00-05:00",
        )) {
            assertEquals(timestamp, 0.0, seconds(timestamp), 0.0)
        }
    }

    @Test
    fun fractionalSecondsPreserveTimeZone() {
        for (timestamp in listOf(
            "1970-01-01T00:00:00.125Z",
            "1970-01-01T00:00:00.125",
            "1970-01-01T09:00:00.125+09:00",
            "1969-12-31T19:00:00.125-05:00",
        )) {
            assertTrue(timestamp, abs(seconds(timestamp) - 0.125) < 0.001)
        }
    }

    @Test
    fun invalidTimestampReturnsNull() {
        for (timestamp in listOf("", "invalid", "2026-09-14", "2026-09-14Tinvalid")) {
            assertNull(timestamp, CollogTimestamp.parse(timestamp))
        }
    }

    @Test
    fun parsesOutsideMainThread() = runBlocking {
        val parsed = withContext(Dispatchers.Default) { CollogTimestamp.parse("1970-01-01T09:00:00.125+09:00") }
        assertNotNull(parsed)
        assertTrue(abs(parsed!!.epochSecond + parsed.nano / 1e9 - 0.125) < 0.001)
    }
}
