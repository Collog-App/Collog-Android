package live.collog.app.features.health.models

import androidx.health.connect.client.records.SleepSessionRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import live.collog.app.shared.services.api.HealthDayDto

/** 수면 단계 한 구간. [type]은 Health Connect `SleepSessionRecord.STAGE_TYPE_*` 값이다. */
data class SleepStageSpan(val start: Instant, val end: Instant, val type: Int)

data class SleepSession(val start: Instant, val end: Instant, val stages: List<SleepStageSpan> = emptyList())

/** 한 시각의 값(심박 bpm 등). */
data class TimedSample(val time: Instant, val value: Double)

data class BloodPressureReading(val time: Instant, val systolic: Double, val diastolic: Double)

data class BloodGlucoseReading(val time: Instant, val millimolesPerLiter: Double)

/** Health Connect 에서 읽어 옮긴 값. 걸음은 Health Connect 가 하루 단위로 합친 값(COUNT_TOTAL)이다. */
data class HealthRecords(
    val sleepSessions: List<SleepSession> = emptyList(),
    val stepsByDay: Map<LocalDate, Long> = emptyMap(),
    val restingHeartRates: List<TimedSample> = emptyList(),
    val heartRates: List<TimedSample> = emptyList(),
    val bloodPressures: List<BloodPressureReading> = emptyList(),
    val bloodGlucoses: List<BloodGlucoseReading> = emptyList(),
)

/**
 * 건강 앱 연결 약속(2026-11-08) 「동기화」 규칙으로 하루 요약을 만든다. 날짜는 모두 Asia/Seoul.
 * 서버 범위 검사를 벗어난 값은 그 칸만 null 로 둔다(한 칸 때문에 요청 전체가 422 로 거절되지 않게).
 * 혈압·혈당은 범위 안 측정값 중 그날 마지막 것을 고른다.
 */
object HealthDailyCalculator {
    val zone: ZoneId = ZoneId.of("Asia/Seoul")

    /** 동기화 때마다 다시 계산해 올리는 날 수(오늘 포함). */
    const val SYNC_DAYS = 14

    /** Health Connect `BloodGlucose` 와 같은 환산값(1 mg/dL = 1/18 mmol/L). */
    const val MG_PER_DL_PER_MMOL_PER_L = 18.0

    private val sleepDayBoundary: LocalTime = LocalTime.of(18, 0)

    /** 잠들지 않은 단계: 깸, 침대에서 깸, 침대 밖. */
    private val awakeStages = setOf(
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
    )

    fun syncDays(today: LocalDate): List<LocalDate> = (SYNC_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }

    fun dayOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** [day]의 수면으로 셀 세션이 끝나는 구간: D-1 18:00 이상, D 18:00 미만. */
    fun sleepWindow(day: LocalDate): Pair<Instant, Instant> =
        day.minusDays(1).atTime(sleepDayBoundary).atZone(zone).toInstant() to
            day.atTime(sleepDayBoundary).atZone(zone).toInstant()

    fun sessionsFor(day: LocalDate, sessions: List<SleepSession>): List<SleepSession> {
        val (start, end) = sleepWindow(day)
        return sessions.filter { !it.end.isBefore(start) && it.end.isBefore(end) }
    }

    /** 안정 시 심박이 없는 날의 수면 세션 구간. 그 구간의 심박 샘플만 읽으면 된다. */
    fun heartRateWindows(days: List<LocalDate>, records: HealthRecords): List<Pair<Instant, Instant>> =
        days.filter { day -> records.restingHeartRates.none { dayOf(it.time) == day } }
            .flatMap { day -> sessionsFor(day, records.sleepSessions).map { it.start to it.end } }
            .distinct()

    fun summaries(days: List<LocalDate>, records: HealthRecords): List<HealthDayDto> = days.map { summary(it, records) }

    fun summary(day: LocalDate, records: HealthRecords): HealthDayDto {
        val sessions = sessionsFor(day, records.sleepSessions)
        val pressure = records.bloodPressures.filter { dayOf(it.time) == day }.sortedByDescending { it.time }
            .firstNotNullOfOrNull { reading -> bloodPressure(reading.systolic, reading.diastolic) }
        val glucose = records.bloodGlucoses.filter { dayOf(it.time) == day }.sortedByDescending { it.time }
            .firstNotNullOfOrNull { reading -> milligramsPerDeciliter(reading.millimolesPerLiter).takeIf { it in 20.0..600.0 } }
        return HealthDayDto(
            date = day.toString(),
            sleepMinutes = sleepMinutes(sessions),
            steps = records.stepsByDay[day]?.takeIf { it in 0..100_000 }?.toInt(),
            restingHeartRate = restingHeartRate(day, sessions, records),
            bloodPressureSystolic = pressure?.first,
            bloodPressureDiastolic = pressure?.second,
            bloodGlucoseMgDl = glucose,
        )
    }

    /**
     * 세션들의 (시작~끝)을 합집합으로 모은 뒤 깨어 있던 단계를 뺀다. 여러 앱이 같은 밤을 겹쳐 적은 경우 두 번 세지 않고,
     * 한 앱이 깬 단계로 적은 구간이 단계 없는 다른 세션 때문에 다시 수면으로 세지지 않게 깬 구간은 마지막에 뺀다.
     * 세션이 없으면 null.
     */
    fun sleepMinutes(sessions: List<SleepSession>): Int? {
        if (sessions.isEmpty()) return null
        val asleep = union(sessions.filter { it.start.isBefore(it.end) }.map { it.start to it.end })
        val awake = union(sessions.flatMap(::awakeSpans))
        val seconds = minus(asleep, awake).sumOf { (start, end) -> Duration.between(start, end).seconds }
        return (seconds / 60).toInt().takeIf { it in 0..1440 }
    }

    fun milligramsPerDeciliter(millimolesPerLiter: Double): Double =
        BigDecimal(millimolesPerLiter * MG_PER_DL_PER_MMOL_PER_L).setScale(1, RoundingMode.HALF_UP).toDouble()

    /** 그날 안정 시 심박 기록의 평균, 없으면 그날 수면 세션 구간의 심박 샘플 평균. 반올림. */
    private fun restingHeartRate(day: LocalDate, sessions: List<SleepSession>, records: HealthRecords): Int? {
        val resting = records.restingHeartRates.filter { dayOf(it.time) == day }
        val values = resting.ifEmpty {
            records.heartRates.filter { sample ->
                sessions.any { !sample.time.isBefore(it.start) && !sample.time.isAfter(it.end) }
            }
        }
        if (values.isEmpty()) return null
        return Math.round(values.map { it.value }.average()).toInt().takeIf { it in 25..220 }
    }

    /** 수축기 50~260, 이완기 30~200, 이완기 < 수축기. 벗어나면 둘 다 null. */
    private fun bloodPressure(systolic: Double, diastolic: Double): Pair<Int, Int>? {
        val high = Math.round(systolic).toInt()
        val low = Math.round(diastolic).toInt()
        if (high !in 50..260 || low !in 30..200 || low >= high) return null
        return high to low
    }

    /** 세션 안의 깬 단계 구간(세션 밖으로 넘친 부분은 자른다). */
    private fun awakeSpans(session: SleepSession): List<Pair<Instant, Instant>> =
        session.stages.filter { it.type in awakeStages }.mapNotNull { stage ->
            val start = maxOf(stage.start, session.start)
            val end = minOf(stage.end, session.end)
            if (start.isBefore(end)) start to end else null
        }

    /** 정렬·병합된 [spans]에서 정렬·병합된 [holes]를 뺀 구간들. */
    private fun minus(spans: List<Pair<Instant, Instant>>, holes: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> =
        spans.flatMap { (spanStart, spanEnd) ->
            val pieces = mutableListOf<Pair<Instant, Instant>>()
            var cursor = spanStart
            for ((start, end) in holes) {
                if (!start.isBefore(spanEnd)) break
                if (cursor.isBefore(start)) pieces += cursor to start
                if (cursor.isBefore(end)) cursor = end
            }
            if (cursor.isBefore(spanEnd)) pieces += cursor to spanEnd
            pieces
        }

    private fun union(spans: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> {
        val merged = mutableListOf<Pair<Instant, Instant>>()
        for (span in spans.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && !span.first.isAfter(last.second)) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, span.second)
            } else {
                merged += span
            }
        }
        return merged
    }
}
