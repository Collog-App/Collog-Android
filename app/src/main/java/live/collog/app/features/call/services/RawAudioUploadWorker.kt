package live.collog.app.features.call.services

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import live.collog.app.CollogApplication

/**
 * 통화가 끝난 뒤 앱이 뒤로 가거나 꺼져도 원음 업로드를 끝까지 이어 간다(iOS `beginBackgroundTask` 대응).
 * 업로드할 파일은 [RawAudioUploadQueue]가 디스크에 들고 있고, 이 작업은 그 대기열을 한 번 끝까지 돌린다.
 */
class RawAudioUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val callCenter = (applicationContext as CollogApplication).callCenter
        // 대기열은 메인 스레드에서만 다룬다
        val remaining = withContext(Dispatchers.Main) { callCenter.drainPendingUploads() }
        return if (remaining) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "raw-audio-upload"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<RawAudioUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .apply {
                    // Android 11 이하는 긴급 작업이 포그라운드 서비스로 돌아 알림과 Play 신고가 따로 필요해 일반 작업으로 둔다
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
