package live.collog.app.features.health

import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import live.collog.app.features.health.models.BloodGlucoseReading
import live.collog.app.features.health.models.BloodPressureReading
import live.collog.app.features.health.models.HealthDailyCalculator
import live.collog.app.features.health.models.HealthRecords
import live.collog.app.features.health.models.SleepSession
import live.collog.app.features.health.models.SleepStageSpan
import live.collog.app.features.health.models.TimedSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 건강 연결 약속(2026-11-08) 「동기화」 규칙. 시각은 모두 서울 기준으로 적는다. */
class HealthDailyCalculatorTests {
    private val day = LocalDate.of(2026, 11, 7)

    private fun seoul(text: String): Instant =
        LocalDateTime.parse(text).atZone(HealthDailyCalculator.zone).toInstant()

    private fun session(start: String, end: String, vararg stages: SleepStageSpan) =
        SleepSession(seoul(start), seoul(end), stages.toList())

    private fun stage(start: String, end: String, type: Int) = SleepStageSpan(seoul(start), seoul(end), type)

    private fun summary(records: HealthRecords) = HealthDailyCalculator.summary(day, records)

    @Test
    fun syncDaysAreTheLastFourteenDaysEndingToday() {
        val days = HealthDailyCalculator.syncDays(day)
        assertEquals(14, days.size)
        assertEquals(LocalDate.of(2026, 10, 25), days.first())
        assertEquals(day, days.last())
    }

    @Test
    fun sleepCountsSessionsEndingFromSixPmTheDayBeforeUntilSixPm() {
        val records = HealthRecords(
            sleepSessions = listOf(
                // 11-06 18:00 정각에 끝남: 11-07 에 든다(30분)
                session("2026-11-06T17:30", "2026-11-06T18:00"),
                // 밤잠: 11-07 아침에 끝남(7시간)
                session("2026-11-06T23:00", "2026-11-07T06:00"),
                // 11-07 18:00 정각에 끝남: 11-08 로 넘어간다
                session("2026-11-07T17:00", "2026-11-07T18:00"),
                // 11-06 17:59 에 끝남: 11-06 의 수면
                session("2026-11-06T16:00", "2026-11-06T17:59"),
            ),
        )
        assertEquals(30 + 7 * 60, summary(records).sleepMinutes)
        assertEquals(60, HealthDailyCalculator.summary(day.plusDays(1), records).sleepMinutes)
    }

    @Test
    fun awakeStagesAreNotCountedAsSleep() {
        val records = HealthRecords(
            sleepSessions = listOf(
                session(
                    "2026-11-06T23:00", "2026-11-07T07:00",
                    stage("2026-11-06T23:00", "2026-11-07T01:00", SleepSessionRecord.STAGE_TYPE_LIGHT),
                    stage("2026-11-07T01:00", "2026-11-07T01:30", SleepSessionRecord.STAGE_TYPE_AWAKE),
                    stage("2026-11-07T01:30", "2026-11-07T05:00", SleepSessionRecord.STAGE_TYPE_DEEP),
                    stage("2026-11-07T05:00", "2026-11-07T05:20", SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
                    stage("2026-11-07T05:20", "2026-11-07T06:30", SleepSessionRecord.STAGE_TYPE_REM),
                    stage("2026-11-07T06:30", "2026-11-07T07:00", SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
                ),
            ),
        )
        // 8시간 - 깸 30분 - 침대에서 깸 20분 - 침대 밖 30분
        assertEquals(8 * 60 - 30 - 20 - 30, summary(records).sleepMinutes)
    }

    @Test
    fun overlappingSessionsFromTwoAppsAreCountedOnce() {
        val records = HealthRecords(
            sleepSessions = listOf(
                session("2026-11-06T23:00", "2026-11-07T06:00"),
                session("2026-11-06T23:30", "2026-11-07T06:30"),
            ),
        )
        assertEquals(7 * 60 + 30, summary(records).sleepMinutes)
    }

    @Test
    fun awakeStageOfOneAppIsNotFilledBackInByAnotherAppsSessionWithoutStages() {
        val records = HealthRecords(
            sleepSessions = listOf(
                // A: 단계가 있고 01:00~01:30 깸
                session(
                    "2026-11-06T23:00", "2026-11-07T07:00",
                    stage("2026-11-06T23:00", "2026-11-07T01:00", SleepSessionRecord.STAGE_TYPE_LIGHT),
                    stage("2026-11-07T01:00", "2026-11-07T01:30", SleepSessionRecord.STAGE_TYPE_AWAKE),
                    stage("2026-11-07T01:30", "2026-11-07T07:00", SleepSessionRecord.STAGE_TYPE_DEEP),
                ),
                // B: 같은 밤, 단계 없음
                session("2026-11-06T23:00", "2026-11-07T07:00"),
            ),
        )
        assertEquals(8 * 60 - 30, summary(records).sleepMinutes)
    }

    @Test
    fun noSessionMeansNoSleepValue() {
        assertNull(summary(HealthRecords()).sleepMinutes)
        assertFalse(summary(HealthRecords()).hasAnyValue)
    }

    @Test
    fun restingHeartRateRecordsAreAveragedAndRounded() {
        val records = HealthRecords(
            restingHeartRates = listOf(
                TimedSample(seoul("2026-11-07T07:00"), 63.0),
                TimedSample(seoul("2026-11-07T21:00"), 64.0),
                // 다른 날 기록은 섞지 않는다
                TimedSample(seoul("2026-11-06T23:59"), 90.0),
            ),
            heartRates = listOf(TimedSample(seoul("2026-11-07T03:00"), 50.0)),
            sleepSessions = listOf(session("2026-11-06T23:00", "2026-11-07T06:00")),
        )
        assertEquals(64, summary(records).restingHeartRate) // 63.5 반올림
    }

    @Test
    fun withoutRestingRecordsSleepHeartRateSamplesAreUsed() {
        val records = HealthRecords(
            sleepSessions = listOf(session("2026-11-06T23:00", "2026-11-07T06:00")),
            heartRates = listOf(
                TimedSample(seoul("2026-11-06T23:00"), 58.0),
                TimedSample(seoul("2026-11-07T03:00"), 61.0),
                TimedSample(seoul("2026-11-07T06:00"), 60.0),
                // 수면 구간 밖 샘플은 뺀다
                TimedSample(seoul("2026-11-07T12:00"), 110.0),
            ),
        )
        assertEquals(60, summary(records).restingHeartRate) // (58+61+60)/3 = 59.67
        assertEquals(
            listOf(seoul("2026-11-06T23:00") to seoul("2026-11-07T06:00")),
            HealthDailyCalculator.heartRateWindows(listOf(day), records),
        )
    }

    @Test
    fun noHeartRateAtAllIsNull() {
        val records = HealthRecords(
            sleepSessions = listOf(session("2026-11-06T23:00", "2026-11-07T06:00")),
            heartRates = listOf(TimedSample(seoul("2026-11-07T12:00"), 80.0)),
        )
        assertNull(summary(records).restingHeartRate)
    }

    @Test
    fun heartRateWindowsSkipDaysThatHaveRestingRecords() {
        val records = HealthRecords(
            sleepSessions = listOf(session("2026-11-06T23:00", "2026-11-07T06:00")),
            restingHeartRates = listOf(TimedSample(seoul("2026-11-07T07:00"), 63.0)),
        )
        assertEquals(emptyList<Pair<Instant, Instant>>(), HealthDailyCalculator.heartRateWindows(listOf(day), records))
    }

    @Test
    fun bloodGlucoseIsConvertedFromMillimolesToMilligrams() {
        assertEquals(112.0, HealthDailyCalculator.milligramsPerDeciliter(6.2222222), 0.0)
        assertEquals(99.0, HealthDailyCalculator.milligramsPerDeciliter(5.5), 0.0)
        val records = HealthRecords(
            bloodGlucoses = listOf(
                BloodGlucoseReading(seoul("2026-11-07T08:00"), 5.5),
                BloodGlucoseReading(seoul("2026-11-07T20:00"), 6.2),
            ),
        )
        assertEquals(111.6, summary(records).bloodGlucoseMgDl!!, 0.0) // 그날 마지막 측정
    }

    @Test
    fun bloodPressureIsTheLastReadingOfTheDay() {
        val records = HealthRecords(
            bloodPressures = listOf(
                BloodPressureReading(seoul("2026-11-07T21:00"), 128.4, 81.6),
                BloodPressureReading(seoul("2026-11-07T08:00"), 140.0, 90.0),
                BloodPressureReading(seoul("2026-11-08T00:10"), 150.0, 95.0),
            ),
        )
        val result = summary(records)
        assertEquals(128, result.bloodPressureSystolic)
        assertEquals(82, result.bloodPressureDiastolic)
    }

    @Test
    fun valuesOutsideTheServerRangesAreDropped() {
        val records = HealthRecords(
            stepsByDay = mapOf(day to 150_000L),
            restingHeartRates = listOf(TimedSample(seoul("2026-11-07T07:00"), 240.0)),
            bloodPressures = listOf(BloodPressureReading(seoul("2026-11-07T08:00"), 80.0, 90.0)),
            bloodGlucoses = listOf(BloodGlucoseReading(seoul("2026-11-07T08:00"), 40.0)),
        )
        val result = summary(records)
        assertNull(result.steps)
        assertNull(result.restingHeartRate)
        assertNull(result.bloodPressureSystolic)
        assertNull(result.bloodPressureDiastolic)
        assertNull(result.bloodGlucoseMgDl)
    }

    @Test
    fun outOfRangeLastReadingFallsBackToTheLastReadingInRange() {
        val records = HealthRecords(
            bloodPressures = listOf(
                BloodPressureReading(seoul("2026-11-07T08:00"), 140.0, 90.0),
                BloodPressureReading(seoul("2026-11-07T12:00"), 132.0, 84.0),
                // 그날 마지막이지만 이완기가 수축기보다 높아 서버가 받지 않는다
                BloodPressureReading(seoul("2026-11-07T21:00"), 80.0, 90.0),
            ),
            bloodGlucoses = listOf(
                BloodGlucoseReading(seoul("2026-11-07T08:00"), 5.5),
                // 그날 마지막이지만 720 mg/dL 라 범위 밖
                BloodGlucoseReading(seoul("2026-11-07T20:00"), 40.0),
            ),
        )
        val result = summary(records)
        assertEquals(132, result.bloodPressureSystolic)
        assertEquals(84, result.bloodPressureDiastolic)
        assertEquals(99.0, result.bloodGlucoseMgDl!!, 0.0)
    }

    @Test
    fun stepsComeFromTheDailyTotal() {
        val result = summary(HealthRecords(stepsByDay = mapOf(day to 3210L, day.minusDays(1) to 99L)))
        assertEquals(3210, result.steps)
        assertEquals("2026-11-07", result.date)
    }
}
