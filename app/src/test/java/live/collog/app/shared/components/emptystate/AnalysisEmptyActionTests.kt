package live.collog.app.shared.components.emptystate

import java.time.Instant
import live.collog.app.shared.services.api.CallSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Test

/** iOS `AnalysisEmptyActionTests`. */
class AnalysisEmptyActionTests {
    @Test
    fun missingCallableFamilyOffersInvitation() {
        assertEquals(AnalysisEmptyAction.Family, resolve(canCall = false))
        assertEquals(AnalysisEmptyAction.Call, resolve(canCall = true))
    }

    @Test
    fun historicalWeekOffersCurrentWeekWithoutCalling() {
        assertEquals(AnalysisEmptyAction.CurrentWeek, resolve(canCall = true, isCurrentWeek = false))
    }

    @Test
    fun guestDoesNotOfferRealCallOrInvitation() {
        assertEquals(AnalysisEmptyAction.None, resolve(canCall = false, isGuest = true))
    }

    @Test
    fun recordedPendingCallOffersStatus() {
        for (state in listOf("ENDED", "PROCESSING")) {
            assertEquals(state, AnalysisEmptyAction.Processing, resolve(calls = listOf(call(state, recorded = true))))
        }
    }

    @Test
    fun terminalAndActiveCallsDoNotClaimAnalysisIsPending() {
        for (state in listOf("ANALYZED", "ACTIVE")) {
            assertEquals(state, AnalysisEmptyAction.Call, resolve(calls = listOf(call(state, recorded = true))))
        }
    }

    // 가장 최근에 이어진 통화가 분석에서 빠졌거나 실패했으면 「데이터 없음」 대신 그 사유를 알린다
    @Test
    fun latestConnectedCallExplainsExclusionOrFailure() {
        assertEquals(AnalysisEmptyAction.Excluded, resolve(calls = listOf(call("ANALYSIS_EXCLUDED", recorded = true))))
        assertEquals(AnalysisEmptyAction.Failed, resolve(calls = listOf(call("ANALYSIS_FAILED", recorded = true))))
        // 지난 주를 보고 있어도 사유가 먼저다
        assertEquals(
            AnalysisEmptyAction.Excluded,
            resolve(calls = listOf(call("ANALYSIS_EXCLUDED", recorded = true)), isCurrentWeek = false),
        )
    }

    @Test
    fun latestCallSkippedAfterTheMonthlyQuotaHasItsOwnReason() {
        // 서버는 분량 초과 통화를 recorded=false 로 둔다
        val skipped = call("ANALYSIS_EXCLUDED", recorded = false).copy(recordingDisabledReason = "ANALYSIS_QUOTA_EXCEEDED")
        assertEquals(AnalysisEmptyAction.QuotaExceeded, resolve(calls = listOf(skipped)))
        val consent = call("ANALYSIS_EXCLUDED", recorded = true).copy(recordingDisabledReason = "CONSENT_DENIED")
        assertEquals(AnalysisEmptyAction.Excluded, resolve(calls = listOf(consent)))
    }

    @Test
    fun onlyTheLatestConnectedCallDecidesTheReason() {
        val earlier = Instant.parse("2026-11-20T03:00:00Z")
        val later = Instant.parse("2026-11-21T03:00:00Z")
        // 그 뒤에 분석된 통화가 있으면 옛 제외 통화는 사유가 아니다
        assertEquals(
            AnalysisEmptyAction.Call,
            resolve(calls = listOf(call("ANALYSIS_EXCLUDED", recorded = true, startedAt = earlier), call("ANALYZED", recorded = true, startedAt = later))),
        )
        // 받지 않은 통화(0초)는 세지 않는다
        assertEquals(
            AnalysisEmptyAction.Call,
            resolve(calls = listOf(call("ANALYSIS_EXCLUDED", recorded = false, durationSec = 0))),
        )
    }

    @Test
    fun unrecordedEndedCallDoesNotOfferAnalysisStatus() {
        assertEquals(AnalysisEmptyAction.Call, resolve(calls = listOf(call("ENDED", recorded = false))))
    }

    @Test
    fun statusUsesLatestFamilyCallList() {
        val states = AnalysisCallStatusSnapshot.states(
            calls = listOf(call("PROCESSING", recorded = true)),
            refreshedCalls = listOf(call("ANALYZED", recorded = true)),
        )
        assertEquals("ANALYZED", states["call"])
    }

    @Test
    fun removedCallDoesNotKeepOldProcessingState() {
        val states = AnalysisCallStatusSnapshot.states(
            calls = listOf(call("PROCESSING", recorded = true)),
            refreshedCalls = emptyList(),
        )
        assertEquals("UNAVAILABLE", states["call"])
    }

    private fun resolve(
        calls: List<CallSummaryDto> = emptyList(),
        canCall: Boolean = true,
        isCurrentWeek: Boolean = true,
        isGuest: Boolean = false,
    ) = AnalysisEmptyAction.resolve(calls, canCall, isCurrentWeek, isGuest)

    private fun call(state: String, recorded: Boolean, startedAt: Instant = Instant.now(), durationSec: Int = 30) = CallSummaryDto(
        callId = "call", parentId = "parent", state = state, timeSlot = null,
        startedAt = startedAt, endedAt = startedAt, durationSec = durationSec, recorded = recorded, parentSpeechSec = null,
    )
}
