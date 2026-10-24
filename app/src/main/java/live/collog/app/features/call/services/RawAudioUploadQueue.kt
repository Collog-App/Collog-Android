package live.collog.app.features.call.services

import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import live.collog.app.shared.services.api.RawAudioUpload

// 자리 파일: port/call-audio 브랜치의 구현으로 바뀐다(시그니처 약속만 둔다).
class RawAudioUploadQueue(
    directory: File,
    currentOwner: () -> String?,
    now: () -> Instant = Instant::now,
    retryDelayMillis: Long = 10_000,
    validatePermission: suspend (callId: String) -> Unit = {},
    requestUpload: suspend (callId: String, durationSec: Double, sampleRate: Int) -> RawAudioUpload,
    upload: suspend (file: File, url: String) -> Unit,
    complete: suspend (callId: String, assetId: String) -> Unit,
    scope: CoroutineScope,
) {
    val hasPending: Boolean get() = false
    fun enqueue(file: File, callId: String, ownerId: String, durationSec: Double, sampleRate: Int) {}
    suspend fun resume() {}
    fun pause() {}
    fun discard(callId: String) {}
    fun discardAll() {}
    fun discardInvalidOwners() {}
}
