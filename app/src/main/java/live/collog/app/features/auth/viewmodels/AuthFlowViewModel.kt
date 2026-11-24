package live.collog.app.features.auth.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import live.collog.app.config.AppEnvironment
import live.collog.app.shared.services.AppEventCode
import live.collog.app.shared.services.api.ConsentRecordDto

/**
 * 피그마 「최종본」 가입 흐름: 시작 장(새 피그마 19) → 온보딩(로그인) → 약관 → (PASS, 꺼 둠) → 가입 정보 → 나의 질환 → 홈.
 * 역할 구분 없이 모두 같은 길로 간다. 이미 가입한 사람은 동의가 낡았을 때만 약관을 다시 본다.
 */
class AuthFlowViewModel {
    enum class Step { Launching, Intro, Onboarding, Terms, Pass, SignUp, Conditions, Ready }

    var step by mutableStateOf(Step.Launching)
        private set

    fun advance(step: Step) {
        this.step = step
    }

    suspend fun resolve(environment: AppEnvironment) {
        if (environment.settings.isGuestMode) {
            step = Step.Ready
            return
        }
        val user = environment.session.user
        if (!environment.session.isAuthenticated || user == null) {
            step = Step.Intro
            return
        }

        val consent: ConsentRecordDto? = try {
            environment.api.myConsent()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // 404 = 아직 동의 기록이 없다. 다른 실패도 약관으로 보낸다(그 화면이 문서를 불러 오류와 다시 시도를 보인다).
            null
        }
        if (environment.session.user != user || environment.settings.isGuestMode) return
        if (consent == null || !consent.isCurrent) {
            step = Step.Terms
            return
        }
        if (!user.signupCompleted) {
            // 질환 저장(PUT 프로필)은 본인 분석 동의가 있어야 하므로 가입은 동의부터 받는다
            step = if (consent.isGranted) Step.SignUp else Step.Terms
            return
        }
        // 설정에서 동의를 철회한 사람도 앱은 쓴다. 질환은 동의 없이 저장할 수 없으니 묻지 않는다.
        if (!consent.isGranted) {
            step = Step.Ready
            return
        }

        try {
            val profile = environment.api.profile(user.id)
            if (environment.session.user != user || environment.settings.isGuestMode) return
            step = if (profile.hasCompletedSetup) Step.Ready else Step.Conditions
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (environment.session.user != user || environment.settings.isGuestMode) return
            step = Step.Ready
        }
    }

    companion object {
        /**
         * 이용 기록 `onboarding_step` 의 단계 이름: [previous]에서 [current]로 넘어갈 때 남길 것, 없으면 null.
         * 시작 장 = WELCOME, 온보딩(로그인) = LOGIN, 약관(분석·외부 AI 동의를 같은 화면에서 받는다) = TERMS,
         * 회원가입 정보 = PROFILE, 나의 질환 = CONDITIONS, 가입 정보·질환을 마치고 홈에 들어옴 = DONE.
         * PASS(꺼 둠)는 남기지 않고, 따로 된 AI 동의·초대 코드 단계는 이 앱의 가입 흐름에 없다.
         */
        fun onboardingStep(previous: Step, current: Step): String? = when (current) {
            Step.Intro -> AppEventCode.WELCOME
            Step.Onboarding -> AppEventCode.LOGIN
            Step.Terms -> AppEventCode.TERMS
            Step.SignUp -> AppEventCode.PROFILE
            Step.Conditions -> AppEventCode.CONDITIONS
            Step.Ready -> if (previous == Step.SignUp || previous == Step.Conditions) AppEventCode.DONE else null
            Step.Launching, Step.Pass -> null
        }
    }
}
