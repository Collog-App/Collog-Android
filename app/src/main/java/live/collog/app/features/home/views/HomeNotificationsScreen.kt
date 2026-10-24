package live.collog.app.features.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import live.collog.app.features.home.views.components.HomeDetailHeader
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.navigation.MainTab
import live.collog.app.shared.ui.CollogToggle
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable

private val notificationTimeFormatter = DateTimeFormatter.ofPattern("yyyy. M. d. a h:mm", Locale.KOREAN)

/** iOS `date.formatted(date: .numeric, time: .shortened)`(ko_KR): `2026. 10. 15. 오후 12:46`. */
internal fun formatNotificationTime(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    notificationTimeFormatter.format(date.atZone(zone))

/** iOS `HomeNotificationsView`: 알림 켜고 끄기와 최근 알림 목록. */
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
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x5),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(CollogColors.gray00, RoundedCornerShape(Radius.card)),
            ) {
                ToggleRow("통화 알림", settings.callNotificationsEnabled) { settings.callNotificationsEnabled = it }

                DividerLine(Modifier.padding(start = Spacing.x4))
                ToggleRow("리포트 알림", settings.reportNotificationsEnabled) { settings.reportNotificationsEnabled = it }

                callCenter.deviceRegistrationError?.let { ErrorText(it) }
                callCenter.notificationPermissionError?.let { ErrorText(it) }
            }

            Text(
                "최근 알림",
                style = CollogType.body01Semibold,
                color = CollogColors.gray900,
                modifier = Modifier.padding(start = Spacing.x1),
            )

            if (settings.isGuestMode) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                    NotificationRow(
                        symbol = "doc.text.fill",
                        title = "이번 주 리포트가 준비됐어요",
                        message = "최근 통화에서 확인한 내용을 정리했어요.",
                        time = "오늘",
                        modifier = Modifier.pressable { openReport() },
                    )
                    NotificationRow(
                        symbol = "bubble.left.and.text.bubble.right.fill",
                        title = "오늘의 질문이 도착했어요",
                        message = "다음 통화에서 나눌 질문을 확인해보세요.",
                        time = "어제",
                    )
                }
            } else if (environment.reportNotifications.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                    environment.reportNotifications.forEach { notification ->
                        NotificationRow(
                            symbol = "doc.text.fill",
                            title = notification.title,
                            message = notification.message,
                            time = formatNotificationTime(notification.date),
                            modifier = Modifier.pressable { openReport() },
                        )
                    }
                }
            } else {
                Text(
                    "표시할 알림이 없어요",
                    style = CollogType.body02Medium,
                    color = CollogColors.gray700,
                    modifier = Modifier.cardSurface(),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(Spacing.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = pretendardPlain(Pretendard.Medium, 14f, CollogColors.gray900),
            modifier = Modifier.weight(1f),
        )
        CollogToggle(checked, onCheckedChange)
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
    Row(modifier.cardSurface(), horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.TopCenter) {
            CollogIcon(symbol, size = 20.dp, color = CollogColors.gray800)
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    style = CollogType.body02Semibold,
                    color = CollogColors.gray900,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.x2))
                Text(time, style = CollogType.caption01Medium, color = CollogColors.gray700)
            }

            Text(message, style = CollogType.body03Medium, color = CollogColors.gray800)
        }
    }
}
