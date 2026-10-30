package live.collog.app.features.home.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import live.collog.app.config.AppEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.models.fromDto
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CallSummaryDto

/** iOS `HomeViewModel`. */
class HomeViewModel {
    var healthSummary by mutableStateOf<FamilyHealthSummary?>(null)
        private set
    var healthFeedback by mutableStateOf<HealthFeedback?>(null)
        private set
    var isLoaded by mutableStateOf(true)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var recentCalls by mutableStateOf<List<CallSummaryDto>>(emptyList())
        private set
    var recentCallsError by mutableStateOf<String?>(null)
        private set
    /** 가족(연락처 id)마다 마지막 통화 시각. 기록이 없거나 못 불러오면 빠진다. */
    var lastCallAt by mutableStateOf<Map<String, Instant>>(emptyMap())
        private set

    private var generation = UUID.randomUUID()

    suspend fun refresh(environment: AppEnvironment, contact: FamilyContact?) {
        val generation = UUID.randomUUID()
        this.generation = generation
        isLoaded = false
        try {
            load(environment, contact, generation)
        } finally {
            if (this.generation == generation) isLoaded = true
        }
    }

    private suspend fun load(environment: AppEnvironment, contact: FamilyContact?, generation: UUID) {
        healthSummary = null
        healthFeedback = null
        loadError = null
        recentCalls = emptyList()
        recentCallsError = null
        lastCallAt = emptyMap()
        if (environment.settings.isGuestMode) {
            healthSummary = FamilyHealthSummary.sample(contact)
            healthFeedback = HealthFeedback.sample(contact)
            // FamilyContact.samples와 같게: 어머니 3일 전, 아버지 그저께
            val now = Instant.now()
            lastCallAt = guestLastCallDays.mapValues { now.minus(Duration.ofDays(it.value)) }
            recentCalls = listOfNotNull(
                contact?.let { lastCallAt[it.id] }?.let { startedAt ->
                    CallSummaryDto(
                        callId = "sample-call", parentId = "sample", state = "ANALYZED",
                        startedAt = startedAt, durationSec = 724, recorded = true,
                    )
                },
            )
            isLoaded = true
            return
        }

        if (!environment.session.isAuthenticated) return
        // 리포트 대상은 고른 가족이다(나의 리포트는 두 사람 분석(S3) 뒤에 붙는다)
        val parentId = contact?.userId ?: return

        val api = environment.api
        val calls: List<CallSummaryDto> = try {
            api.calls(parentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (this.generation != generation) return
            recentCallsError = e.message
            emptyList()
        }
        if (this.generation != generation) return
        recentCalls = calls
        val latest = latestCalls(environment, parentId, calls)
        if (this.generation != generation) return
        lastCallAt = latest

        // 기준선을 못 받아도 리포트는 계속 보여준다(iOS `try?`)
        val baselines: List<BaselineDto> = try {
            api.baselines(parentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val speechRateBaseline = baselines
            .filter { it.kind == "ROLLING" && it.isReady }
            .associateBy { it.metric }["SPEECH_RATE"]

        val dto = try {
            api.report(parentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (this.generation == generation) loadError = e.message
            return
        }
        if (this.generation != generation) return
        healthSummary = FamilyHealthSummary.fromDto(
            dto,
            memberName = contact.name,
            baseline = speechRateBaseline,
        )
        healthFeedback = HealthFeedback.fromDto(dto)
    }

    /** 가족마다 마지막 통화 시각: 가족마다 통화 기록을 따로 받는다(이미 받은 [calls]는 다시 받지 않는다). 못 받은 가족은 빠진다. */
    private suspend fun latestCalls(
        environment: AppEnvironment,
        parentId: String,
        calls: List<CallSummaryDto>,
    ): Map<String, Instant> = coroutineScope {
        environment.family.contacts.mapNotNull { contact ->
            val contactParentId = contact.userId ?: return@mapNotNull null
            async {
                val contactCalls = if (contactParentId == parentId) {
                    calls
                } else {
                    try {
                        environment.api.calls(contactParentId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                contactCalls.maxOfOrNull { it.startedAt }?.let { contact.id to it }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    companion object {
        private val guestLastCallDays = mapOf("sample-mother" to 3L, "sample-father" to 2L)

        /** 홈 가족 줄의 마지막 통화: 오늘 0시와 마지막 통화 날 0시의 날짜 차이. */
        internal fun lastCallDayText(
            latest: Instant?,
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): String {
            if (latest == null) return "기록 없음"
            val days = ChronoUnit.DAYS.between(latest.atZone(zone).toLocalDate(), LocalDate.ofInstant(now, zone))
            return when {
                days < 1 -> "오늘"
                days == 1L -> "어제"
                days == 2L -> "그저께"
                else -> "${days}일 전"
            }
        }
    }
}
