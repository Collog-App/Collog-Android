package live.collog.app.features.settings.models

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import live.collog.app.config.AppEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.shared.models.HealthCondition
import live.collog.app.shared.models.UserRoleOption
import live.collog.app.shared.services.api.FamilyInvitation
import live.collog.app.shared.services.api.FamilyMember

/** iOS `ManagedFamilyMember`(FamilyMembersSettingsView.swift 안의 private 구조체). */
internal data class ManagedFamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val isConnected: Boolean,
    val invitation: FamilyInvitation?,
) {
    constructor(contact: FamilyContact) : this(
        id = contact.id,
        name = contact.name,
        relation = contact.relation,
        isConnected = true,
        invitation = null,
    )

    constructor(member: FamilyMember) : this(
        id = member.id,
        name = member.name,
        relation = member.relation,
        isConnected = member.userId != null,
        invitation = member.invitation,
    )

    val relationTitle: String
        get() = when (relation) {
            "MOTHER" -> "어머니"
            "FATHER" -> "아버지"
            "CHILD" -> "자녀"
            "PARENT" -> "부모"
            else -> "가족"
        }

    /** 목록 오른쪽에 보이는 상태 글자. */
    val statusText: String
        get() = when {
            isConnected -> "가입 완료"
            invitation?.isExpired == true -> "초대 만료"
            else -> "초대 대기"
        }
}

/** iOS `FamilyMembersSettingsView.canInvite`. */
internal fun canInviteFamily(isGuestMode: Boolean, role: String?, serverAllowsInvitations: Boolean): Boolean =
    !isGuestMode && role == "CHILD" && serverAllowsInvitations

/** iOS `AccountSettingsView.roleText`. */
internal fun roleText(role: String?): String = if (role == UserRoleOption.Parent.rawValue) "부모" else "자녀"

/** 역할 저장 버튼은 지금 역할과 다른 것을 골랐을 때만 켜진다. */
internal fun canSaveRole(selected: UserRoleOption, currentRole: String?): Boolean = selected.rawValue != currentRole

/** 계정 정보 화면의 "초대 코드 입력" 줄은 로그인한 부모에게만 보인다. */
internal fun showsInvitationCodeRow(isAuthenticated: Boolean, role: String?): Boolean =
    isAuthenticated && role == "PARENT"

/** iOS `AccountSettingsView.updateRole()`의 서버 호출 부분. */
internal suspend fun updateAccountRole(environment: AppEnvironment, role: UserRoleOption) {
    val user = environment.api.updateRole(role.rawValue)
    environment.session.updateUser(user)
}

/**
 * iOS `AccountSettingsView.deleteAccount()`의 삭제·로그아웃 부분.
 * Apple 재인증은 없고(Google·전화 계정은 서버가 요구하지 않는다), 삭제 뒤 같은 계정일 때만 로그아웃한다.
 */
internal suspend fun deleteAccountAndSignOut(environment: AppEnvironment) {
    val userId = environment.session.user?.id
    environment.api.deleteAccount()
    if (environment.session.user?.id == userId) {
        environment.session.signOut()
        environment.settings.isGuestMode = false
        // 로그아웃으로 이 화면이 사라져도 Google 정리는 끝까지 한다
        withContext(NonCancellable) { environment.afterSignOut?.invoke() }
    }
}

/** iOS `HealthProfileSettingsView.ProfileTarget`. */
internal data class ProfileTarget(val id: String, val name: String)

/** 자녀가 건강 프로필을 고칠 수 있는 대상: 가입을 마친 부모만. */
internal fun parentTargets(members: List<FamilyMember>): List<ProfileTarget> =
    members.mapNotNull { member ->
        val id = member.userId
        if (member.role == "PARENT" && id != null) ProfileTarget(id, member.name) else null
    }

/** 건강 프로필의 선택 상태. "해당 없음"과 질환 선택은 서로 배타적이다. */
internal data class HealthSelection(
    val selected: Set<HealthCondition> = emptySet(),
    val hasNoConditions: Boolean = false,
) {
    /** [condition]이 null이면 "해당 없음" 줄. */
    fun toggled(condition: HealthCondition?): HealthSelection =
        if (condition == null) {
            HealthSelection(emptySet(), !hasNoConditions)
        } else {
            val next = if (condition in selected) selected - condition else selected + condition
            HealthSelection(next, false)
        }

    fun isSelected(condition: HealthCondition?): Boolean =
        if (condition == null) hasNoConditions else condition in selected

    val isChosen: Boolean get() = selected.isNotEmpty() || hasNoConditions

    /** 서버에 보내는 값(정렬). */
    val values: List<String> get() = selected.map { it.rawValue }.sorted()

    companion object {
        /** 게스트가 저장해 둔 쉼표 문자열("HYPERTENSION,ASTHMA")을 읽는다. */
        fun fromGuestString(stored: String): HealthSelection {
            val set = stored.split(",").mapNotNull { raw -> HealthCondition.entries.firstOrNull { it.rawValue == raw } }.toSet()
            return HealthSelection(set, set.isEmpty())
        }

        /** 서버 프로필에서: 설정을 마쳤는데 질환이 없으면 "해당 없음". */
        fun fromProfile(conditions: List<String>, hasCompletedSetup: Boolean): HealthSelection {
            val set = conditions.mapNotNull { raw -> HealthCondition.entries.firstOrNull { it.rawValue == raw } }.toSet()
            return HealthSelection(set, hasCompletedSetup && set.isEmpty())
        }
    }
}

/** iOS `HealthProfileSettingsView.canSave`. */
internal fun canSaveHealthProfile(profileLoaded: Boolean, selection: HealthSelection, isSaving: Boolean, isLoading: Boolean): Boolean =
    profileLoaded && selection.isChosen && !isSaving && !isLoading
