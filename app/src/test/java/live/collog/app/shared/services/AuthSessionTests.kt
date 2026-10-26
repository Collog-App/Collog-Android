package live.collog.app.shared.services

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import live.collog.app.TestEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionTests {
    private fun tokens(role: String) = """
        {"accessToken":"new-token","refreshToken":"new-refresh",
        "user":{"id":"me","role":"$role","name":"Tester","phone":null,"familyId":"family"}}
    """.trimIndent()

    @Test
    fun cancelledWaiterDoesNotStartASecondRefresh() {
        val fixture = TestEnvironment()
        try {
            runBlocking {
                val requested = CompletableDeferred<Unit>()
                val release = CountDownLatch(1)
                val refreshes = AtomicInteger()
                fixture.respond = { request ->
                    assertEquals("/v1/auth/refresh", request.url.encodedPath)
                    refreshes.incrementAndGet()
                    requested.complete(Unit)
                    release.await(2, TimeUnit.SECONDS)
                    200 to tokens("PARENT")
                }
                val session = fixture.environment.session
                val baseUrl = fixture.environment.settings.resolvedBaseUrl
                // 401을 받고 갱신을 기다리던 화면이 사라진다
                val screen = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                screen.launch { session.refresh(baseUrl) }
                requested.await()
                screen.cancel()
                // 다음 화면의 401은 같은 갱신을 기다려야 한다(옛 refresh 토큰으로 또 갱신하면 서버가 거절해 로그아웃된다)
                val next = async(Dispatchers.Main) { session.refresh(baseUrl) }
                release.countDown()
                assertEquals("new-token", next.await())
                assertEquals(1, refreshes.get())
                assertTrue(session.isAuthenticated)
            }
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun refreshThatChangesTheRoleStillReturnsTheNewToken() {
        val fixture = TestEnvironment(role = "PARENT")
        try {
            runBlocking {
                fixture.respond = { 200 to tokens("CHILD") }
                val session = fixture.environment.session
                assertEquals("new-token", session.refresh(fixture.environment.settings.resolvedBaseUrl))
                assertEquals("CHILD", session.user?.role)
            }
        } finally {
            fixture.cleanUp()
        }
    }
}
