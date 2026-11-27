package live.collog.app.features.timeline.views

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.math.abs
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.health.services.LocalHealthLink
import live.collog.app.features.health.views.LifeRecordCard
import live.collog.app.features.health.views.SafetyNoticeCard
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.report.models.ReportState
import live.collog.app.features.report.views.ReportContent
import live.collog.app.features.settings.views.FamilyMembersSettingsScreen
import live.collog.app.features.timeline.models.CallTimelineEntry
import live.collog.app.features.timeline.models.TimelineWeekPage
import live.collog.app.features.timeline.viewmodels.TimelineViewModel
import live.collog.app.features.timeline.views.components.CallTimelineCard
import live.collog.app.features.timeline.views.components.CallTimelineDateHeader
import live.collog.app.features.timeline.views.components.CallTimelineRail
import live.collog.app.features.timeline.views.components.TimelineWeekHeader
import live.collog.app.features.timeline.views.components.ViewerBadgeChip
import live.collog.app.features.timeline.views.components.ViewerProfileHeader
import live.collog.app.features.timeline.views.components.WeekNavigator
import live.collog.app.shared.components.emptystate.AnalysisEmptyAction
import live.collog.app.shared.components.emptystate.AnalysisEmptyState
import live.collog.app.shared.components.emptystate.EmptyState
import live.collog.app.shared.components.emptystate.NotSharedEmptyState
import live.collog.app.shared.components.emptystate.PremiumLockedState
import live.collog.app.shared.components.navbar.CollapsingHeaderScrollView
import live.collog.app.shared.components.navbar.CollapsingLargeTitle
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.navigation.MainTab
import live.collog.app.shared.services.AppEventCode
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.OnChange
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.LocalViewerAccent
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.ViewerAccent
import live.collog.app.shared.ui.pressable

private const val OldestOffset = -25
private val PageOffsets = (OldestOffset..0).toList()
private val VerticalPageOffsets = PageOffsets.reversed()
private val ActiveWeekThreshold = 56.dp // 머리말 막대(52dp) 바로 아래

/**
 * iOS `ReportTimelineView`: 리포트 탭([initialTabIndex] 0)과 타임라인 탭(1)이 함께 쓰는 화면.
 * 리포트는 주 단위로 가로로 넘기고, 타임라인은 주를 세로로 이어 붙인다.
 * HomeScreen처럼 탭 루트는 그려 둔 채(스크롤·불러온 주 유지) 밀어 넣은 통화 분석 화면을 그 위에 덮어 그린다.
 */
@Composable
fun ReportTimelineScreen(initialTabIndex: Int = 1) {
    val navigation = LocalNavigationManager.current
    // iOS `.environment(\.viewerAccent, accent)`: 밀어 넣은 통화 분석 화면도 지금 보는 사람의 색을 쓴다
    var accent by remember { mutableStateOf(ViewerAccent.Family) }
    CompositionLocalProvider(LocalViewerAccent provides accent) {
        Box(Modifier.fillMaxSize()) {
            ReportTimelineRoot(initialTabIndex, onAccentChange = { accent = it })

            val route = navigation.path.lastOrNull() as? CallAnalysisRoute
            if (route != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(CollogColors.gray00)
                        // 아래 탭 화면으로 터치가 새지 않게 막는다
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    key(route) { CallAnalysisScreen(route.callId) }
                }
            }
        }
    }
}

/** 타임라인 본문을 옆으로 밀어 사람을 바꾸는 거리(iOS `personSwipe` 70pt). */
private val PersonSwipeDistance = 70.dp

@Composable
private fun ReportTimelineRoot(initialTabIndex: Int, onAccentChange: (ViewerAccent) -> Unit) {
    val environment = LocalAppEnvironment.current
    val tabManager = LocalTabManager.current
    val navigation = LocalNavigationManager.current
    val callCenter = LocalCallCenter.current
    val healthLink = LocalHealthLink.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val billing = environment.billing
    // 결제가 켜져 있고 가족이 무료: 리포트·타임라인 본문 대신 잠금 화면(서버 값만 본다)
    val isLocked = billing.isLocked

    val tab = if (initialTabIndex == 0) MainTab.Report else MainTab.Timeline
    val viewModel = remember { TimelineViewModel(selectedTabIndex = initialTabIndex) }
    val pagerState = rememberPagerState(initialPage = PageOffsets.lastIndex) { PageOffsets.size }
    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    var activeVerticalWeek by remember { mutableIntStateOf(0) }
    val weekHeaderMinYs = remember { mutableMapOf<Int, Float>() }
    var memberSelectionCount by remember { mutableIntStateOf(0) }

    val contacts = environment.family.contacts
    val subjects = viewModel.subjects(environment)
    val isSelfSelected = viewModel.isSelfSelected
    // 가족 = 기존 그린, 나 = 주황. 둘러보기처럼 고른 사람이 없으면 그린
    val accent = if (isSelfSelected) ViewerAccent.Mine else ViewerAccent.Family
    SideEffect { onAccentChange(accent) }
    val displayTitle = viewModel.displayTitle
    val thresholdPx = with(density) { WindowInsets.statusBars.getTop(this) + ActiveWeekThreshold.toPx() }
    val currentOffsetPage = PageOffsets.lastIndex

    fun moveToCurrentWeek() {
        if (tab == MainTab.Timeline) {
            memberSelectionCount += 1
            activeVerticalWeek = 0
            weekHeaderMinYs.clear()
            viewModel.setWeek(0)
            return
        }

        if (pagerState.currentPage == currentOffsetPage) return
        scope.launch { pagerState.animateScrollToPage(currentOffsetPage, animationSpec = tween(320)) }
    }

    fun move(delta: Int) {
        val target = viewModel.weekOffset + delta
        if (target !in PageOffsets) return
        scope.launch { pagerState.animateScrollToPage(target - OldestOffset, animationSpec = tween(320)) }
    }

    fun select(contact: FamilyContact) {
        if (contact.id == viewModel.selectedContactId) return
        Haptics.focus()
        viewModel.selectMember(contact)
        viewModel.recordReportView(environment, AppEventCode.DIRECT)
        scope.launch { pagerState.scrollToPage(currentOffsetPage) }
        pageHeights.clear()
        activeVerticalWeek = 0
        weekHeaderMinYs.clear()
        memberSelectionCount += 1
        scope.launch { viewModel.refresh(environment) }
    }

    fun selectNeighbor(delta: Int) {
        val index = subjects.indexOfFirst { it.id == viewModel.selectedContactId }
        if (index < 0) return
        subjects.getOrNull(index + delta)?.let(::select)
    }

    fun updateActiveVerticalWeek(offset: Int, minY: Float) {
        weekHeaderMinYs[offset] = minY
        val active = weekHeaderMinYs
            .filter { it.value <= thresholdPx }
            .maxByOrNull { it.value }
            ?.key ?: 0

        if (active == activeVerticalWeek) return
        activeVerticalWeek = active
        viewModel.setWeek(active)
    }

    LaunchedEffect(Unit) {
        // 이용 기록: 리포트 탭에 들어온 순간(알림을 눌러 열었으면 NOTIFICATION)
        if (tab == MainTab.Report) viewModel.recordReportView(environment, environment.takeReportViewSource())
        viewModel.refresh(environment)
    }
    OnChange(environment.family.isLoaded) { _, _ -> viewModel.recordPendingReportView(environment) }
    // 앱으로 돌아왔을 때: 그사이 가족이 공유를 껐으면 보이던 기록을 남겨 두지 않는다(처음 들어올 때도 불리지만 아직 로드 전이라 건너뛴다)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { viewModel.revalidateSharing(environment) } }
    // 내 건강 앱을 연결·동기화·끊으면 내 리포트의 「생활 기록」을 다시 불러온다
    OnChange(healthLink.revision) { _, _ ->
        if (tab == MainTab.Report && viewModel.selectedContactId == TimelineViewModel.SelfContactId) {
            scope.launch { viewModel.refresh(environment, forceReload = true) }
        }
    }
    // 분석 완료 알림을 받으면(누르지 않고 배너로만 봐도) 이미 불러 둔 리포트를 다시 읽는다
    OnChange(environment.reportDataRevision) { _, _ ->
        scope.launch { viewModel.refresh(environment, forceReload = true) }
    }
    // 구매·복원으로 잠금이 풀리면 무료라 막혔던(402) 주를 다시 읽는다
    OnChange(isLocked) { wasLocked, locked ->
        if (wasLocked && !locked) scope.launch { viewModel.refresh(environment, forceReload = true) }
    }
    // 서버가 리포트를 402 로 막았는데 아는 요금제가 무료가 아니면 요금제를 다시 읽는다(타임라인 탭도 같이 잠기게)
    OnChange(viewModel.week.reportPremiumRequired) { _, required ->
        if (required && !isLocked) scope.launch { billing.load(environment) }
    }
    // 가족 목록보다 먼저 그려지면(꺼진 앱에서 리포트 알림을 눌러 바로 이 탭이 열림) 고를 가족이 없어 빈 화면에 머문다.
    // 목록이 오면 다시 고르고 불러온다.
    // 화면 범위에서 돌린다: 목록이 연달아 바뀌어 진행 중인 refresh가 취소되면 그 주가 로딩 중에 멈출 수 있다.
    OnChange(contacts) { _, _ -> scope.launch { viewModel.refresh(environment) } }
    OnChange(tabManager.reselectionCount) { _, _ ->
        if (tabManager.reselectedTab == tab) moveToCurrentWeek()
    }
    // iOS `onScrollPhaseChange(.idle)`: 가로 넘김이 멈추면 그 주로 옮기고 이웃 주까지 불러온다
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val paged = page + OldestOffset
            if (paged == viewModel.weekOffset) return@collect
            Haptics.focus()
            viewModel.setWeek(paged)
            scope.launch { viewModel.refresh(environment) }
        }
    }

    @Composable
    fun LoadingContent() {
        Column(
            Modifier.padding(horizontal = Spacing.x5).padding(top = Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .background(CollogColors.gray100, RoundedCornerShape(Radius.card)),
                )
            }
        }
    }

    @Composable
    fun ErrorContent(message: String, offset: Int) {
        EmptyState(
            symbol = "exclamationmark.circle",
            title = "데이터를 불러오지 못했어요.",
            message = message,
            actionTitle = "다시 시도",
            action = { scope.launch { viewModel.loadPage(offset, environment, forceReload = true) } },
        )
    }

    @Composable
    fun EmptyAnalysisContent(page: TimelineWeekPage) {
        // "나"를 골랐는데 기록이 없으면 전화·초대를 권하지 않는다(가족이 없을 때만 아래 초대 안내).
        // 분석 중이거나 가장 최근 통화가 분석에서 빠졌으면·실패했으면 그 사유는 가족과 같이 알린다
        val selfAction = AnalysisEmptyAction.resolve(page.calls, canCall = false, isCurrentWeek = page.offset == 0, isGuest = false)
        val explainsCall = selfAction in listOf(AnalysisEmptyAction.Processing, AnalysisEmptyAction.Excluded, AnalysisEmptyAction.Failed)
        if (isSelfSelected && contacts.isNotEmpty() && !explainsCall) {
            EmptyState(
                symbol = "doc.text.magnifyingglass",
                title = "아직 분석된 내 통화가 없어요.",
                message = "가족과 통화하고 분석이 끝나면 내 기록이 여기에 보여요.",
            )
            return
        }
        AnalysisEmptyState(
            onStartCall = { contact ->
                val userId = contact.userId ?: return@AnalysisEmptyState
                callCenter.startOutgoingCall(userId, contact.name, emptyList())
            },
            onRefresh = {
                environment.family.refresh(environment)
                viewModel.loadPage(page.offset, environment, forceReload = true)
            },
            // 통화 상대 목록에서만 찾는다: "나"를 고르면 null 이라 전화 걸기를 권하지 않는다
            contact = contacts.firstOrNull { it.id == viewModel.selectedContactId },
            calls = page.calls,
            isCurrentWeek = page.offset == 0,
            onCurrentWeek = ::moveToCurrentWeek,
            familySheet = { dismiss -> FamilyMembersSettingsScreen(onClose = dismiss) },
        )
    }

    @Composable
    fun TimelineEvent(entry: CallTimelineEntry, isLast: Boolean) {
        Row(
            Modifier.padding(horizontal = Spacing.x5).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
            verticalAlignment = Alignment.Top,
        ) {
            CallTimelineRail(isLast)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CallTimelineDateHeader(entry)
                val callId = entry.callId
                CallTimelineCard(
                    entry,
                    if (callId == null) Modifier else Modifier.pressable { navigation.push(CallAnalysisRoute(callId)) },
                )

                if (!isLast) Spacer(Modifier.height(Spacing.x3))
            }
        }
    }

    @Composable
    fun TimelineEntries(page: TimelineWeekPage) {
        val error = page.timelineError
        if (error != null) {
            ErrorContent(error, page.offset)
        } else if (page.timelineNotShared) {
            NotSharedEmptyState()
        } else if (page.entries.isEmpty()) {
            EmptyAnalysisContent(page)
        } else {
            Column {
                page.entries.forEachIndexed { index, entry ->
                    TimelineEvent(entry, isLast = index == page.entries.lastIndex)
                }
            }
        }
    }

    @Composable
    fun ReportPageContent(page: TimelineWeekPage) {
        val reportError = page.reportError
        val timelineError = page.timelineError
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            // 응급 안내(11-08 가드레일)는 리포트 맨 위. 누르면 그 통화의 분석 화면을 연다
            val notices = if (reportError == null) page.report.safetyNotices else emptyList()
            if (notices.isNotEmpty()) {
                Column(
                    Modifier.padding(horizontal = Spacing.x5).padding(top = Spacing.x2),
                    verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                ) {
                    notices.forEach { notice ->
                        SafetyNoticeCard(
                            notice.text,
                            if (notice.callId.isBlank()) Modifier else Modifier.pressable { navigation.push(CallAnalysisRoute(notice.callId)) },
                            caption = notice.dayText,
                        )
                    }
                }
            }

            if (reportError != null) {
                ErrorContent(reportError, page.offset)
            } else if (page.reportNotShared) {
                NotSharedEmptyState()
            } else if (page.reportPremiumRequired) {
                PremiumLockedState(onLearnMore = billing::presentPaywall)
            } else if (page.report.state == ReportState.Empty) {
                if (timelineError != null) ErrorContent(timelineError, page.offset) else EmptyAnalysisContent(page)
            } else {
                ReportContent(
                    page.report,
                    Modifier.padding(horizontal = Spacing.x5).padding(top = Spacing.x2),
                    isLoaded = true,
                )
            }

            // 생활 기록(건강 앱 연결). 내 리포트에서만 「연결하기」를 보인다
            page.lifeRecord?.let { record ->
                val isSelf = viewModel.selectedContactId == TimelineViewModel.SelfContactId
                LifeRecordCard(
                    record,
                    onConnect = if (isSelf) healthLink::present else null,
                    modifier = Modifier.padding(horizontal = Spacing.x5),
                )
            }
        }
    }

    @Composable
    fun WeekContent(page: TimelineWeekPage) {
        if (viewModel.selectedTabIndex == 1) TimelineEntries(page) else ReportPageContent(page)
    }

    @Composable
    fun PagerPage(offset: Int) {
        Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { if (it.height > 0) pageHeights[offset] = it.height },
            ) {
                // 현재 주와 이웃 주만 그린다
                if (abs(offset - viewModel.weekOffset) <= 1) {
                    val page = viewModel.page(offset)
                    if (page.isLoaded) WeekContent(page) else LoadingContent()
                }
            }
        }
    }

    @Composable
    fun VerticalWeek(offset: Int) {
        val page = viewModel.page(offset)
        val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
        // iOS LazyVStack의 `.task`: 화면 가까이 온 주만 불러온다
        var isNear by remember { mutableStateOf(offset == 0) }

        // 불러오는 중에 가족을 바꾸면 isLoaded가 false 그대로라, 가족도 키로 둬야 새 가족 것을 다시 부른다
        LaunchedEffect(isNear, page.isLoaded, viewModel.selectedContactId) {
            if (isNear && !page.isLoaded) viewModel.loadPage(offset, environment)
        }

        Column(
            Modifier
                .padding(bottom = Spacing.x8)
                .onGloballyPositioned {
                    if (!isNear && it.positionInRoot().y < screenHeightPx * 2) isNear = true
                },
        ) {
            if (offset != 0) {
                TimelineWeekHeader(
                    page.title,
                    Modifier.onGloballyPositioned { updateActiveVerticalWeek(offset, it.positionInRoot().y) },
                )
            }

            if (page.isLoaded) {
                TimelineEntries(page)
            } else {
                Box(Modifier.padding(bottom = Spacing.x5)) { LoadingContent() }
            }
        }
    }

    val currentPageHeight = with(density) {
        maxOf(pageHeights[viewModel.weekOffset]?.toDp() ?: 340.dp, 240.dp)
    }

    CompositionLocalProvider(LocalViewerAccent provides accent) {
    CollapsingHeaderScrollView(
        title = displayTitle,
        onRefresh = { viewModel.refresh(environment, forceReload = true) },
        scrollReset = tabManager.reselectionCount + memberSelectionCount,
        showsScrollToTopButton = tab == MainTab.Timeline,
        large = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
                ViewerProfileHeader(subjects, viewModel.selectedContactId, onSelect = ::select)
                CollapsingLargeTitle(displayTitle)
            }
        },
        trailing = { ViewerBadgeChip(viewModel.selectedMember) },
        sticky = if (tab == MainTab.Timeline && !isLocked) {
            { TimelineWeekHeader(viewModel.page(activeVerticalWeek).title) }
        } else {
            null
        },
        pinnedSticky = if (tab == MainTab.Timeline && !isLocked) {
            {
                val page = viewModel.page(activeVerticalWeek)
                TimelineWeekHeader(page.title, rangeText = page.timelineRangeText)
            }
        } else {
            null
        },
    ) {
        Column(Modifier.padding(bottom = Spacing.x8), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            if (isLocked) {
                PremiumLockedState(onLearnMore = billing::presentPaywall)
            } else if (tab == MainTab.Timeline) {
                // 타임라인 본문에서 옆으로 밀면 이전·다음 사람으로 바뀐다. 리포트는 주차 가로 넘기기가 있어 머리말에서만 바꾼다
                Column(
                    Modifier.pointerInput(subjects, viewModel.selectedContactId) {
                        var total = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragCancel = { total = 0f },
                            onDragEnd = { if (abs(total) > PersonSwipeDistance.toPx()) selectNeighbor(if (total < 0) 1 else -1) },
                        ) { _, amount -> total += amount }
                    },
                ) {
                    VerticalPageOffsets.forEach { offset -> VerticalWeek(offset) }
                }
            } else {
                Column {
                    WeekNavigator(
                        title = viewModel.week.title,
                        rangeText = viewModel.week.rangeText,
                        modifier = Modifier.background(accent.background),
                        canGoForward = viewModel.canGoForward,
                        onPrevious = { move(-1) },
                        onNext = { move(1) },
                    )

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().height(currentPageHeight),
                        verticalAlignment = Alignment.Top,
                    ) { index -> PagerPage(index + OldestOffset) }
                }
            }
        }
    }
    }
}
