package live.collog.app.features.health.services

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.KClass
import live.collog.app.features.health.models.BloodGlucoseReading
import live.collog.app.features.health.models.BloodPressureReading
import live.collog.app.features.health.models.HealthDailyCalculator
import live.collog.app.features.health.models.HealthRecords
import live.collog.app.features.health.models.SleepSession
import live.collog.app.features.health.models.SleepStageSpan
import live.collog.app.features.health.models.TimedSample

/** Health Connect(Jetpack `connect-client`)에서 하루 요약에 필요한 기록만 읽어 [HealthRecords]로 옮긴다. */
class HealthConnectSource(private val context: Context) : HealthRecordSource {
    private var cached: HealthConnectClient? = null

    private fun client(): HealthConnectClient = cached ?: HealthConnectClient.getOrCreate(context).also { cached = it }

    override fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NeedsInstall
        else -> HealthAvailability.Unavailable
    }

    override suspend fun grantedPermissions(): Set<String> = client().permissionController.getGrantedPermissions()

    override suspend fun revokeAll() {
        client().permissionController.revokeAllPermissions()
    }

    override suspend fun read(days: List<LocalDate>, granted: Set<String>): HealthRecords {
        val zone = HealthDailyCalculator.zone
        val now = Instant.now()
        val dayStart = days.first().atStartOfDay(zone).toInstant()
        // 수면은 끝난 시각으로 날을 정하므로, 일찍 시작한 세션도 빠지지 않게 하루 더 앞에서부터 읽는다
        val sleepStart = HealthDailyCalculator.sleepWindow(days.first()).first.minus(Duration.ofDays(1))

        val sleep = if (HealthLink.READ_SLEEP in granted) {
            readAll(SleepSessionRecord::class, sleepStart, now).map { record ->
                SleepSession(
                    start = record.startTime,
                    end = record.endTime,
                    stages = record.stages.map { SleepStageSpan(it.startTime, it.endTime, it.stage) },
                )
            }
        } else {
            emptyList()
        }
        val resting = if (HealthLink.READ_RESTING_HEART_RATE in granted) {
            readAll(RestingHeartRateRecord::class, dayStart, now).map { TimedSample(it.time, it.beatsPerMinute.toDouble()) }
        } else {
            emptyList()
        }
        val pressures = if (HealthLink.READ_BLOOD_PRESSURE in granted) {
            readAll(BloodPressureRecord::class, dayStart, now).map {
                BloodPressureReading(it.time, it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury)
            }
        } else {
            emptyList()
        }
        val glucoses = if (HealthLink.READ_BLOOD_GLUCOSE in granted) {
            readAll(BloodGlucoseRecord::class, dayStart, now).map { BloodGlucoseReading(it.time, it.level.inMillimolesPerLiter) }
        } else {
            emptyList()
        }
        val steps = if (HealthLink.READ_STEPS in granted) steps(dayStart, now) else emptyMap()

        val records = HealthRecords(
            sleepSessions = sleep,
            stepsByDay = steps,
            restingHeartRates = resting,
            bloodPressures = pressures,
            bloodGlucoses = glucoses,
        )
        if (HealthLink.READ_HEART_RATE !in granted) return records
        // 안정 시 심박이 없는 날만 그날 수면 구간의 심박을 읽는다(분 단위 기록 전체는 읽지 않는다).
        // readRecords 는 기록의 시작 시각으로 거르므로(플랫폼 RecordHelper), 잠들기 전에 시작해 수면까지 이어진
        // 심박 시리즈도 받게 앞을 넓혀 읽고 수면 구간과 겹치지 않는 시리즈는 버린다
        val heartRates = HealthDailyCalculator.heartRateWindows(days, records).flatMap { (start, end) ->
            readAll(HeartRateRecord::class, start.minus(HEART_RATE_LOOKBACK), end)
                .filter { !it.endTime.isBefore(start) }
                .flatMap { record -> record.samples.map { TimedSample(it.time, it.beatsPerMinute.toDouble()) } }
        }.distinct()
        return records.copy(heartRates = heartRates)
    }

    /** 걸음은 Health Connect 집계(중복 제거)로 서울 자정부터 하루씩 나눠 받는다. 한국은 일광 절약 시간이 없다. */
    private suspend fun steps(start: Instant, end: Instant): Map<LocalDate, Long> {
        if (!start.isBefore(end)) return emptyMap()
        val groups = client().aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Duration.ofDays(1),
            ),
        )
        return groups.mapNotNull { group ->
            group.result[StepsRecord.COUNT_TOTAL]?.let { HealthDailyCalculator.dayOf(group.startTime) to it }
        }.toMap()
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, start: Instant, end: Instant): List<T> {
        if (!start.isBefore(end)) return emptyList()
        val records = mutableListOf<T>()
        var token: String? = null
        var pages = 0
        do {
            val response = client().readRecords(
                ReadRecordsRequest(type, TimeRangeFilter.between(start, end), pageToken = token),
            )
            records += response.records
            // 일부 기기는 끝을 null 대신 빈 글자로 알린다
            token = response.pageToken?.takeIf { it.isNotEmpty() }
            pages += 1
        } while (token != null && pages < MAX_PAGES)
        return records
    }

    companion object {
        /** Android 13 이하의 Health Connect 앱(매니페스트 `<queries>`와 같다). */
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val MAX_PAGES = 20

        /** 수면 구간보다 이만큼 앞에서 시작한 심박 시리즈까지 읽는다. */
        private val HEART_RATE_LOOKBACK: Duration = Duration.ofDays(1)

        /** Health Connect 설치·업데이트 화면(Google Play). Play 가 없으면 웹 주소로 연다. */
        fun openInstallPage(context: Context) {
            val market = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"),
            ).apply {
                setPackage("com.android.vending")
                putExtra("overlay", true)
                putExtra("callerId", context.packageName)
            }
            try {
                context.startActivity(market)
            } catch (_: ActivityNotFoundException) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER_PACKAGE")),
                    )
                }
            }
        }

        /** Health Connect 설정(권한을 직접 켜는 곳). */
        fun openSettings(context: Context) {
            runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
        }
    }
}
