package live.collog.app.features.home.models

import live.collog.app.shared.services.api.Todo

/** 할 일 제목 최대 글자 수(서버와 같다). */
const val TodoTitleLimit = 100

/** 입력칸에 100자까지만 들어가게 자른다. */
fun limitTodoInput(raw: String): String = raw.take(TodoTitleLimit)

/** 공백을 뗀 글이 있어야 「추가」를 누를 수 있다. */
fun canAddTodo(raw: String): Boolean = raw.trim().isNotEmpty()

/** 서버에 보낼 제목(공백을 떼고 100자 이내). */
fun todoTitleToSend(raw: String): String = raw.trim().take(TodoTitleLimit)

/** 체크를 누른 즉시 화면에 반영하는 낙관적 갱신. */
fun List<Todo>.withDone(id: String, done: Boolean): List<Todo> =
    map { if (it.id == id) it.copy(done = done, doneAt = if (done) it.doneAt else null) else it }

fun List<Todo>.without(id: String): List<Todo> = filterNot { it.id == id }

/** 서버가 돌려준 값으로 같은 id 한 줄을 바꾼다. */
fun List<Todo>.replacing(todo: Todo): List<Todo> = map { if (it.id == todo.id) todo else it }

/** 체험 모드 견본 3개. 서버를 부르지 않고 메모리에서만 다룬다. */
fun guestSampleTodos(): List<Todo> =
    listOf("20분 산책하기", "물 6잔 마시기", "자기 전 5분 스트레칭").mapIndexed { index, title ->
        Todo(id = "guest-sample-$index", title = title, source = "SUGGESTED", done = false, createdAt = "")
    }
