package live.collog.app.features.timeline

import java.time.Instant
import live.collog.app.features.timeline.models.RecentCalls
import live.collog.app.shared.services.api.CallSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Test

/** 무료로 잠긴 타임라인 탭의 「최근 통화」(11-28 용진 결정). */
class RecentCallsTests {
    private fun call(
        id: String = "c1",
        state: String = "ANALYZED",
        recorded: Boolean = true,
        startedAt: Instant = Instant.parse("2026-11-27T06:12:00Z"),
        durationSec: Int? = 754,
        reason: String? = null,
    ) = CallSummaryDto(
        callId = id, parentId = "mom", state = state, startedAt = startedAt,
        durationSec = durationSec, recorded = recorded, recordingDisabledReason = reason,
    )

    @Test
    fun rowShowsKoreanDateTimeDurationAndStatus() {
        val row = RecentCalls.rows(listOf(call())).single()
        assertEquals("c1", row.callId)
        // 한국 시간 2026-11-27 15:12(금요일)
        assertEquals("11월 27일 (금) 오후 3:12", row.dateText)
        assertEquals("12분", row.durationText)
        assertEquals("분석 완료", row.statusText)
    }

    @Test
    fun underAMinuteIsWrittenInSeconds() {
        assertEquals("45초", RecentCalls.duration(45))
        assertEquals("0초", RecentCalls.duration(0))
        assertEquals("1분", RecentCalls.duration(119))
    }

    @Test
    fun statusFollowsTheCallState() {
        assertEquals("분석 중", RecentCalls.status(call(state = "ENDED")))
        assertEquals("분석 중", RecentCalls.status(call(state = "PROCESSING")))
        assertEquals("짧아서 분석 제외", RecentCalls.status(call(state = "ANALYSIS_EXCLUDED")))
        assertEquals(
            "이번 달 분석 시간 초과",
            RecentCalls.status(call(state = "ANALYSIS_EXCLUDED", recorded = false, reason = "ANALYSIS_QUOTA_EXCEEDED")),
        )
        assertEquals("분석 실패", RecentCalls.status(call(state = "ANALYSIS_FAILED")))
        // 녹음이 꺼졌거나(동의 없음 등) 받지 않은 통화
        assertEquals("분석 안 함", RecentCalls.status(call(state = "ANALYSIS_EXCLUDED", recorded = false, reason = "CONSENT_DENIED")))
        assertEquals("분석 안 함", RecentCalls.status(call(state = "ENDED", recorded = false)))
    }

    @Test
    fun onlyTheLatestTwentyNewestFirst() {
        val start = Instant.parse("2026-11-01T00:00:00Z")
        val calls = (0 until 25).map { call(id = "c$it", startedAt = start.plusSeconds(it * 3600L)) }
        val rows = RecentCalls.rows(calls.shuffled())
        assertEquals(20, rows.size)
        assertEquals("c24", rows.first().callId)
        assertEquals("c5", rows.last().callId)
    }
}
