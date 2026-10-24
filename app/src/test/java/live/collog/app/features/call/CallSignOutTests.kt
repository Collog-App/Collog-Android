package live.collog.app.features.call

import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `CallAudioTests.logoutNotifiesCallCleanupBeforeDiscardingCredentials`. */
class CallSignOutTests {
    @Test
    fun logoutNotifiesCallCleanupBeforeDiscardingCredentials() {
        val fixture = TestEnvironment()
        try {
            runTest {
                var cleanupSawCredentials = false
                fixture.environment.beforeSignOut = {
                    cleanupSawCredentials = fixture.environment.session.isAuthenticated
                }
                fixture.respond = { 200 to "{}" }
                fixture.environment.signOut()
                assertTrue(cleanupSawCredentials)
                assertFalse(fixture.environment.session.isAuthenticated)
                assertEquals(listOf("/v1/auth/logout"), fixture.paths)
            }
        } finally {
            fixture.cleanUp()
        }
    }
}
