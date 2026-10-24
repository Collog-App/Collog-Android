package live.collog.app.features.call.services

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * iOS CallKit(`CXProvider`·`CXCallController`) 자리. 통화를 시스템 통화 목록에 올리고,
 * 시스템(블루투스 기기·워치·다른 전화)이 요청한 받기·끊기·음소거를 알려준다.
 */
interface CallSystem {
    /** 통화를 시스템에 올린다. 올리지 못하면 false. */
    suspend fun add(call: SystemCall, events: SystemCallEvents): Boolean

    suspend fun answer(id: UUID): Boolean

    /** 발신 통화가 연결됐음을 알린다(iOS `reportOutgoingCall(connectedAt:)`). */
    suspend fun setActive(id: UUID): Boolean

    suspend fun disconnect(id: UUID, cause: EndCause)

    suspend fun setSpeaker(id: UUID, enabled: Boolean): Boolean
}

data class SystemCall(
    val id: UUID,
    val displayName: String,
    val address: String,
    val incoming: Boolean,
)

interface SystemCallEvents {
    suspend fun onAnswer()

    /** 시스템이 통화를 끊었거나 Telecom 세션이 비정상으로 끝났다(iOS `providerDidReset` 포함). */
    suspend fun onDisconnect()

    fun onMuteChanged(muted: Boolean)
}

/** iOS `CXCallEndedReason` 대응. Telecom `disconnect()`가 받는 원인은 이 네 가지뿐이다. */
enum class EndCause(val code: Int) {
    Local(DisconnectCause.LOCAL),
    Remote(DisconnectCause.REMOTE),
    Rejected(DisconnectCause.REJECTED),
    Missed(DisconnectCause.MISSED),
}

/** Jetpack core-telecom(자체 관리 통화)으로 구현한 [CallSystem]. 통화 음성 경로도 Telecom이 맡는다. */
class TelecomCallSystem(context: Context, private val scope: CoroutineScope) : CallSystem {
    private val manager = CallsManager(context.applicationContext)
    private val controls = mutableMapOf<UUID, CallControlScope>()
    private val endpoints = mutableMapOf<UUID, List<CallEndpointCompat>>()
    private val sessions = mutableMapOf<UUID, Job>()

    val isRegistered: Boolean = try {
        manager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
        true
    } catch (error: Exception) {
        Log.w(TAG, "Telecom 등록 실패", error)
        false
    }

    override suspend fun add(call: SystemCall, events: SystemCallEvents): Boolean {
        if (!isRegistered) return false
        val ready = CompletableDeferred<Boolean>()
        val abandoned = AtomicBoolean(false)
        val attributes = CallAttributesCompat(
            displayName = call.displayName,
            address = Uri.fromParts("collog", call.address, null),
            direction = if (call.incoming) {
                CallAttributesCompat.DIRECTION_INCOMING
            } else {
                CallAttributesCompat.DIRECTION_OUTGOING
            },
            callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            // iOS도 보류를 지원하지 않는다(supportsHolding = false).
            callCapabilities = 0,
        )
        sessions[call.id] = scope.launch {
            try {
                manager.addCall(
                    attributes,
                    onAnswer = { events.onAnswer() },
                    onDisconnect = {
                        events.onDisconnect()
                        // 끊긴 세션의 흐름은 끝나지 않으므로(채널이 닫히지 않음) 직접 정리한다.
                        scope.launch { sessions.remove(call.id)?.cancel() }
                    },
                    onSetActive = {},
                    onSetInactive = {},
                ) {
                    if (abandoned.get()) {
                        // 앱이 기다리다 포기한 뒤에 늦게 올라온 통화는 바로 내린다.
                        launch { disconnect(DisconnectCause(DisconnectCause.LOCAL)) }
                        return@addCall
                    }
                    controls[call.id] = this
                    launch {
                        // Telecom은 경로가 바뀔 때마다 같은 음소거 값을 다시 보낸다.
                        // 처음 값과 반복값은 무시하고, 시스템에서 실제로 바뀐 때만 알린다.
                        var last: Boolean? = null
                        isMuted.collect { muted ->
                            if (last != null && last != muted) events.onMuteChanged(muted)
                            last = muted
                        }
                    }
                    launch { availableEndpoints.collect { endpoints[call.id] = it } }
                    ready.complete(true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Telecom 통화 세션 실패", error)
                if (ready.isCompleted && controls.containsKey(call.id)) events.onDisconnect()
            } finally {
                ready.complete(false)
                controls.remove(call.id)
                endpoints.remove(call.id)
                sessions.remove(call.id)
            }
        }
        val added = withTimeoutOrNull(ADD_TIMEOUT_MILLIS) { ready.await() } ?: false
        if (!added) abandoned.set(true)
        return added
    }

    override suspend fun answer(id: UUID): Boolean =
        controls[id]?.answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL) is CallControlResult.Success

    override suspend fun setActive(id: UUID): Boolean =
        controls[id]?.setActive() is CallControlResult.Success

    override suspend fun disconnect(id: UUID, cause: EndCause) {
        val control = controls[id]
        if (control != null) {
            try {
                control.disconnect(DisconnectCause(cause.code))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Telecom 통화를 내리지 못했어요", error)
            }
        }
        sessions.remove(id)?.cancel()
    }

    override suspend fun setSpeaker(id: UUID, enabled: Boolean): Boolean {
        val control = controls[id] ?: return false
        val available = endpoints[id].orEmpty()
        // 끌 때는 iOS(`overrideOutputAudioPort(.none)`)처럼 연결된 헤드셋으로 먼저 돌아간다.
        val preference = if (enabled) {
            listOf(CallEndpointCompat.TYPE_SPEAKER)
        } else {
            listOf(
                CallEndpointCompat.TYPE_BLUETOOTH,
                CallEndpointCompat.TYPE_WIRED_HEADSET,
                CallEndpointCompat.TYPE_EARPIECE,
            )
        }
        val target = preference.firstNotNullOfOrNull { type -> available.firstOrNull { it.type == type } }
            ?: return false
        return control.requestEndpointChange(target) is CallControlResult.Success
    }

    private companion object {
        const val TAG = "CollogTelecom"
        const val ADD_TIMEOUT_MILLIS = 5_000L
    }
}
