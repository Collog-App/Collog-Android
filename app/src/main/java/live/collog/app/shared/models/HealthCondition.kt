package live.collog.app.shared.models

enum class HealthCondition(val rawValue: String, val title: String) {
    Diabetes("DIABETES", "당뇨"),
    Hypertension("HYPERTENSION", "고혈압"),
    Dyslipidemia("DYSLIPIDEMIA", "이상지질혈증"),
    Asthma("ASTHMA", "천식"),
    Obesity("OBESITY", "비만"),
    ;

    val id: String get() = rawValue
}

enum class UserRoleOption(val rawValue: String, val title: String) {
    Child("CHILD", "자녀"),
    Parent("PARENT", "부모"),
    ;

    val id: String get() = rawValue

    companion object {
        fun from(raw: String?): UserRoleOption? = entries.firstOrNull { it.rawValue == raw }
    }
}
