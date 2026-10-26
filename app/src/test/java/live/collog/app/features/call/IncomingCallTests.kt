package live.collog.app.features.call

import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.EndCause
import live.collog.app.features.call.services.handleIncomingPush
import okhttp3.Request
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** iOS `IncomingCallTests`와 FCM 수신 처리(iOS PushKit 처리) 테스트. */
class IncomingCallTests {
    private lateinit var fixture: TestEnvironment
    private lateinit var platform: FakeCallPlatform
    private lateinit var scope: CoroutineScope
    private lateinit var center: CallCenter

    @Before
    fun setUp() {
        fixture = TestEnvironment()
        platform = FakeCallPlatform()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        center = CallCenter(fixture.environment, platform, scope)
    }

    /**
     * 상태 확인(GET /v1/calls/call-1) 응답은 테스트가 끝날 때까지 붙잡는다. 테스트의 Main은 Unconfined라 응답이
     * OkHttp 스레드에서 처리되며 테스트 스레드와 activeCall을 동시에 고쳐 써 결과가 흔들린다(앱은 모두 메인 스레드라 없다).
     */
    private val statusHold = CountDownLatch(1)

    private fun ringing(request: Request): Pair<Int, String> {
        if (request.method == "GET" && request.url.encodedPath == "/v1/calls/call-1") statusHold.await(5, TimeUnit.SECONDS)
        return 200 to """{"state":"RINGING","endedAt":null,"recordingEnabled":true}"""
    }

    @After
    fun tearDown() {
        // 상태 확인 루프가 OkHttp 스레드에서 막 깨어난 채로 Main을 되돌리면 그 루프의 delay가 다음 테스트를 깨뜨린다.
        // 끝날 때까지 기다린 뒤 되돌린다.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        statusHold.countDown()
        platform.cleanUp()
        fixture.cleanUp()
    }

    private fun incomingCall(phase: CallPhase = CallPhase.Ringing) = CallCenter.ActiveCall(
        id = "incoming",
        uuid = UUID.randomUUID(),
        direction = CallCenter.Direction.Incoming,
        peerId = "child",
        peerName = "자녀",
        phase = phase,
        questions = emptyList(),
    )

    private fun push(calleeId: String = "me", expiresAt: Instant = Instant.now().plusSeconds(60), callId: String = "call-1") =
        mapOf(
            "type" to "incoming_call",
            "callId" to callId,
            "callUUID" to UUID.randomUUID().toString(),
            "callerId" to "child",
            "calleeId" to calleeId,
            "callerName" to "자녀",
            "expiresAt" to expiresAt.toString(),
        )

    @Test
    fun answerWaitsForSystemRegistrationAndRejectsDuplicateRequests() {
        center.activeCall = incomingCall()
        assertFalse(center.canAnswerIncomingCall)
        center.incomingCallReported = true
        assertTrue(center.canAnswerIncomingCall)
        center.incomingAnswerRequested = true
        assertFalse(center.canAnswerIncomingCall)
        center.incomingAnswerRequested = false
        center.activeCall = null
        assertFalse(center.canAnswerIncomingCall)
    }

    @Test
    fun onlyRingingCallsOfferAnswer() {
        for (phase in listOf(CallPhase.Connecting, CallPhase.Active, CallPhase.Reconnecting, CallPhase.Ended)) {
            center.incomingCallReported = true
            center.activeCall = incomingCall(phase)
            assertFalse(center.canAnswerIncomingCall)
        }
    }

    @Test
    fun outgoingCallNeverOffersAnswer() {
        center.incomingCallReported = true
        center.activeCall = CallCenter.ActiveCall(
            id = "outgoing",
            uuid = UUID.randomUUID(),
            direction = CallCenter.Direction.Outgoing,
            peerId = "peer",
            peerName = "가족",
            phase = CallPhase.Ringing,
            questions = emptyList(),
        )
        assertFalse(center.canAnswerIncomingCall)
    }

    @Test
    fun pushForAnotherUserIsIgnored() = runTest {
        center.handleIncomingPush(push(calleeId = "someone-else"))
        assertNull(center.activeCall)
        assertTrue(platform.callSystem.added.isEmpty())
        assertTrue(fixture.paths.isEmpty())
    }

    @Test
    fun expiredPushIsIgnored() = runTest {
        center.handleIncomingPush(push(expiresAt = Instant.now().minusSeconds(1)))
        assertNull(center.activeCall)
        assertTrue(platform.shownIncoming.isEmpty())
    }

    @Test
    fun validPushRingsThroughTheSystemAndNotification() = runTest {
        fixture.respond = { ringing(it) }
        center.handleIncomingPush(push())
        val call = center.activeCall!!
        assertEquals(CallPhase.Ringing, call.phase)
        assertEquals(CallCenter.Direction.Incoming, call.direction)
        assertEquals(listOf("call-1"), platform.shownIncoming)
        assertEquals(1, platform.callSystem.added.size)
        assertTrue(platform.callSystem.added.single().incoming)
        assertTrue(center.canAnswerIncomingCall)
    }

    @Test
    fun incomingNotificationIsPostedAgainOnceTelecomHasTheCallWhenNotificationsAreBlocked() = runTest {
        // 알림 권한이 없으면 첫 알림은 버려진다. Telecom 등록 뒤에야 자체 관리 통화의 CallStyle 예외가 적용된다.
        fixture.respond = { ringing(it) }
        platform.notificationsAllowed = false
        center.handleIncomingPush(push())
        assertEquals(listOf("call-1", "call-1"), platform.shownIncoming)
    }

    @Test
    fun secondPushWhileBusyIsDeclined() = runTest {
        fixture.respond = { request ->
            if (request.url.encodedPath.endsWith("/decline")) 204 to "" else
                ringing(request)
        }
        center.handleIncomingPush(push(callId = "call-1"))
        center.handleIncomingPush(push(callId = "call-2"))
        assertEquals("call-1", center.activeCall?.id)
        fixture.awaitPath("/v1/calls/call-2/decline")
    }

    @Test
    fun endingAnUnansweredCallDeclinesItAndRejectsTheSystemCall() = runTest {
        fixture.respond = { request ->
            if (request.url.encodedPath.endsWith("/decline")) 204 to "" else
                ringing(request)
        }
        center.handleIncomingPush(push())
        val uuid = center.activeCall!!.uuid
        center.endActiveCall()
        assertNull(center.activeCall)
        fixture.awaitPath("/v1/calls/call-1/decline")
        assertEquals(listOf(uuid to EndCause.Rejected), platform.callSystem.disconnected)
        assertTrue(platform.incomingCancelled > 0)
    }

    @Test
    fun systemDisconnectTearsDownWithoutReportingBack() = runTest {
        fixture.respond = { request ->
            if (request.url.encodedPath.endsWith("/decline")) 204 to "" else
                ringing(request)
        }
        center.handleIncomingPush(push())
        val uuid = center.activeCall!!.uuid
        platform.callSystem.events.getValue(uuid).onDisconnect()
        assertNull(center.activeCall)
        assertTrue(platform.callSystem.disconnected.isEmpty())
        fixture.awaitPath("/v1/calls/call-1/decline")
    }

    @Test
    fun deniedMicrophoneFailsTheAnswerAndPointsToSettings() = runTest {
        fixture.respond = { request ->
            if (request.url.encodedPath.endsWith("/decline")) 204 to "" else
                ringing(request)
        }
        platform.microphoneGranted = false
        center.handleIncomingPush(push())
        center.answerIncomingCall()
        assertNull(center.activeCall)
        assertTrue(center.needsMicrophoneSettings)
        assertEquals("통화하려면 휴대폰 설정에서 마이크를 허용해주세요", center.callError)
        fixture.awaitPath("/v1/calls/call-1/decline")
    }

    @Test
    fun accountChangeWhilePreparingAnOutgoingCallTakesItOffTheSystem() {
        val uuid = UUID.randomUUID()
        center.pendingOutgoing = CallCenter.PendingOutgoing(uuid, "child", "자녀")
        center.systemCalls += uuid
        center.callOwnerId = "someone-else"
        center.sessionDidChange()
        assertNull(center.pendingOutgoing)
        assertEquals(listOf(uuid to EndCause.Local), platform.callSystem.disconnected)
    }

    @Test
    fun staleNotificationDeclinesOnTheServer() = runTest {
        fixture.respond = { 204 to "" }
        center.declineStaleCall("old-call")
        fixture.awaitPath("/v1/calls/old-call/decline")
        assertTrue(platform.incomingCancelled > 0)
    }

    @Test
    fun answeringAfterTheSystemAlreadyAnsweredStillAcceptsOnTheServer() = runTest {
        fixture.respond = { request ->
            when {
                request.url.encodedPath.endsWith("/accept") -> 409 to """{"code":"ALREADY","message":"fixture"}"""
                else -> ringing(request)
            }
        }
        center.handleIncomingPush(push())
        val uuid = center.activeCall!!.uuid
        platform.callSystem.answers = false
        center.systemAnswered += uuid
        center.answerIncomingCall()
        fixture.awaitPath("/v1/calls/call-1/accept")
    }

    @Test
    fun answerTappedBeforeSystemRegistrationRunsOnceRegistered() = runTest {
        fixture.respond = { request ->
            when {
                request.url.encodedPath.endsWith("/accept") -> 409 to """{"code":"GONE","message":"fixture"}"""
                else -> ringing(request)
            }
        }
        val registered = CompletableDeferred<Unit>()
        platform.callSystem.addGate = registered
        center.handleIncomingPush(push())
        assertFalse(center.incomingCallReported)
        // 알림의 받기를 Telecom 등록보다 먼저 눌렀다
        center.answerIncomingCall()
        assertTrue(fixture.paths.none { it.endsWith("/accept") })
        registered.complete(Unit)
        fixture.awaitPath("/v1/calls/call-1/accept")
    }

    private val accepted = """
        {"callId":"incoming","livekitUrl":"ws://fixture","roomName":"room","accessToken":"token",
        "rawCaptureRequired":false,"audioConstraints":{"echoCancellation":true,"noiseSuppression":true,
        "autoGainControl":true,"dtx":false,"audioBitrate":32000,"rawCaptureSampleRate":16000}}
    """.trimIndent()

    @Test
    fun failingAfterTheServerAlreadyAcceptedHereEndsInsteadOfDeclining() = runTest {
        var acceptBody = ""
        fixture.respond = { request ->
            when {
                // 받기 응답을 못 받았지만 서버에서는 이 기기의 받기로 이미 ACTIVE다
                request.url.encodedPath.endsWith("/decline") -> 409 to """{"detail":"이미 처리된 통화입니다"}"""
                request.url.encodedPath.endsWith("/accept") -> {
                    acceptBody = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                    200 to accepted
                }
                else -> 200 to "{}"
            }
        }
        val call = incomingCall()
        center.activeCall = call
        center.answeringCallIds += call.id
        center.acceptRequestIds[call.id] = "request-1"
        center.failCall(call.uuid, "통화 연결이 끊어졌어요")
        fixture.awaitPath("/v1/calls/incoming/end")
        assertEquals(
            listOf("/v1/calls/incoming/decline", "/v1/calls/incoming/accept", "/v1/calls/incoming/end"),
            fixture.paths.toList(),
        )
        assertTrue(acceptBody.contains("request-1"))
    }

    @Test
    fun failingDoesNotEndACallAnotherDeviceAnswered() = runTest {
        fixture.respond = { request ->
            when {
                request.url.encodedPath.endsWith("/decline") -> 409 to """{"detail":"이미 처리된 통화입니다"}"""
                // 같은 사람의 다른 기기가 받았다: 이 기기의 요청 ID로는 받기가 거절된다
                request.url.encodedPath.endsWith("/accept") -> 409 to """{"detail":"이미 종료되었거나 응답한 통화입니다"}"""
                else -> 200 to "{}"
            }
        }
        val call = incomingCall()
        center.activeCall = call
        center.answeringCallIds += call.id
        center.acceptRequestIds[call.id] = "request-1"
        center.failCall(call.uuid, "통화 연결이 끊어졌어요")
        fixture.awaitPath("/v1/calls/incoming/accept")
        Thread.sleep(200)
        assertEquals(listOf("/v1/calls/incoming/decline", "/v1/calls/incoming/accept"), fixture.paths.toList())
    }

    @Test
    fun failingWithoutSendingAcceptOnlyDeclinesEvenWhenTheCallIsActive() = runTest {
        // 이 기기는 받기를 보낸 적이 없다(상태 확인 실패 등). 다른 기기가 받은 통화일 수 있으니 끝내지 않는다.
        fixture.respond = { request ->
            if (request.url.encodedPath.endsWith("/decline")) 409 to """{"detail":"이미 처리된 통화입니다"}"""
            else 200 to "{}"
        }
        val call = incomingCall()
        center.activeCall = call
        center.failCall(call.uuid, "통화 상태를 확인할 수 없어 통화를 종료했어요.")
        fixture.awaitPath("/v1/calls/incoming/decline")
        Thread.sleep(200)
        assertEquals(listOf("/v1/calls/incoming/decline"), fixture.paths.toList())
    }

    @Test
    fun failingAnUnansweredCallOnlyDeclinesIt() = runTest {
        fixture.respond = { 204 to "" }
        val call = incomingCall()
        center.activeCall = call
        center.failCall(call.uuid, "통화 연결이 끊어졌어요")
        fixture.awaitPath("/v1/calls/incoming/decline")
        assertEquals(listOf("/v1/calls/incoming/decline"), fixture.paths.toList())
    }
}
