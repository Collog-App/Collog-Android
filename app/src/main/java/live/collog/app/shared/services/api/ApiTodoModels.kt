package live.collog.app.shared.services.api

import kotlinx.serialization.Serializable

/** 오늘 할 일 한 줄(서버 `Todo`). 시각은 ISO8601 문자열 그대로 둔다(화면에서 쓰지 않는다). */
@Serializable
data class Todo(
    val id: String,
    val title: String,
    /** "SUGGESTED"(서버 추천) | "USER"(직접 적음) */
    val source: String,
    val done: Boolean,
    val doneAt: String? = null,
    val createdAt: String,
) {
    val isSuggested: Boolean get() = source == "SUGGESTED"
}

@Serializable
data class TodosResponse(val date: String, val items: List<Todo>)

@Serializable
data class TodoCreateBody(val title: String)

@Serializable
data class TodoDoneBody(val done: Boolean)
