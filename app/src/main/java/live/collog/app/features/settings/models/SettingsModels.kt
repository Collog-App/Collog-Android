package live.collog.app.features.settings.models

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import live.collog.app.config.AppEnvironment
import live.collog.app.shared.models.HealthCondition
import live.collog.app.shared.services.api.ApiUser
import live.collog.app.shared.services.api.FamilyInvitation
import live.collog.app.shared.services.api.FamilyMember
import live.collog.app.shared.services.api.FamilyShare
import live.collog.app.shared.services.api.InvitationDto
import live.collog.app.shared.models.familyRelationTitle

/** 초대할 때 고르는 관계(서버 `InvitationCreate.relation`). */
internal val InvitationRelations = listOf("MOTHER", "FATHER", "SPOUSE", "CHILD", "SIBLING", "GRANDPARENT", "OTHER")

/** 관계 이름. 가족을 만든 사람처럼 관계가 비어 있으면 null. */
internal fun relationTitle(relation: String?): String? = familyRelationTitle(relation)

/** "김순희 (어머니)". 관계가 없으면 이름만. */
internal fun memberTitle(name: String, relation: String?): String =
    relationTitle(relation)?.let { "$name ($it)" } ?: name

private val AcceptedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 피그마 24(277:5984) 구성원 카드 한 장. 연결된 구성원과 수락 대기 중인 초대를 같은 모양으로 보인다. */
internal data class ManagedFamilyMember(
    val id: String,
    val userId: String?,
    val name: String,
    val relation: String?,
    val nickname: String? = null,
    val birthDate: String? = null,
    val acceptedAt: Instant? = null,
    val consentGranted: Boolean = false,
    val canRemove: Boolean = false,
    val invitation: FamilyInvitation? = null,
    val isCreator: Boolean = false,
) {
    constructor(member: FamilyMember) : this(
        id = member.memberId,
        userId = member.userId,
        name = member.name,
        relation = member.relation,
        nickname = member.nickname,
        birthDate = member.birthDate,
        acceptedAt = member.acceptedAt,
        consentGranted = member.consentGranted,
        canRemove = member.canRemove,
        invitation = member.invitation,
        isCreator = member.isCreator,
    )

    val isConnected: Boolean get() = userId != null

    val title: String get() = memberTitle(name, relation)

    /** 카드 아래 줄: "2025-01-20 초대수락"(만든 사람은 가족을 만든 날) 또는 초대 상태. 시각을 모르면 null. */
    fun statusText(zone: ZoneId = ZoneId.systemDefault()): String? = when {
        isConnected -> acceptedAt?.let { "${AcceptedDate.format(it.atZone(zone))} ${if (isCreator) "가족 생성" else "초대수락"}" }
        invitation?.isExpired == true -> "초대가 만료됐어요"
        else -> "초대 수락 대기 중"
    }

    val profileTarget: ProfileTarget? get() = userId?.let { ProfileTarget(it, title, birthDate) }
}

/** 구성원 관리에 보일 사람: 나를 뺀 가족(대기 중 초대 포함). */
internal fun otherMembers(members: List<FamilyMember>, userId: String?): List<ManagedFamilyMember> =
    members.filter { it.userId == null || it.userId != userId }.map(::ManagedFamilyMember)

/** 누구나 초대할 수 있다. 가족이 없으면 첫 초대 때 만든다. */
internal fun canInviteFamily(isGuestMode: Boolean, hasFamily: Boolean, serverAllowsInvitations: Boolean): Boolean =
    !isGuestMode && (!hasFamily || serverAllowsInvitations)

/** 서버는 다른 사람이 있는 가족에 이미 속해 있으면 초대 수락을 거절(409)하므로, 혼자일 때만 코드 입력을 보인다. */
internal fun canEnterInvitationCode(isGuestMode: Boolean, others: List<ManagedFamilyMember>): Boolean =
    !isGuestMode && others.none { it.isConnected }

internal data class CreatedInvitation(val familyId: String, val invitation: InvitationDto)

/**
 * 가족이 없으면 먼저 만든다(`POST /v1/families`). 세션의 가족은 여기서 바꾸지 않는다:
 * 바꾸면 앱이 홈으로 돌아가 초대 코드를 보여 줄 수 없으므로, 시트를 닫을 때 `AuthSession.joinFamily`를 부른다.
 */
internal suspend fun createFamilyInvitation(environment: AppEnvironment, name: String, relation: String): CreatedInvitation {
    val familyId = environment.session.familyId ?: environment.api.createFamily()
    return CreatedInvitation(familyId, environment.api.createInvitation(familyId, name, relation))
}

/** 마케팅 소식 수신(`PATCH /v1/account/preferences`). 세션이 고른 가족은 그대로 둔다. */
internal suspend fun updateMarketingOptIn(environment: AppEnvironment, optIn: Boolean) {
    val user = environment.api.updatePreferences(optIn)
    environment.session.updateUser(user.copy(familyId = environment.session.familyId ?: user.familyId))
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

/** 피그마 26(278:6405) "열람 권한 : 리포트 · 타임라인". */
internal fun permissionText(share: FamilyShare): String {
    val parts = listOfNotNull("리포트".takeIf { share.report }, "타임라인".takeIf { share.timeline })
    return "열람 권한 : " + if (parts.isEmpty()) "없음" else parts.joinToString(" · ")
}

/** [viewerUserId]의 한 줄만 바꾼 새 목록. */
internal fun List<FamilyShare>.updating(viewerUserId: String, report: Boolean? = null, timeline: Boolean? = null): List<FamilyShare> =
    map { share ->
        if (share.viewerUserId != viewerUserId) {
            share
        } else {
            share.copy(report = report ?: share.report, timeline = timeline ?: share.timeline)
        }
    }

/** 질환 프로필을 볼 사람. [title]은 "김순희 (어머니)"처럼 화면에 그대로 쓴다. */
internal data class ProfileTarget(val id: String, val title: String, val birthDate: String? = null)

/** 나의 건강 프로필 대상: 닉네임이 있으면 닉네임. */
internal fun ownProfileTarget(user: ApiUser): ProfileTarget = ProfileTarget(user.id, user.nickname ?: user.name, user.birthDate)

/** 만 나이 "N세". 생년월일이 없거나 형식이 다르면 null. */
internal fun ageText(birthDate: String?, today: LocalDate = LocalDate.now()): String? {
    val born = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (born.isAfter(today)) return null
    return "${Period.between(born, today).years}세"
}

/**
 * 건강 프로필의 선택 상태. "해당 없음"과 질환 선택은 서로 배타적이다.
 * [legacy]는 이 앱이 모르는 예전 질환 값(iOS 가 넣은 고혈압 등)이다. 화면에는 안 보이지만 저장할 때 그대로 돌려보내
 * 다른 앱이 넣은 값을 지우지 않는다.
 */
internal data class HealthSelection(
    val selected: Set<HealthCondition> = emptySet(),
    val hasNoConditions: Boolean = false,
    val legacy: List<String> = emptyList(),
) {
    /** [condition]이 null이면 "해당 없음" 줄. */
    fun toggled(condition: HealthCondition?): HealthSelection =
        if (condition == null) {
            copy(selected = emptySet(), hasNoConditions = !hasNoConditions)
        } else {
            val next = if (condition in selected) selected - condition else selected + condition
            copy(selected = next, hasNoConditions = false)
        }

    fun isSelected(condition: HealthCondition?): Boolean =
        if (condition == null) hasNoConditions else condition in selected

    val isChosen: Boolean get() = selected.isNotEmpty() || hasNoConditions

    /** 피그마 순서대로 고른 질환. */
    val ordered: List<HealthCondition> get() = HealthCondition.entries.filter { it in selected }

    /** 입력칸에 보이는 글자. 고른 것이 없으면 null(안내 글자를 보인다). */
    val summary: String?
        get() = when {
            selected.isNotEmpty() -> ordered.joinToString(", ") { it.title }
            hasNoConditions -> "해당 없음"
            else -> null
        }

    /** 서버에 보내는 값(정렬). 모르는 예전 값도 함께 보낸다. */
    val values: List<String> get() = (selected.map { it.rawValue } + legacy).distinct().sorted()

    companion object {
        /** 게스트가 저장해 둔 쉼표 문자열("RHINITIS,RESPIRATORY")을 읽는다. 모르는 값은 건너뛴다. */
        fun fromGuestString(stored: String): HealthSelection {
            val set = stored.split(",").mapNotNull(HealthCondition::from).toSet()
            return HealthSelection(set, set.isEmpty())
        }

        /** 서버 프로필에서: 설정을 마쳤는데 아는 질환이 없으면 "해당 없음". 예전 질환 값은 [legacy]로 보존한다. */
        fun fromProfile(conditions: List<String>, hasCompletedSetup: Boolean): HealthSelection {
            val set = conditions.mapNotNull(HealthCondition::from).toSet()
            val legacy = conditions.filter { HealthCondition.from(it) == null }
            return HealthSelection(set, hasCompletedSetup && set.isEmpty(), legacy)
        }
    }
}

/** iOS `HealthProfileSettingsView.canSave`. */
internal fun canSaveHealthProfile(profileLoaded: Boolean, selection: HealthSelection, isSaving: Boolean, isLoading: Boolean): Boolean =
    profileLoaded && selection.isChosen && !isSaving && !isLoading

/** 걱정되는 부분 글자 수 상한(서버 `concerns` max_length). */
internal const val CONCERNS_LIMIT = 500

/** 체험 모드에서 보이는 예시. 네트워크를 부르지 않는다. */
internal object GuestSamples {
    val user = ApiUser(
        id = "guest",
        role = "CHILD",
        name = "김민준",
        email = "minjun@example.com",
        birthDate = "1994-03-15",
        marketingOptIn = true,
    )

    val members: List<ManagedFamilyMember>
        get() = listOf(
            ManagedFamilyMember(
                id = "sample-mother",
                userId = "sample-mother",
                name = "김순희",
                relation = "MOTHER",
                birthDate = "1960-03-15",
                acceptedAt = Instant.parse("2025-01-20T01:00:00Z"),
                consentGranted = true,
                canRemove = true,
            ),
            ManagedFamilyMember(
                id = "sample-father",
                userId = "sample-father",
                name = "김영수",
                relation = "FATHER",
                birthDate = "1957-08-02",
                acceptedAt = Instant.parse("2025-01-20T01:00:00Z"),
                consentGranted = true,
                canRemove = true,
            ),
            ManagedFamilyMember(
                id = "sample-invited",
                userId = null,
                name = "박정숙",
                relation = "GRANDPARENT",
                canRemove = true,
                invitation = FamilyInvitation(
                    invitationId = "sample-invitation",
                    code = "482913",
                    shareText = "콜록 가족 초대 코드 482913를 앱에 입력해주세요.",
                    expiresAt = Instant.now().plusSeconds(86_400),
                    status = "PENDING",
                ),
            ),
        )

    val shares = listOf(
        FamilyShare("sample-mother", "김순희", "MOTHER"),
        FamilyShare("sample-father", "김영수", "FATHER", timeline = false),
    )

    /** 대상별 예시 질환(쉼표 문자열)과 걱정되는 부분. 체험 중 저장한 값이 있으면 그것이 이긴다. */
    fun profile(targetId: String?): Pair<String, String> = when (targetId) {
        "sample-mother" -> "FORGETFULNESS,RESPIRATORY,RHINITIS" to "요즘 같은 이야기를 자주 반복하세요."
        "sample-father" -> "RESPIRATORY" to "환절기마다 기침이 오래가요."
        else -> "RHINITIS" to ""
    }
}
