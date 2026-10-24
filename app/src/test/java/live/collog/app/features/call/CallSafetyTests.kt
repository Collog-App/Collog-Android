package live.collog.app.features.call

import kotlinx.coroutines.CancellationException
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.models.CallSafety
import live.collog.app.shared.services.api.ApiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `CallSafetyTests`. */
class CallSafetyTests {
    @Test
    fun pushIsAcceptedOnlyByItsCallee() {
        assertFalse(CallSafety.acceptsPush(userId = null, calleeId = "parent"))
        assertFalse(CallSafety.acceptsPush(userId = "child", calleeId = "parent"))
        assertFalse(CallSafety.acceptsPush(userId = "parent", calleeId = null))
        assertTrue(CallSafety.acceptsPush(userId = "parent", calleeId = "parent"))
    }

    @Test
    fun recordingRequiresConsentAndRawCapture() {
        assertFalse(CallSafety.canRecord(enabled = false, rawRequired = true, accepted = true, phase = CallPhase.Active))
        assertFalse(CallSafety.canRecord(enabled = true, rawRequired = false, accepted = true, phase = CallPhase.Active))
        assertTrue(CallSafety.canRecord(enabled = true, rawRequired = true, accepted = true, phase = CallPhase.Active))
    }

    @Test
    fun recordingWaitsForAnActiveAcceptedCall() {
        for (phase in listOf(CallPhase.Connecting, CallPhase.Ringing, CallPhase.Reconnecting, CallPhase.Ended)) {
            assertFalse(CallSafety.canRecord(enabled = true, rawRequired = true, accepted = true, phase = phase))
        }
        assertFalse(CallSafety.canRecord(enabled = true, rawRequired = true, accepted = false, phase = CallPhase.Active))
    }

    @Test
    fun permanentStatusErrorsStopTheCall() {
        for (status in listOf(403, 404, 410)) {
            val error = ApiError.Server(status, "GONE", "fixture")
            assertTrue(CallSafety.isPermanentStatusError(error))
            assertFalse(CallSafety.canRetry(error))
        }
    }

    @Test
    fun onlyTransientErrorsRetry() {
        assertTrue(CallSafety.canRetry(ApiError.Transport("offline")))
        assertTrue(CallSafety.canRetry(ApiError.Server(503, "FAILED", "fixture")))
        assertFalse(CallSafety.canRetry(ApiError.Server(409, "BUSY", "fixture")))
        assertFalse(CallSafety.canRetry(ApiError.Unauthenticated))
        assertFalse(CallSafety.canRetry(CancellationException()))
    }
}
