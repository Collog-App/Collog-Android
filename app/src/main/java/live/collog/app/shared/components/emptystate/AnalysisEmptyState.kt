package live.collog.app.shared.components.emptystate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.ui.PendingPort
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.DividerLine
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/** iOS `AnalysisEmptyAction`: 분석된 통화가 없을 때 빈 화면이 권하는 다음 행동. */
enum class AnalysisEmptyAction {
    Processing,
    Family,
    Call,
    CurrentWeek,
    None;

    companion object {
        fun resolve(
            calls: List<CallSummaryDto>,
            canCall: Boolean,
            isCurrentWeek: Boolean,
            isGuest: Boolean,
        ): AnalysisEmptyAction {
            if (calls.any { it.isPendingAnalysis }) return Processing
            if (!isCurrentWeek) return CurrentWeek
            if (isGuest) return None
            return if (canCall) Call else Family
        }
    }
}

private val CallSummaryDto.isPendingAnalysis: Boolean
    get() = recorded && state in listOf("ENDED", "PROCESSING")

/** iOS `AnalysisCallStatusSnapshot`. */
object AnalysisCallStatusSnapshot {
    /** 통화 번호별 최신 상태. 새로 받은 목록에 없으면 `UNAVAILABLE`. */
    fun states(calls: List<CallSummaryDto>, refreshedCalls: List<CallSummaryDto>): Map<String, String> =
        calls.associate { call ->
            call.id to (
                refreshedCalls.firstOrNull { it.id == call.id && it.parentId == call.parentId }?.state
                    ?: "UNAVAILABLE"
                )
        }
}

/**
 * iOS `AnalysisEmptyStateView`.
 * iOS가 환경에서 꺼내 쓰던 두 가지는 인자로 받는다: 전화 걸기 [onStartCall](`CallCenter.startOutgoingCall`)과
 * 가족 설정 시트 내용 [familySheet](`FamilyMembersSettingsView`, 설정 포팅 전에는 자리 화면).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisEmptyState(
    onStartCall: (FamilyContact) -> Unit,
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    contact: FamilyContact? = null,
    calls: List<CallSummaryDto> = emptyList(),
    isCurrentWeek: Boolean = true,
    onCurrentWeek: (() -> Unit)? = null,
    familySheet: @Composable (onDismiss: () -> Unit) -> Unit = { PendingPort("가족 구성원", Modifier.height(240.dp)) },
) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var showsFamily by remember { mutableStateOf(false) }
    var showsCalls by remember { mutableStateOf(false) }

    val action = AnalysisEmptyAction.resolve(
        calls = calls,
        canCall = contact?.isCallable == true,
        isCurrentWeek = isCurrentWeek,
        isGuest = environment.settings.isGuestMode,
    )
    val message = when (action) {
        AnalysisEmptyAction.Processing -> "통화 분석을 준비하고 있어요. 해당 통화의 상태를 확인할 수 있어요."
        AnalysisEmptyAction.Family -> "가족을 초대하거나 받은 초대 코드를 입력해 첫 통화를 시작해 보세요."
        AnalysisEmptyAction.Call -> "가족과 통화하고 분석에 동의하면 대화 내용을 다시 살펴볼 수 있어요."
        AnalysisEmptyAction.CurrentWeek -> "선택한 기간에는 분석된 통화가 없어요. 이번 주 기록을 확인해 보세요."
        AnalysisEmptyAction.None -> "선택한 기간에 통화 분석이 완료되면 여기에 표시돼요."
    }
    val actionTitle = when (action) {
        AnalysisEmptyAction.Processing -> "통화 상태 보기"
        AnalysisEmptyAction.Family -> "가족 초대하기"
        AnalysisEmptyAction.Call -> "${contact?.name ?: "가족"}에게 전화하기"
        AnalysisEmptyAction.CurrentWeek -> "이번 주 보기"
        AnalysisEmptyAction.None -> null
    }

    EmptyState(
        symbol = if (action == AnalysisEmptyAction.Processing) "waveform" else "doc.text.magnifyingglass",
        title = if (action == AnalysisEmptyAction.Processing) "통화를 분석하고 있어요." else "아직 분석된 데이터가 없어요.",
        modifier = modifier,
        message = message,
        actionTitle = actionTitle,
        action = {
            when (action) {
                AnalysisEmptyAction.Processing -> showsCalls = true
                AnalysisEmptyAction.Family -> showsFamily = true
                AnalysisEmptyAction.Call -> {
                    if (contact?.userId != null) onStartCall(contact)
                }
                AnalysisEmptyAction.CurrentWeek -> onCurrentWeek?.invoke()
                AnalysisEmptyAction.None -> Unit
            }
        },
    )

    if (showsFamily) {
        val dismiss: () -> Unit = {
            showsFamily = false
            scope.launch { onRefresh() }
        }
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CollogColors.gray50,
        ) { familySheet(dismiss) }
    }

    if (showsCalls) {
        val dismiss: () -> Unit = {
            showsCalls = false
            scope.launch { onRefresh() }
        }
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CollogColors.gray50,
        ) { AnalysisCallStatus(calls, dismiss) }
    }
}

/** iOS `AnalysisCallStatusView`: 분석을 기다리는 통화의 최신 상태 목록. */
@Composable
private fun AnalysisCallStatus(calls: List<CallSummaryDto>, onDone: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var states by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pendingCalls = calls.filter { it.isPendingAnalysis }

    suspend fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        error = null
        try {
            val refreshed = mutableListOf<CallSummaryDto>()
            for (parentId in pendingCalls.map { it.parentId }.toSet()) {
                refreshed += environment.api.calls(parentId)
            }
            states = AnalysisCallStatusSnapshot.states(pendingCalls, refreshed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.x5, vertical = Spacing.x3), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text("통화 분석 상태", style = CollogType.body01Semibold, color = CollogColors.gray900)
            Text(
                "완료",
                style = CollogType.body01Semibold,
                color = CollogColors.greenDark,
                modifier = Modifier.weight(1f).pressable(onClick = onDone),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }

        Column(
            Modifier
                .padding(horizontal = Spacing.x4)
                .fillMaxWidth()
                .background(CollogColors.gray00, RoundedCornerShape(Spacing.x3))
                .padding(horizontal = Spacing.x4),
        ) {
            pendingCalls.forEach { call ->
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.x3), verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Text(formatStarted(call), style = CollogType.body01Regular, color = CollogColors.gray900)
                    Text(statusText(states[call.id] ?: call.state), style = CollogType.body03Medium, color = CollogColors.gray700)
                }
                DividerLine()
            }
            error?.let {
                Text(it, style = CollogType.body03Medium, color = CollogColors.red500, modifier = Modifier.padding(vertical = Spacing.x3))
                DividerLine()
            }
            Text(
                "상태 새로고침",
                style = CollogType.body01Regular,
                color = CollogColors.greenDark,
                modifier = Modifier
                    .padding(vertical = Spacing.x3)
                    .pressable(enabled = !isRefreshing) { scope.launch { refresh() } },
            )
        }
        Spacer(Modifier.height(Spacing.x6))
    }
}

// iOS `startedAt.formatted(date: .abbreviated, time: .shortened)`: 한국어 기기에서 `2026년 9월 10일 오전 9:26`
private val startedFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm", Locale.KOREAN)

private fun formatStarted(call: CallSummaryDto): String =
    startedFormatter.format(call.startedAt.atZone(ZoneId.systemDefault()))

private fun statusText(state: String): String = when (state) {
    "ENDED" -> "분석 대기 중"
    "PROCESSING" -> "분석 중"
    "ANALYZED" -> "분석 완료. 이전 화면에서 기록을 확인해 주세요."
    "ANALYSIS_EXCLUDED" -> "분석 대상에서 제외된 통화예요."
    "ANALYSIS_FAILED" -> "분석하지 못했어요."
    "UNAVAILABLE" -> "삭제되었거나 더 이상 확인할 수 없는 통화예요."
    else -> "분석 상태를 확인하지 못했어요."
}
