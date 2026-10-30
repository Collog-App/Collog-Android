package live.collog.app.shared.models

/** 가족 관계 코드(서버 `InvitationCreate.relation`)의 이름. 가족을 만든 사람처럼 비어 있으면 null. 예전 서버의 PARENT는 「부모」. */
fun familyRelationTitle(relation: String?): String? = when (relation) {
    null, "" -> null
    "MOTHER" -> "어머니"
    "FATHER" -> "아버지"
    "SPOUSE" -> "배우자"
    "CHILD" -> "자녀"
    "SIBLING" -> "형제자매"
    "GRANDPARENT" -> "조부모"
    "PARENT" -> "부모"
    else -> "가족"
}
