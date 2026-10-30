package live.collog.app.features.auth.views

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.BuildConfig
import live.collog.app.R
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.viewmodels.LoginViewModel
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.pressable

/** 제목 앞부분의 초록 강조(피그마 채움 스타일 95:8418). */
private val HighlightGreen = Color(0xFF1EA66A)

private class OnboardingPage(
    val highlight: String,
    val title: String,
    val body: String,
    @DrawableRes val image: Int,
)

private val pages = listOf(
    OnboardingPage(
        "통화 한 번",
        "이 건강 기록이 됩니다",
        "부모님과의 통화에서 개인 기준선 대비 변화를\n차분하게 확인하세요.\n진단이 아닌, 우리 가족만의 기록입니다.",
        R.drawable.img_onboarding_record,
    ),
    OnboardingPage(
        "개인 별 건강 질문",
        "을 통해 건강을\n관리할 수 있어요",
        "되묻는 빈도, 기침, 말의 흐름과 같은 신호를 분석하여\n개인별 건강질문을 제공합니다.",
        R.drawable.img_onboarding_questions,
    ),
    OnboardingPage(
        "가족의 건강 변화",
        "를 공유해요",
        "가족의 주간, 월간 건강 데이터와 건강 타임라인을\n공유해서 가족 간 건강 이해도를 맞춰요",
        R.drawable.img_onboarding_sharing,
    ),
)

/**
 * 피그마 「최종본」 온보딩 3장(269:4019, 269:4034, 269:4049). 로그인 화면을 겸한다.
 * 카카오 로그인 자리(모든 장)에 Google 로그인을 둔다. 「가입 없이 둘러보기」는 화면 캡처용으로 디버그 빌드에만 있다.
 */
@Composable
fun OnboardingScreen(onSignedIn: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val viewModel = remember { LoginViewModel() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { pages.size }

    LaunchedEffect(Unit) {
        viewModel.prepareChallenge(environment)
        while (true) {
            delay(5_000)
            viewModel.refreshChallengeIfNeeded(environment)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { viewModel.refreshChallengeIfNeeded(environment) }
    }
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray00)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // 피그마는 상태 막대 아래 64dp 빈 머리(Top Navigator Bar 자리)와 20dp 뒤에 점을 둔다
        Spacer(Modifier.height(84.dp))
        PageDots(count = pages.size, current = pager.currentPage)
        Spacer(Modifier.height(Spacing.x6))
        HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top) { index ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.x5),
            ) {
                PageContent(pages[index])
                Spacer(Modifier.height(Spacing.x17))
                SignInActions(
                    viewModel = viewModel,
                    storageError = environment.session.storageError,
                    onGoogle = {
                        scope.launch {
                            if (viewModel.signIn(context, environment)) onSignedIn()
                        }
                    },
                    onRetry = { scope.launch { viewModel.prepareChallenge(environment) } },
                    onGuest = {
                        environment.settings.isGuestMode = true
                        onSignedIn()
                    },
                )
            }
        }
    }
}

/** 피그마 Indicator: 지금 장은 22x10 알약, 나머지는 10dp 점, 사이 8dp. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.x2, Alignment.CenterHorizontally)) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 22.dp else 10.dp, label = "dot")
            val color by animateColorAsState(if (selected) CollogColors.gray600 else CollogColors.gray300, label = "dotColor")
            Box(Modifier.width(width).height(10.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.x6)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = HighlightGreen)) { append(page.highlight) }
                    append(page.title)
                },
                style = pretendard(Pretendard.Bold, 24f),
                color = CollogColors.gray900,
            )
            Text(page.body, style = pretendard(Pretendard.Regular, 16f), color = CollogColors.gray800)
        }
        Image(
            painterResource(page.image),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(width = 260.dp, height = 212.dp),
        )
    }
}

/** 카카오 버튼 자리(350x50 알약)의 Google 로그인. Google 버튼은 예전 로그인 화면처럼 검은 바탕에 흰 글자. */
@Composable
private fun SignInActions(
    viewModel: LoginViewModel,
    storageError: String?,
    onGoogle: () -> Unit,
    onRetry: () -> Unit,
    onGuest: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.x5), verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        val enabled = viewModel.canSignIn
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(if (enabled) Color.Black else CollogColors.gray500, CircleShape)
                .pressable(enabled = enabled, onClick = onGoogle),
            contentAlignment = Alignment.Center,
        ) {
            Text("Google로 계속하기", style = pretendard(Pretendard.SemiBold, 16f), color = CollogColors.gray00)
        }

        (viewModel.errorMessage ?: storageError)?.let {
            Text(it, style = CollogType.caption01Medium, color = CollogColors.red500, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (viewModel.isLoadingChallenge || viewModel.isSubmitting) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CollogProgress() }
        } else if (!viewModel.canSignIn) {
            TextAction("로그인 다시 준비하기", CollogColors.blue600, onRetry)
        }
        if (BuildConfig.DEBUG) {
            TextAction("가입 없이 둘러보기", CollogColors.gray700, onGuest, enabled = !viewModel.isSubmitting)
        }
    }
}

@Composable
private fun TextAction(text: String, color: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = CollogType.body02Semibold, color = color, modifier = Modifier.pressable(enabled = enabled, onClick = onClick))
    }
}
