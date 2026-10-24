package live.collog.app.shared.services

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

/**
 * iOS가 서비스 안에서 권한을 바로 묻는 것(`AVAudioApplication.requestRecordPermission`)의 대응.
 * 권한 창은 Activity만 띄울 수 있어서 MainActivity가 [attach]로 실행기를 넘겨준다.
 * 화면이 없을 때(백그라운드)는 이미 허용된 권한만 통과한다.
 */
class PermissionRequester(private val context: Context) {
    private var launcher: ((Array<String>) -> Unit)? = null
    private var pending: CompletableDeferred<Map<String, Boolean>>? = null

    fun attach(launch: (Array<String>) -> Unit) {
        launcher = launch
    }

    fun detach() {
        launcher = null
        pending?.complete(emptyMap())
        pending = null
    }

    fun deliver(result: Map<String, Boolean>) {
        pending?.complete(result)
        pending = null
    }

    /** 지금 권한 창을 띄울 수 있는지(화면이 앞에 있는지). */
    val canAsk: Boolean get() = launcher != null

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun request(permissions: List<String>): Boolean {
        val missing = permissions.filterNot(::isGranted)
        if (missing.isEmpty()) return true
        val launch = launcher ?: return false
        pending?.complete(emptyMap())
        val deferred = CompletableDeferred<Map<String, Boolean>>()
        pending = deferred
        launch(missing.toTypedArray())
        val result = deferred.await()
        return missing.all { result[it] == true || isGranted(it) }
    }
}
