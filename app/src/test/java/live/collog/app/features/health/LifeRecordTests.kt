package live.collog.app.features.health

import java.time.LocalDate
import live.collog.app.features.health.models.LifeRecord
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.HealthDayDto
import live.collog.app.shared.services.api.ParentHealthDailyDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 리포트 「생활 기록」 값. 서버 응답 모양은 건강 연결 약속(2026-11-08) 그대로. */
class LifeRecordTests {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun serverResponseDecodesAndFillsTheLastSevenDays() {
        val dto = CollogJson.decodeFromString(
            ParentHealthDailyDto.serializer(),
            """
            { "parentId": "mom", "connected": true, "platform": "HEALTH_CONNECT", "lastSyncedDay": "2026-11-07",
              "days": [
                { "date": "2026-11-01", "sleepMinutes": 412, "steps": 5120, "restingHeartRate": 63,
                  "bloodPressureSystolic": null, "bloodPressureDiastolic": null, "bloodGlucoseMgDl": null },
                { "date": "2026-11-07", "sleepMinutes": 370, "steps": 3210, "restingHeartRate": null,
                  "bloodPressureSystolic": 128, "bloodPressureDiastolic": 82, "bloodGlucoseMgDl": 112.0 },
                { "date": "2026-10-20", "bloodGlucoseMgDl": 101.5 }
              ],
              "disclaimer": "이 정보는 의료 진단이 아니며 건강 변화 참고용입니다." }
            """.trimIndent(),
        )
        val record = LifeRecord.from(dto, end = today, today = today)

        assertEquals("최근 7일", record.rangeText)
        assertEquals(LocalDate.of(2026, 11, 2), record.sleep.first().date)
        assertEquals(today, record.sleep.last().date)
        assertEquals("일", record.sleep.last().label)
        // 11-01 은 7일 밖이라 막대에는 없다
        assertEquals(listOf(null, null, null, null, null, 370.0, null), record.sleep.map { it.value })
        assertEquals("6시간 10분", record.sleepAverageText)
        assertEquals("3,210걸음", record.stepsAverageText)
        // 최근 값은 7일 밖이어도 받은 기록 중 가장 최근 것
        assertEquals("63", record.restingHeartRate!!.value)
        assertEquals("11월 1일", record.restingHeartRate!!.dateText)
        assertEquals("128/82", record.bloodPressure!!.value)
        assertEquals("112", record.bloodGlucose!!.value)
        assertEquals("mg/dL", record.bloodGlucose!!.unit)
        assertEquals("11월 7일", record.bloodGlucose!!.dateText)
        assertEquals("11월 7일", record.lastSyncedText)
    }

    @Test
    fun notConnectedHasNoValues() {
        val record = LifeRecord.from(ParentHealthDailyDto(parentId = "mom", connected = false), end = today, today = today)

        assertFalse(record.connected)
        assertNull(record.sleepAverageText)
        assertNull(record.stepsAverageText)
        assertNull(record.restingHeartRate)
        assertNull(record.lastSyncedText)
        assertEquals(LifeRecord.DEFAULT_DISCLAIMER, record.disclaimer)
    }

    @Test
    fun pastWeekShowsItsOwnRangeAndIgnoresLaterDays() {
        val end = LocalDate.of(2026, 11, 1)
        val dto = ParentHealthDailyDto(
            connected = true,
            days = listOf(
                HealthDayDto("2026-10-30", bloodGlucoseMgDl = 101.5),
                HealthDayDto("2026-11-03", bloodGlucoseMgDl = 140.0),
            ),
        )
        val record = LifeRecord.from(dto, end = end, today = today)

        assertEquals("10/26 ~ 11/1", record.rangeText)
        assertEquals("101.5", record.bloodGlucose!!.value)
        assertEquals("10월 30일", record.bloodGlucose!!.dateText)
    }

    @Test
    fun sleepTextReadsNaturally() {
        assertEquals("45분", LifeRecord.sleepText(45))
        assertEquals("7시간", LifeRecord.sleepText(420))
        assertEquals("6시간 10분", LifeRecord.sleepText(370))
    }
}
