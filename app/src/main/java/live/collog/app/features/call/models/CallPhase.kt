package live.collog.app.features.call.models

enum class CallPhase(val statusText: String) {
    Connecting("통화 연결 중..."),
    Ringing("받으실 때까지 기다리는 중"),
    Active(""),
    Reconnecting("통신 상태를 확인하고 있어요"),
    Ended("통화 종료"),
    ;

    val showsTimer: Boolean get() = this == Active
}

object CallDurationFormatter {
    fun text(intervalSeconds: Double): String {
        val total = intervalSeconds.toInt().coerceAtLeast(0)
        return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    }
}
