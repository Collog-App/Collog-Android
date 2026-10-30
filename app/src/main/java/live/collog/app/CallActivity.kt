package live.collog.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.CallNotifications
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.call.views.ActiveCallScreen
import live.collog.app.features.call.views.CallErrorAlert

/**
 * 알림에서 여는 통화 화면(iOS CallKit 수신 화면 대응). 매니페스트에서 잠금 화면 위에 뜨도록 선언했고
 * 외부 앱은 열 수 없다(exported=false). 통화가 끝나면 스스로 닫힌다.
 */
class CallActivity : ComponentActivity() {
    private val app: CollogApplication get() = application as CollogApplication

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.permissions.deliver(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // 다시 만들어질 때(설정 변경 등) 처음 인텐트를 또 처리하면 다음 전화를 저절로 받는다.
        if (savedInstanceState == null) handle(intent)
        val callCenter = app.callCenter
        setContent {
            CompositionLocalProvider(
                LocalAppEnvironment provides app.environment,
                LocalCallCenter provides callCenter,
            ) {
                val call = callCenter.activeCall
                Box(Modifier.fillMaxSize().background(Color(0xFF111316))) {
                    if (call != null) {
                        BackHandler { moveTaskToBack(true) }
                        ActiveCallScreen(callCenter, call)
                    }
                    CallErrorAlert(callCenter, onDismissed = ::finish)
                }
                LaunchedEffect(Unit) {
                    snapshotFlow { callCenter.activeCall == null && callCenter.callError == null }.first { it }
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        app.permissions.attach(this) { permissionLauncher.launch(it) }
    }

    override fun onStop() {
        app.permissions.detach(this)
        super.onStop()
    }

    private fun handle(intent: Intent?) {
        val callCenter = app.callCenter
        val callId = intent?.getStringExtra(CallNotifications.EXTRA_CALL_ID)
        val current = callCenter.activeCall
        if (callId != null && current?.id != callId) {
            // 이미 끝났거나 프로세스가 다시 시작돼 모르는 통화다.
            callCenter.declineStaleCall(callId)
            finish()
            return
        }
        if (intent?.action == CallNotifications.ACTION_ANSWER) callCenter.answerIncomingCall()
        // 처리한 동작은 지운다(다시 만들어지거나 최근 앱에서 열 때 또 받지 않게).
        setIntent(Intent(intent).setAction(null))
    }
}
