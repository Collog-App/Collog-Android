package live.collog.app.features.home

import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.home.models.canAddTodo
import live.collog.app.features.home.models.guestSampleTodos
import live.collog.app.features.home.models.limitTodoInput
import live.collog.app.features.home.models.replacing
import live.collog.app.features.home.models.todoTitleToSend
import live.collog.app.features.home.models.withDone
import live.collog.app.features.home.models.without
import live.collog.app.features.home.viewmodels.TodoViewModel
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.Todo
import live.collog.app.shared.services.api.TodosResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 오늘 할 일: 순수 로직, `Todo` JSON 해석, 화면 상태(가짜 서버·체험 모드). */
class TodoTests {
    private fun todo(id: String, done: Boolean = false, source: String = "USER") =
        Todo(id = id, title = "t$id", source = source, done = done, createdAt = "2026-11-15T00:00:00Z")

    private fun json(id: String, done: Boolean = false) =
        """{"id":"$id","title":"t$id","source":"USER","done":$done,"doneAt":null,"createdAt":"2026-11-15T01:00:00Z"}"""

    @Test
    fun parsesBothSourcesAndNullDoneAt() {
        val raw = """
            {"date":"2026-11-15","items":[
              {"id":"a","title":"20분 산책하기","source":"SUGGESTED","done":true,"doneAt":"2026-11-15T03:00:00Z","createdAt":"2026-11-15T00:00:00Z"},
              {"id":"b","title":"약 먹기","source":"USER","done":false,"doneAt":null,"createdAt":"2026-11-15T01:00:00Z","extra":1}
            ]}
        """.trimIndent()
        val response = CollogJson.decodeFromString<TodosResponse>(raw)
        assertEquals("2026-11-15", response.date)
        assertTrue(response.items[0].isSuggested)
        assertNotNull(response.items[0].doneAt)
        assertFalse(response.items[1].isSuggested)
        assertNull(response.items[1].doneAt)
    }

    @Test
    fun inputIsLimitedAndTrimmed() {
        assertEquals(100, limitTodoInput("가".repeat(150)).length)
        assertFalse(canAddTodo("   "))
        assertFalse(canAddTodo(""))
        assertTrue(canAddTodo(" a "))
        assertEquals("a b", todoTitleToSend("  a b \n"))
        assertEquals(100, todoTitleToSend(" " + "x".repeat(120)).length)
    }

    @Test
    fun optimisticListUpdates() {
        val list = listOf(todo("1"), todo("2", done = true))
        assertTrue(list.withDone("1", true)[0].done)
        assertFalse(list.withDone("2", false)[1].done)
        assertEquals(listOf("2"), list.without("1").map { it.id })
        assertEquals("새 제목", list.replacing(todo("1").copy(title = "새 제목"))[0].title)
    }

    @Test
    fun guestSamplesAreThreeSuggestedItems() {
        val samples = guestSampleTodos()
        assertEquals(listOf("20분 산책하기", "물 6잔 마시기", "자기 전 5분 스트레칭"), samples.map { it.title })
        assertTrue(samples.all { it.isSuggested && !it.done })
        assertEquals(3, samples.map { it.id }.toSet().size)
    }

    @Test
    fun loadToggleDeleteAddUseServerPaths() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = { request ->
                when (request.method to request.url.encodedPath) {
                    "GET" to "/v1/todos" -> 200 to """{"date":"2026-11-15","items":[${json("a")}]}"""
                    "PATCH" to "/v1/todos/a" -> 200 to json("a", done = true)
                    "POST" to "/v1/todos" -> 201 to json("b")
                    "DELETE" to "/v1/todos/a" -> 204 to ""
                    else -> 500 to "{}"
                }
            }
            val viewModel = TodoViewModel()
            viewModel.load(fixture.environment)
            assertEquals(listOf("a"), viewModel.items.map { it.id })

            viewModel.toggle(fixture.environment, viewModel.items[0])
            assertTrue(viewModel.items[0].done)

            assertTrue(viewModel.add(fixture.environment, " 새 일 "))
            assertEquals(listOf("a", "b"), viewModel.items.map { it.id })

            viewModel.delete(fixture.environment, viewModel.items[0])
            assertEquals(listOf("b"), viewModel.items.map { it.id })
            assertNull(viewModel.actionError)
            assertTrue(fixture.requests.any { it.method == "DELETE" && it.url.encodedPath == "/v1/todos/a" })
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun serverNotFoundShowsLoadFailureWithoutCrashing() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = { 404 to """{"detail":"Not Found"}""" }
            val viewModel = TodoViewModel()
            viewModel.load(fixture.environment)
            assertTrue(viewModel.loadFailed)
            assertTrue(viewModel.items.isEmpty())
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun failedActionsRevertAndShowError() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.respond = { request ->
                if (request.method == "GET") 200 to """{"date":"2026-11-15","items":[${json("a")}]}"""
                else 500 to "{}"
            }
            val viewModel = TodoViewModel()
            viewModel.load(fixture.environment)

            viewModel.toggle(fixture.environment, viewModel.items[0])
            assertFalse(viewModel.items[0].done)
            assertNotNull(viewModel.actionError)

            viewModel.delete(fixture.environment, viewModel.items[0])
            assertEquals(1, viewModel.items.size)

            assertFalse(viewModel.add(fixture.environment, "x"))
            assertEquals(1, viewModel.items.size)
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun guestModeNeverTouchesTheNetwork() = runTest {
        val fixture = TestEnvironment()
        try {
            fixture.environment.settings.isGuestMode = true
            val viewModel = TodoViewModel()
            viewModel.load(fixture.environment)
            assertEquals(3, viewModel.items.size)

            viewModel.toggle(fixture.environment, viewModel.items[0])
            assertTrue(viewModel.items[0].done)
            assertTrue(viewModel.add(fixture.environment, "내 일"))
            assertEquals(4, viewModel.items.size)
            viewModel.delete(fixture.environment, viewModel.items[1])
            assertEquals(3, viewModel.items.size)

            // 다시 불러와도 바꾼 내용이 유지된다
            viewModel.load(fixture.environment)
            assertEquals(3, viewModel.items.size)
            assertTrue(fixture.requests.isEmpty())
        } finally {
            fixture.cleanUp()
        }
    }
}
