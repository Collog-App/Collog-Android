package live.collog.app.shared.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf

enum class MainTab(val title: String, val symbol: String) {
    Home("홈", "house.fill"),
    Report("리포트", "doc.text.fill"),
    Timeline("타임라인", "chart.xyaxis.line"),
    Settings("설정", "gearshape.fill"),
}

/** iOS `Route`. 탭 안에서 밀어 넣는 화면. */
enum class Route {
    FamilyHealthOverview,
    HealthFeedbackDetail,
    Notifications,
}

/** iOS `NavigationManager`: 탭 하나의 화면 스택. 맨 위가 지금 보이는 화면이다. */
class NavigationManager {
    val path: SnapshotStateList<Any> = mutableStateListOf()

    fun push(value: Any) {
        path.add(value)
    }

    fun pop() {
        if (path.isNotEmpty()) path.removeAt(path.lastIndex)
    }

    fun popToRoot() {
        path.clear()
    }
}

class NavigationStore {
    private val managers = mutableMapOf<MainTab, NavigationManager>()

    fun manager(tab: MainTab): NavigationManager = managers.getOrPut(tab) { NavigationManager() }

    fun popToRoot(tab: MainTab) {
        managers[tab]?.popToRoot()
    }

    fun isAtRoot(tab: MainTab): Boolean = managers[tab]?.path?.isEmpty() ?: true
}

class TabManager {
    var selectedTab by mutableStateOf(MainTab.Home)
    var reselectedTab by mutableStateOf<MainTab?>(null)
        private set
    var reselectionCount by mutableIntStateOf(0)
        private set

    fun reselect(tab: MainTab) {
        reselectedTab = tab
        reselectionCount += 1
    }
}

val LocalTabManager = staticCompositionLocalOf<TabManager> { error("TabManager가 제공되지 않았어요") }
val LocalNavigationStore = staticCompositionLocalOf<NavigationStore> { error("NavigationStore가 제공되지 않았어요") }

/** 현재 탭의 화면 스택. iOS `@Environment(\.navigationManager)`. */
val LocalNavigationManager = staticCompositionLocalOf { NavigationManager() }
