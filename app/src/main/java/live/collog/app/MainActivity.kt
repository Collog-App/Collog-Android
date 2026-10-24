package live.collog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import live.collog.app.app.RootScreen
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.LocalCallCenter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // iOS가 `.preferredColorScheme(.light)`로 밝은 화면만 쓰므로 상태 표시줄도 밝은 배경용으로 고정한다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as CollogApplication
        setContent {
            CompositionLocalProvider(
                LocalAppEnvironment provides app.environment,
                LocalCallCenter provides app.callCenter,
            ) {
                RootScreen()
            }
        }
    }
}
