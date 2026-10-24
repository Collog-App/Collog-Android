package live.collog.app.features.auth.services

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import live.collog.app.BuildConfig
import live.collog.app.shared.services.api.ApiError

/** Credential Manager로 Google ID 토큰을 받는다. 서버가 준 nonce를 토큰에 넣어 서버가 대조한다. */
object GoogleSignIn {
    data class Result(val idToken: String, val displayName: String?)

    class Cancelled : Exception()

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** [activityContext]는 Activity여야 계정 선택 창이 뜬다. */
    suspend fun requestIdToken(activityContext: Context, nonce: String): Result {
        if (!isConfigured) throw ApiError.Transport("Google 로그인 설정이 아직 없어요. 관리자에게 알려주세요.")
        val option = GetSignInWithGoogleOption.Builder(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request)
        } catch (error: GetCredentialCancellationException) {
            throw Cancelled()
        } catch (error: NoCredentialException) {
            throw ApiError.Transport("이 기기에서 사용할 Google 계정을 찾지 못했어요. 계정을 추가한 뒤 다시 시도해주세요.")
        } catch (error: GetCredentialException) {
            throw ApiError.Transport("Google 로그인을 시작하지 못했어요. 다시 시도해주세요.")
        }
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw ApiError.Transport("Google 로그인 정보를 확인하지 못했어요. 다시 시도해주세요.")
        }
        val google = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (error: GoogleIdTokenParsingException) {
            throw ApiError.Transport("Google 로그인 정보를 확인하지 못했어요. 다시 시도해주세요.")
        }
        return Result(idToken = google.idToken, displayName = google.displayName)
    }

    /** 로그아웃 때 부른다. 다음 로그인에서 계정 선택 창이 다시 뜬다. */
    suspend fun clear(context: Context) {
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
