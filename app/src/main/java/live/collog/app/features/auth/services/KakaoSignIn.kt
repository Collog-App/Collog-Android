package live.collog.app.features.auth.services

import android.content.Context
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import live.collog.app.BuildConfig
import live.collog.app.shared.services.api.ApiError

/**
 * 카카오 SDK 로그인으로 OpenID Connect ID 토큰을 받는다. 서버가 준 nonce 를 토큰에 넣어 서버가 대조한다.
 * 카카오톡이 있으면 카카오톡으로, 없으면 카카오계정(웹)으로 로그인한다.
 */
object KakaoSignIn {
    class Cancelled : Exception()

    val isConfigured: Boolean get() = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()

    fun init(context: Context) {
        if (isConfigured) KakaoSdk.init(context, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }

    /** [activityContext]는 Activity여야 카카오톡·카카오계정 화면이 뜬다. */
    suspend fun requestIdToken(activityContext: Context, nonce: String): String {
        if (!isConfigured) throw ApiError.Transport("카카오 로그인 설정이 아직 없어요. 관리자에게 알려주세요.")
        val client = UserApiClient.instance
        val token = if (client.isKakaoTalkLoginAvailable(activityContext)) {
            try {
                login { callback -> client.loginWithKakaoTalk(activityContext, nonce = nonce, callback = callback) }
            } catch (error: Cancelled) {
                throw error
            } catch (error: Exception) {
                // 카카오톡에 로그인돼 있지 않은 등 카카오톡 로그인이 안 되면 카카오계정으로 다시 시도한다
                login { callback -> client.loginWithKakaoAccount(activityContext, nonce = nonce, callback = callback) }
            }
        } else {
            login { callback -> client.loginWithKakaoAccount(activityContext, nonce = nonce, callback = callback) }
        }
        // 콘솔에서 OpenID Connect 를 켜야 ID 토큰이 온다
        return token.idToken ?: throw ApiError.Transport("카카오 로그인 설정을 확인하지 못했어요. 관리자에게 알려주세요.")
    }

    /** 로그아웃 때 부른다. 실패해도 앱 로그아웃은 그대로 진행한다. */
    fun clear() {
        if (isConfigured) runCatching { UserApiClient.instance.logout { } }
    }

    private suspend fun login(start: ((OAuthToken?, Throwable?) -> Unit) -> Unit): OAuthToken =
        suspendCancellableCoroutine { continuation ->
            start { token, error ->
                when {
                    error is ClientError && error.reason == ClientErrorCause.Cancelled ->
                        continuation.resumeWithException(Cancelled())
                    error != null -> continuation.resumeWithException(
                        ApiError.Transport("카카오 로그인을 시작하지 못했어요. 다시 시도해주세요."),
                    )
                    token == null -> continuation.resumeWithException(
                        ApiError.Transport("카카오 로그인 정보를 확인하지 못했어요. 다시 시도해주세요."),
                    )
                    else -> continuation.resume(token)
                }
            }
        }
}
