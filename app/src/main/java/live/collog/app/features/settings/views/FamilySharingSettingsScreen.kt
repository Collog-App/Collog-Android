package live.collog.app.features.settings.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import live.collog.app.features.settings.models.GuestSamples
import live.collog.app.features.settings.models.memberTitle
import live.collog.app.features.settings.models.permissionText
import live.collog.app.features.settings.models.updating
import live.collog.app.features.settings.views.components.MemberTitle
import live.collog.app.features.settings.views.components.SettingsCallout
import live.collog.app.features.settings.views.components.SettingsCalloutText
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SettingsMutedColor
import live.collog.app.features.settings.views.components.SettingsSection
import live.collog.app.features.settings.views.components.SettingsSwitch
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.services.api.FamilyShare
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.singleLine

/**
 * 피그마 「최종본」 가족 공유(278:6405). 내 리포트·타임라인을 구성원마다 보일지 정한다.
 * 피그마에 없는 켜고 끄는 스위치를 카드 오른쪽에 두고, 바꿀 때마다 저장한다(`PUT /v1/account/sharing`).
 */
@Composable
fun FamilySharingSettingsScreen() {
    val environment = LocalAppEnvironment.current
    val navigator = LocalNavigationManager.current
    val scope = rememberCoroutineScope()

    var shares by remember { mutableStateOf<List<FamilyShare>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val isGuest = environment.settings.isGuestMode

    suspend fun load() {
        isLoading = true
        errorText = null
        try {
            shares = if (isGuest) GuestSamples.shares else environment.api.sharing()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorText = error.message
        } finally {
            isLoading = false
        }
    }

    fun update(next: List<FamilyShare>) {
        val previous = shares
        shares = next
        if (isGuest) return
        isSaving = true
        errorText = null
        scope.launch {
            try {
                shares = environment.api.updateSharing(next)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                shares = previous
                errorText = error.message
            } finally {
                isSaving = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(CollogColors.gray00).statusBarsPadding()) {
        SettingsDetailHeader("가족 공유 데이터 범위", onBack = { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5)
                .padding(top = Spacing.x5, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x5),
        ) {
            SettingsSection("가족 공유", spacing = Spacing.x5) {
                SettingsCallout {
                    SettingsCalloutText("공유할 가족 구성원을 선택하고 열람 권한을 확인하세요.")
                }
            }

            SettingsSection("공유 대상", spacing = Spacing.x5) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x5)) {
                    when {
                        isLoading -> repeat(2) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(104.dp)
                                    .background(CollogColors.gray200, RoundedCornerShape(12.dp)),
                            )
                        }
                        shares.isEmpty() && errorText == null -> SettingsCallout {
                            SettingsCalloutText("아직 함께하는 가족이 없어요. 가족을 초대하면 여기에서 공유 범위를 정할 수 있어요.")
                        }
                        else -> shares.forEach { share ->
                            ShareCard(
                                share = share,
                                enabled = !isSaving,
                                onReportChange = { update(shares.updating(share.viewerUserId, report = it)) },
                                onTimelineChange = { update(shares.updating(share.viewerUserId, timeline = it)) },
                            )
                        }
                    }
                }
            }

            errorText?.let {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Text(it, style = CollogType.body03Medium, color = CollogColors.red500)
                    SystemTextButton("다시 시도", enabled = !isLoading && !isSaving) { scope.launch { load() } }
                }
            }
        }
    }
}

/** 피그마 공유 대상 카드(278:6500). 둘째 줄 「드링클리 본점」은 자리 글자라 비워 둔다. */
@Composable
private fun ShareCard(
    share: FamilyShare,
    enabled: Boolean,
    onReportChange: (Boolean) -> Unit,
    onTimelineChange: (Boolean) -> Unit,
) {
    val title = memberTitle(share.viewerName, share.relation)
    Row(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray200, RoundedCornerShape(12.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            MemberTitle(title)
            Spacer(Modifier.height(17.dp))
            Text(permissionText(share), style = singleLine(Pretendard.Regular, 14f), color = SettingsMutedColor)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            ShareSwitch("리포트", share.report, enabled, "$title 리포트 공유", onReportChange)
            ShareSwitch("타임라인", share.timeline, enabled, "$title 타임라인 공유", onTimelineChange)
        }
    }
}

@Composable
private fun ShareSwitch(label: String, isOn: Boolean, enabled: Boolean, description: String, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = singleLine(Pretendard.Regular, 14f), color = CollogColors.gray800)
        SettingsSwitch(isOn, onChange, label = description, enabled = enabled)
    }
}
