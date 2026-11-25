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
import live.collog.app.shared.services.api.ApiError
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
    /** 가족이 「가족 공유 데이터 범위」에서 리포트·타임라인 공유를 꺼 둬 서버가 막았다(403). 오류가 아니라 빈 상태로 보인다. */
    var reportNotShared by mutableStateOf(false)
        private set
    var callsNotShared by mutableStateOf(false)
        private set
    /** 가족(연락처 id)마다 마지막 통화 시각. 기록이 없거나 못 불러오면 빠진다. */
    var lastCallAt by mutableStateOf<Map<String, Instant>>(emptyMap())
        private set

    private var generation = UUID.randomUUID()

    /** 첫 읽기가 한 번 끝났는가. `isLoaded` 는 처음부터 true 라 이 값으로 「아직 안 읽음」을 가른다. */
    private var hasLoaded = false

    suspend fun refresh(environment: AppEnvironment, contact: FamilyContact?) {
        val generation = UUID.randomUUID()
        this.generation = generation
        isLoaded = false
        try {
            load(environment, contact, generation)
        } finally {
            if (this.generation == generation) {
                isLoaded = true
                hasLoaded = true
            }
        }
    }

    /**
     * iOS `refreshCalls`: 화면을 비우지 않고 통화 목록만 다시 읽는다(앱 복귀·통화 종료 때).
     * 분석 완료 건수가 바뀌었거나 아직 결과 카드가 없으면 전체를 다시 읽는다.
     * 그사이 가족이 공유를 껐거나 다시 켰으면(보이는 상태와 달라졌으면)도 전체를 다시 읽는다. 403 이 아닌 실패는 그대로 둔다.
     */
    suspend fun refreshCalls(environment: AppEnvironment, contact: FamilyContact?) {
        if (environment.settings.isGuestMode || !environment.session.isAuthenticated || !hasLoaded || !isLoaded) return
        val parentId = contact?.userId ?: return
        val generation = this.generation
        val api = environment.api

        val callList = ApiError.accessCheck { api.calls(parentId) } ?: return
        if (this.generation != generation) return
        if (callList.first != callsNotShared) {
            refresh(environment, contact)
            return
        }
        val calls = callList.second
        if (calls != null) {
            val analyzedBefore = recentCalls.count { it.isAnalyzed }
            recentCalls = calls
            recentCallsError = null
            val latest = calls.maxOfOrNull { it.startedAt }
            lastCallAt = if (latest != null) lastCallAt + (contact.id to latest) else lastCallAt - contact.id
            if (calls.count { it.isAnalyzed } != analyzedBefore || (healthSummary == null && !reportNotShared)) {
                refresh(environment, contact)
                return
            }
        }

        // 리포트 공유는 통화 기록과 따로 켜고 끈다
        if (healthSummary == null && !reportNotShared) return
        val report = ApiError.accessCheck { api.report(parentId) } ?: return
        if (this.generation != generation) return
        if (report.first != reportNotShared) refresh(environment, contact)
    }

    private suspend fun load(environment: AppEnvironment, contact: FamilyContact?, generation: UUID) {
        healthSummary = null
        healthFeedback = null
        loadError = null
        recentCalls = emptyList()
        recentCallsError = null
        reportNotShared = false
        callsNotShared = false
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
            if (e.isNotShared) callsNotShared = true else recentCallsError = e.message
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
            if (this.generation == generation) {
                if (e.isNotShared) reportNotShared = true else loadError = e.message
            }
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

    /** 대상(`/parents/{id}/...`) 요청의 403 은 가족이 그 항목 공유를 꺼 둔 것이다(리포트·타임라인 화면과 같은 판정). */
    private val Exception.isNotShared: Boolean get() = this is ApiError.Server && status == 403

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
