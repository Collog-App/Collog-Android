package live.collog.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import live.collog.app.app.RootScreen
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.CallNotifications
import live.collog.app.features.call.services.LocalCallCenter

class MainActivity : ComponentActivity() {
    private val app: CollogApplication get() = application as CollogApplication

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        app.permissions.deliver(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // iOS가 `.preferredColorScheme(.light)`로 밝은 화면만 쓰므로 상태 표시줄도 밝은 배경용으로 고정한다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleCallIntent(intent)
        setContent {
            CompositionLocalProvider(
                LocalAppEnvironment provides app.environment,
                LocalCallCenter provides app.callCenter,
            ) {
                RootScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        app.permissions.attach { permissionLauncher.launch(it) }
    }

    override fun onStop() {
        app.permissions.detach()
        super.onStop()
    }

    /** 알림에서 들어온 동작. 수신 화면은 잠금 화면 위에도 띄운다(iOS CallKit 수신 화면 대응). */
    private fun handleCallIntent(intent: Intent?) {
        when (intent?.action) {
            CallNotifications.ACTION_ANSWER -> {
                showOverLockScreen()
                app.callCenter.answerIncomingCall()
            }
            CallNotifications.ACTION_SHOW_CALL -> showOverLockScreen()
            CallNotifications.ACTION_OPEN_REPORT -> {
                val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID) ?: return
                app.environment.receiveReportNotification(
                    callId = callId,
                    title = intent.getStringExtra(CallNotifications.EXTRA_TITLE).orEmpty(),
                    message = intent.getStringExtra(CallNotifications.EXTRA_MESSAGE).orEmpty(),
                    openReport = true,
                )
            }
        }
    }

    private fun showOverLockScreen() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }
}
