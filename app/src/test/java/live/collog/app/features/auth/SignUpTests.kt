package live.collog.app.features.auth

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import live.collog.app.TestEnvironment
import live.collog.app.features.auth.viewmodels.Gender
import live.collog.app.features.auth.viewmodels.NicknameStatus
import live.collog.app.features.auth.viewmodels.SignUpViewModel
import live.collog.app.features.auth.viewmodels.TermsAgreement
import live.collog.app.features.auth.viewmodels.TermsItem
import live.collog.app.features.auth.viewmodels.grantConsent
import live.collog.app.features.auth.viewmodels.saveOwnConditions
import live.collog.app.features.settings.models.HealthSelection
import live.collog.app.shared.models.HealthCondition
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.ConsentDocument
import live.collog.app.shared.services.api.GoogleLoginBody
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가입 단계의 서버 호출(Collog-Server `backend/docs/family-symmetric-plan.md` 「API 약속」·「구현에서 정한 세부」).
 * 경로·메서드·본문·응답 해석을 가짜 서버로 확인한다.
 */
class SignUpTests {
    private fun withFixture(familyId: String? = null, body: suspend (TestEnvironment) -> Unit) = runTest {
        val fixture = TestEnvironment(familyId = familyId, signupCompleted = false)
        try {
            body(fixture)
        } finally {
            fixture.cleanUp()
        }
    }

    private fun Request.json(): JsonObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return Json.parseToJsonElement(buffer.readUtf8()).jsonObject
    }

    private fun savedUser(familyId: String? = null) =
        """{"id":"me","role":"CHILD","name":"Tester","nickname":"이예한","gender":"FEMALE","birthDate":"1994-03-15",
        "familyId":${familyId?.let { "\"$it\"" } ?: "null"},"marketingOptIn":false,"signupCompleted":true}"""

    /** 닉네임·성별·생년월일을 다 채운 화면. */
    private suspend fun filledForm(fixture: TestEnvironment, inviteCode: String = ""): SignUpViewModel {
        val model = SignUpViewModel()
        fixture.respond = { 200 to """{"available":true,"reason":null}""" }
        model.updateNickname("이예한")
        model.checkNickname(fixture.environment)
        model.gender = Gender.Female
        model.updateBirthDigits("19940315")
        model.updateInviteCode(inviteCode)
        fixture.paths.clear()
        fixture.requests.clear()
        return model
    }

    @Test
    fun googleLoginSendsNoRole() {
        val json = CollogJson.encodeToString(GoogleLoginBody.serializer(), GoogleLoginBody("token", "challenge", "김민준"))
        val body = Json.parseToJsonElement(json).jsonObject
        assertEquals(setOf("idToken", "challengeId", "name"), body.keys)
    }

    @Test
    fun userViewReadsSignupFields() {
        val user = CollogJson.decodeFromString<ApiUser>(savedUser())
        assertTrue(user.signupCompleted)
        assertEquals("FEMALE", user.gender)
        // 예전 응답(가입 정보 없음)은 가입 전으로 읽는다
        assertFalse(CollogJson.decodeFromString<ApiUser>("""{"id":"me","role":"PARENT","name":"엄마"}""").signupCompleted)
    }

    @Test
    fun nicknameCheckAsksServerAndMapsReasons() = withFixture { fixture ->
        val model = SignUpViewModel()
        fixture.respond = { 200 to """{"available":false,"reason":"TAKEN"}""" }
        model.updateNickname("이예한")
        assertEquals(NicknameStatus.Checking, model.nicknameStatus)
        model.checkNickname(fixture.environment)
        assertEquals(NicknameStatus.Taken, model.nicknameStatus)
        val request = fixture.requests.single()
        assertEquals("/v1/account/nickname-availability", request.url.encodedPath)
        assertEquals("GET", request.method)
        assertEquals("이예한", request.url.queryParameter("nickname"))

        fixture.respond = { 200 to """{"available":true,"reason":null}""" }
        model.updateNickname("이예한2")
        model.checkNickname(fixture.environment)
        assertEquals(NicknameStatus.Available, model.nicknameStatus)
    }

    @Test
    fun invalidNicknameIsNotSentToServer() = withFixture { fixture ->
        val model = SignUpViewModel()
        for (nickname in listOf("가", "공백 있음", "ㅇㅇ", "열한글자가넘는닉네임이에요", "nick!")) {
            model.updateNickname(nickname)
            model.checkNickname(fixture.environment)
            assertEquals(nickname, NicknameStatus.Invalid, model.nicknameStatus)
        }
        assertTrue(fixture.paths.isEmpty())
        assertTrue(SignUpViewModel.isValidNickname("Collog12"))
        assertTrue(SignUpViewModel.isValidNickname("민준"))
    }

    @Test
    fun failedNicknameCheckLetsServerDecideOnSave() = withFixture { fixture ->
        val model = SignUpViewModel()
        fixture.respond = { 503 to "{}" }
        model.updateNickname("민준")
        model.checkNickname(fixture.environment)
        assertEquals(NicknameStatus.Unknown, model.nicknameStatus)
    }

    @Test
    fun birthDateFollowsServerRange() {
        val today = LocalDate.of(2026, 10, 30)
        assertEquals(LocalDate.of(1994, 3, 15), SignUpViewModel.parseBirthDate("19940315", today))
        assertNull(SignUpViewModel.parseBirthDate("19940231", today))
        assertNull(SignUpViewModel.parseBirthDate("18991231", today))
        assertNull(SignUpViewModel.parseBirthDate("20261031", today))
        assertEquals(today, SignUpViewModel.parseBirthDate("20261030", today))
        assertNull(SignUpViewModel.parseBirthDate("199403", today))
    }

    @Test
    fun formNeedsEveryRequiredFieldAndAFullCode() = withFixture { fixture ->
        val model = filledForm(fixture)
        assertTrue(model.canSubmit)
        model.updateInviteCode("12a3")
        assertEquals("123", model.inviteCode)
        assertFalse(model.canSubmit)
        model.updateInviteCode("1234567")
        assertEquals("123456", model.inviteCode)
        assertTrue(model.canSubmit)
        model.gender = null
        assertFalse(model.canSubmit)
    }

    @Test
    fun submitPatchesProfileAndUpdatesSession() = withFixture { fixture ->
        val model = filledForm(fixture)
        fixture.respond = { 200 to savedUser() }
        assertTrue(model.submit(fixture.environment))

        val request = fixture.requests.single()
        assertEquals("/v1/account/profile", request.url.encodedPath)
        assertEquals("PATCH", request.method)
        val body = request.json()
        assertEquals("이예한", body["nickname"]!!.jsonPrimitive.content)
        assertEquals("FEMALE", body["gender"]!!.jsonPrimitive.content)
        assertEquals("1994-03-15", body["birthDate"]!!.jsonPrimitive.content)
        assertFalse("role" in body)
        val user = fixture.environment.session.user!!
        assertTrue(user.signupCompleted)
        assertEquals("이예한", user.nickname)
    }

    @Test
    fun takenNicknameOnSaveMarksTheField() = withFixture { fixture ->
        val model = filledForm(fixture)
        fixture.respond = { 409 to """{"code":"CONFLICT","message":"이미 사용 중인 닉네임이에요"}""" }
        assertFalse(model.submit(fixture.environment))
        assertEquals(NicknameStatus.Taken, model.nicknameStatus)
        assertFalse(fixture.environment.session.user!!.signupCompleted)
    }

    @Test
    fun inviteCodeIsAcceptedAfterTheProfile() = withFixture { fixture ->
        val model = filledForm(fixture, inviteCode = "482913")
        fixture.respond = { request ->
            when (request.url.encodedPath) {
                "/v1/account/profile" -> 200 to savedUser()
                "/v1/invitations/accept" -> 200 to """{"familyId":"joined","memberId":"m1","status":"CONSENT_GRANTED"}"""
                else -> error("unexpected ${request.url.encodedPath}")
            }
        }
        assertTrue(model.submit(fixture.environment))
        assertEquals(listOf("/v1/account/profile", "/v1/invitations/accept"), fixture.paths)
        assertEquals("482913", fixture.requests.last().json()["code"]!!.jsonPrimitive.content)
        assertEquals("joined", fixture.environment.session.familyId)
    }

    @Test
    fun rejectedCodeShowsServerMessageAndKeepsUserOnTheForm() = withFixture { fixture ->
        val model = filledForm(fixture, inviteCode = "482913")
        val message = "이미 다른 가족에 속해 있어요. 지금 가족에서 나온 뒤 다시 시도해주세요"
        fixture.respond = { request ->
            if (request.url.encodedPath == "/v1/account/profile") {
                200 to savedUser(familyId = "own")
            } else {
                409 to """{"code":"CONFLICT","message":"$message"}"""
            }
        }
        assertFalse(model.submit(fixture.environment))
        assertEquals(message, model.inviteCodeError)
        // 세션은 아직 바꾸지 않아 흐름이 다음 단계로 넘어가지 않는다
        assertFalse(fixture.environment.session.user!!.signupCompleted)

        // 코드를 지우면 코드 없이 시작한다
        model.updateInviteCode("")
        fixture.respond = { 200 to savedUser(familyId = "own") }
        fixture.paths.clear()
        assertTrue(model.submit(fixture.environment))
        assertEquals(listOf("/v1/account/profile"), fixture.paths)
        assertEquals("own", fixture.environment.session.familyId)
    }

    @Test
    fun prefillRestoresSavedAnswers() {
        val model = SignUpViewModel()
        model.prefill(CollogJson.decodeFromString<ApiUser>(savedUser()))
        assertEquals("이예한", model.nickname)
        assertEquals(NicknameStatus.Checking, model.nicknameStatus)
        assertEquals(Gender.Female, model.gender)
        assertEquals("19940315", model.birthDigits)
    }

    @Test
    fun termsNeedEveryRequiredItem() {
        var agreement = TermsAgreement()
        assertFalse(agreement.allChecked)
        agreement = agreement.togglingAll()
        assertTrue(agreement.allChecked)
        agreement = agreement.toggling(TermsItem.Adult)
        assertFalse(agreement.allChecked)
        assertTrue(agreement.isChecked(TermsItem.Privacy))
        agreement = agreement.togglingAll()
        assertTrue(agreement.allChecked)
        assertEquals(TermsAgreement(), agreement.togglingAll())
        assertEquals(3, TermsItem.entries.size)
    }

    @Test
    fun consentGrantsEveryServerItemAfterReadingToTheEnd() = withFixture { fixture ->
        fixture.respond = {
            201 to """{"consentId":"c","userId":"me","documentVersion":"v5","status":"GRANTED","agreedItems":[],
            "isCurrent":true,"currentDocumentVersion":"v5"}"""
        }
        val document = ConsentDocument(
            version = "v5",
            fullText = "전문",
            collectedItems = listOf("통화 음성"),
            purpose = "목적",
            retentionPeriod = "보관",
            rawAudioPolicy = "원본",
            requiredItems = listOf("VOICE_FEATURE_EXTRACTION", "CALL_RECORDING"),
        )
        assertTrue(grantConsent(fixture.environment, document))

        val request = fixture.requests.single()
        assertEquals("/v1/consents", request.url.encodedPath)
        assertEquals("POST", request.method)
        val body = request.json()
        assertEquals("v5", body["documentVersion"]!!.jsonPrimitive.content)
        assertEquals("GRANT", body["decision"]!!.jsonPrimitive.content)
        assertTrue(body["scrolledToEnd"]!!.jsonPrimitive.boolean)
        assertEquals(
            listOf("CALL_RECORDING", "VOICE_FEATURE_EXTRACTION"),
            body["agreedItems"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun conditionsArePutOnMyOwnProfile() = withFixture { fixture ->
        fixture.respond = { 200 to """{"parentId":"me","conditions":["RHINITIS"],"isCompleted":true,"concerns":"기침"}""" }
        val selection = HealthSelection().toggled(HealthCondition.Rhinitis)
        assertTrue(saveOwnConditions(fixture.environment, selection, "  기침 "))

        val request = fixture.requests.single()
        assertEquals("/v1/parents/me/profile", request.url.encodedPath)
        assertEquals("PUT", request.method)
        val body = request.json()
        assertEquals(listOf("RHINITIS"), body["conditions"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("기침", body["concerns"]!!.jsonPrimitive.content)
    }

    @Test
    fun noConditionsIsSavedAsAnEmptyList() = withFixture { fixture ->
        fixture.respond = { 200 to """{"parentId":"me","conditions":[],"isCompleted":true}""" }
        assertTrue(saveOwnConditions(fixture.environment, HealthSelection().toggled(null), ""))
        assertTrue(fixture.requests.single().json()["conditions"]!!.jsonArray.isEmpty())
    }
}
