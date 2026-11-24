package live.collog.app.features.call.models

import io.livekit.android.events.DisconnectReason
import java.io.IOException
import kotlinx.coroutines.CancellationException
import live.collog.app.shared.services.AppEventCode
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

    /**
     * 이용 기록 `call_connect_failed` 의 code. 서버 API 는 오류 종류로 나눈다. LiveKit 접속은 거절당하면 서버가 준
     * 사유를, 연결이 안 되면 그 연결 오류를 원인으로 단 예외를 던지므로 메시지와 원인으로 나눈다.
     * 마이크 권한 거절은 부르는 쪽이 따로 MIC_PERMISSION 으로 넘기고, 갈래가 애매한 것(마이크·오디오 준비 실패 등)은 OTHER.
     */
    fun connectFailure(error: Throwable): String = when (error) {
        is ApiError.Unauthenticated -> AppEventCode.TOKEN
        is ApiError.Transport -> AppEventCode.NETWORK
        is ApiError.Server, is ApiError.Decoding -> AppEventCode.SERVER
        else -> {
            val chain = generateSequence(error) { it.cause }.take(8).toList()
            val messages = chain.mapNotNull { it.message?.lowercase() }
            when {
                messages.any { "token" in it || "unauthorized" in it } -> AppEventCode.TOKEN
                chain.any { it is IOException } -> AppEventCode.NETWORK
                else -> AppEventCode.OTHER
            }
        }
    }

    /** LiveKit 이 통화 중에 끊은 사유. 서버가 내보낸 것은 SERVER, 나머지(신호 끊김·시간 초과 등)는 NETWORK. */
    fun disconnectFailure(reason: DisconnectReason): String = when (reason) {
        DisconnectReason.SERVER_SHUTDOWN,
        DisconnectReason.PARTICIPANT_REMOVED,
        DisconnectReason.ROOM_CLOSED,
        DisconnectReason.DUPLICATE_IDENTITY,
        -> AppEventCode.SERVER
        else -> AppEventCode.NETWORK
    }
}
