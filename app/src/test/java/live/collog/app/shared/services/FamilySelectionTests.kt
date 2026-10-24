package live.collog.app.shared.services

import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import okhttp3.Request
import org.junit.Assert.assertEquals
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

    private fun members(parents: List<String>): String {
        val parentJson = parents.map {
            """{"memberId":"$it","userId":"$it","name":"Parent","relation":"PARENT","status":"ACTIVE","role":"PARENT"}"""
        }
        val excluded = listOf(
            """{"memberId":"self","userId":"me","name":"Self","relation":"CHILD","status":"ACTIVE","role":"CHILD"}""",
            """{"memberId":"sibling","userId":"sibling","name":"Sibling","relation":"CHILD","status":"ACTIVE","role":"CHILD"}""",
            """{"memberId":"invited","userId":null,"name":"Invited","relation":"PARENT","status":"INVITED","role":"PARENT"}""",
        )
        return """{"members":[${(parentJson + excluded).joinToString(",")}]}"""
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
}
