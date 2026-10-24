package live.collog.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.MainScope
import live.collog.app.config.AppEnvironment
import live.collog.app.config.AppSettings
import live.collog.app.features.auth.services.GoogleSignIn
import live.collog.app.features.call.services.AndroidCallPlatform
import live.collog.app.features.call.services.CallCenter
import live.collog.app.shared.services.AuthSession
import live.collog.app.shared.services.CredentialStore
import live.collog.app.shared.services.PermissionRequester
import live.collog.app.shared.services.api.CollogApiClient
import live.collog.app.shared.ui.Haptics

/** iOS `AppDelegate`: 앱 전체에서 하나인 환경과 통화 센터를 만든다. */
class CollogApplication : Application() {
    lateinit var environment: AppEnvironment
        private set
    lateinit var callCenter: CallCenter
        private set
    lateinit var permissions: PermissionRequester
        private set

    override fun onCreate() {
        super.onCreate()
        Haptics.init(this)
        environment = AppEnvironment(
            settings = AppSettings(getSharedPreferences("collog.settings", Context.MODE_PRIVATE)),
            session = AuthSession(CredentialStore(this)),
        )
        environment.afterSignOut = { GoogleSignIn.clear(this) }
        permissions = PermissionRequester(this)
        val scope = MainScope()
        callCenter = CallCenter(
            environment = environment,
            platform = AndroidCallPlatform(this, scope, permissions, CollogApiClient.DefaultHttp),
            scope = scope,
        )
        callCenter.start()
    }
}
