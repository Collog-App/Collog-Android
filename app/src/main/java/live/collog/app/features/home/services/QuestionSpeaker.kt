package live.collog.app.features.home.services

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 홈 「통화 시작 전 질문 듣기」(피그마 269:4982): 질문을 기기 한국어 음성으로 차례로 읽는다.
 * 음성 엔진은 처음 누를 때 만들고, 화면이 사라지면 [release]로 닫는다.
 */
class QuestionSpeaker(private val context: Context) {
    var isSpeaking by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var isReady = false
    private var pending: List<String> = emptyList()

    fun toggle(questions: List<String>) {
        if (isSpeaking) stop() else speak(questions)
    }

    fun stop() {
        isSpeaking = false
        pending = emptyList()
        engine?.stop()
    }

    fun release() {
        stop()
        engine?.shutdown()
        engine = null
        isReady = false
    }

    private fun speak(questions: List<String>) {
        if (questions.isEmpty()) return
        error = null
        isSpeaking = true
        val current = engine
        if (current != null && isReady) {
            enqueue(current, questions)
            return
        }
        pending = questions
        if (current == null) {
            engine = TextToSpeech(context.applicationContext) { status -> main.post { onInit(status) } }
                .also { it.setOnUtteranceProgressListener(listener) }
        }
    }

    private fun onInit(status: Int) {
        val current = engine ?: return
        val language = if (status == TextToSpeech.SUCCESS) current.setLanguage(Locale.KOREA) else TextToSpeech.LANG_NOT_SUPPORTED
        isReady = language >= TextToSpeech.LANG_AVAILABLE
        if (!isReady) {
            isSpeaking = false
            error = "휴대폰에 한국어 음성을 설치한 뒤 다시 시도해주세요"
        } else if (isSpeaking) {
            enqueue(current, pending)
        }
        pending = emptyList()
    }

    private fun enqueue(engine: TextToSpeech, questions: List<String>) {
        questions.forEachIndexed { index, text ->
            val isLast = index == questions.lastIndex
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engine.speak(text, mode, null, if (isLast) LAST_UTTERANCE else "question-$index") == TextToSpeech.ERROR) {
                stop()
                error = "기기 음성을 재생하지 못했어요"
                return
            }
            if (!isLast) engine.playSilentUtterance(QUESTION_GAP_MILLIS, TextToSpeech.QUEUE_ADD, "gap-$index")
        }
    }

    // 엔진이 자기 스레드에서 부르므로 상태는 메인 스레드에서 바꾼다
    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            if (utteranceId == LAST_UTTERANCE) main.post { isSpeaking = false }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            main.post { stop() }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            main.post { stop() }
        }
    }

    private companion object {
        const val LAST_UTTERANCE = "question-last"
        const val QUESTION_GAP_MILLIS = 700L
    }
}
