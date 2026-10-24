package live.collog.app

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import live.collog.app.config.AppEnvironment
import live.collog.app.config.AppSettings
import live.collog.app.shared.services.AuthSession
import live.collog.app.shared.services.FamilyStore
import live.collog.app.shared.services.SessionCredentialStorage
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.TokenResponse
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.fail

/** iOS `MemoryCredentials`. */
class MemoryCredentials : SessionCredentialStorage {
    var response: TokenResponse? = null
    override fun read(): TokenResponse? = response
    override fun write(response: TokenResponse) {
        this.response = response
    }
    override fun delete() {
        response = null
    }
}

/** JVM 단위 테스트용 SharedPreferences. */
class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            removed.forEach(values::remove)
            values.putAll(pending)
            return true
        }
        override fun apply() {
            commit()
        }
    }
}

/**
 * iOS `TestEnvironment`: 로그인된 환경과 가짜 서버. [respond]가 요청마다 (상태 코드, 본문)을 돌려준다.
 * 끝나면 [cleanUp]을 부른다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestEnvironment(role: String = "PARENT", familyId: String? = "family") {
    val preferences = MemoryPreferences()
    val credentials = MemoryCredentials()
    val paths = mutableListOf<String>()
    val requests = mutableListOf<Request>()
    var respond: (Request) -> Pair<Int, String> = { request ->
        fail("Unexpected request ${request.url.encodedPath}")
        error("unreachable")
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            paths += request.url.encodedPath
            requests += request
            val (status, body) = respond(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("fixture")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        })
        .build()

    val environment: AppEnvironment

    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        preferences.edit().putBoolean(AppSettings.Key.ONBOARDING_COMPLETED, true).apply()
        val session = AuthSession(
            credentialStore = credentials,
            http = http,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
        session.apply(
            TokenResponse(
                accessToken = "fixture-token",
                refreshToken = "fixture-refresh",
                user = ApiUser(id = "me", role = role, name = "Tester", phone = null, familyId = familyId),
            ),
        )
        environment = AppEnvironment(
            settings = AppSettings(preferences, backendBaseUrl = "https://fixture.test"),
            session = session,
            family = FamilyStore(),
            http = http,
        )
    }

    fun cleanUp() {
        respond = { error("cancelled") }
        Dispatchers.resetMain()
    }

    fun consent(current: Boolean = true, granted: Boolean = true): String =
        """
        {"consentId":"consent","userId":"me","documentVersion":"current","agreedItems":[],
        "status":"${if (granted) "GRANTED" else "DENIED"}","isCurrent":$current,
        "currentDocumentVersion":"current"}
        """.trimIndent()
}
