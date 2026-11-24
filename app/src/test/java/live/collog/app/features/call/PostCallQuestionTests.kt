package live.collog.app.features.call

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
import live.collog.app.TestEnvironment
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.handleIncomingPush
import live.collog.app.shared.services.AppEventCode
import live.collog.app.shared.services.AppEvents
import live.collog.app.shared.services.api.AppEventDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 통화 뒤 한 줄 질문(`post_call_answer`): 건 사람에게만, 추천 질문이 보였고 실제로 연결됐던 통화만, 통화마다 한 번.
 * 질문이 떠 있어도 통화는 이미 정리돼(서비스·시스템 통화·방) 다음 통화를 막지 않는다.
 */
class PostCallQuestionTests {
    private lateinit var fixture: TestEnvironment
    private lateinit var platform: FakeCallPlatform
    private lateinit var scope: CoroutineScope
    private lateinit var center: CallCenter
    private val statusHold = CountDownLatch(1)

    @Before
    fun setUp() {
        fixture = TestEnvironment(recordsEvents = true)
        platform = FakeCallPlatform()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        center = CallCenter(fixture.environment, platform, scope)
        fixture.respond = { request ->
            if (request.method == "GET" && request.url.encodedPath.startsWith("/v1/calls/")) statusHold.await(5, TimeUnit.SECONDS)
            200 to """{"state":"RINGING","endedAt":null,"recordingEnabled":true}"""
        }
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        statusHold.countDown()
        platform.cleanUp()
        fixture.cleanUp()
    }

    private fun call(
        direction: CallCenter.Direction = CallCenter.Direction.Outgoing,
        connected: Boolean = true,
        questions: List<String> = listOf("오늘 산책하셨어요?", "식사는 하셨어요?"),
    ) = CallCenter.ActiveCall(
        id = "call-1",
        uuid = UUID.randomUUID(),
        direction = direction,
        peerId = "mom",
        peerName = "엄마",
        phase = if (connected) CallPhase.Active else CallPhase.Ringing,
        questions = questions,
        connectedAtMillis = if (connected) 1_000L else null,
    )

    /** 통화를 시작한 것처럼 둔다(시작하면 CallCenter 가 건 사람을 적는다). */
    private fun start(call: CallCenter.ActiveCall) {
        center.activeCall = call
        center.callOwnerId = "me"
    }

    private fun answers(): List<AppEventDto> =
        fixture.environment.events.queued.map { it.event }.filter { it.name == AppEvents.POST_CALL_ANSWER }

    @Test
    fun callerIsAskedOnceAfterAConnectedCallWithRecommendedQuestions() {
        start(call())

        center.finishRemoteCall()

        val question = center.postCallQuestion
        assertNotNull(question)
        assertEquals("call-1", question!!.callId)
        assertEquals("엄마", question.peerName)
        assertEquals(listOf("오늘 산책하셨어요?", "식사는 하셨어요?"), question.questions)
        // 통화는 이미 끝났다: 통화 서비스·시스템 통화·방이 남지 않는다
        assertNull(center.activeCall)
        assertEquals(1, platform.stoppedOngoing)
        assertTrue(center.systemCalls.isEmpty())
        assertNull(center.room)
        assertTrue(answers().isEmpty())

        center.answerPostCallQuestion(AppEventCode.YES)
        center.answerPostCallQuestion(AppEventCode.NO)

        val answer = answers().single()
        assertEquals("YES", answer.code)
        assertEquals("mom", answer.subjectId)
        assertEquals("call-1", answer.callId)
        assertNull(center.postCallQuestion)
    }

    @Test
    fun callerHangingUpIsAskedToo() {
        start(call())

        center.endActiveCall()

        assertNotNull(center.postCallQuestion)
        center.answerPostCallQuestion(AppEventCode.NOT_SEEN)
        assertEquals("NOT_SEEN", answers().single().code)
    }

    @Test
    fun questionIsDroppedTwoMinutesAfterTheCallEnds() {
        start(call())
        center.finishRemoteCall()
        val endedAt = center.postCallQuestion!!.endedAtMillis
        assertTrue(System.currentTimeMillis() - endedAt in 0..5_000)

        // 앱이 1분 59초 뒤 앞으로 왔다: 아직 묻는다
        center.dropExpiredPostCallQuestion(endedAt + CallCenter.POST_CALL_QUESTION_TTL_MILLIS - 1)
        assertNotNull(center.postCallQuestion)
        // 2분이 지났다: 몇 시간 뒤 앱을 열어도 지난 통화를 묻지 않는다
        center.dropExpiredPostCallQuestion(endedAt + CallCenter.POST_CALL_QUESTION_TTL_MILLIS)
        assertNull(center.postCallQuestion)
        center.answerPostCallQuestion(AppEventCode.YES)
        assertTrue(answers().isEmpty())
    }

    @Test
    fun closingWithoutAnAnswerRecordsNothing() {
        start(call())
        center.finishRemoteCall()

        center.dismissPostCallQuestion()

        assertNull(center.postCallQuestion)
        center.answerPostCallQuestion(AppEventCode.YES)
        assertTrue(answers().isEmpty())
    }

    @Test
    fun calleeIsNotAsked() {
        start(call(direction = CallCenter.Direction.Incoming))
        center.finishRemoteCall()
        assertNull(center.postCallQuestion)
    }

    @Test
    fun callThatNeverConnectedIsNotAsked() {
        start(call(connected = false))
        center.finishRemoteCall()
        assertNull(center.postCallQuestion)

        // 연결 전에 내가 끊어도
        start(call(connected = false))
        center.endActiveCall()
        assertNull(center.postCallQuestion)
    }

    @Test
    fun callWithoutRecommendedQuestionsIsNotAsked() {
        start(call(questions = emptyList()))
        center.finishRemoteCall()
        assertNull(center.postCallQuestion)
    }

    @Test
    fun failedCallIsNotAskedEvenAfterConnecting() {
        val connected = call()
        start(connected)

        // 연결됐다가 네트워크로 끊겼다: 실패 안내가 먼저다
        center.failCall(connected.uuid, "통화 연결이 끊어졌어요.", failure = AppEventCode.NETWORK)

        assertEquals("통화 연결이 끊어졌어요.", center.callError)
        assertNull(center.postCallQuestion)
    }

    @Test
    fun callEndedBecauseItsStatusIsGoneIsNotAsked() = runBlocking {
        fixture.respond = { 404 to """{"code":"NOT_FOUND","message":"없음"}""" }
        start(call())

        center.monitorCall("call-1")

        withContext(Dispatchers.Default) {
            withTimeout(2_000) { while (center.activeCall != null) delay(10) }
        }
        assertNull(center.postCallQuestion)
    }

    @Test
    fun aNewIncomingCallReplacesTheQuestion() {
        start(call())
        center.finishRemoteCall()
        assertNotNull(center.postCallQuestion)

        center.handleIncomingPush(
            mapOf(
                "type" to "incoming_call",
                "callId" to "call-2",
                "callUUID" to UUID.randomUUID().toString(),
                "callerId" to "child",
                "calleeId" to "me",
                "callerName" to "자녀",
                "expiresAt" to Instant.now().plusSeconds(60).toString(),
            ),
        )

        assertNull(center.postCallQuestion)
        assertEquals("call-2", center.activeCall?.id)
        assertEquals(listOf("call-2"), platform.shownIncoming)
    }

    @Test
    fun startingAnotherCallDropsTheQuestion() {
        start(call())
        center.finishRemoteCall()
        platform.microphoneGranted = false

        center.startOutgoingCall("mom", "엄마", emptyList())

        assertNull(center.postCallQuestion)
    }

    @Test
    fun signingOutDropsTheQuestion() {
        start(call())
        center.finishRemoteCall()
        assertNotNull(center.postCallQuestion)

        fixture.environment.session.signOut()
        center.sessionDidChange()

        assertNull(center.postCallQuestion)
    }
}
