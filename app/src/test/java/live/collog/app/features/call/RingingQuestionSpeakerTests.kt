package live.collog.app.features.call

import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import live.collog.app.features.call.services.ElevenLabsStream
import live.collog.app.features.call.services.downloadQuestionAudio
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.QuestionTtsToken
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** iOS `RingingQuestionSpeaker` 중 Android 런타임 없이 확인할 수 있는 부분(주소·메시지·조각 해석·내려받기). */
class RingingQuestionSpeakerTests {
    private val token = QuestionTtsToken(
        token = "single-use",
        voiceId = "Voice123",
        modelId = "eleven_flash_v2_5",
        outputFormat = "mp3_44100_128",
    )

    @Test
    fun streamUrlCarriesTokenAndKoreanLanguageRule() {
        val url = ElevenLabsStream.url(token)

        assertEquals("api.elevenlabs.io", url.host)
        assertEquals("/v1/text-to-speech/Voice123/stream-input", url.encodedPath)
        assertEquals(
            listOf("single_use_token", "model_id", "output_format", "auto_mode", "language_code"),
            url.queryParameterNames.toList(),
        )
        assertEquals("single-use", url.queryParameter("single_use_token"))
        assertEquals("eleven_flash_v2_5", url.queryParameter("model_id"))
        assertEquals("mp3_44100_128", url.queryParameter("output_format"))
        assertEquals("true", url.queryParameter("auto_mode"))
        assertEquals("ko", url.queryParameter("language_code"))

        val multilingual = ElevenLabsStream.url(token.copy(modelId = "eleven_multilingual_v2"))
        assertNull(multilingual.queryParameter("language_code"))
    }

    @Test
    fun streamUrlRejectsUnsafeSettings() {
        val invalid = listOf(
            token.copy(token = ""),
            token.copy(voiceId = ""),
            token.copy(voiceId = "voice/../x"),
            token.copy(voiceId = "목소리"),
            token.copy(outputFormat = "pcm_16000"),
        )
        for (setting in invalid) {
            val error = assertThrows(ApiError.Transport::class.java) { ElevenLabsStream.url(setting) }
            assertEquals("질문 음성 설정을 확인해주세요", error.message)
        }
    }

    @Test
    fun sendsThreeTextMessagesLikeIos() {
        assertEquals(
            listOf("""{"text":" "}""", """{"text":"오늘 기분 어떠세요? "}""", """{"text":""}"""),
            ElevenLabsStream.messages("오늘 기분 어떠세요?"),
        )
    }

    @Test
    fun collectJoinsBase64ChunksUntilEitherFinalKey() = runBlocking {
        val camel = Channel<String>(Channel.UNLIMITED).apply {
            trySend("""{"audio":"${encode("ab")}","alignment":{"chars":["a"]}}""")
            trySend("""{"audio":"","isFinal":null}""")
            trySend("""{"audio":"${encode("cd")}"}""")
            trySend("""{"isFinal":true}""")
            trySend("""{"audio":"${encode("late")}"}""")
        }
        assertArrayEquals("abcd".toByteArray(), ElevenLabsStream.collect(camel))

        val snake = Channel<String>(Channel.UNLIMITED).apply {
            trySend("""{"audio":"${encode("xy")}","isFinal":null,"is_final":true}""")
        }
        assertArrayEquals("xy".toByteArray(), ElevenLabsStream.collect(snake))
    }

    @Test
    fun badChunksFailWithIosMessages() {
        fun failure(vararg messages: String): String? {
            val audio = ByteArrayOutputStream()
            return assertThrows(ApiError.Transport::class.java) {
                messages.forEach { ElevenLabsStream.append(it, audio) }
            }.message
        }

        assertEquals("질문 음성을 생성하지 못했어요", failure("""{"error":"quota_exceeded"}"""))
        assertEquals("질문 음성 데이터가 올바르지 않아요", failure("""{"audio":"not base64!"}"""))
        assertEquals("질문 음성이 비어 있어요", failure("""{"isFinal":true}"""))
        assertEquals("질문 음성 응답을 이해하지 못했어요", failure("not json"))
        assertEquals("질문 음성 응답을 이해하지 못했어요", failure("""{"isFinal":"yes"}"""))

        val half = encode(String(CharArray(5 * 1024 * 1024) { 'a' }))
        assertEquals("질문 음성 데이터가 올바르지 않아요", failure("""{"audio":"$half"}""", """{"audio":"$half"}"""))
    }

    @Test
    fun socketClosedBeforeFinalChunkFails() {
        val messages = Channel<String>(Channel.UNLIMITED).apply {
            trySend("""{"audio":"${encode("ab")}"}""")
            close()
        }
        assertThrows(ClosedReceiveChannelException::class.java) {
            runBlocking { ElevenLabsStream.collect(messages) }
        }
    }

    @Test
    fun downloadAcceptsOnlyHttpWithinLimit() = runBlocking {
        assertArrayEquals("mp3".toByteArray(), downloadQuestionAudio(client(200, "mp3".toByteArray()), "https://cdn.test/q.mp3"))

        val limit = ElevenLabsStream.MAX_AUDIO_BYTES
        assertEquals(limit, downloadQuestionAudio(client(200, ByteArray(limit), knownLength = false), "https://cdn.test/q.mp3").size)
    }

    @Test
    fun downloadFailuresUseIosMessages() {
        fun failure(address: String?, status: Int = 200, body: ByteArray = "mp3".toByteArray(), knownLength: Boolean = true) =
            assertThrows(ApiError.Transport::class.java) {
                runBlocking { downloadQuestionAudio(client(status, body, knownLength), address) }
            }.message

        val tooLarge = ByteArray(ElevenLabsStream.MAX_AUDIO_BYTES + 1)
        assertEquals("질문 음성 주소가 올바르지 않아요", failure(null))
        assertEquals("질문 음성 주소가 올바르지 않아요", failure("ftp://cdn.test/q.mp3"))
        assertEquals("질문 음성 주소가 올바르지 않아요", failure("not a url"))
        assertEquals("질문 음성을 다운로드하지 못했어요", failure("https://cdn.test/q.mp3", status = 404))
        assertEquals("질문 음성을 다운로드하지 못했어요", failure("https://cdn.test/q.mp3", body = ByteArray(0)))
        assertEquals("질문 음성을 다운로드하지 못했어요", failure("https://cdn.test/q.mp3", body = tooLarge))
        assertEquals("질문 음성을 다운로드하지 못했어요", failure("https://cdn.test/q.mp3", body = tooLarge, knownLength = false))
    }

    private fun encode(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())

    private fun client(status: Int, body: ByteArray, knownLength: Boolean = true): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val media = "audio/mpeg".toMediaType()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("fixture")
                    .body(if (knownLength) body.toResponseBody(media) else UnknownLengthBody(body, media))
                    .build()
            })
            .build()

    private class UnknownLengthBody(bytes: ByteArray, private val media: MediaType) : ResponseBody() {
        private val source = Buffer().write(bytes)
        override fun contentType(): MediaType = media
        override fun contentLength(): Long = -1
        override fun source(): BufferedSource = source
    }
}
