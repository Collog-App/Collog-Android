package live.collog.app.features.home.views

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.R
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.models.FamilyHealthSummary
import live.collog.app.features.home.viewmodels.HomeViewModel
import live.collog.app.features.settings.views.FamilyMembersSettingsScreen
import live.collog.app.features.timeline.models.WeeklySpeechHistory
import live.collog.app.shared.components.chart.TrendSeries
import live.collog.app.shared.components.chart.smoothPath
import live.collog.app.shared.components.emptystate.AnalysisEmptyState
import live.collog.app.shared.components.emptystate.EmptyState
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.navigation.Route
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.OnChange
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.SpecDivider
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

/**
 * 새 피그마 「디자인」 01 홈 · 메인(643:6334). 탭 루트는 항상 그려 두고(스크롤 위치·불러온 값 유지),
 * 화면 스택에 밀어 넣은 화면은 그 위에 덮어 그린다. 전화는 가운데 전화 버튼으로만 건다.
 */
@Composable
fun HomeScreen() {
    val navigation = LocalNavigationManager.current
    val viewModel = remember { HomeViewModel() }

    Box(Modifier.fillMaxSize()) {
        HomeRoot(viewModel)

        val route = navigation.path.lastOrNull()
        if (route != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(CollogColors.gray50)
                    // 아래 홈 화면으로 터치가 새지 않게 막는다
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                when (route) {
                    Route.FamilyHealthOverview -> viewModel.healthSummary?.let { FamilyHealthOverviewScreen(it) }
                    Route.HealthFeedbackDetail -> viewModel.healthFeedback?.let { feedback ->
                        val contact = LocalAppEnvironment.current.family.selectedContact
                        HealthFeedbackDetailScreen(
                            feedback,
                            lastCallText = contact?.let { viewModel.lastCallAt[it.id] }?.let { HomeViewModel.lastCallDayText(it) },
                        )
                    }
                    Route.Notifications -> HomeNotificationsScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeRoot(viewModel: HomeViewModel) {
    val environment = LocalAppEnvironment.current
    val navigation = LocalNavigationManager.current
    val tabManager = LocalTabManager.current
    val callCenter = LocalCallCenter.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val contacts = environment.family.contacts
    val selectedContact = environment.family.selectedContact
    // 서버가 주는 만큼만(최대 2개). 새로 만드는 기능은 서버에 없다.
    val questions = environment.family.questions(selectedContact).map { it.text }.take(2)
    val summary = viewModel.healthSummary
    val isGuest = environment.settings.isGuestMode

    suspend fun refresh() {
        environment.family.refresh(environment)
        viewModel.refresh(environment, environment.family.selectedContact)
    }

    fun select(contact: FamilyContact) {
        if (environment.family.selectedContactId == contact.id) return
        Haptics.focus()
        environment.family.selectContact(contact)
        scope.launch { refresh() }
    }

    // 카드의 작은 그래프: 02 가족 건강과 같은 6주 말씀 속도(체험 모드는 견본)
    var weekly by remember { mutableStateOf<TrendSeries?>(null) }
    LaunchedEffect(selectedContact?.userId, isGuest, summary != null) {
        weekly = null
        if (summary == null) return@LaunchedEffect
        if (isGuest) {
            weekly = summary.trend
            return@LaunchedEffect
        }
        val parentId = selectedContact?.userId ?: return@LaunchedEffect
        weekly = WeeklySpeechHistory.loadTrend(environment.api, parentId)
    }

    LaunchedEffect(Unit) { refresh() }
    OnChange(tabManager.reselectionCount) { _, _ -> scrollState.animateScrollTo(0, tween(320)) }

    Column(Modifier.fillMaxSize().background(SpecColors.page)) {
        HomeTopBar(onNotifications = { navigation.push(Route.Notifications) })

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    try {
                        // 새로고침 표시는 최소 0.65초 보인다
                        val minimumDuration = launch { delay(650) }
                        refresh()
                        minimumDuration.join()
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.weight(1f),
            state = refreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = Color.White,
                    color = SpecColors.sub,
                )
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
                    .padding(top = 21.dp, bottom = Spacing.x5),
            ) {
                if (selectedContact != null) {
                    SubjectHeader(
                        name = selectedContact.name,
                        contacts = contacts,
                        lastCallText = lastCallSentence(viewModel.lastCallAt[selectedContact.id], viewModel.isLoaded),
                        onSelect = ::select,
                    )
                    Spacer(Modifier.height(27.dp))
                }

                val error = viewModel.loadError ?: viewModel.recentCallsError ?: environment.family.loadError
                if (summary != null) {
                    SpeechRateCard(summary, weekly ?: summary.trend) { navigation.push(Route.FamilyHealthOverview) }
                } else if (!viewModel.isLoaded) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.x8),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                    ) {
                        CollogProgress()
                        Text("통화 기록을 불러오고 있어요", style = specText(Pretendard.Medium, 13f, SpecColors.sub))
                    }
                } else if (error != null) {
                    EmptyState(
                        symbol = "exclamationmark.circle",
                        title = "데이터를 불러오지 못했어요.",
                        modifier = Modifier.specCard(padding = 15.dp),
                        message = error,
                        actionTitle = "다시 시도",
                        action = { scope.launch { refresh() } },
                    )
                } else {
                    AnalysisEmptyState(
                        onStartCall = { contact ->
                            callCenter.startOutgoingCall(
                                calleeId = contact.userId!!,
                                name = contact.name,
                                questions = environment.family.questions(contact).map { it.text },
                            )
                        },
                        onRefresh = { refresh() },
                        modifier = Modifier.specCard(padding = 15.dp),
                        contact = selectedContact,
                        calls = viewModel.recentCalls,
                        familySheet = { onDismiss -> FamilyMembersSettingsScreen(onClose = onDismiss) },
                    )
                }

                if (selectedContact != null) {
                    Spacer(Modifier.height(20.dp))
                    TodayQuestionsCard(questions)
                }

                // 건강 피드백 화면은 분석 결과가 있어야 그려지므로, 없으면 이 줄도 두지 않는다
                if (viewModel.healthFeedback != null) {
                    Spacer(Modifier.height(17.dp))
                    FeedbackShortcut { navigation.push(Route.HealthFeedbackDetail) }
                }
            }
        }
    }
}

/** 「3일 전 통화했어요」. 기록이 없으면 안내 문구, 아직 불러오는 중이면 비운다. */
internal fun lastCallSentence(latest: Instant?, isLoaded: Boolean): String? = when {
    latest != null -> "${HomeViewModel.lastCallDayText(latest)} 통화했어요"
    isLoaded -> "아직 통화 기록이 없어요"
    else -> null
}

/** 새 피그마 알림 종(643:6336, 26dp). 읽지 않은 알림을 앱이 알지 못해 빨간 점은 그리지 않는다. */
@Composable
private fun HomeTopBar(onNotifications: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(end = 19.dp, top = 13.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .pressable(onClick = onNotifications)
                .semantics { contentDescription = "알림" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_home_bell),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** 큰 가족 이름과 마지막 통화(643:6339~6342). 가족이 둘 이상이면 이름을 눌러 바꾼다. */
@Composable
private fun SubjectHeader(name: String, contacts: List<FamilyContact>, lastCallText: String?, onSelect: (FamilyContact) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectable = contacts.size > 1
    Column {
        Box {
            Row(
                Modifier.pressable(enabled = selectable) { expanded = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(name, style = specText(Pretendard.Bold, 22f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (selectable) CollogIcon("chevron.down", size = 18.dp, color = SpecColors.sub)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
                contacts.forEach { contact ->
                    DropdownMenuItem(
                        text = { Text(contact.name, style = specText(Pretendard.Medium, 14f)) },
                        onClick = {
                            expanded = false
                            onSelect(contact)
                        },
                    )
                }
            }
        }
        if (lastCallText != null) {
            Spacer(Modifier.height(8.dp))
            Text(lastCallText, style = specText(Pretendard.Regular, 12f, SpecColors.muted))
        }
    }
}

/**
 * 말씀 속도 카드(643:6343). 제목·설명은 02 가족 건강과 같은 요약([FamilyHealthSummary]), 큰 값과 「평소 범위」는
 * 작은 그래프와 같은 6주 추세의 마지막 값이다. 누르면 02로 간다.
 */
@Composable
private fun SpeechRateCard(summary: FamilyHealthSummary, trend: TrendSeries?, onClick: () -> Unit) {
    val latest = trend?.latest
    val chart = trend?.takeIf { it.points.size >= 2 }
    Column(Modifier.specCard(padding = 0.dp).pressable(onClick = onClick)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp)) {
            Text(summary.headline, style = specText(Pretendard.Bold, 16f))
            Spacer(Modifier.height(10.dp))
            Text(summary.detail, style = specText(Pretendard.Regular, 12f, SpecColors.sub))
            if (latest != null && trend != null) {
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(roundedInt(latest.value).toString(), Modifier.alignByBaseline(), style = specText(Pretendard.Bold, 20f))
                        Text(trend.unit, Modifier.alignByBaseline(), style = specText(Pretendard.Regular, 12f, SpecColors.sub))
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.padding(end = 11.dp, bottom = 2.dp)) {
                        if (trend.hasPersonalBaseline) {
                            val within = trend.isWithinNormalRange(latest)
                            Text(
                                if (within) "평소 범위" else "평소와 다름",
                                style = specText(Pretendard.Bold, 11f, if (within) SpecColors.green else SpecColors.orange),
                            )
                        } else {
                            Text("비교할 기록 수집 중", style = specText(Pretendard.Regular, 11f, SpecColors.muted))
                        }
                    }
                }
            }
        }
        if (chart != null) {
            Spacer(Modifier.height(26.dp))
            SpecDivider()
            SpeechSparkline(chart, Modifier.padding(start = 24.dp, end = 22.dp).fillMaxWidth().height(58.dp))
            Spacer(Modifier.height(31.dp))
        } else {
            Spacer(Modifier.height(22.dp))
        }
    }
}

/** 카드 안 작은 그래프(644:7430): 초록 곡선 위 25% 면, 끝 점. 가로축·눈금은 없다. */
@Composable
private fun SpeechSparkline(series: TrendSeries, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val points = series.points
        val dot = 4.5.dp.toPx()
        val lineBottom = 45.dp.toPx()
        val values = points.map { it.value }
        val minimum = values.min()
        val span = maxOf(values.max() - minimum, 0.0001)
        val times = points.map { it.date.toEpochMilli() }
        val start = times.min()
        val end = times.max()
        val width = size.width - dot
        val offsets = points.mapIndexed { index, point ->
            val x = if (start == end) width * index / (points.size - 1) else (point.date.toEpochMilli() - start).toFloat() / (end - start) * width
            Offset(x, dot + (1 - ((point.value - minimum) / span).toFloat()) * (lineBottom - dot * 2))
        }
        val curve = smoothPath(offsets)
        val area = Path().apply {
            addPath(curve)
            lineTo(offsets.last().x, size.height)
            lineTo(offsets.first().x, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(SpecColors.chartGreen.copy(alpha = 0.25f), SpecColors.chartGreen.copy(alpha = 0f)),
                startY = offsets.minOf { it.y },
                endY = size.height,
            ),
        )
        drawPath(curve, SpecColors.chartGreen, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(SpecColors.chartGreen, dot, offsets.last())
    }
}

private val BubbleGreen = Color(0xFFE7F5EC)

/**
 * 오늘의 질문 카드(643:6353). 말풍선은 서버가 준 질문(최대 2개)만 그린다.
 * 「탭해서 새로운 질문을 생성해보세요」와 새로고침 아이콘은 서버에 질문을 새로 만드는 기능이 없어 뺐다(진행규칙 4).
 */
@Composable
private fun TodayQuestionsCard(questions: List<String>) {
    Column(Modifier.specCard(padding = 0.dp).padding(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 24.dp)) {
        Text("오늘의 질문", style = specText(Pretendard.Bold, 14f))
        Spacer(Modifier.height(24.dp))
        if (questions.isEmpty()) {
            Text("아직 준비된 질문이 없어요", style = specText(Pretendard.Medium, 12f, SpecColors.muted))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                questions.forEachIndexed { index, question ->
                    QuestionBubble(index + 1, question, filled = index > 0, startInset = if (index % 2 == 0) 33.dp else 57.dp)
                }
            }
        }
    }
}

@Composable
private fun QuestionBubble(number: Int, text: String, filled: Boolean, startInset: Dp) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        Modifier
            .padding(start = startInset)
            .widthIn(max = 236.dp)
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.07f))
            .background(if (filled) BubbleGreen else Color.White, shape)
            .heightIn(min = 43.dp)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("%02d".format(number), style = specText(Pretendard.Bold, 11f, SpecColors.green), modifier = Modifier.padding(top = 1.dp))
        Text(text, style = specText(Pretendard.Medium, 12f), modifier = Modifier.weight(1f))
    }
}

/** 건강 피드백 바로가기(643:6368). 누르면 03으로 간다. */
@Composable
private fun FeedbackShortcut(onClick: () -> Unit) {
    Row(
        Modifier.specCard(padding = 0.dp).pressable(onClick = onClick).height(68.dp).padding(start = 17.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("건강 피드백", style = specText(Pretendard.Regular, 10f, SpecColors.muted))
            Spacer(Modifier.height(6.dp))
            Text("최근 생활 변화 살펴보기", style = specText(Pretendard.Bold, 13f))
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = SpecColors.chevron, modifier = Modifier.size(18.dp))
    }
}
