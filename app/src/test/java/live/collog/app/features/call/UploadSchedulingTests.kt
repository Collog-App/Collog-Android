package live.collog.app.features.call

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import live.collog.app.TestEnvironment
import live.collog.app.features.call.services.CallCenter
import live.collog.app.features.call.services.drainPendingUploads
import live.collog.app.features.call.services.resumePendingUploads
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 통화 원음이 대기열에 남아 있으면 앱이 뒤로 가도 이어 가도록 업로드 작업을 예약한다(iOS `beginBackgroundTask` 대응). */
class UploadSchedulingTests {
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
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        platform.cleanUp()
        fixture.cleanUp()
    }

    private fun enqueueRecording() {
        val file = File(platform.uploadDirectory.parentFile, "call.wav").apply { writeBytes(ByteArray(32)) }
        val owner = fixture.environment.session.user!!.id
        center.uploads!!.enqueue(file, callId = "call-1", ownerId = owner, durationSec = 1.0, sampleRate = 16_000)
    }

    private fun uploadServer(request: Request): Pair<Int, String> = when (request.url.encodedPath) {
        "/v1/calls/call-1" -> 200 to """{"state":"ENDED","endedAt":null,"recordingEnabled":true}"""
        "/v1/calls/call-1/raw-audio/upload-url" ->
            200 to """{"uploadUrl":"http://localhost/upload/raw","assetId":"asset-1","expiresIn":600}"""
        else -> 200 to "{}"
    }

    @Test
    fun pendingRecordingSchedulesBackgroundUpload() {
        fixture.respond = { 503 to """{"code":"UNAVAILABLE","message":"잠시 후 다시 시도해주세요"}""" }
        enqueueRecording()
        center.resumePendingUploads()
        assertEquals(1, platform.uploadWorkScheduled)
    }

    @Test
    fun nothingPendingSchedulesNothing() {
        center.resumePendingUploads()
        assertEquals(0, platform.uploadWorkScheduled)
    }

    @Test
    fun backgroundWorkRetriesWhileTheServerIsUnavailable() = runBlocking(Dispatchers.Main) {
        fixture.respond = { 503 to """{"code":"UNAVAILABLE","message":"잠시 후 다시 시도해주세요"}""" }
        enqueueRecording()
        assertTrue(center.drainPendingUploads())
    }

    @Test
    fun backgroundWorkFinishesOnceTheRecordingIsUploaded() = runBlocking(Dispatchers.Main) {
        fixture.respond = ::uploadServer
        enqueueRecording()
        assertFalse(center.drainPendingUploads())
        assertTrue("/v1/calls/call-1/raw-audio/complete" in fixture.paths)
    }
}
