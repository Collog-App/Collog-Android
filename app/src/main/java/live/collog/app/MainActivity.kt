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
import live.collog.app.features.call.services.CallService
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.health.services.LocalHealthLink

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
        if (savedInstanceState == null) handleReportIntent(intent)
        setContent {
            CompositionLocalProvider(
                LocalAppEnvironment provides app.environment,
                LocalCallCenter provides app.callCenter,
                LocalHealthLink provides app.healthLink,
            ) {
                RootScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReportIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        app.permissions.attach(this) { permissionLauncher.launch(it) }
    }

    override fun onResume() {
        super.onResume()
        // 블루투스·워치로 받아 마이크 없이 시작된 통화면, 화면이 보이는 지금 마이크를 다시 붙인다.
        CallService.restoreMicrophone(this)
    }

    override fun onStop() {
        app.permissions.detach(this)
        super.onStop()
    }

    /**
     * 리포트 알림을 누르면 리포트 탭 위에 그 통화의 분석 화면을 연다(RootScreen). 이 Activity는 런처라 외부 앱도 열 수 있으므로
     * 인텐트의 제목·본문은 믿지 않고 서버가 보내는 고정 문구를 쓴다.
     */
    private fun handleReportIntent(intent: Intent?) {
        if (intent?.action != CallNotifications.ACTION_OPEN_REPORT) return
        val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID) ?: return
        app.environment.receiveReportNotification(
            callId = callId,
            title = "Collog",
            message = "새 통화 기록을 확인해 주세요.",
            openReport = true,
        )
        setIntent(Intent(intent).setAction(null))
    }
}
