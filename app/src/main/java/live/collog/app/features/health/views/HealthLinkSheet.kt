package live.collog.app.features.health.views

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.health.models.LifeRecord
import live.collog.app.features.health.services.HealthConnectSource
import live.collog.app.features.health.services.HealthLink
import live.collog.app.features.health.services.HealthLinkStep
import live.collog.app.features.health.services.LocalHealthLink
import live.collog.app.features.settings.views.components.SettingsActionButton
import live.collog.app.features.settings.views.components.SettingsAlert
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.services.api.HealthLinkDocument
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard

private val BodyStyle = pretendard(Pretendard.Medium, 17f, CollogColors.gray800)

/**
 * 건강 앱 연결 흐름(피그마 없음): 동의 문서 시트 → Health Connect 권한 창 → 연결 → 동기화 → 「연결됐어요」.
 * 설정·홈·리포트가 [live.collog.app.features.health.services.HealthLink.present]로 연다. 권한 창 결과를 놓치지 않게
 * 늘 그려 두는 탭 화면(MainTabs)에 하나만 둔다. 어르신이 쓰기 쉽게 큰 글씨와 버튼 하나로 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthLinkSheetHost() {
    val link = LocalHealthLink.current
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        // 결과 집합 대신 다시 물어 확인한다(이미 받은 권한도 센다)
        scope.launch {
            when (link.step) {
                is HealthLinkStep.Connecting -> link.completeConnect(environment)
                HealthLinkStep.Regranting -> link.completeRegrant(environment)
                else -> Unit
            }
        }
    }
    if (!link.isPresented) return

    var confirmsDisconnect by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { link.prepare(environment) }

    // 이미 받은 권한이 있어도 늘 권한 창을 거친다(다 받았으면 시스템이 바로 돌려준다). 다른 계정이 전에 준 권한을
    // 창 없이 그대로 쓰지 않게 한다.
    fun requestPermissions() {
        try {
            permissionLauncher.launch(HealthLink.ReadPermissions)
        } catch (_: ActivityNotFoundException) {
            link.showNeedsInstall()
        }
    }

    fun connect(document: HealthLinkDocument) {
        link.beginConnect(document)
        requestPermissions()
    }

    fun regrant() {
        link.beginRegrant()
        requestPermissions()
    }

    ModalBottomSheet(
        onDismissRequest = { link.dismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CollogColors.gray50,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.x5).padding(top = Spacing.x2, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            when (val step = link.step) {
                HealthLinkStep.Loading, HealthLinkStep.Regranting -> Waiting(null)
                is HealthLinkStep.Connecting -> Waiting("연결하고 있어요")
                HealthLinkStep.NotReady -> {
                    Message("건강 앱 연결을 준비하고 있어요", "조금만 기다려 주세요. 준비가 끝나면 여기에서 연결할 수 있어요.")
                    PrimaryButton("확인") { link.dismiss() }
                }
                HealthLinkStep.Unavailable -> {
                    Message("이 휴대폰에서는 건강 앱을 연결할 수 없어요", "건강 앱 연결은 Android 9 이상 휴대폰에서 쓸 수 있어요.")
                    PrimaryButton("확인") { link.dismiss() }
                }
                HealthLinkStep.NeedsInstall -> {
                    Message(
                        "Health Connect 앱이 필요해요",
                        "건강 기록을 읽으려면 Google Play에서 Health Connect를 설치하거나 업데이트해 주세요. " +
                            "설치한 뒤 다시 「건강 앱 연결」을 눌러 주세요.",
                    )
                    PrimaryButton("설치하러 가기") {
                        HealthConnectSource.openInstallPage(context)
                        link.dismiss()
                    }
                }
                is HealthLinkStep.Consent -> ConsentContent(step.document, step.notice) { connect(step.document) }
                HealthLinkStep.PermissionDenied -> {
                    Message("권한이 없어서 연결하지 못했어요", "건강 기록 읽기를 하나 이상 허용해야 연결할 수 있어요.")
                    PrimaryButton("다시 시도") { scope.launch { link.prepare(environment) } }
                    SecondaryButton("Health Connect 설정 열기") { HealthConnectSource.openSettings(context) }
                }
                is HealthLinkStep.Linked -> {
                    Message(
                        if (step.justConnected) "연결됐어요" else "건강 앱이 연결돼 있어요",
                        "가족 리포트의 「생활 기록」에서 수면·걸음 변화를 함께 볼 수 있어요.",
                    )
                    step.lastSyncedDay?.let(::dayText)?.let {
                        Text("마지막으로 보낸 날: $it", style = CollogType.body02Regular, color = CollogColors.gray700)
                    }
                    if (step.hasPermission) {
                        PrimaryButton("확인") { link.dismiss() }
                    } else {
                        Text(
                            "건강 앱 권한이 꺼져 있어서 기록을 보내지 못하고 있어요.",
                            style = pretendard(Pretendard.Medium, 16f, CollogColors.red500),
                        )
                        PrimaryButton("권한 다시 허용") { regrant() }
                    }
                    SecondaryButton("연결 끊기", destructive = true) { confirmsDisconnect = true }
                }
                is HealthLinkStep.Failed -> {
                    Message("연결하지 못했어요", step.message)
                    PrimaryButton("다시 시도") { scope.launch { link.prepare(environment) } }
                }
            }
        }
    }

    if (confirmsDisconnect) {
        SettingsAlert(
            title = "건강 앱 연결을 끊을까요?",
            message = "서버에 저장된 건강 요약이 바로 지워지고, 가족 리포트에서도 보이지 않아요.",
            confirmText = "연결 끊기",
            destructive = true,
            onConfirm = { scope.launch { link.disconnect(environment) } },
            onDismiss = { confirmsDisconnect = false },
        )
    }
}

/** 서버 동의 문서를 그대로(제목·줄 목록) 큰 글씨로 보이고, 「동의하고 연결」 한 버튼. */
@Composable
private fun ColumnScope.ConsentContent(document: HealthLinkDocument, notice: String?, onAgree: () -> Unit) {
    Text(document.title, style = CollogType.headline02, color = CollogColors.gray900)
    if (notice != null) Text(notice, style = pretendard(Pretendard.Medium, 16f, CollogColors.orange600))
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.x3),
    ) {
        document.items.forEach { item ->
            Row(Modifier.fillMaxWidth()) {
                Text("•", style = BodyStyle, modifier = Modifier.width(18.dp))
                Text(item, style = BodyStyle, modifier = Modifier.weight(1f))
            }
        }
    }
    PrimaryButton("동의하고 연결", onClick = onAgree)
}

@Composable
private fun Waiting(text: String?) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            CollogProgress()
            if (text != null) Text(text, style = BodyStyle)
        }
    }
}

@Composable
private fun Message(title: String, body: String) {
    Text(title, style = CollogType.headline02, color = CollogColors.gray900)
    Text(body, style = BodyStyle)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    SettingsActionButton(text, Modifier.fillMaxWidth().padding(top = Spacing.x2), onClick = onClick)
}

@Composable
private fun SecondaryButton(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.x2), contentAlignment = Alignment.Center) {
        SystemTextButton(text, destructive = destructive, onClick = onClick)
    }
}

private fun dayText(raw: String): String? = try {
    LifeRecord.dayText(LocalDate.parse(raw))
} catch (_: DateTimeParseException) {
    null
}
