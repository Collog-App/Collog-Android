package live.collog.app.features.call.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import live.collog.app.config.AppEnvironment

/**
 * iOS `CallCenter`. 통화 연결·수신·녹음·기기 등록을 맡는다.
 * 지금은 다른 화면이 읽는 상태만 있다(구현은 통화 포팅에서 채운다).
 */
class CallCenter(val environment: AppEnvironment) {
    var deviceRegistrationError by mutableStateOf<String?>(null)
        internal set
    var notificationPermissionError by mutableStateOf<String?>(null)
        internal set
    var hasCallInProgress by mutableStateOf(false)
        internal set
}

val LocalCallCenter = staticCompositionLocalOf<CallCenter> { error("CallCenter가 제공되지 않았어요") }
