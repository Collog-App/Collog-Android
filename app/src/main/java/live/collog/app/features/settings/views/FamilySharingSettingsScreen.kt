package live.collog.app.features.settings.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.views.ConsentScreen
import live.collog.app.features.settings.views.components.SettingsAlert
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SettingsSection
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.services.api.ConsentItemLabel
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface

/** iOS `FamilySharingSettingsView`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySharingSettingsScreen() {
    val environment = LocalAppEnvironment.current
    val navigator = LocalNavigationManager.current
    val scope = rememberCoroutineScope()

    var agreedItems by remember { mutableStateOf<List<String>>(emptyList()) }
    var isGranted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showsConsent by remember { mutableStateOf(false) }
    var confirmsRevocation by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val isGuest = environment.settings.isGuestMode

    suspend fun load() {
        isLoading = true
        errorText = null
        try {
            if (isGuest) {
                agreedItems = listOf(
                    "SENSITIVE_HEALTH_COLLECTION",
                    "VOICE_FEATURE_EXTRACTION",
                    "CALL_RECORDING",
                    "REPORT_SHARING_WITH_CHILD",
                )
                isGranted = true
                return
            }
            val consent = environment.api.myConsent()
            agreedItems = consent.agreedItems
            isGranted = consent.isCurrent && consent.isGranted
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorText = error.message
        } finally {
            isLoading = false
        }
    }

    fun revoke() {
        if (isSubmitting) return
        isSubmitting = true
        errorText = null
        scope.launch {
            try {
                val document = environment.api.consentDocument()
                environment.api.submitConsent(
                    documentVersion = document.version,
                    agreedItems = emptyList(),
                    decision = "DENY",
                    scrolledToEnd = false,
                )
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorText = error.message
            } finally {
                isSubmitting = false
            }
        }
    }

    // iOS `.sheet(onDismiss:)`: 동의 시트가 닫히면 상태를 다시 읽는다
    fun closeConsent() {
        showsConsent = false
        scope.launch { load() }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(CollogColors.gray50).statusBarsPadding()) {
        SettingsDetailHeader("가족 공유 데이터 범위", onBack = { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5, vertical = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x5),
        ) {
            Column(Modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text(
                    if (isLoading) "확인 중" else if (isGranted) "통화 분석에 동의함" else "통화 분석 동의 없음",
                    style = CollogType.subtitle01,
                    color = CollogColors.gray900,
                )
                Text(
                    "두 사람 모두 동의해야 통화를 녹음하고 외부 AI로 분석해요.",
                    style = CollogType.body03Medium,
                    color = CollogColors.gray700,
                )
            }

            SettingsSection("가족에게 보이는 내용") {
                ScopeRow("주간 리포트", "말씀 속도, 통화 길이, 변화 신호")
                DividerLine()
                ScopeRow("통화 요약", "대화 주제와 관찰 횟수")
                DividerLine()
                ScopeRow("건강 피드백", "최근 기록에서 확인한 생활 변화")
            }

            SettingsSection("계정과 알림") {
                // Android Keystore 사용. iOS 원문은 "기기 키체인에 보관돼요."
                ScopeRow("로그인 정보", "로그인 토큰은 기기의 보안 저장소에 보관돼요.")
                DividerLine()
                ScopeRow("푸시 토큰", "전화와 알림 수신을 위해 서버에 등록돼요.")
            }

            if (!isGuest) {
                SystemTextButton("분석 동의 내용 확인 및 변경", enabled = !isLoading && !isSubmitting) { showsConsent = true }
                if (isGranted) {
                    SystemTextButton("분석 동의 철회", enabled = !isLoading && !isSubmitting, destructive = true) {
                        confirmsRevocation = true
                    }
                }
            }

            if (agreedItems.isNotEmpty()) {
                Column(Modifier.cardSurface(), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                    Text("동의 항목", style = CollogType.body01Semibold, color = CollogColors.gray900)
                    agreedItems.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.Top) {
                            CollogIcon("checkmark", size = 14.dp, color = CollogColors.greenDark)
                            Text(ConsentItemLabel.korean(item), style = CollogType.body03Medium, color = CollogColors.gray800)
                        }
                    }
                }
            }

            errorText?.let {
                Text(it, style = CollogType.body03Medium, color = CollogColors.red500)
                SystemTextButton("다시 시도", enabled = !isLoading && !isSubmitting) { scope.launch { load() } }
            }
        }
    }

    if (showsConsent) {
        ModalBottomSheet(
            onDismissRequest = ::closeConsent,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CollogColors.gray50,
        ) {
            ConsentScreen(onAgreed = ::closeConsent, showsAccountActions = false)
        }
    }
    if (confirmsRevocation) {
        SettingsAlert(
            title = "통화 분석 동의를 철회할까요?",
            message = "일반 통화는 계속 이용할 수 있어요. 기존 기록 삭제는 계정 관리에서 요청할 수 있어요.",
            confirmText = "철회",
            destructive = true,
            onConfirm = ::revoke,
            onDismiss = { confirmsRevocation = false },
        )
    }
}

/** iOS `SettingsScopeRow`. */
@Composable
private fun ScopeRow(title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.x4, vertical = Spacing.x3),
        verticalArrangement = Arrangement.spacedBy(Spacing.x1),
    ) {
        Text(title, style = CollogType.body02Medium, color = CollogColors.gray900)
        Text(detail, style = CollogType.caption01Medium, color = CollogColors.gray700)
    }
}
