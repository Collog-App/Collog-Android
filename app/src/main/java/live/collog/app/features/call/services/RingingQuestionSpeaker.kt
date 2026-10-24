package live.collog.app.features.call.services

import android.content.Context
import live.collog.app.shared.services.api.ApiQuestion
import live.collog.app.shared.services.api.CollogApi
import okhttp3.OkHttpClient

// 자리 파일: port/call-audio 브랜치의 구현으로 바뀐다(시그니처 약속만 둔다).
class RingingQuestionSpeaker(context: Context, http: OkHttpClient) {
    fun speak(questions: List<ApiQuestion>, callId: String, api: CollogApi, onEvent: (String) -> Unit) {}
    fun stop() {}
}
