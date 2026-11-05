package live.collog.app.features.timeline.views

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.collog.app.R
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.timeline.models.CallAnalysis
import live.collog.app.features.timeline.models.CallAnalysisState
import live.collog.app.features.timeline.viewmodels.CallAnalysisViewModel
import live.collog.app.shared.components.HealthCrossBadge
import live.collog.app.shared.components.emptystate.EmptyState
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing

/** 통화 분석 화면을 탭의 화면 스택(`LocalNavigationManager`)에 넣을 때 쓰는 값. */
data class CallAnalysisRoute(val callId: String)

/**
 * 피그마 「최종본」 통화 분석(269:5235). iOS에는 없는 화면이다.
 * 타임라인 통화 카드와 리포트 알림(`report_ready`)에서 열고, 뒤로는 탭의 화면 스택에서 한 장 뺀다.
 */
@Composable
fun CallAnalysisScreen(callId: String) {
    val environment = LocalAppEnvironment.current
    val navigation = LocalNavigationManager.current
    val scope = rememberCoroutineScope()
    val viewModel = remember(callId) { CallAnalysisViewModel(callId) }
    val reload: () -> Unit = { scope.launch { viewModel.load(environment) } }

    LaunchedEffect(viewModel) { viewModel.load(environment) }

    Column(Modifier.fillMaxSize().background(CollogColors.gray00).statusBarsPadding()) {
        SettingsDetailHeader("", onBack = { navigation.pop() })

        when (val state = viewModel.state) {
            CallAnalysisState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CollogProgress()
            }
            CallAnalysisState.Pending -> EmptyState(
                symbol = "waveform",
                title = "통화를 분석하고 있어요.",
                message = "분석이 끝나면 건강 관련 정보를 보여드릴게요. 잠시 뒤 다시 확인해 주세요.",
                actionTitle = "새로고침",
                action = reload,
            )
            is CallAnalysisState.Unavailable -> EmptyState(
                symbol = "exclamationmark.circle",
                title = state.title,
                message = state.message,
            )
            is CallAnalysisState.Error -> EmptyState(
                symbol = "exclamationmark.circle",
                title = "데이터를 불러오지 못했어요.",
                message = state.message,
                actionTitle = "다시 시도",
                action = reload,
            )
            is CallAnalysisState.Ready -> AnalysisContent(
                state.analysis,
                subjectName = state.analysis.subjectName(environment.family.contacts, environment.session.user),
            )
        }
    }
}

private val BoxShape = RoundedCornerShape(Radius.listItem)

@Composable
private fun AnalysisContent(analysis: CallAnalysis, subjectName: String) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(top = 27.dp, bottom = Spacing.x8),
    ) {
        Image(painterResource(R.drawable.ic_family_health), contentDescription = null, modifier = Modifier.size(24.dp))
        Text(
            "방금 나눈 대화에서 건강 관련 정보를 추출 하였습니다.",
            Modifier.padding(top = 13.dp),
            style = CollogType.body02Medium,
            color = CollogColors.gray700,
        )

        // 피그마 「분석 대상」 칸: 이름과 통화 날
        Row(
            Modifier
                .padding(top = 62.dp)
                .fillMaxWidth()
                .height(56.dp)
                .border(1.dp, CollogColors.gray400, BoxShape)
                .padding(horizontal = Spacing.x5),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                subjectName,
                Modifier.weight(1f, fill = false),
                style = CollogType.body01Regular,
                color = CollogColors.gray800,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(analysis.dayText, style = CollogType.body01Regular, color = CollogColors.gray800, maxLines = 1)
        }

        // 피그마 「건강 정보」 칸
        Column(
            Modifier
                .padding(top = 41.dp)
                .fillMaxWidth()
                .border(1.dp, CollogColors.gray400, BoxShape)
                .padding(start = Spacing.x5, end = Spacing.x5, top = 21.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            Section("건강 대화 항목", analysis.conversation)
            Section("개인 기준선 비교 준비", analysis.baseline)
            Section("음향 지표 계산", analysis.acoustic)
        }

        Row(
            Modifier
                .padding(top = 23.dp)
                .fillMaxWidth()
                .height(50.dp)
                .background(CollogColors.gray100, BoxShape)
                .padding(horizontal = Spacing.x5),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HealthCrossBadge()
            Text("오늘의 권장 사항", style = CollogType.body01Semibold, color = CollogColors.gray1000)
        }

        Text(
            analysis.recommendation,
            Modifier
                .padding(top = Spacing.x4)
                .fillMaxWidth()
                .heightIn(min = 99.dp)
                .background(CollogColors.gray100, BoxShape)
                .padding(start = Spacing.x5, end = Spacing.x5, top = 15.dp, bottom = Spacing.x5),
            style = CollogType.body01Medium,
            color = CollogColors.gray800,
        )
    }
}

/** 피그마 「건강 정보」 칸의 한 묶음: 제목과 내용(입력칸 안내 글자처럼 옅은 회색). */
@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Text(title, style = CollogType.body01Semibold, color = CollogColors.gray600)
        Text(body, style = CollogType.body02Regular, color = CollogColors.gray600)
    }
}
