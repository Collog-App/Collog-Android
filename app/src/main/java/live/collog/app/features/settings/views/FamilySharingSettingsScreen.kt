package live.collog.app.features.settings.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.components.spec.SpecDetailHeader
import live.collog.app.shared.components.spec.SpecToggle
import live.collog.app.shared.services.api.ConsentItemLabel
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.services.api.FamilyShare
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing

/**
 * 새 피그마 18 가족 공유 데이터 범위. 내 리포트·타임라인을 구성원마다 보일지 정한다.
 * 새 피그마 18(안내 전용) 아래에 켜고 끄는 스위치를 구성원 카드 오른쪽에 두고, 바꿀 때마다 저장한다(`PUT /v1/account/sharing`).
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
    var agreedItems by remember { mutableStateOf<List<String>>(emptyList()) }

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

    LaunchedEffect(Unit) {
        load()
        // 동의 항목은 서버 기록 그대로 보인다. 읽지 못하거나 체험 모드면 이 카드는 숨긴다.
        if (!isGuest) {
            try {
                val consent = environment.api.myConsent()
                if (consent.isGranted) agreedItems = consent.agreedItems
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                agreedItems = emptyList()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(SpecColors.page).statusBarsPadding()) {
        SpecDetailHeader("가족 공유 데이터 범위", onBack = { navigator.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 19.dp)
                .padding(top = 9.dp, bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier.specCard(padding = 0.dp, radius = 16.dp).padding(horizontal = 17.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("가족과 공유 중", style = specText(Pretendard.Bold, 17f))
                Text("가족은 요약된 기록만 확인할 수 있어요.", style = specText(Pretendard.Regular, 14f, SpecColors.muted))
            }

            GroupLabel("가족에게 보이는 내용", 12f)
            InfoCard(
                listOf(
                    "주간 리포트" to "말씀 속도, 통화 길이, 변화 신호",
                    "통화 요약" to "대화 주제와 관찰 횟수",
                    "건강 피드백" to "최근 기록에서 확인한 생활 변화",
                ),
            )

            GroupLabel("기기에만 보관", 9.5f)
            InfoCard(
                listOf(
                    "계정 인증 정보" to "로그인 토큰과 기기 정보",
                    "알림 설정" to "개별 알림 선택",
                ),
            )

            if (agreedItems.isNotEmpty()) {
                Column(
                    Modifier.specCard(padding = 0.dp, radius = 16.dp).padding(horizontal = 17.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("동의 항목", style = specText(Pretendard.Bold, 12.5f))
                    agreedItems.sortedBy { ConsentOrder.indexOf(it).let { i -> if (i < 0) ConsentOrder.size else i } }.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CollogIcon("checkmark", size = 13.dp, color = SpecColors.green)
                            Text(ConsentItemLabel.korean(item), style = specText(Pretendard.Regular, 11f, SpecColors.sub))
                        }
                    }
                }
            }

            // 명세에 없는 켜고 끄는 스위치: 구성원마다 리포트·타임라인을 보일지 정하고 바꿀 때마다 저장한다
            GroupLabel("구성원별 공유 설정", 12f, Modifier.padding(top = Spacing.x3))
            when {
                isLoading -> repeat(2) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .background(SpecColors.chip, RoundedCornerShape(16.dp)),
                    )
                }
                shares.isEmpty() && errorText == null -> Text(
                    "아직 함께하는 가족이 없어요. 가족을 초대하면 여기에서 공유 범위를 정할 수 있어요.",
                    style = specText(Pretendard.Regular, 14f, SpecColors.muted),
                )
                else -> shares.forEach { share ->
                    ShareCard(
                        share = share,
                        enabled = !isSaving,
                        onReportChange = { update(shares.updating(share.viewerUserId, report = it)) },
                        onTimelineChange = { update(shares.updating(share.viewerUserId, timeline = it)) },
                    )
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

/** 명세 18 의 동의 항목 순서. 그 밖의 항목은 뒤에 붙는다. */
private val ConsentOrder = listOf("SENSITIVE_HEALTH_COLLECTION", "VOICE_FEATURE_EXTRACTION", "CALL_RECORDING", "REPORT_SHARING_WITH_CHILD")

@Composable
private fun GroupLabel(text: String, size: Float, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 5.dp, top = 4.dp), style = specText(Pretendard.Regular, size, SpecColors.muted))
}

/** 새 피그마 「카드/가족에게 보이는 내용」·「기기에만 보관」: 제목 14/500 + 설명 12/400 muted 줄을 구분선으로 잇는다. */
@Composable
private fun InfoCard(rows: List<Pair<String, String>>) {
    Column(Modifier.specCard(padding = 0.dp, radius = 16.dp)) {
        rows.forEachIndexed { index, (title, caption) ->
            if (index > 0) SpecDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = specText(Pretendard.Medium, 14f))
                Text(caption, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
            }
        }
    }
}

/** 구성원별 공유 카드(새 피그마 카드 모양): 이름 14/500 + 열람 권한 12/400, 오른쪽에 리포트·타임라인 토글. */
@Composable
private fun ShareCard(
    share: FamilyShare,
    enabled: Boolean,
    onReportChange: (Boolean) -> Unit,
    onTimelineChange: (Boolean) -> Unit,
) {
    val title = memberTitle(share.viewerName, share.relation)
    Row(
        Modifier.specCard(padding = 0.dp, radius = 16.dp).padding(start = 17.dp, end = 13.dp, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = specText(Pretendard.Medium, 14f))
            Text(permissionText(share), style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            ShareSwitch("리포트", share.report, enabled, onReportChange)
            ShareSwitch("타임라인", share.timeline, enabled, onTimelineChange)
        }
    }
}

@Composable
private fun ShareSwitch(label: String, isOn: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x2), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = specText(Pretendard.Regular, 12f, SpecColors.sub))
        SpecToggle(isOn, onChange, onColor = SpecColors.toggleOn, enabled = enabled)
    }
}
