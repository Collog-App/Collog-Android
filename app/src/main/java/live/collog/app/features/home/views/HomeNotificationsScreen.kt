package live.collog.app.features.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.home.viewmodels.HomeViewModel
import live.collog.app.features.home.views.components.HomeDetailHeader
import live.collog.app.shared.components.spec.SpecToggle
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.navigation.MainTab
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

private val notificationTimeFormatter = DateTimeFormatter.ofPattern("yyyy. M. d. a h:mm", Locale.KOREAN)

/** iOS `date.formatted(date: .numeric, time: .shortened)`(ko_KR): `2026. 10. 15. 오후 12:46`. */
internal fun formatNotificationTime(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    notificationTimeFormatter.format(date.atZone(zone))

/**
 * iOS `HomeNotificationsView`: 알림 켜고 끄기와 최근 알림 목록. 새 피그마 04(`643:6447`).
 * 서버 푸시는 리포트 알림뿐이라 「오늘의 질문이 도착했어요」 줄은 그리지 않는다(체험 모드 견본에서도 뺐다).
 * 시각은 새 디자인대로 「오늘/어제/N일 전」으로 적는다.
 */
@Composable
fun HomeNotificationsScreen() {
    val environment = LocalAppEnvironment.current
    val callCenter = LocalCallCenter.current
    val tabManager = LocalTabManager.current
    val settings = environment.settings

    fun openReport() {
        tabManager.selectedTab = MainTab.Report
        Haptics.press()
    }

    Column(Modifier.fillMaxSize().background(CollogColors.gray50)) {
        HomeDetailHeader("알림")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 19.dp, end = 19.dp, top = 14.dp, bottom = Spacing.x8),
        ) {
            Column(Modifier.specCard(padding = 0.dp)) {
                ToggleRow("통화 알림", settings.callNotificationsEnabled) { settings.callNotificationsEnabled = it }

                SpecDivider()
                ToggleRow("리포트 알림", settings.reportNotificationsEnabled) { settings.reportNotificationsEnabled = it }

                callCenter.deviceRegistrationError?.let { ErrorText(it) }
                callCenter.notificationPermissionError?.let { ErrorText(it) }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "최근 알림",
                style = specText(Pretendard.Bold, 13f),
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(18.dp))

            if (settings.isGuestMode) {
                NotificationRow(
                    symbol = "doc.text.fill",
                    title = "이번 주 리포트가 준비됐어요",
                    message = "최근 통화에서 확인한 내용을 정리했어요.",
                    time = "오늘",
                    modifier = Modifier.pressable { openReport() },
                )
            } else if (environment.reportNotifications.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    environment.reportNotifications.forEach { notification ->
                        NotificationRow(
                            symbol = "doc.text.fill",
                            title = notification.title,
                            message = notification.message,
                            time = HomeViewModel.lastCallDayText(notification.date),
                            modifier = Modifier.pressable { openReport() },
                        )
                    }
                }
            } else {
                Text(
                    "표시할 알림이 없어요",
                    style = specText(Pretendard.Medium, 14f, SpecColors.muted),
                    modifier = Modifier.specCard(padding = 19.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = specText(Pretendard.Medium, 14f), modifier = Modifier.weight(1f))
        SpecToggle(checked, onCheckedChange)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        style = CollogType.caption01Medium,
        color = CollogColors.red500,
        modifier = Modifier.padding(Spacing.x4),
    )
}

@Composable
private fun NotificationRow(
    symbol: String,
    title: String,
    message: String,
    time: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.specCard(padding = 0.dp).padding(start = 19.dp, end = 19.dp, top = 13.dp, bottom = 17.dp)) {
        CollogIcon(symbol, size = 22.dp, color = SpecColors.sub)
        Spacer(Modifier.width(17.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(title, style = specText(Pretendard.Bold, 14f), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.x2))
                Text(time, style = specText(Pretendard.Regular, 10f, SpecColors.muted))
            }
            Spacer(Modifier.height(6.dp))
            Text(message, style = specText(Pretendard.Regular, 10f, SpecColors.muted))
        }
    }
}
