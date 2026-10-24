package live.collog.app.features.call.viewmodels

import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.shared.ui.Haptics

/**
 * iOS `CallLauncherModel`. 전화 버튼을 누르고 있거나 눌렀을 때 가족 선택 부채꼴의 상태를 관리한다.
 * 벡터는 전화 버튼 중심 기준 dp 단위다. `remember`에서 빠지면 타이머 코루틴을 정리한다.
 */
class CallLauncherModel(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val holdMillis: Long = 220,
) : RememberObserver {
    enum class Mode { Idle, Dragging, Sticky }

    var mode by mutableStateOf(Mode.Idle)
        private set
    var focusedIndex by mutableStateOf<Int?>(null)
        private set
    var targets by mutableStateOf<List<FamilyContact>>(emptyList())
        private set
    var questions by mutableStateOf<List<String>>(emptyList())
        private set

    val isPresented: Boolean get() = mode != Mode.Idle

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var activationJob: Job? = null
    private var didActivateByHold = false
    private var latestDragVector = Offset.Zero

    val arcRadius = 122f

    fun configure(targets: List<FamilyContact>, questions: List<String>) {
        this.targets = targets
        this.questions = questions
    }

    fun pressBegan() {
        if (mode == Mode.Sticky) return
        Haptics.press()
        didActivateByHold = false
        latestDragVector = Offset.Zero
        activationJob?.cancel()
        activationJob = scope.launch {
            delay(holdMillis)
            activateHold()
        }
    }

    fun dragChanged(vector: Offset) {
        latestDragVector = vector
        if (mode != Mode.Dragging) return
        updateFocus(vector)
    }

    fun pressEnded(vector: Offset): FamilyContact? {
        activationJob?.cancel()
        activationJob = null
        latestDragVector = vector
        if (mode == Mode.Dragging) updateFocus(vector)

        if (!didActivateByHold) {
            if (mode == Mode.Sticky) dismiss() else presentSelection()
            return null
        }

        val selected = focusedIndex?.let { targets.getOrNull(it) }
        mode = Mode.Idle
        focusedIndex = null
        didActivateByHold = false
        latestDragVector = Offset.Zero
        if (selected == null) Haptics.cancel() else Haptics.commit()
        return selected
    }

    fun select(index: Int): FamilyContact? {
        val contact = targets.getOrNull(index) ?: return null
        Haptics.commit()
        mode = Mode.Idle
        focusedIndex = null
        return contact
    }

    fun presentSelection() {
        activationJob?.cancel()
        activationJob = null
        focusedIndex = null
        didActivateByHold = false
        latestDragVector = Offset.Zero
        mode = Mode.Sticky
    }

    fun dismiss() {
        activationJob?.cancel()
        activationJob = null
        if (mode != Mode.Idle) Haptics.cancel()
        mode = Mode.Idle
        focusedIndex = null
        didActivateByHold = false
        latestDragVector = Offset.Zero
    }

    fun offset(index: Int): Offset {
        val radians = Math.toRadians(angle(index))
        return Offset((cos(radians) * arcRadius).toFloat(), (sin(radians) * arcRadius).toFloat())
    }

    fun angleRange(index: Int): ClosedFloatingPointRange<Double> {
        val count = maxOf(targets.size, 1)
        val width = 180.0 / count
        val lowerBound = -180.0 + width * index
        return lowerBound..(lowerBound + width)
    }

    private fun angle(index: Int): Double {
        val range = angleRange(index)
        return (range.start + range.endInclusive) / 2
    }

    private fun activateHold() {
        if (targets.isEmpty()) {
            mode = Mode.Sticky
            return
        }
        mode = Mode.Dragging
        didActivateByHold = true
        updateFocus(latestDragVector)
        Haptics.open()
    }

    private fun updateFocus(vector: Offset) {
        val nearest = selectionIndex(vector)
        if (nearest != focusedIndex) {
            focusedIndex = nearest
            if (nearest == null) Haptics.blur() else Haptics.focus()
        }
    }

    internal fun selectionIndex(vector: Offset): Int? {
        val radius = hypot(vector.x, vector.y)
        if (vector.y >= 0 || radius < SELECTION_INNER_RADIUS || radius > SELECTION_OUTER_RADIUS) return null

        val candidate = targets.indices.maxByOrNull { selectionScore(vector, it) } ?: return null
        val focused = focusedIndex
        if (focused == null || focused == candidate || focused !in targets.indices) return candidate

        val margin = radius * arcRadius * SELECTION_HYSTERESIS
        return if (selectionScore(vector, candidate) - selectionScore(vector, focused) > margin) candidate else focused
    }

    private fun selectionScore(vector: Offset, index: Int): Float {
        val target = offset(index)
        return vector.x * target.x + vector.y * target.y
    }

    override fun onRemembered() {}

    override fun onForgotten() {
        scope.cancel()
    }

    override fun onAbandoned() {
        scope.cancel()
    }

    private companion object {
        const val SELECTION_INNER_RADIUS = 30f
        const val SELECTION_OUTER_RADIUS = 224f
        const val SELECTION_HYSTERESIS = 0.12f
    }
}
