package live.collog.app.features.health.models

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt
import live.collog.app.shared.services.api.HealthDayDto
import live.collog.app.shared.services.api.ParentHealthDailyDto

/** 막대 하나: 그날 값이 없으면 [value]가 null. */
data class LifeRecordDay(val date: LocalDate, val label: String, val value: Double?)

/** 최근 값과 그 날짜(「64 회/분 · 11월 7일」). */
data class LifeRecordLatest(val value: String, val unit: String, val dateText: String)

/** 리포트 「생활 기록」 카드에 보이는 값. 진단 표현 없이 기록된 값만 보여 준다. */
data class LifeRecord(
    val connected: Boolean,
    val rangeText: String,
    val sleep: List<LifeRecordDay>,
    val sleepAverageText: String?,
    val steps: List<LifeRecordDay>,
    val stepsAverageText: String?,
    val restingHeartRate: LifeRecordLatest?,
    val bloodPressure: LifeRecordLatest?,
    val bloodGlucose: LifeRecordLatest?,
    val lastSyncedText: String?,
    val disclaimer: String,
) {
    companion object {
        const val DEFAULT_DISCLAIMER = "이 정보는 의료 진단이 아니며 건강 변화 참고용입니다."

        private val weekday = DateTimeFormatter.ofPattern("E", Locale.KOREAN)
        private val shortDate = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)

        /** [end]까지 7일을 막대로, 그 앞 기록까지 포함해 가장 최근 심박·혈압·혈당을 보여 준다. */
        fun from(dto: ParentHealthDailyDto, end: LocalDate, today: LocalDate): LifeRecord {
            val byDate = dto.days.mapNotNull { day -> parseDate(day.date)?.let { it to day } }
                .filter { (date, _) -> !date.isAfter(end) }
                .toMap()
            val week = (6 downTo 0).map { end.minusDays(it.toLong()) }
            val sleep = week.map { LifeRecordDay(it, weekday.format(it), byDate[it]?.sleepMinutes?.toDouble()) }
            val steps = week.map { LifeRecordDay(it, weekday.format(it), byDate[it]?.steps?.toDouble()) }
            val recent = byDate.toSortedMap(compareByDescending { it })

            return LifeRecord(
                connected = dto.connected,
                rangeText = if (end == today) "최근 7일" else "${shortDate.format(week.first())} ~ ${shortDate.format(end)}",
                sleep = sleep,
                sleepAverageText = sleep.mapNotNull { it.value }.takeIf { it.isNotEmpty() }
                    ?.let { sleepText(it.average().roundToInt()) },
                steps = steps,
                stepsAverageText = steps.mapNotNull { it.value }.takeIf { it.isNotEmpty() }
                    ?.let { "%,d걸음".format(Locale.KOREA, it.average().roundToInt()) },
                restingHeartRate = latest(recent) { it.restingHeartRate?.let { bpm -> "$bpm" to "회/분" } },
                bloodPressure = latest(recent) { day ->
                    val high = day.bloodPressureSystolic
                    val low = day.bloodPressureDiastolic
                    if (high != null && low != null) "$high/$low" to "mmHg" else null
                },
                bloodGlucose = latest(recent) { it.bloodGlucoseMgDl?.let { mg -> glucoseText(mg) to "mg/dL" } },
                lastSyncedText = dto.lastSyncedDay?.let(::parseDate)?.let(::dayText),
                disclaimer = dto.disclaimer?.takeIf { it.isNotBlank() } ?: DEFAULT_DISCLAIMER,
            )
        }

        /** 「6시간 10분」, 「45분」, 「7시간」. */
        fun sleepText(minutes: Int): String {
            val hours = minutes / 60
            val rest = minutes % 60
            return when {
                hours == 0 -> "${rest}분"
                rest == 0 -> "${hours}시간"
                else -> "${hours}시간 ${rest}분"
            }
        }

        fun dayText(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일"

        private fun glucoseText(value: Double): String =
            if (value % 1.0 == 0.0) "${value.toInt()}" else "%.1f".format(Locale.US, value)

        private fun latest(
            recent: Map<LocalDate, HealthDayDto>,
            value: (HealthDayDto) -> Pair<String, String>?,
        ): LifeRecordLatest? = recent.firstNotNullOfOrNull { (date, day) ->
            value(day)?.let { (text, unit) -> LifeRecordLatest(text, unit, dayText(date)) }
        }

        private fun parseDate(raw: String): LocalDate? = try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
