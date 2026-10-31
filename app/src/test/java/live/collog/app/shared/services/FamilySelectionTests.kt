package live.collog.app.shared.services

import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FamilySelectionTests`. */
class FamilySelectionTests {
    private fun withFixture(role: String = "CHILD", body: suspend (TestEnvironment) -> Unit) = runTest {
        val fixture = TestEnvironment(role = role)
        try {
            body(fixture)
        } finally {
            fixture.cleanUp()
        }
    }

    /** [connected]는 나를 뺀 연결된 구성원. 예전 서버의 역할을 번갈아 붙여도 모두 연락처가 된다. */
    private fun members(connected: List<String>): String {
        val connectedJson = connected.mapIndexed { index, id ->
            val role = if (index % 2 == 0) "PARENT" else "CHILD"
            """{"memberId":"$id","userId":"$id","name":"Family","relation":"SIBLING","status":"ACTIVE","role":"$role"}"""
        }
        val excluded = listOf(
            """{"memberId":"self","userId":"me","name":"Self","relation":"CHILD","status":"ACTIVE","role":"CHILD"}""",
            """{"memberId":"invited","userId":null,"name":"Invited","relation":"MOTHER","status":"INVITED","role":"PARENT"}""",
        )
        return """{"members":[${(connectedJson + excluded).joinToString(",")}]}"""
    }

    private fun respondWith(fixture: TestEnvironment, parents: () -> List<String>) {
        fixture.respond = { request: Request ->
            if (request.url.encodedPath.endsWith("/members")) {
                200 to members(parents())
            } else {
                200 to """{"source":"fixture","questions":[]}"""
            }
        }
    }

    @Test
    fun everyOtherConnectedMemberIsAContactWhateverTheRole() = withFixture { fixture ->
        respondWith(fixture) { listOf("mom", "sibling", "grandma") }
        val store = fixture.environment.family
        store.refresh(fixture.environment)
        assertEquals(listOf("mom", "sibling", "grandma"), store.contacts.map { it.id })
        // 오늘의 질문은 고른 가족(첫 번째)의 것
        assertEquals("/v1/parents/mom/daily-questions", fixture.paths.last())
    }

    @Test
    fun contactSelectionSurvivesMemberReordering() = withFixture { fixture ->
        var parents = listOf("first", "second")
        respondWith(fixture) { parents }
        val store = fixture.environment.family
        store.refresh(fixture.environment)
        assertEquals(parents, store.contacts.map { it.id })
        store.selectContact(store.contacts.last())
        parents = parents.reversed()
        store.refresh(fixture.environment)
        assertEquals("second", store.selectedContactId)
        assertEquals("/v1/parents/second/daily-questions", fixture.paths.last())
    }

    @Test
    fun removedContactFallsBackToRemainingFamilyMember() = withFixture { fixture ->
        var parents = listOf("first", "second")
        respondWith(fixture) { parents }
        val store = fixture.environment.family
        store.refresh(fixture.environment)
        store.selectContact(store.contacts.last())
        parents = listOf("first")
        store.refresh(fixture.environment)
        assertEquals("first", store.selectedContactId)
        assertEquals("/v1/parents/first/daily-questions", fixture.paths.last())
    }

    @Test
    fun switchingFamilyUpdatesRequestsWithoutLosingAuthentication() = withFixture { fixture ->
        fixture.respond = { 200 to """{"members":[]}""" }
        fixture.environment.session.joinFamily("another-family")
        fixture.environment.family.refresh(fixture.environment)
        assertEquals("/v1/families/another-family/members", fixture.paths.first())
        assertEquals("another-family", fixture.credentials.response?.user?.familyId)
        assertTrue(fixture.environment.session.isAuthenticated)
    }

    @Test
    fun tokenRefreshPreservesChosenFamilyAcrossSessionRestore() = withFixture { fixture ->
        fixture.environment.session.joinFamily("chosen-family")
        fixture.respond = { request ->
            assertEquals("/v1/auth/refresh", request.url.encodedPath)
            200 to """
            {"accessToken":"new-token","refreshToken":"new-refresh",
            "user":{"id":"me","role":"CHILD","name":"Tester","phone":null,"familyId":"family"}}
            """.trimIndent()
        }
        val token = fixture.environment.session.refresh(fixture.environment.settings.resolvedBaseUrl)
        assertEquals("new-token", token)
        assertEquals("chosen-family", fixture.environment.session.familyId)
        assertEquals("chosen-family", fixture.credentials.response?.user?.familyId)
        val restored = AuthSession(credentialStore = fixture.credentials, http = fixture.http)
        assertEquals("chosen-family", restored.familyId)
        assertEquals("new-token", restored.accessToken)
    }

    /** 가족을 만든 사람이 나를 구성원에서 삭제하면 서버가 그 가족을 403 으로 막는다. */
    private fun respondAsRemovedMember(fixture: TestEnvironment, remaining: String) {
        fixture.respond = { request: Request ->
            val path = request.url.encodedPath
            when {
                path == "/v1/families/family/members" -> 403 to """{"code":"FORBIDDEN","message":"가족 접근 권한이 없습니다"}"""
                path == "/v1/families" -> 200 to """{"families":[$remaining]}"""
                path.endsWith("/members") -> 200 to members(listOf("mom"))
                else -> 200 to """{"source":"fixture","questions":[]}"""
            }
        }
    }

    @Test
    fun removedMemberWithNoOtherFamilyFallsBackToNoFamily() = withFixture { fixture ->
        respondAsRemovedMember(fixture, remaining = "")
        val store = fixture.environment.family
        store.refresh(fixture.environment)
        assertNull(fixture.environment.session.familyId)
        assertNull(fixture.credentials.response?.user?.familyId)
        assertEquals(emptyList<String>(), store.contacts.map { it.id })
        assertNull(store.loadError)
        assertTrue(fixture.environment.session.isAuthenticated)
    }

    @Test
    fun removedMemberMovesToTheFamilyTheyStillBelongTo() = withFixture { fixture ->
        respondAsRemovedMember(fixture, remaining = """{"familyId":"other","name":"다른 가족"}""")
        val store = fixture.environment.family
        store.refresh(fixture.environment)
        assertEquals("other", fixture.environment.session.familyId)
        assertEquals(listOf("mom"), store.contacts.map { it.id })
        assertNull(store.loadError)
    }
}
