package live.collog.app.features.call

import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import live.collog.app.TestEnvironment
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.markCallActive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/** 통화 시작 시각은 CallCenter가 들고 있어 통화 화면이 새로 만들어져도 시간이 이어진다. */
class CallConnectedAtTests {
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
        center.serverAccepted = true
        center.isMediaConnected = true
        center.isAudioSessionActive = true
        center.microphoneReady = true
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        platform.cleanUp()
        fixture.cleanUp()
    }

    private fun call(phase: CallPhase, connectedAtMillis: Long? = null) = CallCenter.ActiveCall(
        id = "call-1",
        uuid = UUID.randomUUID(),
        direction = CallCenter.Direction.Incoming,
        peerId = "child",
        peerName = "자녀",
        phase = phase,
        questions = emptyList(),
        connectedAtMillis = connectedAtMillis,
    )

    @Test
    fun firstActivationRecordsConnectedAt() {
        center.activeCall = call(CallPhase.Connecting)
        center.markCallActive()
        assertEquals(CallPhase.Active, center.activeCall?.phase)
        assertNotNull(center.activeCall?.connectedAtMillis)
    }

    @Test
    fun reactivationAfterReconnectKeepsTheOriginalConnectedAt() {
        center.activeCall = call(CallPhase.Reconnecting, connectedAtMillis = 1_000L)
        center.markCallActive()
        assertEquals(CallPhase.Active, center.activeCall?.phase)
        assertEquals(1_000L, center.activeCall?.connectedAtMillis)
    }
}
