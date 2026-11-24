package live.collog.app.features.call

import android.content.SharedPreferences
import io.livekit.android.events.DisconnectReason
import io.livekit.android.room.RoomException
import java.io.IOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import live.collog.app.MemoryPreferences
import live.collog.app.TestEnvironment
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.models.CallSafety
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.handleIncomingPush
import live.collog.app.features.call.services.incomingCallShown
import live.collog.app.shared.services.AppEventCode
import live.collog.app.shared.services.AppEvents
import live.collog.app.shared.services.api.AppEventDto
import live.collog.app.shared.services.api.ApiError
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 쓰기가 늘 실패하는 이용 기록 저장소. */
private class BrokenPreferences : SharedPreferences by MemoryPreferences() {
    override fun edit(): SharedPreferences.Editor = throw IllegalStateException("저장 공간이 없어요")
}

/** 통화 이용 기록: `incoming_call_shown`(울림 화면이 떴을 때 통화마다 한 번), `call_connect_failed`(실패로 끝났을 때만). */
class CallEventTests {
    private lateinit var fixture: TestEnvironment
    private lateinit var platform: FakeCallPlatform
    private lateinit var scope: CoroutineScope
    private lateinit var center: CallCenter

    /** 상태 확인 응답은 테스트가 끝날 때까지 붙잡는다(IncomingCallTests 와 같은 이유: OkHttp 스레드가 상태를 동시에 고친다). */
    private val statusHold = CountDownLatch(1)

    @Before
    fun setUp() {
        fixture = TestEnvironment(recordsEvents = true)
        platform = FakeCallPlatform()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        center = CallCenter(fixture.environment, platform, scope)
        fixture.respond = ::reply
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        statusHold.countDown()
        platform.cleanUp()
        fixture.cleanUp()
    }

    private fun reply(request: Request): Pair<Int, String> {
        if (request.method == "GET" && request.url.encodedPath.startsWith("/v1/calls/")) statusHold.await(5, TimeUnit.SECONDS)
        return 200 to """{"state":"RINGING","endedAt":null,"recordingEnabled":true}"""
    }

    private fun events(name: String): List<AppEventDto> =
        fixture.environment.events.queued.map { it.event }.filter { it.name == name }

    private fun push(callId: String = "call-1") = mapOf(
        "type" to "incoming_call",
        "callId" to callId,
        "callUUID" to UUID.randomUUID().toString(),
        "callerId" to "child",
        "calleeId" to "me",
        "callerName" to "자녀",
        "expiresAt" to Instant.now().plusSeconds(60).toString(),
    )

    private fun call(direction: CallCenter.Direction, phase: CallPhase) = CallCenter.ActiveCall(
        id = "call-1",
        uuid = UUID.randomUUID(),
        direction = direction,
        peerId = "mom",
        peerName = "엄마",
        phase = phase,
        questions = listOf("오늘 산책하셨어요?"),
        connectedAtMillis = if (phase == CallPhase.Active) 1_000L else null,
    )

    /** OkHttp 스레드에서 끝나는 실패를 기다린다. */
    private fun awaitEvent(name: String) = runBlocking {
        withContext(Dispatchers.Default) {
            withTimeout(2_000) { while (events(name).isEmpty()) delay(10) }
        }
    }

    @Test
    fun ringingNotificationRecordsIncomingCallShownOnce() {
        center.handleIncomingPush(push())

        val shown = events(AppEvents.INCOMING_CALL_SHOWN).single()
        assertEquals("call-1", shown.callId)
        assertNull(shown.code)
        assertNull(shown.subjectId)
        // 앱을 보고 있어 앱 안 수신 화면도 그려졌다: 같은 통화는 다시 남기지 않는다
        center.incomingCallShown("call-1")
        assertEquals(1, events(AppEvents.INCOMING_CALL_SHOWN).size)
    }

    @Test
    fun withoutNotificationPermissionTheRepostAfterTelecomCounts() {
        platform.notificationsAllowed = false

        center.handleIncomingPush(push())

        // 처음 올린 알림은 버려지고, Telecom 등록 뒤 다시 올린 알림이 뜬다
        assertEquals(listOf("call-1", "call-1"), platform.shownIncoming)
        assertEquals("call-1", events(AppEvents.INCOMING_CALL_SHOWN).single().callId)
    }

    @Test
    fun whenNothingCouldShowOnlyTheAppScreenCounts() {
        platform.notificationsAllowed = false
        platform.callSystem.accepts = false

        center.handleIncomingPush(push())
        assertTrue(events(AppEvents.INCOMING_CALL_SHOWN).isEmpty())

        center.incomingCallShown("call-1")
        assertEquals("call-1", events(AppEvents.INCOMING_CALL_SHOWN).single().callId)
    }

    @Test
    fun appScreenDoesNotCountForOtherOrOutgoingCalls() {
        center.activeCall = call(CallCenter.Direction.Outgoing, CallPhase.Ringing)
        center.incomingCallShown("call-1")
        center.incomingCallShown("other")
        assertTrue(events(AppEvents.INCOMING_CALL_SHOWN).isEmpty())
    }

    @Test
    fun microphoneRefusedIsRecordedAsMicPermission() {
        platform.microphoneGranted = false

        center.startOutgoingCall("mom", "엄마", emptyList())

        val failed = events(AppEvents.CALL_CONNECT_FAILED).single()
        assertEquals(AppEventCode.MIC_PERMISSION, failed.code)
        assertNull(failed.callId)
    }

    @Test
    fun callCreationFailuresAreRecordedByCause() {
        fixture.respond = { request ->
            if (request.url.encodedPath == "/v1/calls") 503 to """{"code":"UNAVAILABLE","message":"잠시 후"}""" else reply(request)
        }

        center.startOutgoingCall("mom", "엄마", emptyList())

        awaitEvent(AppEvents.CALL_CONNECT_FAILED)
        assertEquals(AppEventCode.SERVER, events(AppEvents.CALL_CONNECT_FAILED).single().code)
    }

    @Test
    fun callThatNeverConnectedRecordsItsCallId() {
        val ringing = call(CallCenter.Direction.Outgoing, CallPhase.Ringing)
        center.activeCall = ringing

        center.failCall(ringing.uuid, "통화 연결이 끊어졌어요.", failure = CallSafety.disconnectFailure(DisconnectReason.SIGNAL_CLOSE))

        val failed = events(AppEvents.CALL_CONNECT_FAILED).single()
        assertEquals(AppEventCode.NETWORK, failed.code)
        assertEquals("call-1", failed.callId)
        assertNull(failed.subjectId)
    }

    @Test
    fun callThatDropsAfterConnectingIsNotAConnectFailure() {
        val active = call(CallCenter.Direction.Incoming, CallPhase.Active)
        center.activeCall = active

        center.failCall(active.uuid, "통화 연결이 끊어졌어요.", failure = CallSafety.disconnectFailure(DisconnectReason.SIGNAL_CLOSE))

        assertNull(center.activeCall)
        assertTrue(events(AppEvents.CALL_CONNECT_FAILED).isEmpty())
    }

    @Test
    fun failingToRecordNeverStopsTheCallCleanup() {
        // 이 환경의 Main 은 setUp 의 fixture 가 tearDown 에서 되돌린다
        val broken = TestEnvironment(recordsEvents = true, eventPreferences = BrokenPreferences())
        val brokenCenter = CallCenter(broken.environment, platform, scope)
        val ringing = call(CallCenter.Direction.Outgoing, CallPhase.Ringing)
        brokenCenter.activeCall = ringing

        brokenCenter.failCall(ringing.uuid, "통화를 연결하지 못했어요", failure = AppEventCode.SERVER)

        assertNull(brokenCenter.activeCall)
        assertEquals("통화를 연결하지 못했어요", brokenCenter.callError)
        assertEquals(1, platform.stoppedOngoing)
    }

    @Test
    fun hangingUpMissedAndDeclinedCallsAreNotFailures() {
        // 사용자가 끊었다
        center.activeCall = call(CallCenter.Direction.Outgoing, CallPhase.Active)
        center.endActiveCall()
        // 상대가 안 받았다·끊었다
        center.activeCall = call(CallCenter.Direction.Outgoing, CallPhase.Ringing)
        center.finishRemoteCall()
        // 받는 쪽이 거절했다
        center.activeCall = call(CallCenter.Direction.Incoming, CallPhase.Ringing)
        center.endActiveCall()

        assertTrue(events(AppEvents.CALL_CONNECT_FAILED).isEmpty())
    }

    @Test
    fun failureCodesFollowTheCause() {
        assertEquals(AppEventCode.TOKEN, CallSafety.connectFailure(ApiError.Unauthenticated))
        assertEquals(AppEventCode.NETWORK, CallSafety.connectFailure(ApiError.Transport("offline")))
        assertEquals(AppEventCode.SERVER, CallSafety.connectFailure(ApiError.Server(500, "X", "x")))
        assertEquals(AppEventCode.SERVER, CallSafety.connectFailure(ApiError.Server(409, "BUSY", "x")))
        // 마이크·오디오 준비 실패처럼 갈래가 애매한 것은 OTHER(마이크 권한 거절은 부르는 쪽이 MIC_PERMISSION 으로 넘긴다)
        assertEquals(AppEventCode.OTHER, CallSafety.connectFailure(SecurityException("RECORD_AUDIO")))
        // LiveKit: 서버가 토큰을 거절한 사유 / 연결이 안 됨 / 웹소켓 업그레이드가 401
        assertEquals(
            AppEventCode.TOKEN,
            CallSafety.connectFailure(RoomException.ConnectException("invalid token: token is expired", Exception("invalid token"))),
        )
        assertEquals(
            AppEventCode.NETWORK,
            CallSafety.connectFailure(RoomException.ConnectException("timeout", SocketTimeoutException("timeout"))),
        )
        assertEquals(
            AppEventCode.TOKEN,
            CallSafety.connectFailure(ProtocolException("Expected HTTP 101 response but was '401 Unauthorized'")),
        )
        assertEquals(AppEventCode.NETWORK, CallSafety.connectFailure(IOException("reset")))
        assertEquals(AppEventCode.OTHER, CallSafety.connectFailure(IllegalStateException("마이크 트랙을 만들지 못했어요")))

        assertEquals(AppEventCode.SERVER, CallSafety.disconnectFailure(DisconnectReason.SERVER_SHUTDOWN))
        assertEquals(AppEventCode.SERVER, CallSafety.disconnectFailure(DisconnectReason.PARTICIPANT_REMOVED))
        assertEquals(AppEventCode.NETWORK, CallSafety.disconnectFailure(DisconnectReason.SIGNAL_CLOSE))
        assertEquals(AppEventCode.NETWORK, CallSafety.disconnectFailure(DisconnectReason.UNKNOWN_REASON))
    }
}
