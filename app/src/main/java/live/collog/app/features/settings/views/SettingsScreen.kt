package live.collog.app.features.settings.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import live.collog.app.BuildConfig
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.settings.models.GuestSamples
import live.collog.app.shared.components.navbar.CollapsingHeaderScrollView
import live.collog.app.shared.components.spec.SpecToggle
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.models.ProfileTarget
import live.collog.app.features.settings.views.components.SettingsCallout
import live.collog.app.features.settings.views.components.SettingsCalloutText
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.navigation.LocalTabManager
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

/** 설정 탭(새 피그마 15). 하위 화면은 탭의 화면 스택(`LocalNavigationManager`)에 [SettingsRoute]로 쌓는다. */
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

/**
 * 새 피그마 15 「설정 · 메인」: 계정(4줄) / 알림(통화·리포트 토글) / 통화(질문 음성 안내 토글) / 로그아웃.
 * 명세에 없는 계정 세부(이메일·전화번호·마케팅·개인정보처리방침·동의 철회·계정 삭제)는 「계정 정보」 줄 안쪽에 그대로 둔다.
 * 초대 코드 입력은 「가족 구성원 관리」 안에 있다.
 */
@Composable
private fun SettingsRoot() {
    val environment = LocalAppEnvironment.current
    val callCenter = LocalCallCenter.current
    val tabManager = LocalTabManager.current
    val navigator = LocalNavigationManager.current
    val scope = rememberCoroutineScope()
    val settings = environment.settings
    val isGuest = settings.isGuestMode
    val user = if (isGuest) GuestSamples.user else environment.session.user
    val userName = user?.let { it.nickname ?: it.name }.orEmpty()

    CollapsingHeaderScrollView(
        title = "설정",
        scrollReset = tabManager.reselectionCount,
        large = { Text("설정", style = specText(Pretendard.Bold, 24f)) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 19.dp).padding(bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsGroup("계정") {
                SpecNavigationRow("계정 정보", value = userName) { navigator.push(SettingsRoute.Account) }
                SpecDivider()
                SpecNavigationRow("나의 건강 프로필") { navigator.push(SettingsRoute.HealthProfile(null)) }
                SpecDivider()
                SpecNavigationRow("가족 구성원 관리") { navigator.push(SettingsRoute.FamilyMembers) }
                SpecDivider()
                SpecNavigationRow("가족 공유 데이터 범위") { navigator.push(SettingsRoute.FamilySharing) }
            }

            SettingsGroup("알림") {
                SpecToggleRow(
                    "통화 알림", "가족이 전화하면 잠금화면에 띄워요",
                    settings.callNotificationsEnabled,
                ) { settings.callNotificationsEnabled = it }
                SpecDivider()
                SpecToggleRow(
                    "리포트 알림", "주간 리포트가 준비되면 알려드려요",
                    settings.reportNotificationsEnabled,
                ) { settings.reportNotificationsEnabled = it }
            }

            SettingsGroup("통화") {
                SpecToggleRow(
                    "질문 음성 안내", "연결을 기다리는 동안 오늘의 질문을 읽어줘요",
                    settings.questionVoiceEnabled,
                ) { settings.questionVoiceEnabled = it }
            }

            if (environment.session.isAuthenticated) {
                val locked = callCenter.hasCallInProgress
                Box(
                    Modifier
                        .specCard(padding = 0.dp, radius = 16.dp)
                        .height(50.dp)
                        .pressable(enabled = !locked) { scope.launch { environment.signOut() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "로그아웃",
                        style = specText(Pretendard.Medium, 18f, if (locked) SpecColors.muted else SpecColors.destructive),
                    )
                }
            }

            if (isGuest) {
                // 체험 모드 안내: 누르면 체험을 끝내고 로그인 화면으로 간다
                SettingsCallout(Modifier.pressable { settings.isGuestMode = false }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingsCalloutText("예시 데이터로 둘러보는 중이에요", Modifier.weight(1f))
                        Text("로그인", style = CollogType.body02Semibold, color = CollogColors.greenDark)
                    }
                }
            }

            Text(
                "콜록 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = specText(Pretendard.Regular, 11f, SpecColors.muted),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, Modifier.padding(start = 3.dp), style = specText(Pretendard.Regular, 14f, SpecColors.muted))
        Column(Modifier.specCard(padding = 0.dp, radius = 16.dp), content = content)
    }
}

/** 카드 안 한 줄: 53dp, 글자 14/500, 오른쪽 값 14/400 muted + 꺾쇠. */
@Composable
private fun SpecNavigationRow(label: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(53.dp).pressable(onClick = onClick).padding(start = 15.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = specText(Pretendard.Medium, 14f), maxLines = 1)
        if (!value.isNullOrEmpty()) {
            Text(value, style = specText(Pretendard.Regular, 14f, SpecColors.muted), maxLines = 1)
            Spacer(Modifier.width(8.dp))
        }
        CollogIcon("chevron.right", size = 18.dp, color = SpecColors.chevron)
    }
}

/** 카드 안 토글 줄: 제목 14/500 + 설명 10/400 muted(간격 5), 오른쪽 63x29 토글. */
@Composable
private fun SpecToggleRow(title: String, caption: String, isOn: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 57.dp).padding(start = 15.dp, end = 13.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = specText(Pretendard.Medium, 14f))
            Text(caption, style = specText(Pretendard.Regular, 10f, SpecColors.muted))
        }
        SpecToggle(isOn, onChange, onColor = SpecColors.toggleOn)
    }
}
