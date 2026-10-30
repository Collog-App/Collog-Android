package live.collog.app.features.settings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.settings.models.GuestSamples
import live.collog.app.features.settings.models.HealthSelection
import live.collog.app.features.settings.models.InvitationRelations
import live.collog.app.features.settings.models.ManagedFamilyMember
import live.collog.app.features.settings.models.ageText
import live.collog.app.features.settings.models.canEnterInvitationCode
import live.collog.app.features.settings.models.canInviteFamily
import live.collog.app.features.settings.models.canSaveHealthProfile
import live.collog.app.features.settings.models.deleteAccountAndSignOut
import live.collog.app.features.settings.models.memberTitle
import live.collog.app.features.settings.models.otherMembers
import live.collog.app.features.settings.models.ownProfileTarget
import live.collog.app.features.settings.models.permissionText
import live.collog.app.features.settings.models.relationTitle
import live.collog.app.features.settings.models.updating
import live.collog.app.shared.models.HealthCondition
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.FamilyInvitation
import live.collog.app.shared.services.api.FamilyMember
import live.collog.app.shared.services.api.FamilyShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 설정 화면의 순수 로직(피그마 「최종본」 설정, 역할 없는 가족). */
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

    private fun member(
        userId: String?,
        relation: String? = "MOTHER",
        acceptedAt: Instant? = null,
        invitation: FamilyInvitation? = null,
        id: String = "m",
    ) = ManagedFamilyMember(id = id, userId = userId, name = "김순희", relation = relation, acceptedAt = acceptedAt, invitation = invitation)

    @Test
    fun memberTitleShowsRelationOnlyWhenKnown() {
        assertEquals("김순희 (어머니)", memberTitle("김순희", "MOTHER"))
        assertEquals("김영수 (아버지)", memberTitle("김영수", "FATHER"))
        assertEquals("민준 (가족)", memberTitle("민준", "UNKNOWN"))
        // 가족을 만든 사람은 관계가 비어 있다
        assertEquals("민준", memberTitle("민준", null))
        assertEquals("민준", memberTitle("민준", ""))
    }

    @Test
    fun everyInvitationRelationHasATitle() {
        assertEquals(
            listOf("어머니", "아버지", "배우자", "자녀", "형제자매", "조부모", "가족"),
            InvitationRelations.map { relationTitle(it) },
        )
    }

    @Test
    fun statusLineFollowsAcceptanceAndInvitation() {
        val seoul = ZoneId.of("Asia/Seoul")
        assertEquals(
            "2025-01-20 초대수락",
            member("u", acceptedAt = Instant.parse("2025-01-19T16:30:00Z")).statusText(seoul),
        )
        assertNull(member("u").statusText(seoul))
        // 만든 사람의 acceptedAt은 가족을 만든 때다
        assertEquals(
            "2025-01-20 가족 생성",
            member("me", relation = "", acceptedAt = Instant.parse("2025-01-19T16:30:00Z")).copy(isCreator = true).statusText(seoul),
        )
        assertEquals("초대 수락 대기 중", member(null, invitation = invitation()).statusText(seoul))
        assertEquals("초대 수락 대기 중", member(null).statusText(seoul))
        assertEquals("초대가 만료됐어요", member(null, invitation = invitation(status = "EXPIRED")).statusText(seoul))
        assertEquals(
            "초대가 만료됐어요",
            member(null, invitation = invitation(expiresAt = Instant.now().minusSeconds(5))).statusText(seoul),
        )
    }

    @Test
    fun onlyConnectedMembersHaveProfiles() {
        val connected = ManagedFamilyMember(
            id = "m1",
            userId = "u1",
            name = "김순희",
            relation = "MOTHER",
            birthDate = "1960-03-15",
        )
        assertEquals("u1", connected.profileTarget?.id)
        assertEquals("김순희 (어머니)", connected.profileTarget?.title)
        assertEquals("1960-03-15", connected.profileTarget?.birthDate)
        assertNull(member(null, invitation = invitation()).profileTarget)
    }

    @Test
    fun memberListLeavesMeOut() {
        val members = listOf(
            FamilyMember(memberId = "me", userId = "me", name = "나", relation = "", status = "ACTIVE", isCreator = true),
            FamilyMember(memberId = "m1", userId = "mom", name = "김순희", relation = "MOTHER", status = "CONSENT_GRANTED"),
            FamilyMember(memberId = "m2", userId = null, name = "박정숙", relation = "MOTHER", status = "INVITED"),
        )
        assertEquals(listOf("m1", "m2"), otherMembers(members, "me").map { it.id })
        assertTrue(otherMembers(members, "other").first().isCreator)
    }

    @Test
    fun anyoneCanInviteAndFamilyIsCreatedOnDemand() {
        assertTrue(canInviteFamily(isGuestMode = false, hasFamily = true, serverAllowsInvitations = true))
        assertTrue(canInviteFamily(isGuestMode = false, hasFamily = false, serverAllowsInvitations = false))
        assertFalse(canInviteFamily(isGuestMode = false, hasFamily = true, serverAllowsInvitations = false))
        assertFalse(canInviteFamily(isGuestMode = true, hasFamily = false, serverAllowsInvitations = true))
    }

    @Test
    fun invitationCodeOnlyWhileAlone() {
        assertTrue(canEnterInvitationCode(isGuestMode = false, others = emptyList()))
        assertTrue(canEnterInvitationCode(isGuestMode = false, others = listOf(member(null, invitation = invitation()))))
        assertFalse(canEnterInvitationCode(isGuestMode = false, others = listOf(member("u"))))
        assertFalse(canEnterInvitationCode(isGuestMode = true, others = emptyList()))
    }

    @Test
    fun ageIsInternationalAge() {
        val today = LocalDate.of(2026, 10, 30)
        assertEquals("66세", ageText("1960-03-15", today))
        assertEquals("65세", ageText("1960-12-01", today))
        assertEquals("0세", ageText("2026-10-30", today))
        assertNull(ageText(null, today))
        assertNull(ageText("1960/03/15", today))
        assertNull(ageText("2027-01-01", today))
    }

    @Test
    fun ownProfileUsesNickname() {
        val user = ApiUser(id = "me", role = "CHILD", name = "김민준", nickname = "민준이", birthDate = "1994-03-15")
        assertEquals("민준이", ownProfileTarget(user).title)
        assertEquals("김민준", ownProfileTarget(user.copy(nickname = null)).title)
        assertEquals("me", ownProfileTarget(user).id)
    }

    @Test
    fun permissionTextListsWhatIsShared() {
        assertEquals("열람 권한 : 리포트 · 타임라인", permissionText(FamilyShare("u", "엄마")))
        assertEquals("열람 권한 : 리포트", permissionText(FamilyShare("u", "엄마", timeline = false)))
        assertEquals("열람 권한 : 타임라인", permissionText(FamilyShare("u", "엄마", report = false)))
        assertEquals("열람 권한 : 없음", permissionText(FamilyShare("u", "엄마", report = false, timeline = false)))
    }

    @Test
    fun updatingChangesOnlyThatViewer() {
        val shares = listOf(FamilyShare("a", "엄마"), FamilyShare("b", "아빠"))
        val next = shares.updating("b", timeline = false)
        assertEquals(shares[0], next[0])
        assertTrue(next[1].report)
        assertFalse(next[1].timeline)
        assertFalse(shares.updating("a", report = false)[0].report)
    }

    @Test
    fun finalDesignConditionsDescribeThemselves() {
        assertEquals(listOf("FORGETFULNESS", "RESPIRATORY", "RHINITIS"), HealthCondition.entries.map { it.rawValue })
        assertEquals(listOf("건망증", "호흡기 질환", "비염"), HealthCondition.entries.map { it.title })
        assertTrue(HealthCondition.entries.all { it.collected.isNotBlank() && it.usage.isNotBlank() })
        assertEquals(HealthCondition.Rhinitis, HealthCondition.from("RHINITIS"))
        assertNull(HealthCondition.from("HYPERTENSION"))
    }

    @Test
    fun savingKeepsConditionsThisAppDoesNotShow() {
        // iOS 가 넣은 고혈압은 화면에 없지만, 안드에서 저장해도 지워지면 안 된다.
        val loaded = HealthSelection.fromProfile(listOf("HYPERTENSION", "RHINITIS"), hasCompletedSetup = true)
        assertEquals(setOf(HealthCondition.Rhinitis), loaded.selected)
        val edited = loaded.toggled(HealthCondition.Forgetfulness).toggled(HealthCondition.Rhinitis)
        assertEquals(listOf("FORGETFULNESS", "HYPERTENSION"), edited.values)
        assertEquals(listOf("HYPERTENSION"), edited.toggled(null).values)
    }

    @Test
    fun healthSelectionIsExclusiveWithNoConditions() {
        var selection = HealthSelection().toggled(HealthCondition.Rhinitis).toggled(HealthCondition.Forgetfulness)
        assertEquals(setOf(HealthCondition.Forgetfulness, HealthCondition.Rhinitis), selection.selected)
        assertEquals(listOf("FORGETFULNESS", "RHINITIS"), selection.values)
        assertEquals("건망증, 비염", selection.summary)

        selection = selection.toggled(null)
        assertTrue(selection.selected.isEmpty())
        assertTrue(selection.hasNoConditions)
        assertEquals("해당 없음", selection.summary)

        selection = selection.toggled(HealthCondition.Respiratory)
        assertFalse(selection.hasNoConditions)
        assertTrue(selection.isSelected(HealthCondition.Respiratory))

        selection = selection.toggled(HealthCondition.Respiratory)
        assertFalse(selection.isChosen)
        assertNull(selection.summary)
    }

    @Test
    fun legacyConditionCodesAreSkipped() {
        val guest = HealthSelection.fromGuestString("HYPERTENSION,RHINITIS")
        assertEquals(setOf(HealthCondition.Rhinitis), guest.selected)
        assertTrue(HealthSelection.fromGuestString("HYPERTENSION").hasNoConditions)
        assertTrue(HealthSelection.fromGuestString("").hasNoConditions)

        val profile = HealthSelection.fromProfile(listOf("DIABETES", "RESPIRATORY"), hasCompletedSetup = true)
        assertEquals(setOf(HealthCondition.Respiratory), profile.selected)
        assertFalse(profile.hasNoConditions)
        assertTrue(HealthSelection.fromProfile(listOf("ASTHMA"), hasCompletedSetup = true).hasNoConditions)
        assertFalse(HealthSelection.fromProfile(emptyList(), hasCompletedSetup = false).hasNoConditions)
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
    fun guestSamplesFillEveryField() {
        val user = GuestSamples.user
        assertNotNull(user.email)
        assertNotNull(ageText(user.birthDate))
        val members = GuestSamples.members
        val connected = members.filter { it.isConnected }
        assertTrue(connected.isNotEmpty())
        assertTrue(connected.all { it.statusText() != null && it.consentGranted && ageText(it.birthDate) != null })
        val invited = members.single { !it.isConnected }
        assertEquals("초대 수락 대기 중", invited.statusText())
        assertTrue(GuestSamples.shares.isNotEmpty())
        // 예시 질환은 모두 지금 목록에 있는 값이다
        for (target in connected.map { it.id } + listOf<String?>(null)) {
            val (conditions, _) = GuestSamples.profile(target)
            assertEquals(conditions.split(",").size, HealthSelection.fromGuestString(conditions).selected.size)
        }
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
