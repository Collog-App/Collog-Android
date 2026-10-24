package live.collog.app.shared.services.api

import java.time.Instant
import kotlinx.serialization.Serializable

// iOS `APIModels.swift`와 필드 이름·뜻이 같다. 서버 JSON이 camelCase라 이름을 그대로 쓴다.

@Serializable
data class ApiUser(
    val id: String,
    val role: String,
    val name: String,
    val phone: String? = null,
    val appleUserId: String? = null,
    val familyId: String? = null,
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: ApiUser,
)

@Serializable
data class LoginChallenge(
    val challengeId: String,
    val nonce: String,
    val expiresIn: Int,
)

@Serializable
data class GoogleLoginBody(
    val idToken: String,
    val challengeId: String,
    val role: String,
    val name: String? = null,
)

@Serializable
data class RefreshTokenBody(val refreshToken: String)

@Serializable
data class AccountRoleBody(val role: String)

@Serializable
data class InvitationAccepted(
    val familyId: String,
    val memberId: String,
    val status: String,
)

@Serializable
data class CallStatus(
    val state: String,
    @Serializable(with = CollogInstantSerializer::class) val endedAt: Instant? = null,
    val recordingEnabled: Boolean? = null,
    val recordingDisabledMessage: String? = null,
) {
    val hasEnded: Boolean
        get() = endedAt != null ||
            state in listOf("ENDED", "PROCESSING", "ANALYZED", "ANALYSIS_EXCLUDED", "ANALYSIS_FAILED")
}

@Serializable
data class FamilyMember(
    val memberId: String,
    val userId: String? = null,
    val name: String,
    val relation: String,
    val status: String,
    val role: String? = null,
    val invitation: FamilyInvitation? = null,
) {
    val id: String get() = memberId
    val isCallable: Boolean get() = userId != null

    val relationTitle: String
        get() = when (relation) {
            "MOTHER" -> "어머니"
            "FATHER" -> "아버지"
            "CHILD" -> "자녀"
            "PARENT" -> "부모"
            else -> name
        }
}

@Serializable
data class FamilyInvitation(
    val invitationId: String,
    val code: String,
    val shareText: String,
    @Serializable(with = CollogInstantSerializer::class) val expiresAt: Instant,
    val status: String,
) {
    val id: String get() = invitationId
    val isExpired: Boolean get() = status == "EXPIRED" || !expiresAt.isAfter(Instant.now())
}

@Serializable
data class FamilyMembersResponse(
    val members: List<FamilyMember>,
    val canInvite: Boolean? = null,
)

@Serializable
data class FamilySummary(val familyId: String, val name: String) {
    val id: String get() = familyId
}

@Serializable
data class FamiliesResponse(val families: List<FamilySummary>)

@Serializable
data class ApiQuestion(
    val questionId: String,
    val text: String,
    val conditionCode: String? = null,
    val ttsAssetUrl: String? = null,
    val durationMs: Int? = null,
    val ttsMode: String,
) {
    val id: String get() = questionId
    val usesRemoteAudio: Boolean get() = ttsMode == "REMOTE_ASSET" && ttsAssetUrl != null
}

@Serializable
data class DailyQuestionsResponse(
    val source: String,
    val questions: List<ApiQuestion>,
)

@Serializable
data class QuestionTtsToken(
    val token: String,
    val voiceId: String,
    val modelId: String,
    val outputFormat: String,
)

@Serializable
data class AudioConstraints(
    val echoCancellation: Boolean,
    val noiseSuppression: Boolean,
    val autoGainControl: Boolean,
    val dtx: Boolean,
    val audioBitrate: Int,
    val rawCaptureSampleRate: Int,
)

@Serializable
data class CallCreated(
    val callId: String,
    val callerId: String? = null,
    val calleeId: String? = null,
    val rawCaptureRequired: Boolean? = null,
    val livekitUrl: String,
    val roomName: String,
    val accessToken: String,
    val recordingEnabled: Boolean,
    val recordingDisabledReason: String? = null,
    val recordingDisabledMessage: String? = null,
    val questions: List<ApiQuestion>,
    val audioConstraints: AudioConstraints,
)

@Serializable
data class CallAcceptBody(val requestId: String? = null)

@Serializable
data class CallAccepted(
    val callId: String,
    val livekitUrl: String,
    val roomName: String,
    val accessToken: String,
    val rawCaptureRequired: Boolean,
    val audioConstraints: AudioConstraints,
    val recordingEnabled: Boolean? = null,
)

@Serializable
data class DeviceCreated(val deviceId: String)

@Serializable
data class RawAudioUpload(
    val uploadUrl: String,
    val assetId: String,
    val expiresIn: Int,
)

@Serializable
data class OtpRequestBody(val phone: String, val role: String, val name: String)

@Serializable
data class OtpVerifyBody(val phone: String, val code: String)

@Serializable
data class DeviceCreateBody(
    val platform: String,
    val token: String,
    val voipToken: String? = null,
    val callNotificationsEnabled: Boolean,
    val pushToken: String? = null,
    val reportNotificationsEnabled: Boolean,
)

@Serializable
data class CallCreateBody(val calleeId: String)

@Serializable
data class RawAudioUploadBody(
    val contentType: String,
    val durationSec: Double,
    val sampleRate: Int,
)

@Serializable
data class RawAudioCompleteBody(val assetId: String)

@Serializable
data class ConsentSubmitBody(
    val documentVersion: String,
    val decision: String,
    val scrolledToEnd: Boolean,
    val agreedItems: List<String>,
)

@Serializable
data class ProfilePutBody(val conditions: List<String>)

@Serializable
data class InvitationCreateBody(val name: String, val relation: String)

@Serializable
data class InvitationAcceptBody(val code: String)
