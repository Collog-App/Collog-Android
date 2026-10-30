package live.collog.app.shared.models

/**
 * 피그마 「최종본」 질환(277:5137): 항목마다 수집 정보와 활용을 설명한다.
 * 서버는 예전 값(DIABETES 등)도 받으므로 모르는 값은 [from]이 null로 돌려 건너뛴다.
 */
enum class HealthCondition(val rawValue: String, val title: String, val collected: String, val usage: String) {
    Forgetfulness(
        "FORGETFULNESS",
        "건망증",
        "통화 중 단어 찾기 지연, 같은 말 반복, 문장 중단 및 재시작 빈도",
        "발화 패턴을 누적해 이상 변화를 감지하고, 조기 알림 제공에 사용해요.",
    ),
    Respiratory(
        "RESPIRATORY",
        "호흡기 질환",
        "말하는 도중 숨소리, 기침 빈도, 발화 중 호흡 간격",
        "호흡 패턴 변화 추이를 분석해 급성 악화 징후를 포착할 때 사용해요.",
    ),
    Rhinitis(
        "RHINITIS",
        "비염",
        "콧소리 섞인 발음(비음도), 훌쩍임 빈도, 목소리 톤 변화",
        "비염 증상의 패턴을 기록해 알레르기 시즌 예측과 악화 시 알림에 활용해요.",
    ),
    ;

    val id: String get() = rawValue

    companion object {
        fun from(raw: String): HealthCondition? = entries.firstOrNull { it.rawValue == raw }
    }
}
