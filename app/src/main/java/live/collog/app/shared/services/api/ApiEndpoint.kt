package live.collog.app.shared.services.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

/** iOS `APIEndpoint`. body는 직렬화기와 값 쌍으로 들고 다닌다. */
data class ApiEndpoint(
    val path: String,
    val method: HttpMethod = HttpMethod.GET,
    val query: List<Pair<String, String>> = emptyList(),
    val body: ApiBody<*>? = null,
    val requiresAuth: Boolean = true,
    val timeoutSeconds: Long = 60,
)

class ApiBody<T>(val value: T, val serializer: KSerializer<T>)

sealed class ApiError(override val message: String) : Exception(message) {
    data object Unauthenticated : ApiError("로그인이 필요해요")

    data class Server(val status: Int, val code: String, val serverMessage: String) : ApiError(serverMessage)

    data class Transport(val description: String) : ApiError(description)

    data class Decoding(val detail: String) : ApiError("서버 응답을 이해하지 못했어요")

    val isConsentRequired: Boolean get() = this is Server && code == "CONSENT_REQUIRED"

    /**
     * 결제가 켜져 있고 보는 사람의 가족이 무료라 리포트가 막혔다(402 `PREMIUM_REQUIRED`). 403(가족이 공유를 껐다)과 섞지 않는다.
     * 코드가 빠져 와도(서버 기본값 `REQUEST_FAILED`) 402 는 이 뜻 하나라 상태 코드로만 가른다.
     */
    val isPremiumRequired: Boolean get() = this is Server && status == 402

    companion object {
        /** 요청이 403 으로 막혔는지(`first`), 열렸다면 그 결과까지(`second`). 다른 실패(연결·서버 오류)로는 알 수 없어 null. iOS `APIError.accessCheck`. */
        suspend fun <T> accessCheck(request: suspend () -> T): Pair<Boolean, T?>? = try {
            false to request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Server) {
            if (e.status == 403) true to null else null
        } catch (e: Exception) {
            null
        }
    }
}

@Serializable
data class ApiErrorBody(val code: String, val message: String)
