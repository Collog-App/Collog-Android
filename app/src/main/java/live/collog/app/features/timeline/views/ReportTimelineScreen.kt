package live.collog.app.features.timeline.views

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.LocalCallCenter
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
import live.collog.app.features.timeline.views.components.WeekNavigator
import live.collog.app.shared.components.chip.FilterChipLabel
import live.collog.app.shared.components.emptystate.AnalysisEmptyState
import live.collog.app.shared.components.emptystate.EmptyState
import live.collog.app.shared.components.navbar.CollapsingHeaderScrollView
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.navigation.MainTab
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.OnChange
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

private const val OldestOffset = -25
private val PageOffsets = (OldestOffset..0).toList()
private val VerticalPageOffsets = PageOffsets.reversed()
private val ActiveWeekThreshold = 56.dp // 머리말 막대(52dp) 바로 아래

/**
 * iOS `ReportTimelineView`: 리포트 탭([initialTabIndex] 0)과 타임라인 탭(1)이 함께 쓰는 화면.
 * 리포트는 주 단위로 가로로 넘기고, 타임라인은 주를 세로로 이어 붙인다.
 */
@Composable
fun ReportTimelineScreen(initialTabIndex: Int = 1) {
    val environment = LocalAppEnvironment.current
    val tabManager = LocalTabManager.current
    val callCenter = LocalCallCenter.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val tab = if (initialTabIndex == 0) MainTab.Report else MainTab.Timeline
    val viewModel = remember { TimelineViewModel(selectedTabIndex = initialTabIndex) }
    val pagerState = rememberPagerState(initialPage = PageOffsets.lastIndex) { PageOffsets.size }
    val pageHeights = remember { mutableStateMapOf<Int, Int>() }
    var activeVerticalWeek by remember { mutableIntStateOf(0) }
    val weekHeaderMinYs = remember { mutableMapOf<Int, Float>() }
    var memberSelectionCount by remember { mutableIntStateOf(0) }

    val contacts = environment.family.contacts
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
        scope.launch { pagerState.scrollToPage(currentOffsetPage) }
        pageHeights.clear()
        activeVerticalWeek = 0
        weekHeaderMinYs.clear()
        memberSelectionCount += 1
        scope.launch { viewModel.refresh(environment) }
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

    LaunchedEffect(Unit) { viewModel.refresh(environment) }
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
        AnalysisEmptyState(
            onStartCall = { contact ->
                val userId = contact.userId ?: return@AnalysisEmptyState
                callCenter.startOutgoingCall(userId, contact.name, emptyList())
            },
            onRefresh = {
                environment.family.refresh(environment)
                viewModel.loadPage(page.offset, environment, forceReload = true)
            },
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

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                CallTimelineDateHeader(entry)
                CallTimelineCard(entry)

                if (!isLast) Spacer(Modifier.height(Spacing.x3))
            }
        }
    }

    @Composable
    fun TimelineEntries(page: TimelineWeekPage) {
        val error = page.timelineError
        if (error != null) {
            ErrorContent(error, page.offset)
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
        if (reportError != null) {
            ErrorContent(reportError, page.offset)
        } else if (page.report.state == ReportState.Empty) {
            if (timelineError != null) ErrorContent(timelineError, page.offset) else EmptyAnalysisContent(page)
        } else {
            ReportContent(
                page.report,
                Modifier.padding(horizontal = Spacing.x5).padding(top = Spacing.x2),
                isLoaded = true,
            )
        }
    }

    @Composable
    fun WeekContent(page: TimelineWeekPage) {
        if (viewModel.selectedTabIndex == 1) TimelineEntries(page) else ReportPageContent(page)
    }

    @Composable
    fun MemberSelector() {
        if (contacts.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                FilterChipLabel(viewModel.selectedMember, Modifier.pressable { expanded = true })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    contacts.forEach { contact ->
                        DropdownMenuItem(
                            text = { Text(contact.name, style = CollogType.body02Medium, color = CollogColors.gray900) },
                            leadingIcon = if (contact.id == viewModel.selectedContactId) {
                                { CollogIcon("checkmark", size = 20.dp, color = CollogColors.gray900) }
                            } else {
                                null
                            },
                            onClick = {
                                expanded = false
                                select(contact)
                            },
                        )
                    }
                }
            }
        } else {
            FilterChipLabel(viewModel.selectedMember)
        }
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

        LaunchedEffect(isNear, page.isLoaded) {
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

    CollapsingHeaderScrollView(
        title = viewModel.title,
        onRefresh = { viewModel.refresh(environment, forceReload = true) },
        scrollReset = tabManager.reselectionCount + memberSelectionCount,
        showsScrollToTopButton = tab == MainTab.Timeline,
        large = { MemberSelector() },
        sticky = if (tab == MainTab.Timeline) {
            { TimelineWeekHeader(viewModel.page(activeVerticalWeek).title) }
        } else {
            null
        },
        pinnedSticky = if (tab == MainTab.Timeline) {
            {
                val page = viewModel.page(activeVerticalWeek)
                TimelineWeekHeader(page.title, rangeText = page.timelineRangeText)
            }
        } else {
            null
        },
    ) {
        Column(Modifier.padding(bottom = Spacing.x8), verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
            if (tab == MainTab.Timeline) {
                Column {
                    VerticalPageOffsets.forEach { offset -> VerticalWeek(offset) }
                }
            } else {
                Column {
                    WeekNavigator(
                        title = viewModel.week.title,
                        rangeText = viewModel.week.rangeText,
                        modifier = Modifier.background(CollogColors.gray50),
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
