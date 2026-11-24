package live.collog.app.features.auth

import live.collog.app.features.auth.viewmodels.AuthFlowViewModel
import live.collog.app.features.auth.viewmodels.AuthFlowViewModel.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `onboarding_step`: 이 앱의 가입 화면을 약속 문서의 단계 이름에 맞춘다. */
class OnboardingStepEventTests {
    @Test
    fun screensMapToTheAgreedStepNames() {
        assertEquals("WELCOME", AuthFlowViewModel.onboardingStep(Step.Launching, Step.Intro))
        assertEquals("LOGIN", AuthFlowViewModel.onboardingStep(Step.Intro, Step.Onboarding))
        assertEquals("TERMS", AuthFlowViewModel.onboardingStep(Step.Onboarding, Step.Terms))
        assertEquals("PROFILE", AuthFlowViewModel.onboardingStep(Step.Terms, Step.SignUp))
        assertEquals("CONDITIONS", AuthFlowViewModel.onboardingStep(Step.SignUp, Step.Conditions))
    }

    @Test
    fun doneOnlyAfterFinishingSignUp() {
        assertEquals("DONE", AuthFlowViewModel.onboardingStep(Step.Conditions, Step.Ready))
        assertEquals("DONE", AuthFlowViewModel.onboardingStep(Step.SignUp, Step.Ready))
        // 이미 가입한 사람이 앱을 열거나, 동의만 다시 받거나, 둘러보기로 들어오면 가입을 마친 것이 아니다
        assertNull(AuthFlowViewModel.onboardingStep(Step.Launching, Step.Ready))
        assertNull(AuthFlowViewModel.onboardingStep(Step.Terms, Step.Ready))
        assertNull(AuthFlowViewModel.onboardingStep(Step.Onboarding, Step.Ready))
    }

    @Test
    fun launchingAndTheDisabledPassStepAreNotRecorded() {
        assertNull(AuthFlowViewModel.onboardingStep(Step.Intro, Step.Launching))
        assertNull(AuthFlowViewModel.onboardingStep(Step.Terms, Step.Pass))
    }
}
