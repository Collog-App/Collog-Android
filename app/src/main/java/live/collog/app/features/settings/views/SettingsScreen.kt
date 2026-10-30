package live.collog.app.features.settings.views

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import live.collog.app.BuildConfig
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.models.ProfileTarget
import live.collog.app.features.settings.views.components.SettingsCallout
import live.collog.app.features.settings.views.components.SettingsCalloutText
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SettingsNavigationRow
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.ui.OnChange
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/** 설정 탭 안에서 밀어 넣는 화면. */
internal sealed interface SettingsRoute {
    data object Account : SettingsRoute
    data object FamilyMembers : SettingsRoute
    data object FamilySharing : SettingsRoute

    /** [target]이 null이면 나의 건강 프로필. */
    data class HealthProfile(val target: ProfileTarget?) : SettingsRoute
}

/** 피그마 「최종본」 설정(275:5311). 하위 화면은 탭의 화면 스택(`LocalNavigationManager`)에 [SettingsRoute]로 쌓는다. */
@Composable
fun SettingsScreen() {
    val navigator = LocalNavigationManager.current
    when (val route = navigator.path.lastOrNull() as? SettingsRoute) {
        SettingsRoute.Account -> AccountSettingsScreen()
        SettingsRoute.FamilyMembers -> FamilyMembersSettingsScreen()
        SettingsRoute.FamilySharing -> FamilySharingSettingsScreen()
        is SettingsRoute.HealthProfile -> HealthProfileSettingsScreen(route.target, onBack = { navigator.pop() })
        null -> SettingsRoot()
    }
}

@Composable
private fun SettingsRoot() {
    val environment = LocalAppEnvironment.current
    val tabManager = LocalTabManager.current
    val navigator = LocalNavigationManager.current
    val settings = environment.settings
    val scrollState = rememberScrollState()

    OnChange(tabManager.reselectionCount) { _, _ -> scrollState.animateScrollTo(0, tween(320)) }

    Column(Modifier.fillMaxSize().background(CollogColors.gray00).statusBarsPadding()) {
        SettingsDetailHeader("설정", onBack = null)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(top = Spacing.x5, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x6),
        ) {
            // 피그마 MySettingList: 위 여백 24dp, 줄은 화면 양옆 8dp 안쪽
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.x2)) {
                Spacer(Modifier.height(Spacing.x6))
                SettingsNavigationRow("계정 정보") { navigator.push(SettingsRoute.Account) }
                SettingsNavigationRow("나의 건강 프로필") { navigator.push(SettingsRoute.HealthProfile(null)) }
                SettingsNavigationRow("가족 구성원 관리") { navigator.push(SettingsRoute.FamilyMembers) }
                SettingsNavigationRow("가족 공유 데이터 범위") { navigator.push(SettingsRoute.FamilySharing) }
            }

            if (settings.isGuestMode) {
                // 체험 모드 안내: 누르면 체험을 끝내고 로그인 화면으로 간다
                SettingsCallout(Modifier.padding(horizontal = Spacing.x5).pressable { settings.isGuestMode = false }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsCalloutText("예시 데이터로 둘러보는 중이에요", Modifier.weight(1f))
                        Text("로그인", style = CollogType.body02Semibold, color = CollogColors.greenDark)
                    }
                }
            }

            Text(
                "콜록 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = CollogType.caption01Medium,
                color = CollogColors.gray600,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
