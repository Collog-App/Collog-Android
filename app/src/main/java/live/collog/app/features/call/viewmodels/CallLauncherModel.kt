package live.collog.app.features.call.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import live.collog.app.features.home.models.FamilyContact

// 포팅 전 자리: 공개 멤버 이름·인자는 유지하고 iOS `CallLauncherModel` 동작으로 통째로 바꾼다.
class CallLauncherModel {
    enum class Mode { Idle, Dragging, Sticky }

    var mode by mutableStateOf(Mode.Idle)
        private set
    var targets by mutableStateOf<List<FamilyContact>>(emptyList())
        private set
    var questions by mutableStateOf<List<String>>(emptyList())
        private set

    val isPresented: Boolean get() = mode != Mode.Idle

    fun configure(targets: List<FamilyContact>, questions: List<String>) {
        this.targets = targets
        this.questions = questions
    }

    fun select(index: Int): FamilyContact? {
        mode = Mode.Idle
        return targets.getOrNull(index)
    }

    fun presentSelection() {
        mode = Mode.Sticky
    }

    fun dismiss() {
        mode = Mode.Idle
    }
}
