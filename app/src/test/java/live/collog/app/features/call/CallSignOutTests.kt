package live.collog.app.features.call

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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

    @Test
    fun logoutFinishesAfterTheCallingScreenIsGone() {
        val fixture = TestEnvironment()
        try {
            runBlocking {
                val requested = CompletableDeferred<Unit>()
                val release = CountDownLatch(1)
                fixture.respond = {
                    requested.complete(Unit)
                    release.await(2, TimeUnit.SECONDS)
                    200 to "{}"
                }
                var cleared = false
                fixture.environment.afterSignOut = {
                    yield()
                    cleared = true
                }
                // 설정 화면의 rememberCoroutineScope. 로그아웃하면 로그인 화면으로 바뀌며 취소된다.
                val screen = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                val job = screen.launch { fixture.environment.signOut() }
                requested.await()
                screen.cancel()
                release.countDown()
                job.join()
                assertTrue(cleared)
            }
        } finally {
            fixture.cleanUp()
        }
    }
}
