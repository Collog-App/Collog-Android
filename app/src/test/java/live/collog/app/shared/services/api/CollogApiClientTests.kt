package live.collog.app.shared.services.api

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CollogApiClientTests {
    @Test
    fun responseBodyIsReadOffTheCallingThread() {
        // 실제 기기에서는 다 받지 못한 본문을 메인에서 읽으면 NetworkOnMainThreadException(메시지 없음)이 나서
        // 홈이 통화 기록을 「아직 통화 기록이 없어요」로 보였다(10-27).
        var readThread: String? = null
        val body = object : ResponseBody() {
            override fun contentType(): MediaType = "application/json".toMediaType()
            override fun contentLength(): Long = -1
            override fun source(): BufferedSource {
                readThread = Thread.currentThread().name
                return Buffer().writeUtf8("""{"calls":[]}""")
            }
        }
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("fixture")
                    .body(body)
                    .build()
            }
            .build()
        val api = CollogApi(CollogApiClient("https://fixture.test", accessToken = "token", http = http))
        val main = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }.asCoroutineDispatcher()
        try {
            val calls = runBlocking { withContext(main) { api.calls("parent") } }
            assertEquals(0, calls.size)
            // 테스트에서는 스레드 이름 뒤에 코루틴 이름(" @coroutine#1")이 붙는다
            assertFalse(readThread.orEmpty().startsWith("fake-main"))
        } finally {
            main.close()
        }
    }
}
