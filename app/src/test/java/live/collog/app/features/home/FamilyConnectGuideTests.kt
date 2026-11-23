package live.collog.app.features.home

import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.home.models.FamilyConnectGuide
import live.collog.app.features.home.models.familyConnectGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 홈 가족 연결 안내: 아직 아무와도 연결되지 않은 사람에게만, 보낸 초대가 기다리는 중이면 기다림 안내로. */
class FamilyConnectGuideTests {
    private fun withFixture(familyId: String? = "family", body: suspend (TestEnvironment) -> Unit) = runTest {
        val fixture = TestEnvironment(familyId = familyId)
        try {
            body(fixture)
        } finally {
            fixture.cleanUp()
        }
    }

    private val me = """{"memberId":"me","userId":"me","name":"Tester","relation":"","status":"ACTIVE","isCreator":true}"""

    private fun invited(status: String = "PENDING", expiresAt: String = "2099-01-01T00:00:00Z") =
        """{"memberId":"m2","userId":null,"name":"박정숙","relation":"MOTHER","status":"INVITED",
           "invitation":{"invitationId":"inv","code":"482913","shareText":"공유","expiresAt":"$expiresAt","status":"$status"}}"""

    private fun serveMembers(fixture: TestEnvironment, vararg others: String) {
        fixture.respond = { request ->
            if (request.url.encodedPath.endsWith("/members")) {
                200 to """{"members":[${(listOf(me) + others).joinToString(",")}]}"""
            } else {
                200 to """{"questions":[]}"""
            }
        }
    }

    @Test
    fun aloneWithoutInvitationShowsTheInviteSteps() = withFixture { fixture ->
        serveMembers(fixture)
        // 가족 목록을 받기 전에는 연결 여부를 몰라 띄우지 않는다
        assertNull(familyConnectGuide(fixture.environment))
        fixture.environment.family.refresh(fixture.environment)
        assertEquals(FamilyConnectGuide.Invite, familyConnectGuide(fixture.environment))
    }

    @Test
    fun pendingInvitationShowsTheWaitingGuide() = withFixture { fixture ->
        serveMembers(fixture, invited())
        fixture.environment.family.refresh(fixture.environment)
        assertEquals(FamilyConnectGuide.Waiting, familyConnectGuide(fixture.environment))
    }

    @Test
    fun expiredInvitationIsNotWaiting() = withFixture { fixture ->
        serveMembers(fixture, invited(status = "EXPIRED"))
        fixture.environment.family.refresh(fixture.environment)
        assertEquals(FamilyConnectGuide.Invite, familyConnectGuide(fixture.environment))

        serveMembers(fixture, invited(expiresAt = "2020-01-01T00:00:00Z"))
        fixture.environment.family.refresh(fixture.environment)
        assertEquals(FamilyConnectGuide.Invite, familyConnectGuide(fixture.environment))
    }

    @Test
    fun connectedFamilyHidesTheGuide() = withFixture { fixture ->
        serveMembers(fixture, """{"memberId":"m1","userId":"mom","name":"김순희","relation":"MOTHER","status":"ACTIVE"}""", invited())
        fixture.environment.family.refresh(fixture.environment)
        assertNull(familyConnectGuide(fixture.environment))
    }

    @Test
    fun noFamilyYetShowsTheInviteStepsWithoutAsking() = withFixture(familyId = null) { fixture ->
        fixture.environment.family.refresh(fixture.environment)
        assertTrue(fixture.paths.isEmpty())
        assertEquals(FamilyConnectGuide.Invite, familyConnectGuide(fixture.environment))
    }

    @Test
    fun failedFamilyLoadKeepsTheErrorInsteadOfTheGuide() = withFixture { fixture ->
        fixture.respond = { 503 to """{"code":"UNAVAILABLE","message":"잠시 뒤 다시 시도해주세요"}""" }
        fixture.environment.family.refresh(fixture.environment)
        assertNull(familyConnectGuide(fixture.environment))
    }

    @Test
    fun guestAndSignedOutNeverSeeTheGuide() = withFixture(familyId = null) { fixture ->
        fixture.environment.settings.isGuestMode = true
        fixture.environment.family.refresh(fixture.environment)
        assertNull(familyConnectGuide(fixture.environment))

        fixture.environment.settings.isGuestMode = false
        fixture.environment.family.refresh(fixture.environment)
        fixture.environment.session.signOut()
        assertNull(familyConnectGuide(fixture.environment))
    }
}
