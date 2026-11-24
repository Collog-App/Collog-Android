package live.collog.app.shared.services

import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import live.collog.app.BuildConfig
import live.collog.app.MemoryPreferences
import live.collog.app.TestEnvironment
import live.collog.app.features.settings.models.deleteAccountAndSignOut
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.TokenResponse
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 이용 기록 쌓기·보내기(약속 문서 「앱 쪽 보내기 규칙」). 서버는 가짜 서버로 대신한다. */
class AppEventLogTests {
    private fun withEvents(body: suspend (TestEnvironment, AppEventLog) -> Unit) = runTest {
        val fixture = TestEnvironment(recordsEvents = true)
        try {
            body(fixture, fixture.environment.events)
        } finally {
            fixture.cleanUp()
        }
    }

    /** 자동 보내기를 돌리지 않는 범위. 테스트가 send()를 직접 부른다. */
    private fun idleScope() = CoroutineScope(Job()).apply { cancel() }

    private fun Request.events(): List<JsonObject> {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return Json.parseToJsonElement(buffer.readUtf8()).jsonObject["events"]!!.jsonArray.map { it.jsonObject }
    }

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content

    private fun signIn(fixture: TestEnvironment, userId: String, token: String = "$userId-token") {
        fixture.environment.session.apply(
            TokenResponse(token, "$userId-refresh", ApiUser(id = userId, role = "CHILD", name = userId, signupCompleted = true)),
        )
    }

    @Test
    fun recordsTheAgreedFieldsAndSendsThemTogether() = withEvents { fixture, events ->
        fixture.respond = { 200 to """{"accepted":2}""" }
        events.record(AppEvents.REPORT_VIEW, code = AppEventCode.DIRECT, subjectId = "mom")
        events.record(AppEvents.INCOMING_CALL_SHOWN, callId = "call-1")
        assertEquals(listOf("me", "me"), events.queued.map { it.userId })

        events.send()

        val request = fixture.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/v1/events", request.url.encodedPath)
        assertEquals("Bearer fixture-token", request.header("Authorization"))
        val (view, shown) = request.events()
        assertEquals("report_view", view.text("name"))
        assertTrue(view.text("id")!!.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        Instant.parse(view.text("at"))
        assertEquals("ANDROID", view.text("platform"))
        assertEquals("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", view.text("appVersion"))
        assertTrue(view.text("appVersion")!!.matches(Regex("""\S+ \(\d+\)""")))
        assertEquals("DIRECT", view.text("code"))
        assertEquals("mom", view.text("subjectId"))
        assertFalse("callId" in view)
        assertEquals("incoming_call_shown", shown.text("name"))
        assertEquals("call-1", shown.text("callId"))
        assertFalse("code" in shown)
        assertTrue(events.queued.isEmpty())
    }

    @Test
    fun sendsAtMostFiftyAtATime() = withEvents { fixture, events ->
        fixture.respond = { 200 to "{}" }
        repeat(120) { events.record(AppEvents.REPORT_VIEW, code = AppEventCode.DIRECT, subjectId = "mom") }

        events.send()

        assertEquals(listOf(50, 50, 20), fixture.requests.map { it.events().size })
        assertTrue(events.queued.isEmpty())
    }

    @Test
    fun stopsBetweenBatchesWhenTheSignedInUserChanges() = withEvents { fixture, events ->
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.respond = {
            received.countDown()
            release.await(5, TimeUnit.SECONDS)
            200 to "{}"
        }
        repeat(60) { events.record(AppEvents.REPORT_VIEW, code = AppEventCode.DIRECT, subjectId = "mom") }

        val sending = CoroutineScope(Dispatchers.Default).launch { events.send() }
        assertTrue(received.await(5, TimeUnit.SECONDS))
        // 첫 묶음 응답을 기다리는 사이 다른 사람이 로그인했다
        signIn(fixture, "other")
        release.countDown()
        sending.join()

        assertEquals(1, fixture.requests.size)
        assertEquals(List(10) { "me" }, events.queued.map { it.userId })
    }

    @Test
    fun connectionErrorsAndServerErrorsKeepTheEventsForLater() = withEvents { fixture, events ->
        var reply: () -> Pair<Int, String> = { throw IOException("offline") }
        fixture.respond = { reply() }
        events.record(AppEvents.APP_OPEN)
        events.record(AppEvents.REPORT_VIEW, code = AppEventCode.DIRECT, subjectId = "mom")

        // 실패해도 조용히 끝난다
        events.send()
        assertEquals(2, events.queued.size)

        reply = { 503 to "{}" }
        events.send()
        assertEquals(2, events.queued.size)
        assertEquals(2, fixture.requests.size)

        reply = { 200 to "{}" }
        events.send()
        assertTrue(events.queued.isEmpty())
    }

    @Test
    fun unauthorizedKeepsTheEventsUntilThatUserSignsInAgain() = withEvents { fixture, events ->
        // 이벤트도 토큰 갱신도 401: 세션이 끝난다
        fixture.respond = { 401 to "{}" }
        events.record(AppEvents.APP_OPEN)

        events.send()
        assertFalse(fixture.environment.session.isAuthenticated)
        assertEquals(1, events.queued.size)

        signIn(fixture, "me")
        fixture.respond = { 200 to "{}" }
        events.send()
        assertTrue(events.queued.isEmpty())
    }

    @Test
    fun otherClientErrorsDropThatBatch() = withEvents { fixture, events ->
        // 서버가 아직 이 주소를 모른다
        fixture.respond = { 404 to """{"detail":"Not Found"}""" }
        events.record(AppEvents.APP_OPEN)
        events.send()
        assertTrue(events.queued.isEmpty())

        fixture.respond = { 422 to """{"detail":"bad"}""" }
        events.record(AppEvents.APP_OPEN)
        events.send()
        assertTrue(events.queued.isEmpty())
        assertEquals(2, fixture.requests.size)

        // 익명 주소의 본문이 너무 크면 413
        fixture.environment.session.signOut()
        fixture.respond = { 413 to "{}" }
        events.recordOnboardingStep(AppEventCode.WELCOME)
        events.send()
        assertTrue(events.queued.isEmpty())
        assertEquals("/v1/events/anonymous", fixture.requests.last().url.encodedPath)
    }

    @Test
    fun eventsWithoutACodeSendNoCodeAndOnlyAllowedIdFields() = withEvents { fixture, events ->
        fixture.respond = { 200 to "{}" }
        // 서버는 code 가 없어야 하는 이벤트에 code 가 있으면 그 줄을 버린다
        events.record(AppEvents.APP_OPEN)
        events.record(AppEvents.INCOMING_CALL_SHOWN, callId = "call-1")
        events.record(AppEvents.CALL_CONNECT_FAILED, code = AppEventCode.NETWORK, callId = "call-1")

        events.send()

        val (open, shown, failed) = fixture.requests.single().events()
        assertEquals(setOf("id", "name", "at", "platform", "appVersion"), open.keys)
        assertEquals(setOf("id", "name", "at", "platform", "appVersion", "callId"), shown.keys)
        assertEquals(setOf("id", "name", "at", "platform", "appVersion", "code", "callId"), failed.keys)
    }

    @Test
    fun keepsOnlyTheNewestTwoHundred() = withEvents { _, events ->
        repeat(205) { events.record(AppEvents.INCOMING_CALL_SHOWN, callId = "call-$it") }

        assertEquals(AppEventLog.CAPACITY, events.queued.size)
        assertEquals("call-5", events.queued.first().event.callId)
        assertEquals("call-204", events.queued.last().event.callId)
    }

    @Test
    fun signingOutClearsTheQueue() = withEvents { fixture, events ->
        fixture.respond = { 200 to "{}" }
        events.record(AppEvents.APP_OPEN)

        fixture.environment.signOut()

        assertTrue(events.queued.isEmpty())
        assertTrue(fixture.paths.none { it.startsWith("/v1/events") })
    }

    @Test
    fun deletingTheAccountClearsTheQueue() = withEvents { fixture, events ->
        fixture.respond = { 204 to "" }
        events.record(AppEvents.APP_OPEN)

        deleteAccountAndSignOut(fixture.environment)

        assertTrue(events.queued.isEmpty())
    }

    @Test
    fun guestModeRecordsNothing() = withEvents { fixture, events ->
        fixture.environment.settings.isGuestMode = true

        events.record(AppEvents.REPORT_VIEW, code = AppEventCode.DIRECT, subjectId = "guest")
        events.recordAppOpen()
        events.recordOnboardingStep(AppEventCode.WELCOME)

        assertTrue(events.queued.isEmpty())
    }

    @Test
    fun sendsOnlyTheSignedInUsersEvents() = withEvents { fixture, events ->
        fixture.respond = { 200 to "{}" }
        events.record(AppEvents.REPORT_VIEW, code = AppEventCode.DIRECT, subjectId = "mom")
        signIn(fixture, "other")
        events.record(AppEvents.APP_OPEN)

        events.send()

        val request = fixture.requests.single()
        assertEquals("Bearer other-token", request.header("Authorization"))
        assertEquals(listOf("app_open"), request.events().map { it.text("name") })
        // 앞 사람 것은 그 사람이 다시 로그인할 때까지 남는다
        assertEquals(listOf("me"), events.queued.map { it.userId })
    }

    @Test
    fun appOpenIsRecordedOncePerSeoulDayForEachUser() = withEvents { fixture, _ ->
        var clock = Instant.parse("2026-11-23T14:59:00Z") // 서울 11-23 23:59
        val events = AppEventLog(fixture.environment, MemoryPreferences(), idleScope(), now = { clock })

        events.recordAppOpen()
        events.recordAppOpen()
        assertEquals(1, events.queued.size)
        assertEquals("app_open", events.queued.single().event.name)
        assertEquals("2026-11-23T14:59:00Z", events.queued.single().event.at)

        clock = Instant.parse("2026-11-23T15:00:00Z") // 서울 11-24 00:00
        events.recordAppOpen()
        assertEquals(2, events.queued.size)

        // 같은 날 다른 계정은 그 계정의 첫 번째다
        signIn(fixture, "other")
        events.recordAppOpen()
        assertEquals(listOf("me", "me", "other"), events.queued.map { it.userId })

        // 로그인하지 않았으면 남기지 않는다
        fixture.environment.session.signOut()
        clock = Instant.parse("2026-11-25T00:00:00Z")
        events.recordAppOpen()
        assertEquals(3, events.queued.size)
    }

    @Test
    fun onboardingStepsAreRecordedOncePerInstallAndBeforeLoginWithoutAUser() = withEvents { fixture, events ->
        fixture.environment.session.signOut()
        fixture.respond = { 200 to "{}" }

        events.recordOnboardingStep(AppEventCode.WELCOME)
        events.recordOnboardingStep(AppEventCode.WELCOME)
        events.recordOnboardingStep(AppEventCode.LOGIN)
        assertEquals(listOf(null, null), events.queued.map { it.userId })

        events.send()
        val anonymous = fixture.requests.single()
        assertEquals("/v1/events/anonymous", anonymous.url.encodedPath)
        assertNull(anonymous.header("Authorization"))
        assertEquals(listOf("onboarding_step", "onboarding_step"), anonymous.events().map { it.text("name") })
        assertEquals(listOf("WELCOME", "LOGIN"), anonymous.events().map { it.text("code") })
        assertTrue(events.queued.isEmpty())

        // 로그인 뒤 단계는 그 사용자 것으로 보낸다
        signIn(fixture, "me")
        events.recordOnboardingStep(AppEventCode.TERMS)
        assertEquals(listOf("me"), events.queued.map { it.userId })
        events.send()
        assertEquals("/v1/events", fixture.requests.last().url.encodedPath)

        // 로그아웃해도 이미 지난 단계는 다시 남기지 않는다(설치당 한 번)
        fixture.environment.signOut()
        events.recordOnboardingStep(AppEventCode.WELCOME)
        events.recordOnboardingStep(AppEventCode.TERMS)
        assertTrue(events.queued.isEmpty())
    }

    @Test
    fun anonymousEventsAreSentTwentyAtATime() = withEvents { fixture, events ->
        fixture.environment.session.signOut()
        fixture.respond = { 200 to "{}" }
        repeat(25) { events.recordOnboardingStep("STEP_$it") }

        events.send()

        assertEquals(listOf(20, 5), fixture.requests.map { it.events().size })
        assertTrue(fixture.requests.all { it.url.encodedPath == "/v1/events/anonymous" })
    }

    @Test
    fun eventsLeftUnsentSurviveARestartAndGoOutWhenFlushed() = withEvents { fixture, _ ->
        val prefs = MemoryPreferences()
        // 꺼진 앱이 전화 알림으로 깨어나 기록했지만 보내지 못했다
        fixture.respond = { throw IOException("offline") }
        val woken = AppEventLog(fixture.environment, prefs, idleScope())
        woken.record(AppEvents.INCOMING_CALL_SHOWN, callId = "call-1")
        woken.send()
        assertEquals(1, woken.queued.size)

        // 다음에 앱이 앞으로 오면(새 프로세스) 쌓인 것을 보낸다
        fixture.respond = { 200 to "{}" }
        val restarted = AppEventLog(fixture.environment, prefs, CoroutineScope(SupervisorJob() + Dispatchers.Main))
        assertEquals("call-1", restarted.queued.single().event.callId)
        restarted.flush()
        fixture.awaitPath("/v1/events")
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { while (restarted.queued.isNotEmpty()) delay(10) }
        }
        assertEquals("call-1", fixture.requests.last().events().single().text("callId"))
    }

    @Test
    fun recordingSendsInTheBackgroundWithoutWaiting() = withEvents { fixture, _ ->
        fixture.respond = { 200 to "{}" }
        val events = AppEventLog(fixture.environment, MemoryPreferences(), CoroutineScope(SupervisorJob() + Dispatchers.Main))

        events.record(AppEvents.APP_OPEN)

        fixture.awaitPath("/v1/events")
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { while (events.queued.isNotEmpty()) delay(10) }
        }
    }

    @Test
    fun withoutStorageNothingIsRecorded() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.environment.events.record(AppEvents.APP_OPEN)
            fixture.environment.events.recordOnboardingStep(AppEventCode.WELCOME)
            fixture.environment.events.flush()
            assertTrue(fixture.environment.events.queued.isEmpty())
            assertTrue(fixture.requests.isEmpty())
        } finally {
            fixture.cleanUp()
        }
    }
}
