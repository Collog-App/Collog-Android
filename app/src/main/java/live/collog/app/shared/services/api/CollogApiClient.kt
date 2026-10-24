package live.collog.app.shared.services.api

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

val CollogJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** iOS `CollogAPIClient`가 401 재시도에 쓰는 `AuthSession`의 부분. */
interface SessionAuthentication {
    val accessToken: String?
    val currentUserId: String?
    val isAuthenticated: Boolean
    suspend fun refresh(baseUrl: String): String
}

class CollogApiClient(
    val baseUrl: String,
    private val accessToken: String? = null,
    private val http: OkHttpClient = DefaultHttp,
    private val authentication: SessionAuthentication? = null,
) {
    suspend inline fun <reified T> send(endpoint: ApiEndpoint): T = send(endpoint, serializer<T>())

    suspend fun <T> send(endpoint: ApiEndpoint, deserializer: DeserializationStrategy<T>): T {
        val data = sendRaw(endpoint)
        return try {
            CollogJson.decodeFromString(deserializer, data)
        } catch (error: Exception) {
            throw ApiError.Decoding(error.toString())
        }
    }

    suspend fun sendRaw(endpoint: ApiEndpoint): String {
        val originalToken = if (authentication == null) accessToken else authentication.accessToken
        val originalUserId = authentication?.currentUserId
        return try {
            sendRaw(endpoint, originalToken)
        } catch (error: ApiError.Unauthenticated) {
            if (!endpoint.requiresAuth) throw error
            val authentication = authentication ?: throw error
            if (!authentication.isAuthenticated) throw error
            if (authentication.currentUserId != originalUserId) throw error
            val current = authentication.accessToken
            val token = if (current != null && current != originalToken) {
                current
            } else {
                authentication.refresh(baseUrl)
            }
            sendRaw(endpoint, token)
        }
    }

    private suspend fun sendRaw(endpoint: ApiEndpoint, token: String?): String {
        val url = (baseUrl.trimEnd('/') + endpoint.path).toHttpUrlOrNull()?.newBuilder()?.apply {
            endpoint.query.forEach { (name, value) -> addQueryParameter(name, value) }
        }?.build() ?: throw ApiError.Transport("주소를 만들 수 없어요")

        val builder = Request.Builder().url(url)
        if (endpoint.requiresAuth) {
            token ?: throw ApiError.Unauthenticated
            builder.header("Authorization", "Bearer $token")
        }
        val body = endpoint.body?.let { encode(it).toRequestBody(JsonMedia) }
        builder.method(endpoint.method.name, body ?: emptyBodyFor(endpoint.method))
        val call = http.newCall(builder.build())
        call.timeout().timeout(endpoint.timeoutSeconds, TimeUnit.SECONDS)
        return perform(call)
    }

    suspend fun upload(file: File, url: String, contentType: String) {
        val target = url.toHttpUrlOrNull() ?: throw ApiError.Transport("업로드 주소가 올바르지 않아요")
        val request = Request.Builder()
            .url(target)
            .put(file.asRequestBody(contentType.toMediaType()))
            .header("Content-Type", contentType)
            .build()
        perform(http.newCall(request))
    }

    private suspend fun perform(call: Call): String {
        val response = try {
            call.await()
        } catch (error: IOException) {
            throw transportError(error)
        }
        return response.use {
            val data = it.body.string()
            validate(it.code, data)
            data
        }
    }

    companion object {
        private val JsonMedia = "application/json".toMediaType()

        val DefaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

        @Suppress("UNCHECKED_CAST")
        private fun encode(body: ApiBody<*>): String =
            CollogJson.encodeToString((body as ApiBody<Any?>).serializer, body.value)

        private fun emptyBodyFor(method: HttpMethod) = when (method) {
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH -> ByteArray(0).toRequestBody(null)
            else -> null
        }

        internal fun transportError(error: IOException): ApiError = when (error) {
            is SocketTimeoutException ->
                ApiError.Transport("서버 응답 시간이 초과됐어요. 잠시 후 다시 시도해주세요.")
            is UnknownHostException, is ConnectException, is NoRouteToHostException ->
                ApiError.Transport("서버에 접속하지 못했어요. 인터넷 상태를 확인한 뒤 다시 시도해주세요.")
            is SSLException ->
                ApiError.Transport("보안 통신을 시작하지 못했어요. 잠시 후 다시 시도해주세요.")
            is InterruptedIOException ->
                ApiError.Transport("서버 응답 시간이 초과됐어요. 잠시 후 다시 시도해주세요.")
            else -> ApiError.Transport(error.message ?: "요청을 처리하지 못했어요")
        }

        internal fun validate(status: Int, data: String) {
            if (status in 200 until 300) return
            if (status == 401) throw ApiError.Unauthenticated
            val body = runCatching { CollogJson.decodeFromString<ApiErrorBody>(data) }.getOrNull()
            throw ApiError.Server(
                status = status,
                code = body?.code ?: "REQUEST_FAILED",
                serverMessage = body?.message ?: "요청을 처리하지 못했어요",
            )
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
}
