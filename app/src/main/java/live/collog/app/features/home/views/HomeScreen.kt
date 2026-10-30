package live.collog.app.features.home.views

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.R
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.services.QuestionSpeaker
import live.collog.app.features.home.viewmodels.HomeViewModel
import live.collog.app.features.home.views.components.FamilyHealthCard
import live.collog.app.features.home.views.components.MemberBadge
import live.collog.app.features.settings.views.FamilyMembersSettingsScreen
import live.collog.app.shared.components.HealthQuestionCard
import live.collog.app.shared.components.emptystate.AnalysisEmptyState
import live.collog.app.shared.components.emptystate.EmptyState
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.navigation.MainTab
import live.collog.app.shared.navigation.Route
import live.collog.app.shared.services.api.CallSummaryDto
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.OnChange
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.designsystem.singleLine
import live.collog.app.shared.ui.pressable

/**
 * 피그마 「최종본」 홈(269:4879, 분석 전 269:4991). 탭 루트는 항상 그려 두고(스크롤 위치·불러온 값 유지),
 * 화면 스택에 밀어 넣은 화면은 그 위에 덮어 그린다. 가족 줄을 누르면 아래 전화 버튼과 같은 [onStartCall]로 건다.
 */
@Composable
fun HomeScreen(onStartCall: (FamilyContact) -> Unit) {
    val navigation = LocalNavigationManager.current
    val viewModel = remember { HomeViewModel() }

    Box(Modifier.fillMaxSize()) {
        HomeRoot(viewModel, onStartCall)

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
                    Route.HealthFeedbackDetail -> viewModel.healthFeedback?.let { HealthFeedbackDetailScreen(it) }
                    Route.Notifications -> HomeNotificationsScreen()
                }
            }
        }
    }
}

private enum class HomeTab(val title: String) {
    Questions("질문 미리보기"),
    CallLog("전화 로그"),
}

@Composable
private fun HomeRoot(viewModel: HomeViewModel, onStartCall: (FamilyContact) -> Unit) {
    val environment = LocalAppEnvironment.current
    val navigation = LocalNavigationManager.current
    val tabManager = LocalTabManager.current
    val callCenter = LocalCallCenter.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val refreshState = rememberPullToRefreshState()
    val speaker = remember { QuestionSpeaker(context) }
    var isRefreshing by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(HomeTab.Questions) }

    val contacts = environment.family.contacts
    val selectedContact = environment.family.selectedContact
    val questions = environment.family.questions(selectedContact).map { it.text }

    suspend fun refresh() {
        environment.family.refresh(environment)
        viewModel.refresh(environment, environment.family.selectedContact)
    }

    fun select(contact: FamilyContact) {
        if (environment.family.selectedContactId == contact.id) return
        Haptics.focus()
        speaker.stop()
        environment.family.selectContact(contact)
        scope.launch { refresh() }
    }

    fun call(contact: FamilyContact) {
        speaker.stop()
        Haptics.commit()
        onStartCall(contact)
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(speaker) { onDispose { speaker.release() } }
    OnChange(tab) { _, _ -> speaker.stop() }
    OnChange(callCenter.activeCall != null) { _, inCall -> if (inCall) speaker.stop() }
    OnChange(tabManager.reselectionCount) { _, _ -> scrollState.animateScrollTo(0, tween(320)) }

    Column(Modifier.fillMaxSize().background(CollogColors.gray00)) {
        TopBar(
            onProfile = { tabManager.selectedTab = MainTab.Settings },
            onNotifications = { navigation.push(Route.Notifications) },
        )

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
                    containerColor = CollogColors.gray00,
                    color = CollogColors.gray600,
                )
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.x6)
                    .padding(top = Spacing.x6, bottom = Spacing.x5),
                verticalArrangement = Arrangement.spacedBy(Spacing.x4),
            ) {
                if (contacts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x1), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_family_call),
                            contentDescription = null,
                            tint = CollogColors.greenNormal,
                            modifier = Modifier.size(24.dp),
                        )
                        Text("가족 전화하기", style = CollogType.subtitle01, color = CollogColors.gray1000)
                    }
                    contacts.forEach { contact ->
                        FamilyCallRow(contact, HomeViewModel.lastCallDayText(viewModel.lastCallAt[contact.id])) { call(contact) }
                    }
                }

                val summary = viewModel.healthSummary
                val error = viewModel.loadError ?: viewModel.recentCallsError ?: environment.family.loadError
                if (summary != null) {
                    FamilyHealthCard(
                        summary = summary,
                        feedback = viewModel.healthFeedback,
                        lastCallText = selectedContact?.let { viewModel.lastCallAt[it.id] }?.let { HomeViewModel.lastCallDayText(it) },
                        contacts = contacts,
                        onSelect = ::select,
                        onShowFamily = { navigation.push(Route.FamilyHealthOverview) },
                        onShowFeedback = { navigation.push(Route.HealthFeedbackDetail) },
                    )
                } else {
                    // 분석이 없는 가족을 골랐어도 다른 가족으로 돌아갈 수 있게 이름표를 남긴다
                    if (contacts.size > 1 && selectedContact != null) MemberBadge(selectedContact.name, contacts, ::select)
                    if (!viewModel.isLoaded) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = Spacing.x8),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.x2),
                        ) {
                            CollogProgress()
                            Text("통화 기록을 불러오고 있어요", style = CollogType.body03Medium, color = CollogColors.gray700)
                        }
                    } else if (error != null) {
                        EmptyState(
                            symbol = "exclamationmark.circle",
                            title = "데이터를 불러오지 못했어요.",
                            modifier = Modifier.cardSurface(),
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
                            modifier = Modifier.cardSurface(),
                            contact = selectedContact,
                            calls = viewModel.recentCalls,
                            familySheet = { onDismiss -> FamilyMembersSettingsScreen(onClose = onDismiss) },
                        )
                    }
                }

                HomeTabs(tab) { tab = it }

                when (tab) {
                    HomeTab.Questions -> QuestionPreview(questions, speaker)
                    HomeTab.CallLog -> CallLog(viewModel.recentCalls, viewModel.recentCallsError, viewModel.isLoaded)
                }
            }
        }
    }
}

/** 피그마 Top Navigator Bar(269:4882): 왼쪽 프로필, 오른쪽 알림·메뉴. 프로필과 메뉴는 설정 탭으로 간다. */
@Composable
private fun TopBar(onProfile: () -> Unit, onNotifications: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CollogColors.gray00)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = Spacing.x5)
            .padding(top = 10.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .pressable(onClick = onProfile)
                .background(CollogColors.gray00, CircleShape)
                .border(1.dp, CollogColors.greenNormal, CircleShape)
                .semantics { contentDescription = "내 정보" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_profile),
                contentDescription = null,
                tint = CollogColors.greenNormal,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        TopBarButton(R.drawable.ic_bell, "알림", onNotifications)
        TopBarButton(R.drawable.ic_menu, "메뉴", onProfile)
    }
}

@Composable
private fun TopBarButton(icon: Int, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .pressable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = CollogColors.gray600, modifier = Modifier.size(24.dp))
    }
}

/**
 * 피그마 가족 줄(269:4885). 피그마의 「휴대전화」와 방향 화살표 자리에는 받는 값이 없어 통화 경로 「콜록 통화」만 쓴다.
 */
@Composable
private fun FamilyCallRow(contact: FamilyContact, lastCall: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(75.dp)
            .pressable(onClick = onClick)
            .background(CollogColors.gray100, RoundedCornerShape(10.dp))
            .padding(start = Spacing.x2, end = Spacing.x6)
            .semantics { contentDescription = "${contact.name}에게 전화하기, 마지막 통화 $lastCall" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).background(CollogColors.avatarGradient, CircleShape))
        Column(
            Modifier.weight(1f).padding(start = 11.dp, end = Spacing.x2),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                contact.name,
                style = singleLine(Pretendard.Medium, 18f),
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("콜록 통화", style = singleLine(Pretendard.Medium, 14f), color = CollogColors.gray700)
        }
        Text(lastCall, style = singleLine(Pretendard.Medium, 14f), color = CollogColors.gray700)
    }
}

/** 피그마 Tab Bar(269:4946). */
@Composable
private fun HomeTabs(selection: HomeTab, onSelect: (HomeTab) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(CollogColors.gray300, shape)
            .padding(4.dp),
    ) {
        HomeTab.entries.forEach { tab ->
            val selected = tab == selection
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) CollogColors.gray00 else Color.Transparent, shape)
                    .pressable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.title,
                    style = CollogType.body02Semibold,
                    color = if (selected) CollogColors.gray900 else CollogColors.gray600,
                )
            }
        }
    }
}

/** 피그마 「질문 미리보기」(269:4947~269:4982): 고른 가족의 다음 통화 질문과 소리로 듣기 버튼. */
@Composable
private fun QuestionPreview(questions: List<String>, speaker: QuestionSpeaker) {
    Column {
        Text("다음 통화 전 미리 확인해보세요", style = singleLine(Pretendard.Medium, 15f), color = Color.Black)
        if (questions.isEmpty()) {
            Text(
                "아직 준비된 질문이 없어요",
                style = CollogType.body02Medium,
                color = CollogColors.gray700,
                modifier = Modifier.padding(top = Spacing.x4),
            )
            return@Column
        }

        Column(Modifier.padding(top = Spacing.x4), verticalArrangement = Arrangement.spacedBy(Spacing.x5)) {
            questions.forEach { HealthQuestionCard(it) }
        }
        Box(
            Modifier
                .padding(top = Spacing.x5)
                .fillMaxWidth()
                .height(50.dp)
                .pressable { speaker.toggle(questions) }
                .background(CollogColors.greenNormal, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (speaker.isSpeaking) "그만 듣기" else "통화 시작 전 질문 듣기",
                style = singleLine(Pretendard.SemiBold, 15f),
                color = CollogColors.gray00,
            )
        }
        speaker.error?.let {
            Text(it, style = CollogType.caption01Medium, color = CollogColors.red500, modifier = Modifier.padding(top = Spacing.x2))
        }
    }
}

/** 「전화 로그」: 고른 가족과의 최근 통화. 피그마에 화면이 없어 가족 줄 모양을 따른다. */
@Composable
private fun CallLog(calls: List<CallSummaryDto>, error: String?, isLoaded: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
        error?.let { Text(it, style = CollogType.body02Medium, color = CollogColors.red500) }
        if (calls.isEmpty() && error == null && isLoaded) {
            Text("아직 통화 기록이 없어요", style = CollogType.body02Medium, color = CollogColors.gray700)
        }
        calls.sortedByDescending { it.startedAt }.forEach { CallLogRow(it) }
    }
}

@Composable
private fun CallLogRow(call: CallSummaryDto) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 75.dp)
            .background(CollogColors.gray100, RoundedCornerShape(10.dp))
            .padding(horizontal = Spacing.x5, vertical = Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.x2), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(callDateText(call.startedAt), style = singleLine(Pretendard.Medium, 18f), color = Color.Black)
            Text(
                listOfNotNull(callTimeText(call.startedAt), callStateNote(call)).joinToString(" · "),
                style = singleLine(Pretendard.Medium, 14f),
                color = CollogColors.gray700,
            )
        }
        val seconds = call.durationSec ?: call.endedAt?.let { Duration.between(call.startedAt, it).seconds.toInt() }
        seconds?.let { Text(callDurationText(it), style = singleLine(Pretendard.Medium, 14f), color = CollogColors.gray700) }
    }
}

private val callDateFormatter = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val callTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/** 「10월 27일 월요일」 */
internal fun callDateText(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    callDateFormatter.format(date.atZone(zone))

/** 「오후 3:12」 */
internal fun callTimeText(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    callTimeFormatter.format(date.atZone(zone))

/** 「12분 4초」, 1분이 안 되면 「45초」. */
internal fun callDurationText(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    return if (total >= 60) "${total / 60}분 ${total % 60}초" else "${total}초"
}

private fun callStateNote(call: CallSummaryDto): String? = when {
    call.isAnalyzed -> null
    !call.recorded -> "녹음 안 함"
    call.state == "ENDED" -> "분석 대기 중"
    call.state == "PROCESSING" -> "분석 중"
    call.state == "ANALYSIS_EXCLUDED" -> "분석 제외"
    call.state == "ANALYSIS_FAILED" -> "분석 실패"
    else -> null
}
