package live.collog.app.features.call.services

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.ApiQuestion
import live.collog.app.shared.services.api.CollogApi
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.QuestionTtsToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * iOS `RingingQuestionSpeaker`. 통화가 울리는 동안 오늘의 질문을 통화 음성 경로로 읽어 준다.
 * 메인 스레드에서 부른다.
 */
class RingingQuestionSpeaker(context: Context, private val http: OkHttpClient) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val attributes: AudioAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
    private val downloadHttp: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private var task: Job? = null
    private var generation = Any()
    private var socket: WebSocket? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null
    private val utterances = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    fun speak(questions: List<ApiQuestion>, callId: String, api: CollogApi, onEvent: (String) -> Unit) {
        stop()
        val generation = generation
        task = scope.launch {
            try {
                for (question in questions) {
                    if (!isActive || this@RingingQuestionSpeaker.generation !== generation) return@launch
                    try {
                        speakQuestion(question, callId, api, onEvent)
                        delay(QUESTION_GAP_MILLIS)
                    } catch (error: Exception) {
                        ensureActive()
                        onEvent(error.message ?: error.toString())
                        return@launch
                    }
                }
            } finally {
                if (this@RingingQuestionSpeaker.generation === generation) {
                    task = null
                    releaseTextToSpeech()
                }
            }
        }
    }

    fun stop() {
        generation = Any()
        task?.cancel()
        task = null
        socket?.cancel()
        socket = null
        player?.release()
        player = null
        releaseTextToSpeech()
    }

    private suspend fun speakQuestion(question: ApiQuestion, callId: String, api: CollogApi, onEvent: (String) -> Unit) {
        if (!question.usesRemoteAudio && question.ttsMode != "ELEVENLABS_DIRECT") {
            speakLocal(question.text)
            return
        }
        try {
            if (question.ttsMode == "ELEVENLABS_DIRECT") {
                val token = api.questionTtsToken(callId, question.id)
                currentCoroutineContext().ensureActive()
                playAudio(receiveAudio(question.text, token))
            } else {
                currentCoroutineContext().ensureActive()
                playAudio(downloadQuestionAudio(downloadHttp, question.ttsAssetUrl))
            }
        } catch (error: Exception) {
            // 우리 작업이 취소됐을 때만 끝낸다. 토큰 갱신 취소처럼 밖에서 온 취소는 iOS처럼 기기 음성으로 넘긴다.
            currentCoroutineContext().ensureActive()
            onEvent("질문 음성 다운로드 또는 재생 실패, 기기 음성으로 재생해요")
            speakLocal(question.text)
        }
    }

    private suspend fun receiveAudio(text: String, token: QuestionTtsToken): ByteArray {
        val request = Request.Builder().url(ElevenLabsStream.url(token)).build()
        val messages = Channel<String>(Channel.UNLIMITED)
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                messages.trySend(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                messages.trySend(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                messages.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                messages.close(t)
            }
        })
        this.socket = socket
        var received = false
        try {
            val audio = withTimeoutOrNull(STREAM_TIMEOUT_MILLIS) {
                for (message in ElevenLabsStream.messages(text)) {
                    if (!socket.send(message)) throw ApiError.Transport("질문 음성을 생성하지 못했어요")
                }
                ElevenLabsStream.collect(messages)
            } ?: throw ApiError.Transport("질문 음성을 생성하지 못했어요")
            received = true
            return audio
        } finally {
            // 다 받았으면 정상 종료, 아니면 남은 전송까지 버리고 바로 끊는다
            if (received) socket.close(NORMAL_CLOSURE, null) else socket.cancel()
            if (this.socket === socket) this.socket = null
        }
    }

    // iOS AVAudioPlayer(data:)처럼 메모리의 MP3를 재생한다. MediaPlayer가 파일을 읽도록 임시 파일을 거친다.
    private suspend fun playAudio(data: ByteArray) {
        currentCoroutineContext().ensureActive()
        val directory = File(context.cacheDir, AUDIO_DIRECTORY)
        val file = File(directory, "${UUID.randomUUID()}.mp3")
        val player = MediaPlayer()
        this.player = player
        try {
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                directory.listFiles()?.forEach { it.delete() }
                file.writeBytes(data)
            }
            val prepared = CompletableDeferred<Boolean>()
            val finished = CompletableDeferred<Boolean>()
            player.setAudioAttributes(attributes)
            player.setOnPreparedListener { prepared.complete(true) }
            player.setOnCompletionListener { finished.complete(true) }
            player.setOnErrorListener { _, _, _ ->
                prepared.complete(false)
                finished.complete(false)
                true
            }
            try {
                player.setDataSource(file.path)
                player.prepareAsync()
            } catch (error: IOException) {
                throw ApiError.Transport("질문 음성을 재생하지 못했어요")
            } catch (error: IllegalStateException) {
                throw ApiError.Transport("질문 음성을 재생하지 못했어요")
            }
            if (withTimeoutOrNull(PLAYBACK_CAP_MILLIS) { prepared.await() } != true) {
                throw ApiError.Transport("질문 음성을 재생하지 못했어요")
            }
            player.start()
            val duration = max(player.duration, 0) / 1_000.0
            val deadline = (min(max(duration + 3, 3.0), 120.0) * 1_000).toLong()
            val result = withTimeoutOrNull(deadline) { finished.await() }
                ?: throw ApiError.Transport("질문 음성 재생 시간이 초과됐어요")
            currentCoroutineContext().ensureActive()
            if (!result) throw ApiError.Transport("질문 음성 재생이 중단됐어요")
        } finally {
            if (this.player === player) {
                this.player = null
                player.release()
            }
            file.delete()
        }
    }

    private suspend fun speakLocal(text: String) {
        currentCoroutineContext().ensureActive()
        val engine = koreanTextToSpeech()
            ?: throw ApiError.Transport("휴대폰에 한국어 음성을 설치한 뒤 다시 시도해주세요")
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        utterances[id] = done
        try {
            // 읽다가 멈추거나 오류가 나면 iOS의 didCancel처럼 끝난 것으로 본다
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) {
                throw ApiError.Transport("기기 음성을 재생하지 못했어요")
            }
            if (withTimeoutOrNull(PLAYBACK_CAP_MILLIS) { done.await() } == null) {
                engine.stop()
                throw ApiError.Transport("기기 음성 재생 시간이 초과됐어요")
            }
        } finally {
            utterances.remove(id)
        }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun koreanTextToSpeech(): TextToSpeech? {
        val engine = tts ?: run {
            val ready = CompletableDeferred<Boolean>()
            TextToSpeech(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }.also {
                it.setAudioAttributes(attributes)
                it.setOnUtteranceProgressListener(utteranceListener)
                tts = it
                ttsReady = ready
            }
        }
        val ready = ttsReady ?: return null
        if (withTimeoutOrNull(PLAYBACK_CAP_MILLIS) { ready.await() } != true) return null
        val language = engine.setLanguage(Locale.KOREA)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) return null
        return engine
    }

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) = finish(utteranceId)

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish(utteranceId)

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)

        override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)

        private fun finish(utteranceId: String?) {
            utteranceId?.let { utterances.remove(it)?.complete(Unit) }
        }
    }

    private fun releaseTextToSpeech() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = null
    }

    private companion object {
        const val QUESTION_GAP_MILLIS = 700L
        const val PLAYBACK_CAP_MILLIS = 120_000L
        const val STREAM_TIMEOUT_MILLIS = 15_000L
        const val DOWNLOAD_TIMEOUT_SECONDS = 8L
        const val AUDIO_DIRECTORY = "ringing-question-audio"
        const val NORMAL_CLOSURE = 1000
    }
}

/** iOS `RingingQuestionSpeaker.receiveAudio`의 주소·메시지·응답 해석 부분. */
internal object ElevenLabsStream {
    const val MAX_AUDIO_BYTES = 8 * 1024 * 1024

    @Serializable
    private class Output(
        val audio: String? = null,
        val isFinal: Boolean? = null,
        @SerialName("is_final") val snakeCaseFinal: Boolean? = null,
        val error: String? = null,
    )

    /** wss 주소. OkHttp 웹소켓은 https 주소를 받아 wss로 연결한다. */
    fun url(token: QuestionTtsToken): HttpUrl {
        val voiceIdValid = token.voiceId.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        if (token.token.isEmpty() || token.voiceId.isEmpty() || !voiceIdValid || !token.outputFormat.startsWith("mp3_")) {
            throw ApiError.Transport("질문 음성 설정을 확인해주세요")
        }
        return HttpUrl.Builder()
            .scheme("https")
            .host("api.elevenlabs.io")
            .addPathSegments("v1/text-to-speech/${token.voiceId}/stream-input")
            .addQueryParameter("single_use_token", token.token)
            .addQueryParameter("model_id", token.modelId)
            .addQueryParameter("output_format", token.outputFormat)
            .addQueryParameter("auto_mode", "true")
            .apply { if (token.modelId != "eleven_multilingual_v2") addQueryParameter("language_code", "ko") }
            .build()
    }

    fun messages(text: String): List<String> =
        listOf(" ", "$text ", "").map { buildJsonObject { put("text", it) }.toString() }

    /** 마지막 조각까지 받아 이어 붙인 MP3를 돌려준다. */
    suspend fun collect(messages: ReceiveChannel<String>): ByteArray {
        val audio = ByteArrayOutputStream()
        while (true) {
            if (append(messages.receive(), audio)) return audio.toByteArray()
        }
    }

    /** 조각 하나를 [audio]에 붙인다. 마지막 조각이면 true. */
    fun append(message: String, audio: ByteArrayOutputStream): Boolean {
        if (message.length > MAX_AUDIO_BYTES) throw ApiError.Transport("질문 음성 데이터가 올바르지 않아요")
        val chunk = try {
            CollogJson.decodeFromString(Output.serializer(), message)
        } catch (error: IllegalArgumentException) {
            throw ApiError.Transport("질문 음성 응답을 이해하지 못했어요")
        }
        if (chunk.error != null) throw ApiError.Transport("질문 음성을 생성하지 못했어요")
        val encoded = chunk.audio
        if (!encoded.isNullOrEmpty()) {
            val bytes = try {
                Base64.getDecoder().decode(encoded)
            } catch (error: IllegalArgumentException) {
                null
            }
            if (bytes == null || audio.size() + bytes.size > MAX_AUDIO_BYTES) {
                throw ApiError.Transport("질문 음성 데이터가 올바르지 않아요")
            }
            audio.write(bytes)
        }
        if ((chunk.isFinal ?: chunk.snakeCaseFinal) != true) return false
        if (audio.size() == 0) throw ApiError.Transport("질문 음성이 비어 있어요")
        return true
    }
}

/** iOS `RingingQuestionSpeaker.playRemote`의 내려받기 부분. http(s)만, 8 MiB까지 받는다. */
internal suspend fun downloadQuestionAudio(http: OkHttpClient, address: String?): ByteArray {
    val url = address?.toHttpUrlOrNull() ?: throw ApiError.Transport("질문 음성 주소가 올바르지 않아요")
    val call = http.newCall(Request.Builder().url(url).build())
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching { response.use(::readQuestionAudio) })
            }
        })
    }
}

private fun readQuestionAudio(response: Response): ByteArray {
    val failure = ApiError.Transport("질문 음성을 다운로드하지 못했어요")
    if (!response.isSuccessful || response.body.contentLength() > ElevenLabsStream.MAX_AUDIO_BYTES) throw failure
    val source = response.body.source()
    if (source.request(ElevenLabsStream.MAX_AUDIO_BYTES + 1L)) throw failure
    val data = source.readByteArray()
    if (data.isEmpty()) throw failure
    return data
}
