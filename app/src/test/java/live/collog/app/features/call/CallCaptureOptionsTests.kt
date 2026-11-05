package live.collog.app.features.call

import live.collog.app.features.call.services.callCaptureOptions
import live.collog.app.shared.services.api.AudioConstraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallCaptureOptionsTests {
    private fun server(echo: Boolean = true, noise: Boolean = false, gain: Boolean = false) =
        AudioConstraints(echo, noise, gain, dtx = false, audioBitrate = 32000, rawCaptureSampleRate = 16000)

    @Test
    fun callAudioAlwaysSuppressesNoise() {
        // 운영 서버는 분석용 원음을 지키려고 잡음 제거를 끄고 보낸다. 분석 PCM 은 전송 처리 앞의
        // 원본이라, 통화로 보내는 소리에는 잡음 제거를 켠다.
        assertTrue(callCaptureOptions(server(noise = false)).noiseSuppression)
        assertTrue(callCaptureOptions(server(noise = true)).noiseSuppression)
    }

    @Test
    fun otherProcessingFollowsTheServer() {
        val options = callCaptureOptions(server(echo = true, gain = false))
        assertTrue(options.echoCancellation)
        assertFalse(options.autoGainControl)
        assertFalse(options.highPassFilter)
        assertFalse(options.typingNoiseDetection)
        assertEquals(false, callCaptureOptions(server(echo = false)).echoCancellation)
        assertEquals(true, callCaptureOptions(server(gain = true)).autoGainControl)
    }
}
