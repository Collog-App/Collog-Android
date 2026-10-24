package live.collog.app.features.auth.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import live.collog.app.config.AppEnvironment
import live.collog.app.shared.models.UserRoleOption

/** iOS `AuthFlowViewModel`: 온보딩 → 로그인 → 초대 → 동의 → 건강 프로필 → 홈. */
class AuthFlowViewModel {
    enum class Step { Launching, Onboarding, Login, Invitation, Consent, Profile, Ready }

    var step by mutableStateOf(Step.Launching)
        private set

    fun advance(step: Step) {
        this.step = step
    }

    suspend fun resolve(environment: AppEnvironment) {
        if (!environment.settings.onboardingCompleted) {
            step = Step.Onboarding
            return
        }
        if (environment.settings.isGuestMode) {
            step = Step.Ready
            return
        }
        val user = environment.session.user
        if (!environment.session.isAuthenticated || user == null) {
            step = Step.Login
            return
        }
        if (user.role == UserRoleOption.Parent.rawValue && user.familyId == null) {
            step = Step.Invitation
            return
        }

        try {
            val consent = environment.api.myConsent()
            if (environment.session.user != user || environment.settings.isGuestMode) return
            if (!consent.isCurrent) {
                step = Step.Consent
                return
            }
            if (!consent.isGranted || user.role != UserRoleOption.Parent.rawValue) {
                step = Step.Ready
                return
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (environment.session.user != user || environment.settings.isGuestMode) return
            step = Step.Consent
            return
        }

        try {
            val profile = environment.api.profile(user.id)
            if (environment.session.user != user || environment.settings.isGuestMode) return
            step = if (profile.hasCompletedSetup) Step.Ready else Step.Profile
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (environment.session.user != user || environment.settings.isGuestMode) return
            step = Step.Ready
        }
    }
}
