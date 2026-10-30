package live.collog.app.shared.services.api

import java.io.File
import kotlinx.serialization.serializer

/** iOS `CollogAPI`. 경로·메서드·본문이 같다. Apple 로그인 대신 Google 로그인을 쓴다. */
class CollogApi(val client: CollogApiClient) {

    suspend fun googleLoginChallenge(): LoginChallenge =
        client.send(
            ApiEndpoint(
                path = "/v1/auth/google/challenge",
                method = HttpMethod.POST,
                requiresAuth = false,
                timeoutSeconds = 10,
            ),
        )

    suspend fun loginWithGoogle(idToken: String, challengeId: String, role: String, name: String?): TokenResponse =
        client.send(
            ApiEndpoint(
                path = "/v1/auth/google",
                method = HttpMethod.POST,
                body = body(GoogleLoginBody(idToken = idToken, challengeId = challengeId, role = role, name = name)),
                requiresAuth = false,
            ),
        )

    suspend fun requestOtp(phone: String, role: String, name: String) {
        client.sendRaw(
            ApiEndpoint(
                path = "/v1/auth/otp/request",
                method = HttpMethod.POST,
                body = body(OtpRequestBody(phone, role, name)),
                requiresAuth = false,
            ),
        )
    }

    suspend fun verifyOtp(phone: String, code: String): TokenResponse =
        client.send(
            ApiEndpoint(
                path = "/v1/auth/otp/verify",
                method = HttpMethod.POST,
                body = body(OtpVerifyBody(phone, code)),
                requiresAuth = false,
            ),
        )

    /** Android는 FCM 토큰 하나를 [token]·[voipToken]·[pushToken]에 같이 넣는다(서버가 플랫폼으로 발송 경로를 고른다). */
    suspend fun registerDevice(
        token: String,
        voipToken: String?,
        callNotificationsEnabled: Boolean = true,
        pushToken: String? = null,
        reportNotificationsEnabled: Boolean = true,
    ): DeviceCreated =
        client.send(
            ApiEndpoint(
                path = "/v1/devices",
                method = HttpMethod.POST,
                body = body(
                    DeviceCreateBody(
                        platform = "ANDROID",
                        token = token,
                        voipToken = voipToken,
                        callNotificationsEnabled = callNotificationsEnabled,
                        pushToken = pushToken,
                        reportNotificationsEnabled = reportNotificationsEnabled,
                    ),
                ),
            ),
        )

    suspend fun members(familyId: String): List<FamilyMember> = familyMembers(familyId).members

    suspend fun families(): List<FamilySummary> =
        client.send<FamiliesResponse>(ApiEndpoint(path = "/v1/families")).families

    suspend fun familyMembers(familyId: String): FamilyMembersResponse =
        client.send(ApiEndpoint(path = "/v1/families/$familyId/members"))

    /** 가족이 없는 사람의 첫 초대 전에 부른다. 이미 가족이 있으면 그 가족을 돌려준다. */
    suspend fun createFamily(): String =
        client.send<FamilyCreated>(ApiEndpoint(path = "/v1/families", method = HttpMethod.POST)).familyId

    /** 대기 중 초대는 구성원 누구나, 연결된 구성원은 가족을 만든 사람 또는 본인이 지운다(204). */
    suspend fun removeFamilyMember(familyId: String, memberId: String) {
        client.sendRaw(ApiEndpoint(path = "/v1/families/$familyId/members/$memberId", method = HttpMethod.DELETE))
    }

    suspend fun dailyQuestions(parentId: String): List<ApiQuestion> =
        client.send<DailyQuestionsResponse>(ApiEndpoint(path = "/v1/parents/$parentId/daily-questions")).questions

    suspend fun consentDocument(): ConsentDocument =
        client.send(ApiEndpoint(path = "/v1/consents/document", requiresAuth = false))

    suspend fun submitConsent(
        documentVersion: String,
        agreedItems: List<String>,
        decision: String = "GRANT",
        scrolledToEnd: Boolean = true,
    ): ConsentRecordDto =
        client.send(
            ApiEndpoint(
                path = "/v1/consents",
                method = HttpMethod.POST,
                body = body(
                    ConsentSubmitBody(
                        documentVersion = documentVersion,
                        decision = decision,
                        scrolledToEnd = scrolledToEnd,
                        agreedItems = agreedItems,
                    ),
                ),
            ),
        )

    suspend fun myConsent(): ConsentRecordDto = client.send(ApiEndpoint(path = "/v1/consents/me"))

    suspend fun profile(parentId: String): ProfileDto =
        client.send(ApiEndpoint(path = "/v1/parents/$parentId/profile"))

    /** [concerns]가 null이면 저장된 걱정 내용은 그대로 둔다. */
    suspend fun updateProfile(parentId: String, conditions: List<String>, concerns: String? = null): ProfileDto =
        client.send(
            ApiEndpoint(
                path = "/v1/parents/$parentId/profile",
                method = HttpMethod.PUT,
                body = body(ProfilePutBody(conditions, concerns)),
            ),
        )

    suspend fun createInvitation(familyId: String, name: String, relation: String): InvitationDto =
        client.send(
            ApiEndpoint(
                path = "/v1/families/$familyId/invitations",
                method = HttpMethod.POST,
                body = body(InvitationCreateBody(name, relation)),
            ),
        )

    suspend fun resendInvitation(invitationId: String): InvitationDto =
        client.send(ApiEndpoint(path = "/v1/invitations/$invitationId/resend", method = HttpMethod.POST))

    suspend fun acceptInvitation(code: String): InvitationAccepted =
        client.send(
            ApiEndpoint(
                path = "/v1/invitations/accept",
                method = HttpMethod.POST,
                body = body(InvitationAcceptBody(code)),
            ),
        )

    suspend fun calls(parentId: String, from: String? = null, to: String? = null): List<CallSummaryDto> {
        val query = buildList {
            from?.let { add("from" to it) }
            to?.let { add("to" to it) }
        }
        return client.send<CallsResponse>(ApiEndpoint(path = "/v1/parents/$parentId/calls", query = query)).calls
    }

    suspend fun report(parentId: String, period: String = "WEEKLY", date: String? = null): ReportDto {
        val query = buildList {
            add("period" to period)
            date?.let { add("date" to it) }
        }
        return client.send(ApiEndpoint(path = "/v1/parents/$parentId/reports", query = query))
    }

    suspend fun baselines(parentId: String): List<BaselineDto> =
        client.send<BaselinesResponse>(ApiEndpoint(path = "/v1/parents/$parentId/baseline")).baselines

    suspend fun acousticFeatures(callId: String): AcousticFeaturesDto =
        client.send(ApiEndpoint(path = "/v1/calls/$callId/acoustic-features"))

    suspend fun extraction(callId: String): ExtractionDto =
        client.send(ApiEndpoint(path = "/v1/calls/$callId/extraction"))

    suspend fun transcript(callId: String): TranscriptDto =
        client.send(ApiEndpoint(path = "/v1/calls/$callId/transcript"))

    suspend fun createCall(calleeId: String): CallCreated =
        client.send(ApiEndpoint(path = "/v1/calls", method = HttpMethod.POST, body = body(CallCreateBody(calleeId))))

    suspend fun callStatus(callId: String): CallStatus = client.send(ApiEndpoint(path = "/v1/calls/$callId"))

    suspend fun questionTtsToken(callId: String, questionId: String): QuestionTtsToken =
        client.send(
            ApiEndpoint(path = "/v1/calls/$callId/questions/$questionId/tts-token", method = HttpMethod.POST),
        )

    suspend fun refreshSession(refreshToken: String): TokenResponse =
        client.send(
            ApiEndpoint(
                path = "/v1/auth/refresh",
                method = HttpMethod.POST,
                body = body(RefreshTokenBody(refreshToken)),
                requiresAuth = false,
            ),
        )

    suspend fun logout(refreshToken: String) {
        client.sendRaw(
            ApiEndpoint(
                path = "/v1/auth/logout",
                method = HttpMethod.POST,
                body = body(RefreshTokenBody(refreshToken)),
                requiresAuth = false,
            ),
        )
    }

    suspend fun updatePreferences(marketingOptIn: Boolean): ApiUser =
        client.send(
            ApiEndpoint(
                path = "/v1/account/preferences",
                method = HttpMethod.PATCH,
                body = body(PreferencesBody(marketingOptIn)),
            ),
        )

    /** 내 기록을 볼 수 있는 가족 구성원마다 한 줄. */
    suspend fun sharing(): List<FamilyShare> =
        client.send<SharingBody>(ApiEndpoint(path = "/v1/account/sharing")).shares

    suspend fun updateSharing(shares: List<FamilyShare>): List<FamilyShare> =
        client.send<SharingBody>(
            ApiEndpoint(path = "/v1/account/sharing", method = HttpMethod.PUT, body = body(SharingBody(shares))),
        ).shares

    /** Google·OTP 계정은 재인증 본문 없이 삭제된다(Apple만 재인증). */
    suspend fun deleteAccount() {
        client.sendRaw(ApiEndpoint(path = "/v1/account", method = HttpMethod.DELETE))
    }

    suspend fun acceptCall(callId: String, requestId: String? = null): CallAccepted =
        client.send(
            ApiEndpoint(
                path = "/v1/calls/$callId/accept",
                method = HttpMethod.POST,
                body = body(CallAcceptBody(requestId)),
            ),
        )

    suspend fun declineCall(callId: String) {
        client.sendRaw(ApiEndpoint(path = "/v1/calls/$callId/decline", method = HttpMethod.POST))
    }

    suspend fun endCall(callId: String) {
        client.sendRaw(ApiEndpoint(path = "/v1/calls/$callId/end", method = HttpMethod.POST))
    }

    suspend fun rawAudioUploadUrl(callId: String, durationSec: Double, sampleRate: Int): RawAudioUpload =
        client.send(
            ApiEndpoint(
                path = "/v1/calls/$callId/raw-audio/upload-url",
                method = HttpMethod.POST,
                body = body(RawAudioUploadBody(contentType = "audio/wav", durationSec = durationSec, sampleRate = sampleRate)),
            ),
        )

    suspend fun uploadRawAudio(file: File, uploadUrl: String) {
        client.upload(file, uploadUrl, contentType = "audio/wav")
    }

    suspend fun completeRawAudio(callId: String, assetId: String) {
        client.sendRaw(
            ApiEndpoint(
                path = "/v1/calls/$callId/raw-audio/complete",
                method = HttpMethod.POST,
                body = body(RawAudioCompleteBody(assetId)),
            ),
        )
    }

    private inline fun <reified T> body(value: T): ApiBody<T> = ApiBody(value, serializer<T>())
}

