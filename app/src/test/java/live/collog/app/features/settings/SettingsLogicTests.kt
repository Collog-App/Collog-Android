package live.collog.app.features.settings

import java.time.Instant
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.settings.models.HealthSelection
import live.collog.app.features.settings.models.ManagedFamilyMember
import live.collog.app.features.settings.models.canInviteFamily
import live.collog.app.features.settings.models.canSaveHealthProfile
import live.collog.app.features.settings.models.canSaveRole
import live.collog.app.features.settings.models.deleteAccountAndSignOut
import live.collog.app.features.settings.models.parentTargets
import live.collog.app.features.settings.models.roleText
import live.collog.app.features.settings.models.showsInvitationCodeRow
import live.collog.app.features.settings.models.updateAccountRole
import live.collog.app.shared.models.HealthCondition
import live.collog.app.shared.models.UserRoleOption
import live.collog.app.shared.services.api.FamilyInvitation
import live.collog.app.shared.services.api.FamilyMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS 설정 화면의 순수 로직(iOS에는 대응 테스트가 없다). */
class SettingsLogicTests {
    private fun withFixture(role: String = "PARENT", familyId: String? = "family", body: suspend (TestEnvironment) -> Unit) =
        runTest {
            val fixture = TestEnvironment(role = role, familyId = familyId)
            try {
                body(fixture)
            } finally {
                fixture.cleanUp()
            }
        }

    private fun invitation(status: String = "PENDING", expiresAt: Instant = Instant.now().plusSeconds(3600)) =
        FamilyInvitation("inv", "123456", "share", expiresAt, status)

    private fun member(userId: String?, relation: String = "MOTHER", role: String? = null, invitation: FamilyInvitation? = null) =
        FamilyMember("m", userId, "엄마", relation, "ACTIVE", role, invitation)

    @Test
    fun memberStatusFollowsConnectionAndInvitation() {
        assertEquals("가입 완료", ManagedFamilyMember(member("u")).statusText)
        assertEquals("초대 대기", ManagedFamilyMember(member(null, invitation = invitation())).statusText)
        assertEquals("초대 만료", ManagedFamilyMember(member(null, invitation = invitation(status = "EXPIRED"))).statusText)
        assertEquals(
            "초대 만료",
            ManagedFamilyMember(member(null, invitation = invitation(expiresAt = Instant.now().minusSeconds(5)))).statusText,
        )
        assertEquals("초대 대기", ManagedFamilyMember(member(null)).statusText)
    }

    @Test
    fun relationTitleFallsBackToFamily() {
        assertEquals("어머니", ManagedFamilyMember(member("u", "MOTHER")).relationTitle)
        assertEquals("아버지", ManagedFamilyMember(member("u", "FATHER")).relationTitle)
        assertEquals("자녀", ManagedFamilyMember(member("u", "CHILD")).relationTitle)
        assertEquals("부모", ManagedFamilyMember(member("u", "PARENT")).relationTitle)
        assertEquals("가족", ManagedFamilyMember(member("u", "UNKNOWN")).relationTitle)
    }

    @Test
    fun guestContactsAreConnected() {
        val managed = ManagedFamilyMember(FamilyContact.samples.first())
        assertTrue(managed.isConnected)
        assertNull(managed.invitation)
        assertEquals("어머니", managed.relationTitle)
    }

    @Test
    fun onlyChildrenWithServerPermissionCanInvite() {
        assertTrue(canInviteFamily(isGuestMode = false, role = "CHILD", serverAllowsInvitations = true))
        assertFalse(canInviteFamily(isGuestMode = true, role = "CHILD", serverAllowsInvitations = true))
        assertFalse(canInviteFamily(isGuestMode = false, role = "PARENT", serverAllowsInvitations = true))
        assertFalse(canInviteFamily(isGuestMode = false, role = "CHILD", serverAllowsInvitations = false))
    }

    @Test
    fun roleRules() {
        assertEquals("부모", roleText("PARENT"))
        assertEquals("자녀", roleText("CHILD"))
        assertEquals("자녀", roleText(null))
        assertFalse(canSaveRole(UserRoleOption.Parent, "PARENT"))
        assertTrue(canSaveRole(UserRoleOption.Child, "PARENT"))
        assertTrue(showsInvitationCodeRow(isAuthenticated = true, role = "PARENT"))
        assertFalse(showsInvitationCodeRow(isAuthenticated = true, role = "CHILD"))
        assertFalse(showsInvitationCodeRow(isAuthenticated = false, role = "PARENT"))
    }

    @Test
    fun healthSelectionIsExclusiveWithNoConditions() {
        var selection = HealthSelection().toggled(HealthCondition.Diabetes).toggled(HealthCondition.Asthma)
        assertEquals(setOf(HealthCondition.Diabetes, HealthCondition.Asthma), selection.selected)
        assertEquals(listOf("ASTHMA", "DIABETES"), selection.values)

        selection = selection.toggled(null)
        assertTrue(selection.selected.isEmpty())
        assertTrue(selection.hasNoConditions)
        assertTrue(selection.isSelected(null))

        selection = selection.toggled(HealthCondition.Obesity)
        assertFalse(selection.hasNoConditions)
        assertTrue(selection.isSelected(HealthCondition.Obesity))

        selection = selection.toggled(HealthCondition.Obesity)
        assertFalse(selection.isChosen)
    }

    @Test
    fun healthSelectionReadsStoredValues() {
        val guest = HealthSelection.fromGuestString("HYPERTENSION,BOGUS")
        assertEquals(setOf(HealthCondition.Hypertension), guest.selected)
        assertTrue(HealthSelection.fromGuestString("").hasNoConditions)

        assertTrue(HealthSelection.fromProfile(emptyList(), hasCompletedSetup = true).hasNoConditions)
        assertFalse(HealthSelection.fromProfile(emptyList(), hasCompletedSetup = false).hasNoConditions)
        assertFalse(HealthSelection.fromProfile(listOf("ASTHMA"), hasCompletedSetup = true).hasNoConditions)
    }

    @Test
    fun saveNeedsLoadedProfileAndChoice() {
        val chosen = HealthSelection().toggled(null)
        assertTrue(canSaveHealthProfile(true, chosen, isSaving = false, isLoading = false))
        assertFalse(canSaveHealthProfile(false, chosen, isSaving = false, isLoading = false))
        assertFalse(canSaveHealthProfile(true, HealthSelection(), isSaving = false, isLoading = false))
        assertFalse(canSaveHealthProfile(true, chosen, isSaving = true, isLoading = false))
        assertFalse(canSaveHealthProfile(true, chosen, isSaving = false, isLoading = true))
    }

    @Test
    fun childCanEditOnlyConnectedParents() {
        val targets = parentTargets(
            listOf(
                member("p1", role = "PARENT"),
                member(null, role = "PARENT"),
                member("c1", role = "CHILD"),
            ),
        )
        assertEquals(1, targets.size)
        assertEquals("p1", targets.single().id)
    }

    @Test
    fun roleChangeUpdatesSessionUser() = withFixture(role = "CHILD") { fixture ->
        fixture.respond = { 200 to """{"id":"me","role":"PARENT","name":"Tester","familyId":"family"}""" }
        updateAccountRole(fixture.environment, UserRoleOption.Parent)
        assertEquals(listOf("/v1/account/role"), fixture.paths)
        assertEquals("PARENT", fixture.environment.session.user?.role)
    }

    @Test
    fun deletingAccountSignsOutWithoutReauthentication() = withFixture { fixture ->
        fixture.environment.settings.isGuestMode = true
        fixture.respond = { 204 to "" }
        deleteAccountAndSignOut(fixture.environment)
        assertEquals(listOf("/v1/account"), fixture.paths)
        assertEquals("DELETE", fixture.requests.single().method)
        assertFalse(fixture.environment.session.isAuthenticated)
        assertFalse(fixture.environment.settings.isGuestMode)
    }

    @Test
    fun failedDeletionKeepsSession() = withFixture { fixture ->
        fixture.respond = { 500 to """{"code":"server","message":"실패"}""" }
        val result = runCatching { deleteAccountAndSignOut(fixture.environment) }
        assertTrue(result.isFailure)
        assertTrue(fixture.environment.session.isAuthenticated)
    }
}
