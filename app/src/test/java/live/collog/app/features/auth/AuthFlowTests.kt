package live.collog.app.features.auth

import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.auth.viewmodels.AuthFlowViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `AuthFlowTests`. */
class AuthFlowTests {
    private fun withFixture(role: String = "PARENT", familyId: String? = "family", body: suspend (TestEnvironment) -> Unit) =
        runTest {
            val fixture = TestEnvironment(role = role, familyId = familyId)
            try {
                body(fixture)
            } finally {
                fixture.cleanUp()
            }
        }

    @Test
    fun parentWithoutFamilyCanEnterInvitation() = withFixture(familyId = null) { fixture ->
        val model = AuthFlowViewModel()
        model.resolve(fixture.environment)
        assertEquals(AuthFlowViewModel.Step.Invitation, model.step)
        assertTrue(fixture.paths.isEmpty())
    }

    @Test
    fun outdatedConsentRequiresPermissionForBothRoles() {
        for (role in listOf("PARENT", "CHILD")) {
            withFixture(role = role) { fixture ->
                fixture.respond = { 200 to fixture.consent(current = false) }
                val model = AuthFlowViewModel()
                model.resolve(fixture.environment)
                assertEquals(AuthFlowViewModel.Step.Consent, model.step)
                assertEquals(1, fixture.paths.size)
            }
        }
    }

    @Test
    fun declinedAnalysisDoesNotBlockAppAccess() = withFixture { fixture ->
        fixture.respond = { 200 to fixture.consent(granted = false) }
        val model = AuthFlowViewModel()
        model.resolve(fixture.environment)
        assertEquals(AuthFlowViewModel.Step.Ready, model.step)
        assertEquals(1, fixture.paths.size)
    }

    @Test
    fun emptyHealthProfileUsesCompletionFlag() {
        for (completed in listOf(true, false)) {
            withFixture { fixture ->
                fixture.respond = { request ->
                    if (request.url.encodedPath.contains("consent")) {
                        200 to fixture.consent()
                    } else {
                        200 to """{"parentId":"me","conditions":[],"isCompleted":$completed,"updatedAt":null}"""
                    }
                }
                val model = AuthFlowViewModel()
                model.resolve(fixture.environment)
                assertEquals(if (completed) AuthFlowViewModel.Step.Ready else AuthFlowViewModel.Step.Profile, model.step)
                assertEquals(2, fixture.paths.size)
            }
        }
    }

    @Test
    fun consentFailureKeepsPermissionRequired() = withFixture { fixture ->
        fixture.respond = { 503 to "{}" }
        val model = AuthFlowViewModel()
        model.resolve(fixture.environment)
        assertEquals(AuthFlowViewModel.Step.Consent, model.step)
    }
}
