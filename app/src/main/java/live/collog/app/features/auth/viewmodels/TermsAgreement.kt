package live.collog.app.features.auth.viewmodels

import live.collog.app.config.AppEnvironment
import live.collog.app.shared.services.api.ConsentDocument

/** 피그마 약관 동의(269:3944)의 필수 항목. 위치정보 이용약관은 위치를 모으지 않아 뺐다. */
enum class TermsItem(val title: String) {
    Service("(필수) 서비스 이용약관"),
    Privacy("(필수) 개인정보 수집 및 이용 동의서"),
    Adult("(필수) 만 19세 이상입니다"),
}

/** 체크한 항목. 「전체 동의하기」는 모두 체크됐을 때 켜지고, 누르면 모두 켜거나 모두 끈다. */
data class TermsAgreement(val checked: Set<TermsItem> = emptySet()) {
    val allChecked: Boolean get() = checked.containsAll(TermsItem.entries)

    fun isChecked(item: TermsItem): Boolean = item in checked

    fun toggling(item: TermsItem): TermsAgreement = copy(checked = if (item in checked) checked - item else checked + item)

    fun togglingAll(): TermsAgreement = copy(checked = if (allChecked) emptySet() else TermsItem.entries.toSet())
}

/**
 * 약관 「다음으로」: 서버 동의 문서의 필수 항목 모두에 GRANT 를 남긴다(`POST /v1/consents`).
 * 서버는 끝까지 읽지 않은 동의를 받지 않으므로 화면이 끝까지 내려간 뒤에만 부른다. 그사이 계정이 바뀌지 않았으면 true.
 */
internal suspend fun grantConsent(environment: AppEnvironment, document: ConsentDocument): Boolean {
    val userId = environment.session.user?.id
    environment.api.submitConsent(
        documentVersion = document.version,
        agreedItems = document.requiredItems.sorted(),
        decision = "GRANT",
        scrolledToEnd = true,
    )
    return environment.session.user?.id == userId
}
