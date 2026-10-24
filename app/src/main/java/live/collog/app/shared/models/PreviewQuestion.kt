package live.collog.app.shared.models

import java.util.UUID

data class PreviewQuestion(
    val text: String,
    val id: UUID = UUID.randomUUID(),
) {
    companion object {
        val samples: List<PreviewQuestion> = listOf(
            PreviewQuestion("요즘 밤에 주무실 때 불편한 점은 없으신가요?"),
            PreviewQuestion("이번 주에 산책이나 가벼운 외출은 하셨나요?"),
            PreviewQuestion("이번 주에 산책이나 가벼운 외출은 하셨나요?"),
            PreviewQuestion("이번 주에 산책이나 가벼운 외출은 하셨나요?"),
        )
    }
}
