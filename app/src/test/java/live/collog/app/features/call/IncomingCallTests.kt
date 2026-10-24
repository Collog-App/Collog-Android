package live.collog.app.features.call

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.EndCause
import live.collog.app.features.call.services.handleIncomingPush
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

    @After
    fun tearDown() {
        scope.cancel()
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
        fixture.respond = { 200 to """{"state":"RINGING","endedAt":null,"recordingEnabled":true}""" }
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
    fun secondPushWhileBusyIsDeclined() = runTest {
        fixture.respond = { request ->
            if (request.url.encodedPath.endsWith("/decline")) 204 to "" else
                200 to """{"state":"RINGING","endedAt":null,"recordingEnabled":true}"""
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
                200 to """{"state":"RINGING","endedAt":null,"recordingEnabled":true}"""
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
                200 to """{"state":"RINGING","endedAt":null,"recordingEnabled":true}"""
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
                200 to """{"state":"RINGING","endedAt":null,"recordingEnabled":true}"""
        }
        platform.microphoneGranted = false
        center.handleIncomingPush(push())
        center.answerIncomingCall()
        assertNull(center.activeCall)
        assertTrue(center.needsMicrophoneSettings)
        assertEquals("통화하려면 휴대폰 설정에서 마이크를 허용해주세요", center.callError)
        fixture.awaitPath("/v1/calls/call-1/decline")
    }
}
