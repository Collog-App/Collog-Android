package live.collog.app.features.timeline.models

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import live.collog.app.shared.services.api.CallSummaryDto

/** 무료로 잠긴 타임라인 탭의 「최근 통화」 한 줄(11-28 용진 결정). 누르면 그 통화의 분석 화면. */
data class RecentCallRow(
    val callId: String,
    /** 「11월 27일 (금) 오후 3:12」(한국 시간) */
    val dateText: String,
    /** 1분 미만은 초, 그 위는 분(버림) */
    val durationText: String,
    val statusText: String,
    /** 내가 낀 통화만 연다. 다른 가족끼리 한 통화는 서버가 통화별 분석을 403 으로 막는다. */
    val isOpenable: Boolean,
)

/** 「최근 통화」 목록 상태. 그 탭에서 고른 사람의 `/parents/{id}/calls` 한 번으로 채운다. */
sealed interface RecentCallsState {
    data object Loading : RecentCallsState

    /** 가족이 통화 기록 공유를 껐다(403). */
    data object NotShared : RecentCallsState

    data class Failed(val message: String) : RecentCallsState

    data class Loaded(val rows: List<RecentCallRow>) : RecentCallsState
}

object RecentCalls {
    const val TITLE = "최근 통화"
    const val EMPTY = "아직 통화 기록이 없어요"
    const val LIMIT = 20

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")
    private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN)

    /** 최근 것부터 [LIMIT]건. [userId]는 로그인한 나(내가 낀 통화만 누를 수 있다). */
    fun rows(calls: List<CallSummaryDto>, userId: String?): List<RecentCallRow> =
        calls.sortedByDescending { it.startedAt }.take(LIMIT).map { call ->
            RecentCallRow(
                callId = call.callId,
                dateText = dateFormatter.format(call.startedAt.atZone(zone)),
                durationText = duration(call.durationSec ?: 0),
                statusText = status(call),
                isOpenable = userId != null && userId in listOf(call.parentId, call.childId),
            )
        }

    fun duration(seconds: Int): String = if (seconds < 60) "${seconds}초" else "${seconds / 60}분"

    fun status(call: CallSummaryDto): String = when {
        call.isAnalyzed -> "분석 완료"
        call.isAnalysisQuotaExceeded -> "이번 달 분석 시간 초과"
        // 녹음·분석을 하던 통화가 빠졌으면 말씀이 짧았던 것(동의·녹음 꺼짐은 recorded=false 라 아래 「분석 안 함」)
        call.isAnalysisExcluded && call.recorded -> "짧아서 분석 제외"
        call.state == "ANALYSIS_FAILED" -> "분석 실패"
        call.recorded && call.state in listOf("ENDED", "PROCESSING") -> "분석 중"
        else -> "분석 안 함"
    }
}
