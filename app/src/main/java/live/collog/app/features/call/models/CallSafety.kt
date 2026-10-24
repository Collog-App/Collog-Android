package live.collog.app.features.call.models

import java.io.IOException
import kotlinx.coroutines.CancellationException
import live.collog.app.shared.services.api.ApiError

object CallSafety {
    fun acceptsPush(userId: String?, calleeId: String?): Boolean =
        userId != null && calleeId != null && userId == calleeId

    fun canRecord(enabled: Boolean, rawRequired: Boolean, accepted: Boolean, phase: CallPhase): Boolean =
        enabled && rawRequired && accepted && phase == CallPhase.Active

    fun isPermanentStatusError(error: Throwable): Boolean = when (error) {
        is ApiError.Unauthenticated -> true
        is ApiError.Server -> error.status in listOf(403, 404, 410)
        else -> false
    }

    fun canRetry(error: Throwable): Boolean = when (error) {
        is CancellationException -> false
        is ApiError.Transport -> true
        is ApiError.Server -> error.status == 429 || error.status >= 500
        else -> error is IOException
    }
}
