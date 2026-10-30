package live.collog.app.features.auth.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import live.collog.app.config.AppEnvironment
import live.collog.app.features.auth.services.GoogleSignIn
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.LoginChallenge

/**
 * iOS `LoginViewModel`의 Google판. 서버 challenge를 미리 받아 두고 만료 10초 전에 새로 받는다.
 * 역할과 이름은 묻지 않는다: 이름은 Google 계정 이름을 보내고, 닉네임은 가입 정보 단계에서 정한다.
 */
class LoginViewModel {
    var isSubmitting by mutableStateOf(false)
        private set
    var isLoadingChallenge by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var challenge by mutableStateOf<LoginChallenge?>(null)
    private var challengeExpiresAt: Instant? = null
    private var preparationId = UUID.randomUUID()

    val canSignIn: Boolean get() = challenge != null && !isSubmitting && !isLoadingChallenge

    suspend fun prepareChallenge(environment: AppEnvironment, clearError: Boolean = true) {
        if (isSubmitting) return
        val preparationId = UUID.randomUUID()
        this.preparationId = preparationId
        if (clearError) errorMessage = null
        challenge = null
        challengeExpiresAt = null
        isLoadingChallenge = true
        try {
            val response = environment.api.googleLoginChallenge()
            if (this.preparationId != preparationId) return
            if (response.expiresIn <= 0 || response.nonce.isEmpty() || response.challengeId.isEmpty()) {
                throw ApiError.Decoding("Invalid Google login challenge")
            }
            challenge = response
            challengeExpiresAt = Instant.now().plusSeconds(response.expiresIn.toLong())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (this.preparationId == preparationId) errorMessage = error.message
        } finally {
            if (this.preparationId == preparationId) isLoadingChallenge = false
        }
    }

    suspend fun refreshChallengeIfNeeded(environment: AppEnvironment) {
        val expiry = challengeExpiresAt ?: return
        if (isSubmitting || expiry.isAfter(Instant.now().plusSeconds(10))) return
        prepareChallenge(environment, clearError = false)
    }

    /** 로그인에 성공하면 true. 취소하면 오류 없이 false. */
    suspend fun signIn(activityContext: Context, environment: AppEnvironment): Boolean {
        errorMessage = null
        val challenge = challenge
        val expiry = challengeExpiresAt
        if (challenge == null || expiry == null || !expiry.isAfter(Instant.now())) {
            this.challenge = null
            errorMessage = "로그인 준비 시간이 지났어요. 다시 시도해주세요."
            return false
        }
        isSubmitting = true
        try {
            val google = GoogleSignIn.requestIdToken(activityContext, challenge.nonce)
            val response = environment.api.loginWithGoogle(
                idToken = google.idToken,
                challengeId = challenge.challengeId,
                name = google.displayName?.trim()?.ifEmpty { null },
            )
            environment.session.apply(response)
            clearRequest()
            return true
        } catch (error: CancellationException) {
            clearRequest()
            throw error
        } catch (error: Exception) {
            if (error !is GoogleSignIn.Cancelled) errorMessage = error.message
            clearRequest()
            prepareChallenge(environment, clearError = false)
            return false
        }
    }

    private fun clearRequest() {
        challenge = null
        challengeExpiresAt = null
        isSubmitting = false
    }
}
