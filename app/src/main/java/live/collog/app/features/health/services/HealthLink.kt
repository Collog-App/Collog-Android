package live.collog.app.features.health.services

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import live.collog.app.config.AppEnvironment
import live.collog.app.features.health.models.HealthDailyCalculator
import live.collog.app.features.health.models.HealthRecords
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.HealthLinkDocument
import live.collog.app.shared.services.api.HealthLinkStatusDto

enum class HealthAvailability {
    /** Android 9 미만이거나 업무 프로필: 연결 진입점을 숨긴다. */
    Unavailable,

    /** Health Connect 앱 설치·업데이트가 필요하다. */
    NeedsInstall,
    Available,
}

/** Health Connect 와 맞닿는 부분. 테스트는 가짜를 넣는다. */
interface HealthRecordSource {
    fun availability(): HealthAvailability
    suspend fun grantedPermissions(): Set<String>

    /** [granted]에 든 권한의 기록만 읽는다. 읽다가 실패하면 던진다(일부만 올리지 않게). */
    suspend fun read(days: List<LocalDate>, granted: Set<String>): HealthRecords
    suspend fun revokeAll()
}

sealed interface HealthLinkStatus {
    data object Unknown : HealthLinkStatus

    /** 서버에 아직 건강 연결 API 가 없다(404 등). 진입점은 「준비 중」으로 둔다. */
    data object Unsupported : HealthLinkStatus

    data class Failed(val message: String) : HealthLinkStatus

    data class Loaded(val link: HealthLinkStatusDto) : HealthLinkStatus
}

/** 연결 시트에 보이는 단계. */
sealed interface HealthLinkStep {
    data object Loading : HealthLinkStep
    data object NotReady : HealthLinkStep
    data object Unavailable : HealthLinkStep
    data object NeedsInstall : HealthLinkStep
    data class Consent(val document: HealthLinkDocument, val notice: String? = null) : HealthLinkStep

    /** 권한 창을 띄웠거나 서버에 연결하는 중. */
    data class Connecting(val document: HealthLinkDocument) : HealthLinkStep

    /** 연결된 뒤 꺼진 권한을 다시 받는 중. */
    data object Regranting : HealthLinkStep
    data object PermissionDenied : HealthLinkStep
    data class Linked(val justConnected: Boolean, val lastSyncedDay: String?, val hasPermission: Boolean) : HealthLinkStep
    data class Failed(val message: String) : HealthLinkStep
}

/**
 * 건강 앱 연결(2026-11-08 약속): 내 연결 상태, 연결 흐름(동의 → 권한 → 연결 → 동기화), 하루 한 번 이하 동기화.
 * 앱 전체에서 하나다(CollogApplication). 서버에 API 가 없으면(404) 조용히 「준비 중」으로 둔다.
 */
class HealthLink(
    private val source: HealthRecordSource,
    private val prefs: SharedPreferences,
    private val today: () -> LocalDate = { LocalDate.now(HealthDailyCalculator.zone) },
) {
    var isPresented by mutableStateOf(false)
        private set
    var step by mutableStateOf<HealthLinkStep>(HealthLinkStep.Loading)
        private set
    var availability by mutableStateOf(HealthAvailability.Unavailable)
        private set

    /** 연결·동기화·끊기가 끝날 때마다 오른다. 리포트가 「생활 기록」을 다시 불러온다. */
    var revision by mutableIntStateOf(0)
        private set

    private var status by mutableStateOf<HealthLinkStatus>(HealthLinkStatus.Unknown)
    private var statusOwner: String? = null
    private var hintRevision by mutableIntStateOf(0)
    private val statusMutex = Mutex()
    private val syncMutex = Mutex()

    /** [userId]의 연결 상태. 다른 계정의 것이면 Unknown. */
    fun statusFor(userId: String?): HealthLinkStatus =
        if (userId != null && userId == statusOwner) status else HealthLinkStatus.Unknown

    /** 설정 「건강 앱 연결」 줄 오른쪽 글자. */
    fun rowText(userId: String?): String = when (val current = statusFor(userId)) {
        is HealthLinkStatus.Loaded -> when {
            current.link.connected -> "연결됨"
            availability == HealthAvailability.Unavailable -> "지원 안 함"
            else -> "연결 안 됨"
        }
        HealthLinkStatus.Unsupported -> "준비 중"
        else -> ""
    }

    /** 홈 안내 카드: 아직 연결하지 않았고, 쓸 수 있는 기기이고, 닫은 적이 없을 때. */
    fun shouldShowHint(environment: AppEnvironment): Boolean {
        hintRevision
        val userId = userId(environment) ?: return false
        if (prefs.getBoolean(hintKey(userId), false)) return false
        if (availability == HealthAvailability.Unavailable) return false
        val link = (statusFor(userId) as? HealthLinkStatus.Loaded)?.link ?: return false
        return !link.connected
    }

    fun dismissHint(environment: AppEnvironment) {
        val userId = environment.session.user?.id ?: return
        prefs.edit().putBoolean(hintKey(userId), true).apply()
        hintRevision += 1
    }

    /** 아직 이 계정의 상태를 모르면(또는 못 불러왔으면) 불러온다. */
    suspend fun ensureStatus(environment: AppEnvironment) {
        val userId = userId(environment) ?: return
        val current = statusFor(userId)
        if (current is HealthLinkStatus.Loaded || current == HealthLinkStatus.Unsupported) return
        refreshStatus(environment)
    }

    suspend fun refreshStatus(environment: AppEnvironment): HealthLinkStatus = statusMutex.withLock {
        val userId = userId(environment) ?: return@withLock HealthLinkStatus.Unknown
        availability = source.availability()
        val next = try {
            HealthLinkStatus.Loaded(environment.api.healthLink())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isUnsupported) HealthLinkStatus.Unsupported else HealthLinkStatus.Failed(e.message ?: FAILED_TEXT)
        }
        setStatus(userId, next)
        next
    }

    fun present() {
        step = HealthLinkStep.Loading
        isPresented = true
    }

    fun dismiss() {
        isPresented = false
    }

    /** 시트를 열 때: 기기·서버 상태에 맞는 단계를 고른다. */
    suspend fun prepare(environment: AppEnvironment) {
        step = HealthLinkStep.Loading
        availability = source.availability()
        if (availability == HealthAvailability.Unavailable) {
            step = HealthLinkStep.Unavailable
            return
        }
        step = when (val current = refreshStatus(environment)) {
            HealthLinkStatus.Unknown -> HealthLinkStep.Failed(FAILED_TEXT)
            HealthLinkStatus.Unsupported -> HealthLinkStep.NotReady
            is HealthLinkStatus.Failed -> HealthLinkStep.Failed(current.message)
            is HealthLinkStatus.Loaded -> when {
                current.link.connected ->
                    linkedStep(current.link, justConnected = false, hasPermission = grantedReadPermissions().isNotEmpty())
                availability == HealthAvailability.NeedsInstall -> HealthLinkStep.NeedsInstall
                else -> current.link.document?.let { HealthLinkStep.Consent(it) }
                    ?: HealthLinkStep.Failed("동의 문서를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
            }
        }
    }

    /** 권한 창에 띄울 권한. 모두 받았으면 null(창 없이 바로 연결한다). */
    suspend fun permissionsToRequest(): Set<String>? =
        if (grantedReadPermissions().containsAll(ReadPermissions)) null else ReadPermissions

    fun beginConnect(document: HealthLinkDocument) {
        step = HealthLinkStep.Connecting(document)
    }

    fun beginRegrant() {
        step = HealthLinkStep.Regranting
    }

    fun showNeedsInstall() {
        step = HealthLinkStep.NeedsInstall
    }

    /** 권한 창이 닫힌 뒤: 하나라도 받았으면 동의를 서버에 남기고 바로 동기화한다. */
    suspend fun completeConnect(environment: AppEnvironment) {
        val document = (step as? HealthLinkStep.Connecting)?.document ?: return
        val userId = userId(environment) ?: return
        if (grantedReadPermissions().isEmpty()) {
            step = HealthLinkStep.PermissionDenied
            return
        }
        val link = try {
            environment.api.connectHealthLink(document.version)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            when {
                e.isUnsupported -> {
                    setStatus(userId, HealthLinkStatus.Unsupported)
                    step = HealthLinkStep.NotReady
                }
                e is ApiError.Server && e.code == "HEALTH_CONSENT_VERSION_MISMATCH" -> {
                    val refreshed = (refreshStatus(environment) as? HealthLinkStatus.Loaded)?.link?.document
                    step = if (refreshed != null) {
                        HealthLinkStep.Consent(refreshed, notice = "동의 문서가 바뀌었어요. 다시 읽고 동의해 주세요.")
                    } else {
                        HealthLinkStep.Failed(FAILED_TEXT)
                    }
                }
                else -> step = HealthLinkStep.Failed(e.message ?: FAILED_TEXT)
            }
            return
        }
        setStatus(userId, HealthLinkStatus.Loaded(link.copy(connected = true)))
        val before = revision
        sync(environment, force = true)
        // 리포트는 동기화가 끝난 뒤 한 번만 다시 불러오게 한다
        if (revision == before) revision += 1
        step = linkedStep(currentLink(userId) ?: link, justConnected = true, hasPermission = true)
    }

    /** 연결된 뒤 꺼진 권한을 다시 받았을 때. */
    suspend fun completeRegrant(environment: AppEnvironment) {
        if (step != HealthLinkStep.Regranting) return
        val userId = userId(environment) ?: return
        val granted = grantedReadPermissions().isNotEmpty()
        if (granted) sync(environment, force = true)
        val link = currentLink(userId)
        step = if (link != null) linkedStep(link, justConnected = false, hasPermission = granted) else HealthLinkStep.Failed(FAILED_TEXT)
    }

    /** 연결 끊기: 서버가 요약을 지우고, 이 앱의 Health Connect 권한도 거둔다. */
    suspend fun disconnect(environment: AppEnvironment) {
        val userId = userId(environment) ?: return
        step = HealthLinkStep.Loading
        try {
            environment.api.disconnectHealthLink()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            step = HealthLinkStep.Failed(e.message ?: "연결을 끊지 못했어요. 잠시 후 다시 시도해 주세요.")
            return
        }
        try {
            if (source.availability() == HealthAvailability.Available) source.revokeAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect 권한을 거두지 못했어요", e)
        }
        prefs.edit().remove(syncKey(userId)).apply()
        val link = currentLink(userId) ?: HealthLinkStatusDto()
        setStatus(userId, HealthLinkStatus.Loaded(link.copy(connected = false, lastSyncedDay = null)))
        revision += 1
        isPresented = false
    }

    /** 앱이 앞으로 올 때: 오늘 아직 보내지 않았고 연결돼 있으면 최근 14일을 다시 계산해 올린다. */
    suspend fun syncIfDue(environment: AppEnvironment) {
        val userId = userId(environment) ?: return
        if (prefs.getString(syncKey(userId), null) == today().toString()) return
        ensureStatus(environment)
        if (currentLink(userId)?.connected != true) return
        sync(environment, force = false)
    }

    private suspend fun sync(environment: AppEnvironment, force: Boolean): Boolean = syncMutex.withLock {
        val userId = userId(environment) ?: return@withLock false
        val day = today()
        if (!force && prefs.getString(syncKey(userId), null) == day.toString()) return@withLock true
        if (source.availability() != HealthAvailability.Available) return@withLock false
        val granted = grantedReadPermissions()
        if (granted.isEmpty()) return@withLock false

        val days = HealthDailyCalculator.syncDays(day)
        val summaries = try {
            HealthDailyCalculator.summaries(days, source.read(days, granted)).filter { it.hasAnyValue }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect 기록을 읽지 못했어요", e)
            return@withLock false
        }
        if (summaries.isEmpty()) {
            markSynced(userId, day)
            return@withLock true
        }

        try {
            val result = environment.api.uploadHealthDaily(summaries)
            currentLink(userId)?.let { link ->
                setStatus(userId, HealthLinkStatus.Loaded(link.copy(lastSyncedDay = result.lastSyncedDay ?: link.lastSyncedDay)))
            }
            markSynced(userId, day)
            revision += 1
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "건강 요약을 올리지 못했어요", e)
            when {
                e.isUnsupported -> setStatus(userId, HealthLinkStatus.Unsupported)
                e is ApiError.Server && e.code == "HEALTH_LINK_REQUIRED" ->
                    setStatus(userId, HealthLinkStatus.Loaded((currentLink(userId) ?: HealthLinkStatusDto()).copy(connected = false)))
            }
            // 서버가 받지 않은 요청(4xx)은 같은 날 다시 보내지 않는다. 연결·서버 오류는 다음에 앞으로 올 때 다시.
            if (e is ApiError.Server && e.status in 400..499) markSynced(userId, day)
            false
        }
    }

    private fun linkedStep(link: HealthLinkStatusDto, justConnected: Boolean, hasPermission: Boolean): HealthLinkStep =
        HealthLinkStep.Linked(justConnected = justConnected, lastSyncedDay = link.lastSyncedDay, hasPermission = hasPermission)

    private fun currentLink(userId: String): HealthLinkStatusDto? = (statusFor(userId) as? HealthLinkStatus.Loaded)?.link

    private fun setStatus(userId: String, next: HealthLinkStatus) {
        statusOwner = userId
        status = next
    }

    private fun markSynced(userId: String, day: LocalDate) {
        prefs.edit().putString(syncKey(userId), day.toString()).apply()
    }

    private suspend fun grantedReadPermissions(): Set<String> {
        if (source.availability() != HealthAvailability.Available) return emptySet()
        return try {
            source.grantedPermissions().intersect(ReadPermissions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect 권한을 확인하지 못했어요", e)
            emptySet()
        }
    }

    private fun userId(environment: AppEnvironment): String? {
        if (!environment.session.isAuthenticated || environment.settings.isGuestMode) return null
        return environment.session.user?.id
    }

    companion object {
        private const val TAG = "HealthLink"
        private const val FAILED_TEXT = "건강 앱 연결 상태를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."

        const val READ_SLEEP = "android.permission.health.READ_SLEEP"
        const val READ_STEPS = "android.permission.health.READ_STEPS"
        const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
        const val READ_RESTING_HEART_RATE = "android.permission.health.READ_RESTING_HEART_RATE"
        const val READ_BLOOD_PRESSURE = "android.permission.health.READ_BLOOD_PRESSURE"
        const val READ_BLOOD_GLUCOSE = "android.permission.health.READ_BLOOD_GLUCOSE"

        /** 매니페스트에 선언한 읽기 권한과 같다. */
        val ReadPermissions: Set<String> = setOf(
            READ_SLEEP, READ_STEPS, READ_HEART_RATE, READ_RESTING_HEART_RATE, READ_BLOOD_PRESSURE, READ_BLOOD_GLUCOSE,
        )

        private fun syncKey(userId: String) = "health.lastSyncDay.$userId"
        private fun hintKey(userId: String) = "health.hintDismissed.$userId"

        /** 서버에 아직 이 API 가 없을 때(경로 없음·메서드 없음·미구현). */
        private val Exception.isUnsupported: Boolean
            get() = this is ApiError.Server && status in setOf(404, 405, 501)
    }
}

val LocalHealthLink = staticCompositionLocalOf<HealthLink> { error("HealthLink가 제공되지 않았어요") }
