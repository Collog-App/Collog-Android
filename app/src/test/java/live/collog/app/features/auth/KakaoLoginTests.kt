package live.collog.app.features.auth

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import live.collog.app.TestEnvironment
import live.collog.app.features.auth.services.KakaoSignIn
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 카카오 로그인 서버 계약: 서버가 준 nonce 로 받은 ID 토큰과 준비 번호만 보낸다(이름은 서버가 닉네임으로 정한다). */
class KakaoLoginTests {
    private val token = """
        {"accessToken":"kakao-access","refreshToken":"kakao-refresh",
        "user":{"id":"kakao-user","role":"CHILD","name":"카카오친구","phone":null,"familyId":null}}
    """.trimIndent()

    private fun bodyOf(request: Request): String = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

    @Test
    fun kakaoLoginUsesTheServerChallengeAndAppliesTheSession() = runTest {
        val fixture = TestEnvironment(familyId = null)
        try {
            var sent: String? = null
            fixture.respond = { request ->
                when (request.url.encodedPath) {
                    "/v1/auth/kakao/challenge" -> 200 to """{"challengeId":"challenge-1","nonce":"nonce-1","expiresIn":300}"""
                    "/v1/auth/kakao" -> {
                        sent = bodyOf(request)
                        200 to token
                    }
                    else -> 404 to """{"code":"NOT_FOUND","message":"없음"}"""
                }
            }
            val api = fixture.environment.api
            val challenge = api.kakaoLoginChallenge()
            assertEquals("nonce-1", challenge.nonce)
            fixture.environment.session.apply(api.loginWithKakao("id-token", challenge.challengeId))

            val body = Json.parseToJsonElement(sent!!).jsonObject
            assertEquals(setOf("idToken", "challengeId"), body.keys)
            assertEquals("id-token", body["idToken"]!!.jsonPrimitive.content)
            assertEquals("challenge-1", body["challengeId"]!!.jsonPrimitive.content)
            assertEquals("kakao-user", fixture.environment.session.user?.id)
            assertNull(fixture.environment.session.familyId)
            assertEquals(listOf("/v1/auth/kakao/challenge", "/v1/auth/kakao"), fixture.paths)
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun kakaoButtonStaysHiddenWithoutAnAppKey() {
        // 단위 테스트 빌드에는 collog.kakaoNativeAppKey 가 없다
        assertFalse(KakaoSignIn.isConfigured)
    }
}
