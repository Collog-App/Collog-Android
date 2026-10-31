package live.collog.app.shared.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import kotlinx.coroutines.CancellationException
import live.collog.app.config.AppEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.shared.models.PreviewQuestion
import live.collog.app.shared.services.api.ApiError

/**
 * iOS `FamilyStore`. iOS는 생성된 질문을 UserDefaults에 쓰지만 시작할 때마다 지우므로
 * 실제로는 메모리에만 남는다. 여기서는 처음부터 메모리에만 둔다.
 */
class FamilyStore {
    var contacts by mutableStateOf<List<FamilyContact>>(emptyList())
        private set
    var questions by mutableStateOf<List<PreviewQuestion>>(emptyList())
        private set
    var generatedQuestions by mutableStateOf<Map<String, List<PreviewQuestion>>>(emptyMap())
        private set
    var selectedContactId by mutableStateOf<String?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    private var refreshGeneration = UUID.randomUUID()

    val callableContacts: List<FamilyContact> get() = contacts.filter { it.isCallable }

    val selectedContact: FamilyContact?
        get() = contacts.firstOrNull { it.id == selectedContactId } ?: contacts.firstOrNull()

    val selectedQuestionTexts: List<String> get() = questions(selectedContact).map { it.text }

    fun questions(contact: FamilyContact?): List<PreviewQuestion> {
        val source = contact?.let { generatedQuestions[it.id] } ?: emptyList()
        return source.distinctBy { it.text }
    }

    fun selectContact(contact: FamilyContact) {
        selectedContactId = contact.id
        refreshGeneration = UUID.randomUUID()
    }

    suspend fun refresh(environment: AppEnvironment) {
        val generation = UUID.randomUUID()
        refreshGeneration = generation
        if (environment.settings.isGuestMode) {
            contacts = FamilyContact.samples
            questions = PreviewQuestion.samples
            generatedQuestions = generatedQuestions + contacts
                .filter { generatedQuestions[it.id] == null }
                .associate { it.id to questions }
            selectedContactId = selectedContact?.id
            return
        }
        val familyId = environment.session.familyId ?: run {
            reset()
            return
        }
        val userId = environment.session.user?.id
        try {
            // 역할 구분 없이 나를 뺀 연결된 가족 모두가 연락처다
            val members = environment.api.members(familyId).filter { it.isCallable && it.userId != userId }
            if (environment.session.user?.id != userId || refreshGeneration != generation) return
            contacts = members.map { FamilyContact(it, lastCallText = it.relationTitle) }
            if (contacts.none { it.id == selectedContactId }) selectedContactId = contacts.firstOrNull()?.id
            loadError = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (environment.session.user?.id != userId || refreshGeneration != generation) return
            if (error is ApiError.Server && error.status == 403 && dropRevokedFamily(environment, familyId)) {
                refresh(environment)
                return
            }
            loadError = error.message
            return
        }

        val contactId = selectedContactId
        // 오늘의 질문은 고른 가족(통화 상대)의 프로필로 만든다
        val parentId = selectedContact?.userId ?: return
        try {
            val remote = environment.api.dailyQuestions(parentId)
            if (environment.session.user?.id != userId || refreshGeneration != generation ||
                selectedContactId != contactId
            ) return
            questions = remote.map { PreviewQuestion(it.text) }
            if (contactId != null) generatedQuestions = generatedQuestions + (contactId to questions)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (environment.session.user?.id != userId || refreshGeneration != generation ||
                selectedContactId != contactId
            ) return
            questions = emptyList()
            if (contactId != null) generatedQuestions = generatedQuestions - contactId
            loadError = error.message
        }
    }

    fun reset() {
        refreshGeneration = UUID.randomUUID()
        contacts = emptyList()
        questions = emptyList()
        generatedQuestions = emptyMap()
        selectedContactId = null
        loadError = null
    }
}

/**
 * 서버가 [familyId] 를 막으면(가족을 만든 사람이 나를 구성원에서 삭제함) 지금 속한 가족 목록으로 세션을 맞춘다.
 * 남은 가족이 있으면 그 가족으로 옮기고, 없으면 가족 없음으로 되돌린다. 세션을 바꿨으면 true.
 */
internal suspend fun dropRevokedFamily(environment: AppEnvironment, familyId: String): Boolean {
    val available = try {
        environment.api.families()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        return false
    }
    if (environment.session.familyId != familyId || available.any { it.id == familyId }) return false
    val next = available.firstOrNull()
    if (next != null) environment.session.joinFamily(next.id) else environment.session.leaveFamily()
    return true
}
