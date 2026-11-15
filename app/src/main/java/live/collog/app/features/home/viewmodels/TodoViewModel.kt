package live.collog.app.features.home.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlinx.coroutines.CancellationException
import live.collog.app.config.AppEnvironment
import live.collog.app.features.home.models.guestSampleTodos
import live.collog.app.features.home.models.replacing
import live.collog.app.features.home.models.todoTitleToSend
import live.collog.app.features.home.models.withDone
import live.collog.app.features.home.models.without
import live.collog.app.shared.services.api.Todo

/**
 * 홈의 「오늘 할 일」 카드 상태. 내 할 일만 다루며 홈의 가족 선택과 무관하다.
 * 체험 모드는 네트워크 없이 메모리에서만 바꾼다. 운영 서버에 아직 API 가 없어도(404) 홈은 멈추지 않는다.
 */
class TodoViewModel {
    var items by mutableStateOf<List<Todo>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    /** 처음 불러오기에 실패했을 때 true(카드가 「다시 시도」를 보인다). */
    var loadFailed by mutableStateOf(false)
        private set
    /** 체크·삭제·추가가 실패했을 때 카드 안에 보이는 짧은 줄. */
    var actionError by mutableStateOf<String?>(null)
        private set

    private var hasLoaded = false
    private var generation = UUID.randomUUID()

    suspend fun load(environment: AppEnvironment) {
        actionError = null
        if (environment.settings.isGuestMode) {
            // 체험 모드는 한 번만 견본을 깔고, 그 뒤 바꾼 내용을 지우지 않는다
            if (!hasLoaded) items = guestSampleTodos()
            hasLoaded = true
            loadFailed = false
            return
        }
        val generation = UUID.randomUUID()
        this.generation = generation
        isLoading = true
        try {
            val response = environment.api.todos()
            if (this.generation != generation) return
            items = response.items
            loadFailed = false
            hasLoaded = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (this.generation != generation) return
            // 이미 보이던 목록은 그대로 두고, 아직 한 번도 못 불렀을 때만 실패 화면을 보인다
            loadFailed = !hasLoaded
            if (hasLoaded) actionError = "할 일을 새로 불러오지 못했어요"
        } finally {
            if (this.generation == generation) isLoading = false
        }
    }

    suspend fun toggle(environment: AppEnvironment, todo: Todo) {
        val before = items
        val done = !todo.done
        actionError = null
        items = items.withDone(todo.id, done)
        if (environment.settings.isGuestMode) return
        try {
            items = items.replacing(environment.api.setTodoDone(todo.id, done))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            items = before
            actionError = "체크를 저장하지 못했어요"
        }
    }

    suspend fun delete(environment: AppEnvironment, todo: Todo) {
        val before = items
        actionError = null
        items = items.without(todo.id)
        if (environment.settings.isGuestMode) return
        try {
            environment.api.deleteTodo(todo.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            items = before
            actionError = "삭제하지 못했어요"
        }
    }

    /** 성공하면 true(입력칸을 비운다). 실패하면 글을 남겨 다시 누를 수 있게 한다. */
    suspend fun add(environment: AppEnvironment, rawTitle: String): Boolean {
        val title = todoTitleToSend(rawTitle)
        actionError = null
        if (environment.settings.isGuestMode) {
            items = items + Todo(id = "guest-${UUID.randomUUID()}", title = title, source = "USER", done = false, createdAt = "")
            return true
        }
        return try {
            items = items + environment.api.createTodo(title)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            actionError = "할 일을 추가하지 못했어요"
            false
        }
    }
}
