package live.collog.app.features.call

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import live.collog.app.features.call.services.RawAudioUploadQueue
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.RawAudioUpload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `RawAudioUploadQueueTests`. */
@OptIn(ExperimentalCoroutinesApi::class)
class RawAudioUploadQueueTests {
    private val fixture = UploadFixture()

    @After
    fun tearDown() = fixture.cleanup()

    @Test
    fun transientUploadFailureSurvivesQueueRecreation() = runTest {
        fixture.uploadError = ApiError.Transport("offline")
        // iOS에서 queue = nil로 놓아주는 것처럼, 첫 대기열의 작업 범위를 통째로 닫는다
        val firstScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val queue = fixture.makeQueue(firstScope)
        fixture.enqueue(queue)
        queue.resume()
        assertTrue(queue.hasPending)
        assertEquals(1, fixture.requests)
        firstScope.cancel()

        fixture.uploadError = null
        val restored = fixture.makeQueue(backgroundScope)
        restored.resume()

        assertFalse(restored.hasPending)
        assertEquals(1, fixture.requests)
        assertEquals(2, fixture.uploads)
        assertEquals(1, fixture.completions)
    }

    @Test
    fun lostCompletionResponseRetriesOnlyCompletion() = runTest {
        fixture.completeError = ApiError.Transport("response lost")
        val queue = fixture.makeQueue(backgroundScope)
        fixture.enqueue(queue)
        queue.resume()
        fixture.completeError = null
        queue.resume()

        assertFalse(queue.hasPending)
        assertEquals(1, fixture.requests)
        assertEquals(1, fixture.uploads)
        assertEquals(2, fixture.completions)
    }

    @Test
    fun differentAccountCannotUploadRetainedAudio() = runTest {
        val queue = fixture.makeQueue(backgroundScope)
        fixture.enqueue(queue)
        fixture.owner = "other-user"
        queue.resume()

        assertFalse(queue.hasPending)
        assertEquals(0, fixture.requests)
        assertTrue(fixture.files().isEmpty())
    }

    @Test
    fun expiredQueueEntryIsDeletedWithoutNetworkRequest() = runTest {
        val queue = fixture.makeQueue(backgroundScope)
        fixture.enqueue(queue)
        fixture.date = fixture.date.plusSeconds(901)
        queue.resume()

        assertFalse(queue.hasPending)
        assertEquals(0, fixture.requests)
        assertTrue(fixture.files().isEmpty())
    }

    @Test
    fun terminalResponseDeletesAudio() = runTest {
        fixture.uploadError = ApiError.Server(status = 410, code = "EXPIRED", serverMessage = "expired")
        val queue = fixture.makeQueue(backgroundScope)
        fixture.enqueue(queue)
        queue.resume()

        assertFalse(queue.hasPending)
        assertTrue(fixture.files().isEmpty())
    }

    @Test
    fun discardDuringUploadPreventsCompletion() = runTest {
        val queue = fixture.makeQueue(backgroundScope)
        fixture.onUpload = { queue.discardAll() }
        fixture.enqueue(queue)
        queue.resume()

        assertEquals(0, fixture.completions)
        assertFalse(queue.hasPending)
        assertTrue(fixture.files().isEmpty())
    }

    @Test
    fun accountChangeDuringUploadPreventsCompletion() = runTest {
        val queue = fixture.makeQueue(backgroundScope)
        fixture.onUpload = { fixture.owner = "other-user" }
        fixture.enqueue(queue)
        queue.resume()

        assertEquals(0, fixture.completions)
        assertFalse(queue.hasPending)
        assertTrue(fixture.files().isEmpty())
    }

    @Test
    fun signOutImmediatelyPurgesPendingAudio() = runTest {
        val queue = fixture.makeQueue(backgroundScope)
        fixture.enqueue(queue)
        fixture.owner = null
        queue.discardInvalidOwners()

        assertFalse(queue.hasPending)
        assertTrue(fixture.files().isEmpty())
    }

    @Test
    fun discardCallDuringUploadCannotRestoreJob() = runTest {
        val queue = fixture.makeQueue(backgroundScope)
        fixture.onUpload = { queue.discard("call") }
        fixture.enqueue(queue)
        queue.resume()

        assertEquals(0, fixture.completions)
        assertFalse(queue.hasPending)
        assertTrue(fixture.files().isEmpty())
    }

    @Test
    fun pauseDuringUploadPreservesJobWithoutCompletingIt() = runTest {
        val queue = fixture.makeQueue(backgroundScope)
        fixture.onUpload = { queue.pause() }
        fixture.enqueue(queue)
        queue.resume()

        assertEquals(0, fixture.completions)
        assertTrue(queue.hasPending)

        fixture.onUpload = null
        queue.resume()
        assertEquals(1, fixture.completions)
        assertFalse(queue.hasPending)
    }

    @Test
    fun revokedPermissionPreventsCachedUploadAndPurgesAudio() = runTest {
        fixture.uploadError = ApiError.Transport("offline")
        val queue = fixture.makeQueue(backgroundScope)
        fixture.enqueue(queue)
        queue.resume()
        assertTrue(queue.hasPending)

        fixture.uploadError = null
        fixture.permissionError = ApiError.Server(status = 403, code = "CONSENT_REQUIRED", serverMessage = "denied")
        queue.resume()

        assertFalse(queue.hasPending)
        assertEquals(1, fixture.uploads)
        assertEquals(0, fixture.completions)
        assertTrue(fixture.files().isEmpty())
    }

    // 아래는 iOS에 따로 없는, 같은 규칙을 직접 확인하는 테스트다

    @Test
    fun remainingJobRetriesAfterDelay() = runTest {
        fixture.uploadError = ApiError.Transport("offline")
        val queue = fixture.makeQueue(backgroundScope)
        fixture.enqueue(queue)
        queue.resume()
        fixture.uploadError = null

        advanceTimeBy(RETRY_DELAY_MILLIS - 1)
        runCurrent()
        assertEquals(1, fixture.uploads)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(2, fixture.uploads)
        assertEquals(1, fixture.completions)
        assertFalse(queue.hasPending)
    }

    @Test
    fun retryRulesFollowErrorKind() {
        val retryable = listOf(
            ApiError.Server(408, "TIMEOUT", "timeout"),
            ApiError.Server(429, "RATE_LIMITED", "busy"),
            ApiError.Server(500, "INTERNAL", "error"),
            ApiError.Server(503, "UNAVAILABLE", "error"),
            ApiError.Transport("offline"),
            ApiError.Decoding("bad"),
            IllegalStateException("unknown"),
        )
        val terminal = listOf(
            ApiError.Unauthenticated,
            ApiError.Server(400, "BAD_REQUEST", "bad"),
            ApiError.Server(404, "NOT_FOUND", "missing"),
        )
        for (error in retryable + terminal) {
            val case = UploadFixture()
            try {
                runTest {
                    case.uploadError = error
                    val queue = case.makeQueue(backgroundScope)
                    case.enqueue(queue)
                    queue.resume()
                    assertEquals("$error", error in retryable, queue.hasPending)
                }
            } finally {
                case.cleanup()
            }
        }
    }

    @Test
    fun uploadUrlIsReusedUntilFiveSecondsBeforeExpiry() = runTest {
        fixture.uploadError = ApiError.Transport("offline")
        val queue = fixture.makeQueue(backgroundScope)
        fixture.enqueue(queue)
        queue.resume()
        fixture.date = fixture.date.plusSeconds(894)
        queue.resume()
        assertEquals(1, fixture.requests)

        fixture.date = fixture.date.plusSeconds(1)
        queue.resume()
        assertEquals(2, fixture.requests)
        assertEquals(3, fixture.uploads)
    }

    @Test
    fun enqueueRejectsOtherOwnerAndInvalidMetadata() = runTest {
        val queue = fixture.makeQueue(backgroundScope)
        val file = File(fixture.directory, "source.wav").apply { writeText("audio") }
        assertThrows(ApiError.Unauthenticated::class.java) {
            queue.enqueue(file, "call", "other-user", 30.0, 16_000)
        }
        assertThrows(ApiError.Unauthenticated::class.java) {
            queue.enqueue(file, "call", "parent", Double.NaN, 16_000)
        }
        assertThrows(ApiError.Unauthenticated::class.java) {
            queue.enqueue(file, "call", "parent", 0.0, 16_000)
        }
        assertThrows(ApiError.Unauthenticated::class.java) {
            queue.enqueue(file, "call", "parent", 30.0, 0)
        }
        assertTrue(file.exists())
        assertFalse(queue.hasPending)

        queue.enqueue(file, "call", "parent", 30.0, 16_000)
        assertFalse(file.exists())
        assertTrue(queue.hasPending)
        assertEquals(setOf("json", "wav"), fixture.files().map { it.extension }.toSet())
    }

    @Test
    fun staleFilesArePurgedOnCreation() = runTest {
        val queueDirectory = File(fixture.directory, "queue").apply { mkdirs() }
        val stale = File(queueDirectory, "stale.wav").apply { writeText("audio") }
        stale.setLastModified(fixture.date.minusSeconds(86_400).toEpochMilli())
        val fresh = File(queueDirectory, "fresh.wav").apply { writeText("audio") }
        fresh.setLastModified(fixture.date.minusSeconds(86_399).toEpochMilli())
        fixture.makeQueue(backgroundScope)

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
    }

    private companion object {
        const val RETRY_DELAY_MILLIS = 3_600_000L
    }

    private class UploadFixture {
        val directory: File = Files.createTempDirectory("upload-queue").toFile()
        var owner: String? = "parent"
        var date: Instant = Instant.now()
        var requests = 0
        var uploads = 0
        var completions = 0
        var uploadError: Exception? = null
        var completeError: Exception? = null
        var permissionError: Exception? = null
        var onUpload: (() -> Unit)? = null

        fun makeQueue(scope: CoroutineScope) = RawAudioUploadQueue(
            directory = File(directory, "queue"),
            currentOwner = { owner },
            now = { date },
            retryDelayMillis = RETRY_DELAY_MILLIS,
            validatePermission = { permissionError?.let { throw it } },
            requestUpload = { _, _, _ ->
                requests += 1
                RawAudioUpload(uploadUrl = "https://storage.example/audio", assetId = "asset", expiresIn = 900)
            },
            upload = { _, _ ->
                uploads += 1
                onUpload?.invoke()
                uploadError?.let { throw it }
            },
            complete = { _, _ ->
                completions += 1
                completeError?.let { throw it }
            },
            scope = scope,
        )

        fun enqueue(queue: RawAudioUploadQueue) {
            val file = File(directory, UUID.randomUUID().toString())
            file.writeText("audio")
            queue.enqueue(file, callId = "call", ownerId = "parent", durationSec = 30.0, sampleRate = 16_000)
        }

        fun files(): List<File> = File(directory, "queue").listFiles()?.toList().orEmpty()

        fun cleanup() {
            directory.deleteRecursively()
        }
    }
}
