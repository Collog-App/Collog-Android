package live.collog.app.features.auth

import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.auth.viewmodels.AuthFlowViewModel
import live.collog.app.features.auth.viewmodels.AuthFlowViewModel.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 피그마 「최종본」 가입 흐름의 단계 고르기. 역할은 보지 않는다. */
class AuthFlowTests {
    private fun withFixture(
        role: String = "CHILD",
        familyId: String? = "family",
        signupCompleted: Boolean = true,
        body: suspend (TestEnvironment) -> Unit,
    ) = runTest {
        val fixture = TestEnvironment(role = role, familyId = familyId, signupCompleted = signupCompleted)
        try {
            body(fixture)
        } finally {
            fixture.cleanUp()
        }
    }

    private fun profile(completed: Boolean) =
        """{"parentId":"me","conditions":[],"isCompleted":$completed,"updatedAt":null}"""

    /** 동의는 [consent](null 이면 404), 프로필은 [profileCompleted]로 답한다. */
    private fun TestEnvironment.serve(consent: String?, profileCompleted: Boolean = true) {
        respond = { request ->
            if (request.url.encodedPath == "/v1/consents/me") {
                if (consent == null) 404 to """{"code":"NOT_FOUND","message":"동의 기록이 없습니다"}""" else 200 to consent
            } else {
                assertEquals("/v1/parents/me/profile", request.url.encodedPath)
                200 to profile(profileCompleted)
            }
        }
    }

    private suspend fun resolved(fixture: TestEnvironment): Step {
        val model = AuthFlowViewModel()
        model.resolve(fixture.environment)
        return model.step
    }

    @Test
    fun signedOutStartsAtOnboarding() = withFixture { fixture ->
        fixture.environment.session.signOut()
        assertEquals(Step.Onboarding, resolved(fixture))
        assertTrue(fixture.paths.isEmpty())
    }

    @Test
    fun guestGoesHomeWithoutRequests() = withFixture { fixture ->
        fixture.environment.settings.isGuestMode = true
        assertEquals(Step.Ready, resolved(fixture))
        assertTrue(fixture.paths.isEmpty())
    }

    @Test
    fun newAccountWithoutConsentStartsAtTerms() = withFixture(familyId = null, signupCompleted = false) { fixture ->
        fixture.serve(consent = null)
        assertEquals(Step.Terms, resolved(fixture))
        assertEquals(listOf("/v1/consents/me"), fixture.paths)
    }

    @Test
    fun signedInButNotCompletedResumesAtSignUpForm() = withFixture(familyId = null, signupCompleted = false) { fixture ->
        fixture.serve(consent = fixture.consent())
        assertEquals(Step.SignUp, resolved(fixture))
        assertEquals(1, fixture.paths.size)
    }

    @Test
    fun notCompletedWithDeclinedConsentGoesBackToTerms() = withFixture(signupCompleted = false) { fixture ->
        // 질환 저장에 본인 동의가 필요하므로, 예전 앱에서 거절한 사람은 약관부터 다시 본다
        fixture.serve(consent = fixture.consent(granted = false))
        assertEquals(Step.Terms, resolved(fixture))
    }

    @Test
    fun completedAccountWithOutdatedConsentSeesTermsOnly() = withFixture { fixture ->
        fixture.serve(consent = fixture.consent(current = false))
        assertEquals(Step.Terms, resolved(fixture))
        assertEquals(1, fixture.paths.size)
    }

    @Test
    fun completedAccountWithEmptyProfileAsksForConditions() {
        for (completed in listOf(true, false)) {
            withFixture { fixture ->
                fixture.serve(consent = fixture.consent(), profileCompleted = completed)
                assertEquals(if (completed) Step.Ready else Step.Conditions, resolved(fixture))
                assertEquals(listOf("/v1/consents/me", "/v1/parents/me/profile"), fixture.paths)
            }
        }
    }

    @Test
    fun withdrawnConsentDoesNotBlockAppAccess() = withFixture { fixture ->
        fixture.serve(consent = fixture.consent(granted = false))
        assertEquals(Step.Ready, resolved(fixture))
        assertEquals(1, fixture.paths.size)
    }

    @Test
    fun consentFailureKeepsTermsRequired() = withFixture { fixture ->
        fixture.respond = { 503 to "{}" }
        assertEquals(Step.Terms, resolved(fixture))
    }

    @Test
    fun profileFailureStillOpensHome() = withFixture { fixture ->
        fixture.respond = { request ->
            if (request.url.encodedPath == "/v1/consents/me") 200 to fixture.consent() else 503 to "{}"
        }
        assertEquals(Step.Ready, resolved(fixture))
    }

    @Test
    fun legacyRolesFollowTheSameSteps() {
        // 예전 서버의 부모 계정도, 가족이 없어도 초대 코드 단계로 따로 가지 않는다
        for (role in listOf("PARENT", "CHILD")) {
            withFixture(role = role, familyId = null) { fixture ->
                fixture.serve(consent = fixture.consent(), profileCompleted = false)
                assertEquals(Step.Conditions, resolved(fixture))
            }
            withFixture(role = role, familyId = null, signupCompleted = false) { fixture ->
                fixture.serve(consent = fixture.consent())
                assertEquals(Step.SignUp, resolved(fixture))
            }
        }
    }
}
