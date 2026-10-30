package live.collog.app.config

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import live.collog.app.BuildConfig

/** iOS `AppSettings`. 값은 SharedPreferences에 두고 Compose가 바뀐 값을 바로 본다. */
class AppSettings(
    private val prefs: SharedPreferences,
    val backendBaseUrl: String = BuildConfig.API_BASE_URL,
) {
    object Key {
        const val CALL_NOTIFICATIONS_ENABLED = "settings.callNotificationsEnabled"
        const val REPORT_NOTIFICATIONS_ENABLED = "settings.reportNotificationsEnabled"
        const val QUESTION_VOICE_ENABLED = "settings.questionVoiceEnabled"
        const val GUEST_MODE = "settings.guestMode"
    }

    var callNotificationsEnabled by BooleanPreference(Key.CALL_NOTIFICATIONS_ENABLED, true)
    var reportNotificationsEnabled by BooleanPreference(Key.REPORT_NOTIFICATIONS_ENABLED, true)
    var questionVoiceEnabled by BooleanPreference(Key.QUESTION_VOICE_ENABLED, true)
    var isGuestMode by BooleanPreference(Key.GUEST_MODE, false)

    val resolvedBaseUrl: String get() = backendBaseUrl.trimEnd('/')

    private inner class BooleanPreference(
        private val key: String,
        default: Boolean,
    ) : ReadWriteProperty<Any?, Boolean> {
        private val state = mutableStateOf(prefs.getBoolean(key, default))

        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }
}
