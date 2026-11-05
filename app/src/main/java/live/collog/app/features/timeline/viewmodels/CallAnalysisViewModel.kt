package live.collog.app.features.timeline.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import live.collog.app.config.AppEnvironment
import live.collog.app.features.timeline.models.CallAnalysis
import live.collog.app.features.timeline.models.CallAnalysisBundle
import live.collog.app.features.timeline.models.CallAnalysisState
import live.collog.app.features.timeline.models.fromBundle
import live.collog.app.shared.services.api.ApiEndpoint
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.services.api.CollogApi

/** 피그마 「최종본」 통화 분석(269:5235): 통화 하나의 상태를 보고, 분석이 끝났으면 결과를 불러온다. iOS에는 없다. */
class CallAnalysisViewModel(private val callId: String) {
    var state by mutableStateOf<CallAnalysisState>(CallAnalysisState.Loading)
        private set

    suspend fun load(environment: AppEnvironment) {
        state = CallAnalysisState.Loading
        state = try {
            fetch(environment.api)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CallAnalysisState.Error(e.message ?: "요청을 처리하지 못했어요")
        }
    }

    private suspend fun fetch(api: CollogApi): CallAnalysisState {
        val call = api.callSummary(callId)
        when (call.state) {
            "ANALYZED" -> Unit
            "ANALYSIS_EXCLUDED" -> return CallAnalysisState.Unavailable(
                "분석 대상에서 제외된 통화예요.",
                "동의가 없거나 대화가 짧은 통화는 분석하지 않아요.",
            )
            "ANALYSIS_FAILED" -> return CallAnalysisState.Unavailable("통화를 분석하지 못했어요.", null)
            else -> return CallAnalysisState.Pending
        }

        return coroutineScope {
            val features = async { notFoundAsNull { api.acousticFeatures(callId) } }
            val extraction = async { notFoundAsNull { api.extraction(callId) } }
            // 타임라인과 같은 기준선. 못 받으면 비교 없이 보여 준다(iOS `try?`).
            val baselines = async {
                try {
                    api.baselines(call.parentId).filter { it.kind == "ROLLING" && it.isReady }.associateBy { it.metric }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyMap()
                }
            }
            val extracted = extraction.await() ?: return@coroutineScope CallAnalysisState.Pending
            CallAnalysisState.Ready(
                CallAnalysis.fromBundle(
                    CallAnalysisBundle(call, features.await(), extracted, transcript = null),
                    baselines.await(),
                ),
            )
        }
    }
}

/** `GET /v1/calls/{callId}`를 통화 요약으로 받는다. `CollogApi.callStatus`는 시작 시각·분석 대상을 읽지 않는다. */
private suspend fun CollogApi.callSummary(callId: String): CallSummaryDto =
    client.send(ApiEndpoint(path = "/v1/calls/$callId"))

/** 서버가 결과를 아직 만들지 않았으면(404) null. 다른 오류는 그대로 던진다. */
private suspend fun <T> notFoundAsNull(block: suspend () -> T): T? = try {
    block()
} catch (e: ApiError.Server) {
    if (e.status == 404) null else throw e
}
