package live.collog.app.features.home.models

import live.collog.app.config.AppEnvironment

/**
 * 홈의 가족 연결 안내(피그마 없음). [Invite]는 아직 보낸 초대가 없을 때, [Waiting]은 보낸 초대가 수락을 기다릴 때,
 * [Expired]는 보낸 초대가 모두 만료됐을 때.
 */
internal enum class FamilyConnectGuide { Invite, Waiting, Expired }

/**
 * 로그인한 사람이 아직 아무와도 연결되지 않았을 때만 안내를 보인다(체험 모드 제외).
 * 가족 목록을 받기 전이나 못 받았으면 연결 여부를 몰라 띄우지 않는다(연결된 사람에게 잠깐 보이지 않게).
 */
internal fun familyConnectGuide(environment: AppEnvironment): FamilyConnectGuide? {
    val family = environment.family
    return when {
        environment.settings.isGuestMode || !environment.session.isAuthenticated -> null
        !family.isLoaded || family.loadError != null || family.contacts.isNotEmpty() -> null
        family.hasPendingInvitation -> FamilyConnectGuide.Waiting
        family.hasSentInvitation -> FamilyConnectGuide.Expired
        else -> FamilyConnectGuide.Invite
    }
}
