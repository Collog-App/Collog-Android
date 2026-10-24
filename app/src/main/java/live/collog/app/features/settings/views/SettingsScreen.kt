package live.collog.app.features.settings.views

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.collog.app.BuildConfig
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.settings.views.components.SettingsNavigationRow
import live.collog.app.features.settings.views.components.SettingsSection
import live.collog.app.features.settings.views.components.SettingsToggleRow
import live.collog.app.shared.components.navbar.CollapsingHeaderScrollView
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.pressable

/** 설정 탭 안에서 밀어 넣는 화면. iOS `SettingsRoute`. */
private enum class SettingsRoute { Account, HealthProfile, FamilyMembers, FamilySharing }

private const val PRIVACY_POLICY_URL = "https://collog-app.github.io/Collog-Privacy/"

/** iOS `SettingsView`. 하위 화면은 탭의 화면 스택(`LocalNavigationManager`)에 [SettingsRoute]로 쌓는다. */
@Composable
fun SettingsScreen() {
    val navigator = LocalNavigationManager.current
    val route = navigator.path.lastOrNull() as? SettingsRoute
    when (route) {
        SettingsRoute.Account -> AccountSettingsScreen()
        SettingsRoute.HealthProfile -> HealthProfileSettingsScreen()
        SettingsRoute.FamilyMembers -> FamilyMembersSettingsScreen()
        SettingsRoute.FamilySharing -> FamilySharingSettingsScreen()
        null -> SettingsRoot()
    }
}

@Composable
private fun SettingsRoot() {
    val environment = LocalAppEnvironment.current
    val callCenter = LocalCallCenter.current
    val tabManager = LocalTabManager.current
    val navigator = LocalNavigationManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = environment.settings

    CollapsingHeaderScrollView(title = "설정", scrollReset = tabManager.reselectionCount) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.x5)
                .padding(bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x6),
        ) {
            if (settings.isGuestMode) {
                // 체험 모드 안내: 누르면 체험을 끝내고 로그인 화면으로 간다
                Row(
                    Modifier
                        .cardSurface()
                        .pressable { settings.isGuestMode = false },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
                        Text("DEMO", style = CollogType.body02Semibold, color = CollogColors.gray900)
                        Text("예시 데이터로 둘러보는 중이에요", style = CollogType.caption01Medium, color = CollogColors.gray700)
                    }
                    Text("로그인", style = CollogType.body02Semibold, color = CollogColors.greenDark)
                }
            }

            SettingsSection("계정") {
                SettingsNavigationRow("계정 정보", value = environment.session.user?.name ?: "데모 사용자") {
                    navigator.push(SettingsRoute.Account)
                }
                DividerLine()
                SettingsNavigationRow("건강 프로필") { navigator.push(SettingsRoute.HealthProfile) }
                DividerLine()
                SettingsNavigationRow("가족 구성원 관리") { navigator.push(SettingsRoute.FamilyMembers) }
                DividerLine()
                SettingsNavigationRow("가족 공유 데이터 범위") { navigator.push(SettingsRoute.FamilySharing) }
            }

            SettingsSection("알림") {
                SettingsToggleRow(
                    label = "통화 알림",
                    caption = "가족이 전화하면 잠금화면에 띄워요",
                    isOn = settings.callNotificationsEnabled,
                    onIsOnChange = { settings.callNotificationsEnabled = it },
                )
                DividerLine()
                SettingsToggleRow(
                    label = "리포트 알림",
                    caption = "새 통화 기록이 준비되면 알려드려요",
                    isOn = settings.reportNotificationsEnabled,
                    onIsOnChange = { settings.reportNotificationsEnabled = it },
                )
                callCenter.deviceRegistrationError?.let {
                    Text(it, style = CollogType.caption01Medium, color = CollogColors.red500, modifier = Modifier.padding(Spacing.x4))
                }
                callCenter.notificationPermissionError?.let {
                    Text(it, style = CollogType.caption01Medium, color = CollogColors.red500, modifier = Modifier.padding(Spacing.x4))
                }
            }

            SettingsSection("통화") {
                SettingsToggleRow(
                    label = "질문 음성 안내",
                    caption = "상대가 전화를 받기 전에 오늘의 질문을 읽어줘요",
                    isOn = settings.questionVoiceEnabled,
                    onIsOnChange = { settings.questionVoiceEnabled = it },
                )
            }

            SettingsSection("정보") {
                SettingsNavigationRow("개인정보처리방침") {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                }
            }

            if (environment.session.isAuthenticated) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(CollogColors.gray00, RoundedCornerShape(Radius.card))
                        .pressable { scope.launch { environment.signOut() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("로그아웃", style = CollogType.body02Semibold, color = CollogColors.red500)
                }
            }

            Text(
                "콜록 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = CollogType.caption01Medium,
                color = CollogColors.gray700,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
