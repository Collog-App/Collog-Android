package live.collog.app.features.auth

import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.auth.views.hasUnacceptedSentInvitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 받은 코드를 넣으면 내가 보낸 초대가 취소된다는 안내: 수락되지 않은 초대(만료 포함)가 있을 때만. */
class InvitationAcceptNoticeTests {
    private fun withFixture(familyId: String? = "family", body: suspend (TestEnvironment) -> Unit) = runTest {
        val fixture = TestEnvironment(familyId = familyId)
        try {
            body(fixture)
        } finally {
            fixture.cleanUp()
        }
    }

    private val me = """{"memberId":"me","userId":"me","name":"Tester","relation":"","status":"ACTIVE","isCreator":true}"""

    private fun invited(status: String) =
        """{"memberId":"m2","userId":null,"name":"박정숙","relation":"MOTHER","status":"INVITED",
           "invitation":{"invitationId":"inv","code":"482913","shareText":"공유","expiresAt":"2020-01-01T00:00:00Z","status":"$status"}}"""

    private fun serveMembers(fixture: TestEnvironment, vararg others: String) {
        fixture.respond = { 200 to """{"members":[${(listOf(me) + others).joinToString(",")}]}""" }
    }

    @Test
    fun pendingOrExpiredSentInvitationShowsTheNotice() = withFixture { fixture ->
        serveMembers(fixture, invited("PENDING"))
        assertTrue(hasUnacceptedSentInvitation(fixture.environment))
        serveMembers(fixture, invited("EXPIRED"))
        assertTrue(hasUnacceptedSentInvitation(fixture.environment))
        assertEquals(listOf("/v1/families/family/members"), fixture.paths.distinct())
    }

    @Test
    fun noSentInvitationShowsNoNotice() = withFixture { fixture ->
        serveMembers(fixture)
        assertFalse(hasUnacceptedSentInvitation(fixture.environment))
    }

    @Test
    fun noFamilyOrGuestModeSkipsTheServer() = withFixture(familyId = null) { fixture ->
        assertFalse(hasUnacceptedSentInvitation(fixture.environment))
        fixture.environment.settings.isGuestMode = true
        assertFalse(hasUnacceptedSentInvitation(fixture.environment))
        assertTrue(fixture.paths.isEmpty())
    }
}
