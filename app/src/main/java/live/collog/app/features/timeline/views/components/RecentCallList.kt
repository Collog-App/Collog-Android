package live.collog.app.features.timeline.views.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.collog.app.features.timeline.models.RecentCallRow
import live.collog.app.features.timeline.models.RecentCalls
import live.collog.app.features.timeline.models.RecentCallsState
import live.collog.app.shared.components.emptystate.EmptyState
import live.collog.app.shared.components.emptystate.NotSharedEmptyState
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

/**
 * 무료로 잠긴 타임라인 탭의 잠금 안내 아래 「최근 통화」(11-28 용진 결정): 줄마다 날짜·시각, 통화 길이, 분석 상태.
 * 누르면 [onOpen]으로 그 통화의 분석 화면을 연다.
 */
@Composable
fun RecentCallList(
    state: RecentCallsState,
    onOpen: (callId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = Spacing.x5), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
        Text(RecentCalls.TITLE, style = specText(Pretendard.Bold, 15f))
        when (state) {
            RecentCallsState.Loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CollogProgress()
            }
            RecentCallsState.NotShared -> NotSharedEmptyState()
            is RecentCallsState.Failed -> EmptyState(
                symbol = "exclamationmark.circle",
                title = "데이터를 불러오지 못했어요.",
                message = state.message,
                actionTitle = "다시 시도",
                action = onRetry,
            )
            is RecentCallsState.Loaded -> if (state.rows.isEmpty()) {
                EmptyState(symbol = "phone.fill", title = RecentCalls.EMPTY)
            } else {
                Column(Modifier.specCard(padding = 0.dp, radius = 16.dp)) {
                    state.rows.forEachIndexed { index, row ->
                        RecentCallLine(row) { onOpen(row.callId) }
                        if (index != state.rows.lastIndex) SpecDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCallLine(row: RecentCallRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(start = 15.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.dateText, style = specText(Pretendard.Medium, 14f), maxLines = 1)
            Text("${row.durationText} · ${row.statusText}", style = specText(Pretendard.Regular, 12f, SpecColors.muted), maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        CollogIcon("chevron.right", size = 18.dp, color = SpecColors.chevron)
    }
}
