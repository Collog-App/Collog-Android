package live.collog.app.features.home.models

import live.collog.app.shared.services.api.FamilyMember

data class FamilyContact(
    val id: String,
    val userId: String?,
    val name: String,
    val relation: String,
    val lastCallText: String,
) {
    val isCallable: Boolean get() = userId != null

    constructor(member: FamilyMember, lastCallText: String) : this(
        id = member.memberId,
        userId = member.userId,
        name = member.name,
        relation = member.relation.orEmpty(),
        lastCallText = lastCallText,
    )

    companion object {
        val samples: List<FamilyContact> = listOf(
            FamilyContact("sample-mother", null, "어머니", "MOTHER", "3일 전 통화"),
            FamilyContact("sample-father", null, "아버지", "FATHER", "그저께 통화"),
        )
    }
}
