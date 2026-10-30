package live.collog.app.shared.services.api

import java.time.Instant
import kotlinx.serialization.Serializable
import live.collog.app.shared.models.familyRelationTitle

// iOS `APIModels.swift`와 필드 이름·뜻이 같다. 서버 JSON이 camelCase라 이름을 그대로 쓴다.

@Serializable
data class ApiUser(
    val id: String,
    val role: String,
    val name: String,
    val phone: String? = null,
    val appleUserId: String? = null,
    val familyId: String? = null,
    val nickname: String? = null,
    /** "YYYY-MM-DD" */
    val birthDate: String? = null,
    val email: String? = null,
    val marketingOptIn: Boolean = false,
    /** "MALE" | "FEMALE" */
    val gender: String? = null,
    /** 닉네임까지 정했으면 true(서버 `UserView.signupCompleted`). */
    val signupCompleted: Boolean = false,
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

/** 역할은 보내지 않는다. 서버는 응답 호환용으로 CHILD 를 저장하고 가족을 만들지 않는다. */
@Serializable
data class GoogleLoginBody(
    val idToken: String,
    val challengeId: String,
    val name: String? = null,
)

@Serializable
data class RefreshTokenBody(val refreshToken: String)

@Serializable
data class PreferencesBody(val marketingOptIn: Boolean)

/** `PATCH /v1/account/profile`. [birthDate]는 "YYYY-MM-DD", [gender]는 "MALE" | "FEMALE". */
@Serializable
data class AccountProfileBody(val nickname: String, val gender: String, val birthDate: String)

/** `GET /v1/account/nickname-availability`. [reason]은 "TAKEN" | "INVALID" | null. */
@Serializable
data class NicknameAvailability(val available: Boolean, val reason: String? = null)

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
    /** 가족을 만든 사람은 비어 있다([isCreator]). */
    val relation: String? = null,
    val status: String,
    val role: String? = null,
    val invitation: FamilyInvitation? = null,
    @Serializable(with = CollogInstantSerializer::class) val acceptedAt: Instant? = null,
    val consentGranted: Boolean = false,
    val nickname: String? = null,
    /** "YYYY-MM-DD" */
    val birthDate: String? = null,
    val canRemove: Boolean = false,
    val isCreator: Boolean = false,
) {
    val id: String get() = memberId
    val isCallable: Boolean get() = userId != null

    val relationTitle: String
        get() = familyRelationTitle(relation) ?: name
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
data class FamilyCreated(val familyId: String)

/** `GET/PUT /v1/account/sharing`의 한 줄: [viewerUserId]가 내 리포트·타임라인을 볼 수 있는지. */
@Serializable
data class FamilyShare(
    val viewerUserId: String,
    val viewerName: String,
    val relation: String? = null,
    val report: Boolean = true,
    val timeline: Boolean = true,
)

@Serializable
data class SharingBody(val shares: List<FamilyShare>)

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
data class ProfilePutBody(
    val conditions: List<String>,
    /** null이면 보내지 않아 서버가 저장된 내용을 그대로 둔다. 빈 글자는 지운다. */
    val concerns: String? = null,
)

@Serializable
data class InvitationCreateBody(val name: String, val relation: String)

@Serializable
data class InvitationAcceptBody(val code: String)
