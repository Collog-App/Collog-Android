package live.collog.app.features.settings.views

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.views.ConsentScreen
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.settings.models.GuestSamples
import live.collog.app.features.settings.models.deleteAccountAndSignOut
import live.collog.app.features.settings.models.updateMarketingOptIn
import live.collog.app.features.settings.views.components.SettingsAlert
import live.collog.app.features.settings.views.components.SettingsBullet
import live.collog.app.features.settings.views.components.SettingsCallout
import live.collog.app.features.settings.views.components.SettingsCalloutText
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SettingsLink
import live.collog.app.features.settings.views.components.SettingsSection
import live.collog.app.features.settings.views.components.SettingsToggleRow
import live.collog.app.features.settings.views.components.SettingsValueRow
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing

private const val PRIVACY_POLICY_URL = "https://collog-app.github.io/Collog-Privacy/"

/**
 * 피그마 「최종본」 계정 정보(277:5822). 시트로 띄우면 [onClose]를 넘기고, 탭 안에서 밀어 넣으면 null이다.
 * 소셜 로그인이라 「비밀번호 변경」과 이메일 「변경」은 없다. 피그마에 없는 개인정보처리방침·분석 동의·로그아웃·
 * 계정 삭제(Play 정책상 앱 안 삭제 필요)는 「개인정보 및 보안」 상자에 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(onClose: (() -> Unit)? = null) {
    val environment = LocalAppEnvironment.current
    val callCenter = LocalCallCenter.current
    val navigator = LocalNavigationManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = environment.settings
    val isGuest = settings.isGuestMode
    val isAuthenticated = environment.session.isAuthenticated
    val user = if (isGuest) GuestSamples.user else environment.session.user

    var marketingOptIn by remember(user?.id) { mutableStateOf(user?.marketingOptIn ?: false) }
    var isUpdatingMarketing by remember { mutableStateOf(false) }
    var consentGranted by remember { mutableStateOf(false) }
    var showsConsent by remember { mutableStateOf(false) }
    var confirmsRevocation by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var confirmsDeletion by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val locked = isSubmitting || callCenter.hasCallInProgress

    suspend fun loadConsent() {
        if (isGuest || !isAuthenticated) return
        try {
            val consent = environment.api.myConsent()
            consentGranted = consent.isCurrent && consent.isGranted
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            consentGranted = false
        }
    }

    fun setMarketing(value: Boolean) {
        marketingOptIn = value
        if (isGuest || isUpdatingMarketing) return
        isUpdatingMarketing = true
        errorMessage = null
        scope.launch {
            try {
                updateMarketingOptIn(environment, value)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                marketingOptIn = !value
                errorMessage = error.message
            } finally {
                isUpdatingMarketing = false
            }
        }
    }

    fun revokeConsent() {
        if (isSubmitting) return
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                val document = environment.api.consentDocument()
                environment.api.submitConsent(
                    documentVersion = document.version,
                    agreedItems = emptyList(),
                    decision = "DENY",
                    scrolledToEnd = false,
                )
                loadConsent()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message
            } finally {
                isSubmitting = false
            }
        }
    }

    fun deleteAccount() {
        if (isSubmitting || callCenter.hasCallInProgress) return
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                deleteAccountAndSignOut(environment)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message
            } finally {
                isSubmitting = false
            }
        }
    }

    // iOS `.sheet(onDismiss:)`: 동의 시트가 닫히면 상태를 다시 읽는다
    fun closeConsent() {
        showsConsent = false
        scope.launch { loadConsent() }
    }

    LaunchedEffect(Unit) { loadConsent() }

    Column(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray00)
            .then(if (onClose == null) Modifier.statusBarsPadding() else Modifier),
    ) {
        SettingsDetailHeader("계정 정보", onBack = onClose ?: { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5)
                .padding(top = 27.dp, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            SettingsSection("기본 정보") {
                SettingsCallout(spacing = 21.dp) {
                    user?.email?.let { SettingsValueRow("이메일", it) }
                    SettingsValueRow("이름", user?.let { it.nickname ?: it.name } ?: "데모 사용자")
                    user?.phone?.let { SettingsValueRow("전화번호", it) }
                    user?.birthDate?.let { SettingsValueRow("생년월일", it) }
                }
            }

            SettingsSection("알림 설정") {
                Column {
                    SettingsToggleRow(
                        label = "통화 알림",
                        isOn = settings.callNotificationsEnabled,
                        onIsOnChange = { settings.callNotificationsEnabled = it },
                    )
                    SettingsToggleRow(
                        label = "리포트 발송 알림",
                        isOn = settings.reportNotificationsEnabled,
                        onIsOnChange = { settings.reportNotificationsEnabled = it },
                    )
                    SettingsToggleRow(
                        label = "마케팅 소식 수신",
                        isOn = marketingOptIn,
                        onIsOnChange = ::setMarketing,
                        enabled = !isUpdatingMarketing,
                    )
                    SettingsToggleRow(
                        label = "질문 음성 안내",
                        caption = "상대가 전화를 받기 전에 오늘의 질문을 읽어줘요",
                        isOn = settings.questionVoiceEnabled,
                        onIsOnChange = { settings.questionVoiceEnabled = it },
                    )
                }
                callCenter.deviceRegistrationError?.let {
                    Text(it, style = CollogType.caption01Medium, color = CollogColors.red500)
                }
                callCenter.notificationPermissionError?.let {
                    Text(it, style = CollogType.caption01Medium, color = CollogColors.red500)
                }
            }

            SettingsSection("음성 데이터 처리", Modifier.padding(top = Spacing.x4)) {
                SettingsCallout {
                    SettingsCalloutText("원본 음성 데이터는 다음 정책을 따릅니다.", Modifier.padding(vertical = 10.dp))
                    SettingsBullet("음향 신호 추출 후 원본 오디오는 즉시 폐기됩니다.")
                    SettingsBullet("저장되는 것은 음향 지표와 대화 텍스트뿐입니다.")
                    SettingsBullet("이 설정은 변경할 수 없습니다.")
                }
            }

            SettingsSection("개인정보 및 보안", Modifier.padding(top = Spacing.x4)) {
                SettingsCallout(spacing = Spacing.x4) {
                    SettingsLink("개인정보처리방침") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                    }
                    if (isAuthenticated && !isGuest) {
                        SettingsLink("통화 분석 동의 확인 및 변경", onClick = { if (!locked) showsConsent = true })
                        if (consentGranted) {
                            SettingsLink("통화 분석 동의 철회", onClick = { if (!locked) confirmsRevocation = true })
                        }
                    }
                    if (isAuthenticated) {
                        SettingsLink("로그아웃", onClick = { if (!locked) scope.launch { environment.signOut() } })
                        SettingsLink(
                            "계정 삭제",
                            color = if (locked) CollogColors.gray600 else CollogColors.red500,
                            onClick = { if (!locked) confirmsDeletion = true },
                        )
                    }
                }
                if (callCenter.hasCallInProgress) {
                    Text("통화를 마친 뒤 계정 삭제를 할 수 있어요.", style = CollogType.caption01Medium, color = CollogColors.gray700)
                }
                errorMessage?.let {
                    Text(it, style = CollogType.caption01Medium, color = CollogColors.red500)
                }
                if (isSubmitting) CollogProgress()
            }
        }
    }

    if (showsConsent) {
        ModalBottomSheet(
            onDismissRequest = ::closeConsent,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CollogColors.gray50,
        ) {
            ConsentScreen(onAgreed = ::closeConsent)
        }
    }
    if (confirmsRevocation) {
        SettingsAlert(
            title = "통화 분석 동의를 철회할까요?",
            message = "일반 통화는 계속 이용할 수 있어요. 기존 기록 삭제는 계정 정보에서 요청할 수 있어요.",
            confirmText = "철회",
            destructive = true,
            onConfirm = ::revokeConsent,
            onDismiss = { confirmsRevocation = false },
        )
    }
    if (confirmsDeletion) {
        SettingsAlert(
            title = "계정을 삭제할까요?",
            message = "계정과 관련 통화 기록, 녹음, 건강 정보가 삭제돼요. 삭제 후에는 복구할 수 없어요.",
            confirmText = "삭제",
            destructive = true,
            onConfirm = ::deleteAccount,
            onDismiss = { confirmsDeletion = false },
        )
    }
}
