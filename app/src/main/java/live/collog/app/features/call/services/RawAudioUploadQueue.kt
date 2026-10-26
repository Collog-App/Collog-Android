package live.collog.app.features.call.services

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.RawAudioUpload

/**
 * iOS `RawAudioUploadQueue`. 통화 음성을 [directory]에 JSON + WAV로 남겨 두고 업로드를 이어 간다.
 * iOS의 `@MainActor`처럼 모든 호출과 [scope]의 작업은 한 스레드(메인)에서 돈다.
 * [scope]는 대기열보다 오래 살아야 하고(앱 범위), 한 폴더에는 인스턴스 하나만 둔다. 버릴 때는 먼저 [pause]를 부른다.
 */
class RawAudioUploadQueue(
    private val directory: File,
    private val currentOwner: () -> String?,
    private val now: () -> Instant = Instant::now,
    private val retryDelayMillis: Long = 10_000,
    private val validatePermission: suspend (callId: String) -> Unit = {},
    private val requestUpload: suspend (callId: String, durationSec: Double, sampleRate: Int) -> RawAudioUpload,
    private val upload: suspend (file: File, url: String) -> Unit,
    private val complete: suspend (callId: String, assetId: String) -> Unit,
    private val scope: CoroutineScope,
) {
    @Serializable
    private data class UploadTarget(val url: String, val assetId: String, val expiresAt: Long)

    @Serializable
    private data class UploadJob(
        val id: String,
        val callId: String,
        val ownerId: String,
        val createdAt: Long,
        val duration: Double,
        val sampleRate: Int,
        val upload: UploadTarget? = null,
        val uploaded: Boolean = false,
    )

    private var worker: Job? = null
    private var retry: Job? = null
    private var generation = Any()

    val hasPending: Boolean get() = runCatching { jobs().isNotEmpty() }.getOrDefault(false)

    init {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("음성 업로드 폴더를 만들 수 없어요")
        removeExpiredFiles()
    }

    fun enqueue(file: File, callId: String, ownerId: String, durationSec: Double, sampleRate: Int) {
        if (currentOwner() != ownerId || !durationSec.isFinite() || durationSec <= 0 || sampleRate <= 0) {
            throw ApiError.Unauthenticated
        }
        val job = UploadJob(
            id = UUID.randomUUID().toString(),
            callId = callId,
            ownerId = ownerId,
            createdAt = nowMillis(),
            duration = durationSec,
            sampleRate = sampleRate,
        )
        val destination = audioFile(job)
        // 같은 저장소 안이라 복사 대신 옮긴다(긴 통화의 큰 파일을 메인 스레드에서 복사하지 않게).
        Files.move(file.toPath(), destination.toPath())
        try {
            save(job)
        } catch (error: Exception) {
            destination.delete()
            throw error
        }
    }

    suspend fun resume() {
        worker?.let {
            it.join()
            return
        }
        // 지연 재시도가 스스로 resume()을 부를 때는 자기 자신을 취소하지 않는다
        val caller = currentCoroutineContext()[Job]
        retry?.let { if (it !== caller) it.cancel() }
        retry = null
        val expected = generation
        val task = scope.launch(start = CoroutineStart.LAZY) {
            try {
                process(expected)
            } finally {
                // 작업이 어떻게 끝나든 다음 resume()이 끝난 작업을 기다리며 멈추지 않게 한다
                if (generation === expected) {
                    worker = null
                    if (hasPending) scheduleRetry(retryDelayMillis)
                }
            }
        }
        worker = task
        task.start()
        task.join()
    }

    fun pause() {
        generation = Any()
        worker?.cancel()
        worker = null
        retry?.cancel()
        retry = null
    }

    fun discard(callId: String) {
        val removed = jobsOrEmpty().filter { it.callId == callId }
        if (removed.isEmpty()) return
        pause()
        removed.forEach(::remove)
        if (hasPending) scheduleRetry(0)
    }

    fun discardAll() {
        pause()
        directory.listFiles()?.forEach { it.delete() }
    }

    fun discardInvalidOwners() {
        val current = currentOwner()
        if (current == null) {
            discardAll()
            return
        }
        jobsOrEmpty().filter { it.ownerId != current }.forEach(::remove)
    }

    private fun scheduleRetry(delayMillis: Long) {
        val task = scope.launch(start = CoroutineStart.LAZY) {
            delay(delayMillis)
            resume()
        }
        retry = task
        task.start()
    }

    private suspend fun process(expected: Any) {
        runCatching { removeExpiredFiles() }
        val pending = runCatching { jobs() }.getOrNull() ?: return
        for (entry in pending) {
            var job = entry
            if (generation !== expected || !currentCoroutineContext().isActive) return
            if (currentOwner() != job.ownerId || isExpired(job)) {
                remove(job)
                continue
            }
            try {
                validatePermission(job.callId)
                if (!valid(job, expected)) return
                val urlExpired = job.upload?.let { it.expiresAt <= nowMillis() } ?: true
                if (job.upload == null || (!job.uploaded && urlExpired)) {
                    val response = requestUpload(job.callId, job.duration, job.sampleRate)
                    if (!valid(job, expected)) return
                    job = job.copy(
                        upload = UploadTarget(
                            url = response.uploadUrl,
                            assetId = response.assetId,
                            expiresAt = nowMillis() + max(0, response.expiresIn - 5) * 1_000L,
                        ),
                    )
                    save(job)
                    validatePermission(job.callId)
                    if (!valid(job, expected)) return
                }
                val target = job.upload ?: continue
                if (!job.uploaded) {
                    upload(audioFile(job), target.url)
                    if (!valid(job, expected)) return
                    job = job.copy(uploaded = true)
                    save(job)
                    validatePermission(job.callId)
                    if (!valid(job, expected)) return
                }
                complete(job.callId, target.assetId)
                if (!valid(job, expected)) return
                remove(job)
            } catch (error: Exception) {
                // 우리 작업이 취소됐으면 그대로 끝낸다. 콜백이 던진 취소(시간 초과 등)는 iOS처럼 다시 시도할 오류로 본다.
                currentCoroutineContext().ensureActive()
                if (generation !== expected) return
                if (currentOwner() != job.ownerId || !canRetry(error)) remove(job)
            }
        }
    }

    private suspend fun valid(job: UploadJob, expected: Any): Boolean {
        if (generation !== expected || !currentCoroutineContext().isActive) return false
        if (currentOwner() != job.ownerId || isExpired(job)) {
            remove(job)
            return false
        }
        return true
    }

    private fun canRetry(error: Throwable): Boolean = when (error) {
        is ApiError.Unauthenticated -> false
        is ApiError.Server -> error.status == 408 || error.status == 429 || error.status >= 500
        else -> true
    }

    private fun nowMillis(): Long = now().toEpochMilli()

    private fun isExpired(job: UploadJob): Boolean = nowMillis() - job.createdAt >= JOB_LIFETIME_MILLIS

    private fun jobs(): List<UploadJob> {
        val files = directory.listFiles() ?: throw IOException("음성 업로드 폴더를 읽을 수 없어요")
        return files
            .filter { it.extension == "json" }
            .mapNotNull { file -> runCatching { CollogJson.decodeFromString(UploadJob.serializer(), file.readText()) }.getOrNull() }
            .sortedBy { it.createdAt }
    }

    private fun jobsOrEmpty(): List<UploadJob> = runCatching { jobs() }.getOrDefault(emptyList())

    // iOS `.atomic`처럼 임시 파일에 쓴 뒤 바꿔 끼운다
    private fun save(job: UploadJob) {
        val target = File(directory, "${job.id}.json")
        val temporary = File(directory, "${job.id}.json.tmp")
        temporary.writeText(CollogJson.encodeToString(UploadJob.serializer(), job))
        Files.move(
            temporary.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun audioFile(job: UploadJob): File = File(directory, "${job.id}.wav")

    private fun remove(job: UploadJob) {
        audioFile(job).delete()
        File(directory, "${job.id}.json").delete()
    }

    private fun removeExpiredFiles() {
        jobs().filter(::isExpired).forEach(::remove)
        val files = directory.listFiles() ?: throw IOException("음성 업로드 폴더를 읽을 수 없어요")
        for (file in files) {
            val modified = file.lastModified()
            if (modified > 0 && nowMillis() - modified >= STALE_FILE_MILLIS) file.delete()
        }
    }

    private companion object {
        const val JOB_LIFETIME_MILLIS = 900_000L
        const val STALE_FILE_MILLIS = 86_400_000L
    }
}
