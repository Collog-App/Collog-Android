package live.collog.app.shared.services

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * iOS가 서비스 안에서 권한을 바로 묻는 것(`AVAudioApplication.requestRecordPermission`)의 대응.
 * 권한 창은 Activity만 띄울 수 있어서 앞에 있는 Activity가 [attach]로 실행기를 넘겨준다.
 * 화면이 없을 때(백그라운드)는 이미 허용된 권한만 통과한다. 요청은 한 번에 하나씩 처리한다.
 */
class PermissionRequester(private val context: Context) {
    private var owner: Any? = null
    private var launcher: ((Array<String>) -> Unit)? = null
    private var pending: CompletableDeferred<Map<String, Boolean>>? = null
    private val mutex = Mutex()

    fun attach(owner: Any, launch: (Array<String>) -> Unit) {
        this.owner = owner
        launcher = launch
    }

    /** [owner]가 지금 붙어 있는 화면일 때만 뗀다(다른 Activity가 먼저 붙었을 수 있다). */
    fun detach(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
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

    suspend fun request(permissions: List<String>): Boolean = mutex.withLock {
        val missing = permissions.filterNot(::isGranted)
        if (missing.isEmpty()) return@withLock true
        val launch = launcher ?: return@withLock false
        val deferred = CompletableDeferred<Map<String, Boolean>>()
        pending = deferred
        launch(missing.toTypedArray())
        val result = deferred.await()
        missing.all { result[it] == true || isGranted(it) }
    }
}
