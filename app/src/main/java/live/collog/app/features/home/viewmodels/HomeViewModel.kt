package live.collog.app.features.home.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import live.collog.app.config.AppEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.models.fromDto
import live.collog.app.shared.services.api.BaselineDto
import live.collog.app.shared.services.api.CallSummaryDto

private const val NO_CALLS_TEXT = "아직 통화 기록이 없어요"

/** iOS `HomeViewModel`. */
class HomeViewModel {
    var healthSummary by mutableStateOf<FamilyHealthSummary?>(null)
        private set
    var healthFeedback by mutableStateOf<HealthFeedback?>(null)
        private set
    var lastCallText by mutableStateOf(NO_CALLS_TEXT)
        private set
    var isLoaded by mutableStateOf(true)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var recentCalls by mutableStateOf<List<CallSummaryDto>>(emptyList())
        private set
    var recentCallsError by mutableStateOf<String?>(null)
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
        lastCallText = NO_CALLS_TEXT
        if (environment.settings.isGuestMode) {
            healthSummary = FamilyHealthSummary.sample(contact)
            healthFeedback = HealthFeedback.sample(contact)
            lastCallText = contact?.let { it.lastCallText + "했어요" } ?: "최근 통화했어요"
            isLoaded = true
            return
        }

        if (!environment.session.isAuthenticated) return
        val user = environment.session.user
        val parentId = (
            if (user?.role == "PARENT") {
                user.id
            } else if (contact?.userId != null) {
                contact.userId
            } else {
                environment.subjectParentId()
            }
            ) ?: return

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
        lastCallText = if (recentCallsError == null) lastCallText(calls) else "통화 기록을 불러오지 못했어요"

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
            memberName = if (user?.role == "PARENT") user.name ?: "나" else contact?.name ?: "가족",
            baseline = speechRateBaseline,
        )
        healthFeedback = HealthFeedback.fromDto(dto)
    }

    companion object {
        /** iOS `HomeViewModel.lastCallText(calls:)`: 오늘 0시와 마지막 통화 날 0시의 날짜 차이. */
        internal fun lastCallText(
            calls: List<CallSummaryDto>,
            now: Instant = Instant.now(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): String {
            val latest = calls.maxByOrNull { it.startedAt } ?: return NO_CALLS_TEXT
            val days = ChronoUnit.DAYS.between(
                latest.startedAt.atZone(zone).toLocalDate(),
                LocalDate.ofInstant(now, zone),
            )
            return when {
                days < 1 -> "오늘 통화했어요"
                days == 1L -> "어제 통화했어요"
                days == 2L -> "그저께 통화했어요"
                else -> "${days}일 전에 통화했어요"
            }
        }
    }
}
