package live.collog.app.shared.services

import android.content.SharedPreferences
import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import live.collog.app.BuildConfig
import live.collog.app.config.AppEnvironment
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.AppEventDto
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.CollogTimestamp

/** 이용 기록 이벤트 이름(약속 문서 `private/events_contract.md`). 두 앱이 같은 글자를 쓴다. */
object AppEvents {
    const val APP_OPEN = "app_open"
    const val REPORT_VIEW = "report_view"
    const val POST_CALL_ANSWER = "post_call_answer"
    const val INCOMING_CALL_SHOWN = "incoming_call_shown"
    const val CALL_CONNECT_FAILED = "call_connect_failed"
    const val ONBOARDING_STEP = "onboarding_step"
    const val HEALTH_LINK_RESULT = "health_link_result"
}

/** 이용 기록 code 값(약속 문서의 표). */
object AppEventCode {
    // report_view
    const val DIRECT = "DIRECT"
    const val NOTIFICATION = "NOTIFICATION"

    // post_call_answer
    const val YES = "YES"
    const val NO = "NO"
    const val NOT_SEEN = "NOT_SEEN"

    // call_connect_failed
    const val MIC_PERMISSION = "MIC_PERMISSION"
    const val NETWORK = "NETWORK"
    const val TOKEN = "TOKEN"
    const val SERVER = "SERVER"
    const val OTHER = "OTHER"

    // onboarding_step
    const val WELCOME = "WELCOME"
    const val LOGIN = "LOGIN"
    const val TERMS = "TERMS"
    const val PROFILE = "PROFILE"
    const val CONDITIONS = "CONDITIONS"
    const val DONE = "DONE"

    // health_link_result
    const val CONNECTED = "CONNECTED"
    const val PERMISSION_DENIED = "PERMISSION_DENIED"
    const val CONNECTED_NO_RECORDS = "CONNECTED_NO_RECORDS"
}

/**
 * 이용 기록(앱 이벤트). 기기에 쌓아 두고(최대 [CAPACITY]개, 넘치면 오래된 것부터 버림) 앱이 앞으로 올 때와
 * 새 이벤트 뒤에 묶어 보낸다. 실패는 조용히 넘긴다: 화면에 알리지 않고, 부른 쪽은 보내기를 기다리지 않는다.
 * 둘러보기에서는 남기지 않는다. 쌓을 때 사용자 ID 를 적고 지금 로그인한 사용자 것만 보낸다.
 * [prefs]가 없으면 아무것도 하지 않는다(단위 테스트 기본값). 모든 함수는 메인 스레드에서 부른다.
 */
class AppEventLog(
    private val environment: AppEnvironment,
    private val prefs: SharedPreferences?,
    private val scope: CoroutineScope,
    private val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    private val now: () -> Instant = Instant::now,
) {
    /** 쌓인 이벤트 하나. [userId]가 null 이면 로그인 전 단계 기록이라 인증 없는 주소로 보낸다. */
    @Serializable
    internal data class Queued(val userId: String? = null, val event: AppEventDto)

    private var queue: List<Queued> = load()
    private var flushJob: Job? = null
    private var needsFlush = false

    internal val queued: List<Queued> get() = queue

    /** 로그인한 사용자의 이벤트를 남긴다. */
    fun record(name: String, code: String? = null, subjectId: String? = null, callId: String? = null) = quietly {
        val userId = signedInUserId() ?: return
        enqueue(userId, name, code, subjectId, callId)
    }

    /**
     * `app_open`: 로그인한 사용자가 앱을 앞으로 가져온 그날(서울 날짜) 첫 번째만 남긴다. 앱이 앞에 있는 동안 로그인을
     * 마친 직후에도 부른다(가입·로그인한 날이 빠지지 않게).
     */
    fun recordAppOpen() = quietly {
        val prefs = prefs ?: return
        val userId = signedInUserId() ?: return
        val marker = "$userId ${now().atZone(Seoul).toLocalDate()}"
        if (prefs.getString(KEY_APP_OPEN, null) == marker) return
        prefs.edit().putString(KEY_APP_OPEN, marker).apply()
        enqueue(userId, AppEvents.APP_OPEN)
    }

    /** `onboarding_step`: 단계마다 설치당 한 번. 로그인 전 단계는 사용자 없이 쌓는다. */
    fun recordOnboardingStep(step: String) = quietly {
        val prefs = prefs ?: return
        if (environment.settings.isGuestMode) return
        val recorded = prefs.getStringSet(KEY_STEPS, null).orEmpty()
        if (step in recorded) return
        prefs.edit().putStringSet(KEY_STEPS, (recorded + step).toMutableSet()).apply()
        enqueue(signedInUserId(), AppEvents.ONBOARDING_STEP, code = step)
    }

    /**
     * 직접 로그아웃·계정 삭제 때 쌓인 것과 하루 첫 실행 표시(지난 사용자 ID 가 들어 있다)를 지운다.
     * 지난 가입 단계 표시는 설치 기준이라 둔다.
     */
    fun clear() = quietly {
        queue = emptyList()
        save()
        prefs?.edit()?.remove(KEY_APP_OPEN)?.apply()
    }

    /** 쌓인 것을 뒤에서 보낸다. 보내는 중이면 끝난 뒤 한 번 더 본다. 보내다 난 예외는 [sendFailure]가 받는다. */
    fun flush() = quietly {
        if (prefs == null || queue.isEmpty()) return
        needsFlush = true
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(sendFailure) {
            while (needsFlush) {
                needsFlush = false
                send()
            }
        }
    }

    /** 로그인 전 기록, 그다음 지금 사용자 것을 묶어 보낸다. 다른 계정 것은 그 계정이 다시 로그인할 때까지 둔다. */
    internal suspend fun send() {
        sendAll(userId = null, batchSize = ANONYMOUS_BATCH)
        signedInUserId()?.let { sendAll(it, BATCH) }
    }

    private suspend fun sendAll(userId: String?, batchSize: Int) {
        while (true) {
            // 묶음 사이에 로그아웃하거나 다른 사람이 로그인했으면 멈춘다(앞 사람 것을 새 토큰으로 보내지 않는다)
            if (userId != null && signedInUserId() != userId) return
            val events = queue.filter { it.userId == userId }.take(batchSize).map { it.event }
            if (events.isEmpty()) return
            try {
                val api = environment.api
                if (userId == null) api.sendAnonymousEvents(events) else api.sendEvents(events)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 연결 오류·5xx·401 은 남겨 두고 다음에 다시 보낸다. 그 밖의 4xx(404 = 서버가 아직 이 주소를 모름)는 그 묶음을 버린다
                if (e !is ApiError.Server || e.status !in 400..499) return
            }
            val done = events.map { it.id }.toSet()
            queue = queue.filter { it.event.id !in done }
            save()
        }
    }

    private fun enqueue(
        userId: String?,
        name: String,
        code: String? = null,
        subjectId: String? = null,
        callId: String? = null,
    ) {
        if (prefs == null) return
        val event = AppEventDto(
            id = UUID.randomUUID().toString(),
            name = name,
            at = CollogTimestamp.format(now().truncatedTo(ChronoUnit.MILLIS)),
            platform = PLATFORM,
            appVersion = appVersion,
            code = code,
            subjectId = subjectId,
            callId = callId,
        )
        queue = (queue + Queued(userId, event)).takeLast(CAPACITY)
        save()
        flush()
    }

    private fun signedInUserId(): String? {
        if (environment.settings.isGuestMode || !environment.session.isAuthenticated) return null
        return environment.session.user?.id
    }

    private fun load(): List<Queued> = try {
        prefs?.getString(KEY_QUEUE, null)?.let { CollogJson.decodeFromString<List<Queued>>(it) }.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** 기록이 실패해도 부른 쪽(통화 정리·화면 전환)은 그대로 돈다. 취소는 그대로 전달한다. */
    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "이용 기록을 남기지 못했어요", e)
        }
    }

    private val sendFailure = CoroutineExceptionHandler { _, error -> Log.w(TAG, "이용 기록을 보내지 못했어요", error) }

    private fun save() {
        prefs?.edit()?.putString(KEY_QUEUE, CollogJson.encodeToString(queue))?.apply()
    }

    companion object {
        const val CAPACITY = 200
        const val BATCH = 50
        const val ANONYMOUS_BATCH = 20
        private const val TAG = "AppEventLog"
        private const val PLATFORM = "ANDROID"
        private const val KEY_QUEUE = "events.queue"
        private const val KEY_APP_OPEN = "events.appOpen"
        private const val KEY_STEPS = "events.onboardingSteps"
        private val Seoul: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
