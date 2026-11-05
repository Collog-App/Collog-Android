package live.collog.app.shared.components.spec

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import live.collog.app.shared.components.stat.CallStat
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specText

/** [SpecStatRow]에서 「지난주 2건」·「평소 12분」 같은 비교 글자를 놓는 자리. */
enum class SpecNotePlacement { End, Inline }

/**
 * 새 피그마 「통계 칸」: 라벨 12/400 muted, 값 22/700, 단위 13/400, 비교 글자는 [note] 자리에.
 * 05 「카드/통화 요약」(비교 글자 오른쪽 끝, 11)과 08 타임라인 카드(값 바로 옆, 12)가 같이 쓴다.
 * 칸 사이는 세로 구분선 1dp(`#eef0f2`).
 */
@Composable
fun SpecStatRow(
    stats: List<CallStat>,
    modifier: Modifier = Modifier,
    notePlacement: SpecNotePlacement = SpecNotePlacement.End,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) {
                Box(Modifier.padding(horizontal = 16.dp).fillMaxHeight().width(1.dp).background(SpecColors.line))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(stat.label, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(stat.value, Modifier.alignByBaseline(), style = specText(Pretendard.Bold, 22f))
                    Text(stat.unit, Modifier.alignByBaseline(), style = specText(Pretendard.Regular, 13f, SpecColors.sub))
                    val note = stat.note
                    if (note != null) {
                        if (notePlacement == SpecNotePlacement.End) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            note.text,
                            Modifier.alignByBaseline(),
                            style = specText(
                                Pretendard.Regular,
                                if (notePlacement == SpecNotePlacement.End) 11f else 12f,
                                SpecColors.muted,
                            ),
                        )
                    }
                }
            }
        }
    }
}
