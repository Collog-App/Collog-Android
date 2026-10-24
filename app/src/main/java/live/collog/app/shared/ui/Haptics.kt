package live.collog.app.shared.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** iOS `Haptics`와 같은 이름의 진동. Application.onCreate에서 [init]을 부른다. */
object Haptics {
    private var vibrator: Vibrator? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    fun press() = effect(VibrationEffect.EFFECT_TICK, 10)

    fun open() {
        effect(VibrationEffect.EFFECT_CLICK, 20)
        scope.launch {
            delay(70)
            effect(VibrationEffect.EFFECT_TICK, 10)
        }
    }

    fun focus() = effect(VibrationEffect.EFFECT_TICK, 8)

    fun blur() = effect(VibrationEffect.EFFECT_TICK, 8)

    fun commit() {
        effect(VibrationEffect.EFFECT_HEAVY_CLICK, 30)
        scope.launch {
            delay(90)
            effect(VibrationEffect.EFFECT_DOUBLE_CLICK, 20)
        }
    }

    fun cancel() = effect(VibrationEffect.EFFECT_TICK, 12)

    private fun effect(predefined: Int, fallbackMillis: Long) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(predefined)
        } else {
            VibrationEffect.createOneShot(fallbackMillis, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        runCatching { vibrator.vibrate(effect) }
    }
}
