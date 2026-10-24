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
        for (state in listOf("ANALYZED", "ANALYSIS_EXCLUDED", "ANALYSIS_FAILED", "ACTIVE")) {
            assertEquals(state, AnalysisEmptyAction.Call, resolve(calls = listOf(call(state, recorded = true))))
        }
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

    private fun call(state: String, recorded: Boolean) = CallSummaryDto(
        callId = "call", parentId = "parent", state = state, timeSlot = null,
        startedAt = Instant.now(), endedAt = Instant.now(), durationSec = 30, recorded = recorded, parentSpeechSec = null,
    )
}
