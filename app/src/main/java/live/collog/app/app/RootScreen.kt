package live.collog.app.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.viewmodels.AuthFlowViewModel
import live.collog.app.features.auth.views.ConsentScreen
import live.collog.app.features.auth.views.HealthProfileSetupScreen
import live.collog.app.features.auth.views.InvitationAcceptScreen
import live.collog.app.features.auth.views.LoginScreen
import live.collog.app.features.auth.views.OnboardingScreen
import live.collog.app.features.call.models.CallPhase
import live.collog.app.features.call.viewmodels.CallLauncherModel
import live.collog.app.features.call.views.CallLauncherOverlay
import live.collog.app.features.call.views.CallScreen
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.views.HomeScreen
import live.collog.app.features.settings.views.SettingsScreen
import live.collog.app.features.timeline.views.ReportTimelineScreen
import live.collog.app.shared.components.tabbar.BottomNavBar
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.navigation.LocalNavigationStore
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.navigation.MainTab
import live.collog.app.shared.navigation.NavigationStore
import live.collog.app.shared.navigation.TabManager
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.OnChange
import live.collog.app.shared.ui.designsystem.CollogColors

/** iOS `RootView`. 인증 단계에 따라 화면을 고르고, 준비되면 탭 화면과 통화 화면을 띄운다. */
@Composable
fun RootScreen() {
    val environment = LocalAppEnvironment.current
    val tabManager = remember { TabManager() }
    var navigation by remember { mutableStateOf(NavigationStore()) }
    val authFlow = remember { AuthFlowViewModel() }
    val launcher = remember { CallLauncherModel() }
    val scope = rememberCoroutineScope()

    var simulatedContact by remember { mutableStateOf<FamilyContact?>(null) }
    var simulatedPhase by remember { mutableStateOf(CallPhase.Connecting) }
    var simulationJob by remember { mutableStateOf<Job?>(null) }

    val isGuest = environment.settings.isGuestMode
    val resolve: () -> Unit = { scope.launch { authFlow.resolve(environment) } }

    fun endSimulatedCall() {
        simulationJob?.cancel()
        simulationJob = null
        simulatedContact = null
    }

    fun startSimulatedCall(contact: FamilyContact) {
        simulationJob?.cancel()
        simulatedPhase = CallPhase.Connecting
        simulatedContact = contact
        simulationJob = scope.launch {
            delay(1_600)
            simulatedPhase = CallPhase.Ringing
            delay(2_000)
            simulatedPhase = CallPhase.Active
            Haptics.commit()
        }
    }

    fun startCall(contact: FamilyContact) {
        if (isGuest) {
            startSimulatedCall(contact)
            return
        }
        // TODO(call): CallCenter.startOutgoingCall 연결
    }

    LaunchedEffect(Unit) { authFlow.resolve(environment) }
    OnChange(environment.session.isAuthenticated) { _, authenticated ->
        if (!authenticated) launcher.dismiss()
        authFlow.resolve(environment)
    }
    OnChange(environment.settings.isGuestMode) { _, _ ->
        environment.family.reset()
        authFlow.resolve(environment)
    }
    OnChange(environment.session.user) { previous, current ->
        if (previous == null || current == null) return@OnChange
        if (previous.role == current.role && previous.familyId == current.familyId) return@OnChange
        launcher.dismiss()
        navigation = NavigationStore()
        tabManager.selectedTab = MainTab.Home
        authFlow.resolve(environment)
    }
    OnChange(environment.reportNotificationRevision) { _, _ ->
        navigation.popToRoot(MainTab.Report)
        tabManager.selectedTab = MainTab.Report
    }

    CompositionLocalProvider(LocalTabManager provides tabManager, LocalNavigationStore provides navigation) {
        Box(Modifier.fillMaxSize().background(CollogColors.gray50)) {
            Crossfade(authFlow.step, animationSpec = tween(200), label = "auth") { step ->
                when (step) {
                    AuthFlowViewModel.Step.Launching -> Box(Modifier.fillMaxSize().background(CollogColors.gray50))
                    AuthFlowViewModel.Step.Onboarding -> OnboardingScreen {
                        environment.settings.onboardingCompleted = true
                        resolve()
                    }
                    AuthFlowViewModel.Step.Login -> LoginScreen { resolve() }
                    AuthFlowViewModel.Step.Consent -> ConsentScreen { resolve() }
                    AuthFlowViewModel.Step.Invitation -> InvitationAcceptScreen { resolve() }
                    AuthFlowViewModel.Step.Profile -> HealthProfileSetupScreen { resolve() }
                    AuthFlowViewModel.Step.Ready -> {
                        val identity = if (isGuest) {
                            "guest"
                        } else {
                            "${environment.session.user?.id.orEmpty()}-${environment.session.user?.role.orEmpty()}"
                        }
                        key(identity) {
                            MainTabs(
                                tabManager = tabManager,
                                navigation = navigation,
                                launcher = launcher,
                                isGuest = isGuest,
                                onStartCall = ::startCall,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = simulatedContact != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                val contact = simulatedContact
                if (contact != null) {
                    BackHandler { endSimulatedCall() }
                    CallScreen(
                        peerName = contact.name,
                        phase = simulatedPhase,
                        questions = environment.family.questions(contact).map { it.text },
                        notice = "체험 모드입니다. 통화 화면만 미리 보여드려요",
                        onEnd = ::endSimulatedCall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabs(
    tabManager: TabManager,
    navigation: NavigationStore,
    launcher: CallLauncherModel,
    isGuest: Boolean,
    onStartCall: (FamilyContact) -> Unit,
) {
    val environment = LocalAppEnvironment.current
    val family = environment.family

    fun syncLauncher() {
        val callable = family.callableContacts
        launcher.configure(
            targets = if (isGuest || callable.isEmpty()) family.contacts else callable,
            questions = family.selectedQuestionTexts,
        )
    }

    fun reselect(tab: MainTab) {
        val isAtRoot = navigation.isAtRoot(tab)
        navigation.popToRoot(tab)
        if (isAtRoot) tabManager.reselect(tab)
    }

    LaunchedEffect(Unit) {
        family.refresh(environment)
        syncLauncher()
    }
    OnChange(family.contacts) { _, _ -> syncLauncher() }
    OnChange(family.selectedQuestionTexts) { _, _ -> syncLauncher() }

    val tab = tabManager.selectedTab
    val manager = navigation.manager(tab)
    BackHandler(enabled = manager.path.isNotEmpty()) { manager.pop() }
    BackHandler(enabled = launcher.isPresented) { launcher.dismiss() }

    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().background(CollogColors.gray50)) {
        Column(Modifier.fillMaxSize().padding(bottom = BottomNavBar.barHeight + navigationBarInset)) {
            Crossfade(tab to environment.reportNotificationRevision, animationSpec = tween(180), label = "tab") { (shown, _) ->
                CompositionLocalProvider(LocalNavigationManager provides navigation.manager(shown)) {
                    when (shown) {
                        MainTab.Home -> HomeScreen()
                        MainTab.Report -> ReportTimelineScreen(initialTabIndex = 0)
                        MainTab.Timeline -> ReportTimelineScreen(initialTabIndex = 1)
                        MainTab.Settings -> SettingsScreen()
                    }
                }
            }
        }

        if (launcher.isPresented) {
            CallLauncherOverlay(
                model = launcher,
                anchorInset = BottomNavBar.anchorInset,
                notice = if (isGuest) "체험 모드입니다. 통화 화면만 미리 보여드려요" else null,
                onSelect = { index -> launcher.select(index)?.let(onStartCall) },
                onDismiss = { launcher.dismiss() },
            )
        }

        BottomNavBar(
            selection = tab,
            onSelectionChange = { tabManager.selectedTab = it },
            launcher = launcher,
            onLaunch = onStartCall,
            onReselect = ::reselect,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}
