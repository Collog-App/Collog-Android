package live.collog.app.features.call.services

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.models.CallSafety
import live.collog.app.shared.services.api.CollogTimestamp

// iOS CallCenter+Notifications. APNs·PushKit 대신 FCM 토큰 하나로 통화·리포트 알림을 받는다.

fun CallCenter.setPushToken(token: String?) {
    pushToken = token
    log(if (token == null) "푸시 토큰 없음" else "푸시 토큰 갱신")
    registerDeviceIfPossible()
}

fun CallCenter.registerDeviceIfPossible() {
    if (!environment.session.isAuthenticated) return
    if (pushToken == null) {
        deviceRegistrationError = if (platform.isPushConfigured) {
            "통화 알림 등록을 기다리고 있어요"
        } else {
            "통화 알림 설정이 아직 없어요"
        }
        return
    }
    needsDeviceRegistration = true
    if (deviceRegistrationJob != null) return
    deviceRegistrationJob = scope.launch {
        try {
            var failures = 0
            while (needsDeviceRegistration && environment.session.isAuthenticated) {
                val token = pushToken ?: break
                needsDeviceRegistration = false
                val ownerId = environment.session.user?.id
                try {
                    environment.api.registerDevice(
                        token = token,
                        voipToken = token,
                        callNotificationsEnabled = environment.settings.callNotificationsEnabled,
                        pushToken = token,
                        reportNotificationsEnabled = environment.settings.reportNotificationsEnabled &&
                            reportNotificationsAuthorized,
                    )
                    deviceRegistrationError = null
                    failures = 0
                    log("기기 등록 완료")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    deviceRegistrationError = error.message
                    log("기기 등록 실패: ${error.message}")
                    failures += 1
                    if (failures < 4 && CallSafety.canRetry(error) && ownerId == environment.session.user?.id) {
                        needsDeviceRegistration = true
                        delay(5_000L * failures)
                    }
                }
            }
        } finally {
            deviceRegistrationJob = null
        }
    }
}

/**
 * iOS와 달리 안드로이드는 전화 수신 알림도 알림 권한이 있어야 뜬다.
 * 그래서 통화 알림이나 리포트 알림 중 하나라도 켜져 있으면 권한을 묻는다.
 */
fun CallCenter.updateNotificationAuthorization() {
    if (notificationAuthorizationJob != null) return
    notificationAuthorizationJob = scope.launch {
        try {
            val settings = environment.settings
            var authorized = platform.notificationsEnabled()
            if (!authorized && (settings.reportNotificationsEnabled || settings.callNotificationsEnabled)) {
                authorized = platform.requestNotificationsIfUndetermined()
            }
            reportNotificationsAuthorized = authorized
            notificationPermissionError = when {
                authorized -> null
                settings.callNotificationsEnabled -> "휴대폰 설정에서 콜록 알림을 허용해야 전화를 받을 수 있어요"
                settings.reportNotificationsEnabled -> "휴대폰 설정에서 콜록 알림을 허용해주세요"
                else -> null
            }
            registerDeviceIfPossible()
        } finally {
            notificationAuthorizationJob = null
        }
    }
}

/**
 * iOS `pushRegistry(_:didReceiveIncomingPushWith:)`. FCM 데이터 메시지에는 통화 신호만 있고,
 * LiveKit 접속 정보는 받기를 눌렀을 때 서버 `/accept`가 준다.
 */
fun CallCenter.handleIncomingPush(data: Map<String, String>) {
    val callId = data["callId"] ?: return
    val uuid = data["callUUID"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()
    val callerName = data["callerName"] ?: "콜록"
    val callerId = data["callerId"]
    val contact = environment.family.contacts.firstOrNull { it.userId == callerId || it.name == callerName }
    val questions = environment.family.questions(contact).map { it.text }

    val userId = if (environment.session.isAuthenticated) environment.session.user?.id else null
    if (!CallSafety.acceptsPush(userId, data["calleeId"])) return
    if (activeCall != null || pendingOutgoing != null) {
        if (activeCall?.id != callId) declineIncomingCall(callId)
        return
    }
    val expiry = data["expiresAt"]?.let(CollogTimestamp::parse)
    if (expiry != null && expiry.isBefore(Instant.now())) {
        log("만료된 push 무시: $callId")
        return
    }

    callOwnerId = userId
    val call = CallCenter.ActiveCall(
        id = callId,
        uuid = uuid,
        direction = CallCenter.Direction.Incoming,
        peerId = callerId,
        peerName = callerName,
        phase = CallPhase.Ringing,
        questions = questions,
    )
    activeCall = call
    scope.launch {
        val added = platform.callSystem.add(
            SystemCall(uuid, callerName, callerId ?: "collog", incoming = true),
            systemEvents(uuid),
        )
        if (activeCall?.uuid != uuid) {
            if (added) platform.callSystem.disconnect(uuid, EndCause.Missed)
            return@launch
        }
        if (added) systemCalls += uuid else log("시스템 통화 등록 실패, 알림으로만 수신해요")
        platform.showIncoming(call)
        incomingCallReported = true
        log("수신 통화 표시: $callerName")
        monitorCall(callId)
    }
}

private fun CallCenter.declineIncomingCall(callId: String) {
    scope.launch {
        try {
            environment.api.declineCall(callId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("수신 거절 실패: ${error.message}")
        }
    }
}
