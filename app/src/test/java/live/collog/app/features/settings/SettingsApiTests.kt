package live.collog.app.features.settings

import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import live.collog.app.TestEnvironment
import live.collog.app.features.settings.models.createFamilyInvitation
import live.collog.app.features.settings.models.otherMembers
import live.collog.app.features.settings.models.updateMarketingOptIn
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.FamilyShare
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가족 대칭 구조 API 약속(Collog-Server `backend/docs/family-symmetric-plan.md` 「API 약속」)에 맞춘 호출.
 * 서버가 아직 없으므로 경로·메서드·본문·응답 해석을 가짜 서버로 확인한다.
 */
class SettingsApiTests {
    private fun withFixture(familyId: String? = "family", body: suspend (TestEnvironment) -> Unit) =
        runTest {
            val fixture = TestEnvironment(role = "CHILD", familyId = familyId)
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

    private val invitationJson =
        """{"invitationId":"inv","code":"482913","shareText":"콜록 가족 초대 코드 482913","status":"PENDING"}"""

    @Test
    fun marketingPreferenceIsPatchedAndKeepsChosenFamily() = withFixture { fixture ->
        fixture.respond = {
            200 to """{"id":"me","role":"CHILD","name":"Tester","email":"me@example.com","marketingOptIn":false,"familyId":null}"""
        }
        updateMarketingOptIn(fixture.environment, false)

        val request = fixture.requests.single()
        assertEquals("/v1/account/preferences", request.url.encodedPath)
        assertEquals("PATCH", request.method)
        assertEquals(false, request.json()["marketingOptIn"]!!.jsonPrimitive.boolean)
        val user = fixture.environment.session.user!!
        assertFalse(user.marketingOptIn)
        assertEquals("me@example.com", user.email)
        assertEquals("family", user.familyId)
    }

    @Test
    fun sharingIsReadAndWrittenInTheSameShape() = withFixture { fixture ->
        val sharesJson = """{"shares":[
            {"viewerUserId":"mom","viewerName":"김순희","relation":"MOTHER","report":true,"timeline":false},
            {"viewerUserId":"dad","viewerName":"김영수","relation":null,"report":true,"timeline":true}]}"""
        fixture.respond = { 200 to sharesJson }

        val shares = fixture.environment.api.sharing()
        assertEquals(listOf("mom", "dad"), shares.map { it.viewerUserId })
        assertFalse(shares[0].timeline)
        assertNull(shares[1].relation)

        val saved = fixture.environment.api.updateSharing(listOf(FamilyShare("mom", "김순희", "MOTHER", report = false)))
        assertEquals(2, saved.size)

        assertEquals(listOf("/v1/account/sharing", "/v1/account/sharing"), fixture.paths)
        assertEquals(listOf("GET", "PUT"), fixture.requests.map { it.method })
        val sent = fixture.requests[1].json()["shares"]!!.jsonArray.single().jsonObject
        assertEquals("mom", sent["viewerUserId"]!!.jsonPrimitive.content)
        assertEquals("김순희", sent["viewerName"]!!.jsonPrimitive.content)
        assertEquals(false, sent["report"]!!.jsonPrimitive.boolean)
        assertEquals(true, sent["timeline"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun removingMemberSendsDelete() = withFixture { fixture ->
        fixture.respond = { 204 to "" }
        fixture.environment.api.removeFamilyMember("family", "m1")
        assertEquals(listOf("/v1/families/family/members/m1"), fixture.paths)
        assertEquals("DELETE", fixture.requests.single().method)
    }

    @Test
    fun firstInvitationCreatesTheFamilyWithoutSwitchingSession() = withFixture(familyId = null) { fixture ->
        fixture.respond = { request ->
            when (request.url.encodedPath) {
                "/v1/families" -> 201 to """{"familyId":"f-new"}"""
                "/v1/families/f-new/invitations" -> 201 to invitationJson
                else -> 404 to """{"code":"NOT_FOUND","message":"없음"}"""
            }
        }
        val created = createFamilyInvitation(fixture.environment, "김순희", "MOTHER")

        assertEquals(listOf("/v1/families", "/v1/families/f-new/invitations"), fixture.paths)
        assertEquals(listOf("POST", "POST"), fixture.requests.map { it.method })
        assertEquals("f-new", created.familyId)
        assertEquals("482913", created.invitation.code)
        val body = fixture.requests[1].json()
        assertEquals("김순희", body["name"]!!.jsonPrimitive.content)
        assertEquals("MOTHER", body["relation"]!!.jsonPrimitive.content)
        // 세션 가족은 시트를 닫을 때 바뀐다(바로 바꾸면 앱이 홈으로 돌아간다)
        assertNull(fixture.environment.session.familyId)
    }

    @Test
    fun invitationUsesTheCurrentFamily() = withFixture { fixture ->
        fixture.respond = { 201 to invitationJson }
        val created = createFamilyInvitation(fixture.environment, "김영수", "FATHER")
        assertEquals(listOf("/v1/families/family/invitations"), fixture.paths)
        assertEquals("family", created.familyId)
    }

    @Test
    fun membersDecodeSymmetricFields() = withFixture { fixture ->
        fixture.respond = {
            200 to """{"canInvite":true,"members":[
                {"memberId":"me","userId":"me","name":"Tester","relation":"","status":"ACTIVE","isCreator":true,
                 "acceptedAt":"2024-12-01T00:00:00Z","consentGranted":true,"nickname":"테스터","birthDate":"1994-03-15","canRemove":false},
                {"memberId":"m1","userId":"mom","name":"김순희","relation":"SPOUSE","status":"CONSENT_GRANTED",
                 "acceptedAt":"2025-01-20T01:00:00Z","consentGranted":true,"nickname":"순희","birthDate":"1960-03-15",
                 "canRemove":true,"isCreator":false},
                {"memberId":"m2","userId":null,"name":"박정숙","relation":"MOTHER","status":"INVITED",
                 "acceptedAt":null,"consentGranted":false,"canRemove":true,
                 "invitation":{"invitationId":"inv","code":"482913","shareText":"공유","expiresAt":"2099-01-01T00:00:00Z","status":"PENDING"}}]}"""
        }
        val response = fixture.environment.api.familyMembers("family")
        assertEquals(true, response.canInvite)
        val creator = response.members.first()
        assertTrue(creator.isCreator)
        assertEquals("Tester", otherMembers(response.members, "someone-else").first().title)

        val others = otherMembers(response.members, "me")
        assertEquals(listOf("m1", "m2"), others.map { it.id })
        val mom = others[0]
        assertEquals(Instant.parse("2025-01-20T01:00:00Z"), mom.acceptedAt)
        assertTrue(mom.consentGranted)
        assertTrue(mom.canRemove)
        assertEquals("순희", mom.nickname)
        assertEquals("김순희 (배우자)", mom.title)
        assertEquals("1960-03-15", mom.profileTarget?.birthDate)
        assertEquals("482913", others[1].invitation?.code)
    }

    @Test
    fun profileConcernsAreReadAndSent() = withFixture { fixture ->
        fixture.respond = { request ->
            if (request.method == "GET") {
                200 to """{"parentId":"mom","conditions":["FORGETFULNESS","HYPERTENSION"],"concerns":"밤에 자주 깨세요","isCompleted":true}"""
            } else {
                200 to """{"parentId":"mom","conditions":["RHINITIS"],"concerns":null,"isCompleted":true}"""
            }
        }
        val profile = fixture.environment.api.profile("mom")
        assertEquals("밤에 자주 깨세요", profile.concerns)
        assertEquals(listOf("FORGETFULNESS", "HYPERTENSION"), profile.conditions)

        val saved = fixture.environment.api.updateProfile("mom", listOf("RHINITIS"), "")
        assertNull(saved.concerns)
        val sent = fixture.requests[1].json()
        assertEquals("PUT", fixture.requests[1].method)
        assertEquals("RHINITIS", sent["conditions"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("", sent["concerns"]!!.jsonPrimitive.content)

        // 걱정 내용을 넘기지 않으면 보내지 않아 서버가 저장된 것을 그대로 둔다(가입 화면)
        fixture.environment.api.updateProfile("mom", listOf("RHINITIS"))
        assertFalse("concerns" in fixture.requests[2].json())
        assertEquals(listOf("/v1/parents/mom/profile"), fixture.paths.distinct())
    }

    @Test
    fun userViewReadsNewFieldsAndOldPayloads() {
        val user = CollogJson.decodeFromString<ApiUser>(
            """{"id":"me","role":"CHILD","name":"김민준","nickname":"민준","gender":"MALE","birthDate":"1994-03-15",
               "email":"me@example.com","marketingOptIn":true,"signupCompleted":true,"familyId":"f"}""",
        )
        assertEquals("민준", user.nickname)
        assertEquals("1994-03-15", user.birthDate)
        assertEquals("me@example.com", user.email)
        assertTrue(user.marketingOptIn)

        val old = CollogJson.decodeFromString<ApiUser>("""{"id":"me","role":"PARENT","name":"엄마"}""")
        assertNull(old.email)
        assertNull(old.birthDate)
        assertFalse(old.marketingOptIn)
    }
}
