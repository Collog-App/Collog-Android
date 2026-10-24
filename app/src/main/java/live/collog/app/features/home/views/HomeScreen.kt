package live.collog.app.features.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.call.services.LocalCallCenter
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.home.models.HealthFeedback
import live.collog.app.features.home.services.QuestionGenerator
import live.collog.app.features.home.viewmodels.HomeViewModel
import live.collog.app.features.home.views.components.HealthStatusCard
import live.collog.app.features.home.views.components.QuestionList
import live.collog.app.features.settings.views.FamilyMembersSettingsScreen
import live.collog.app.shared.components.emptystate.AnalysisEmptyState
import live.collog.app.shared.components.emptystate.EmptyState
import live.collog.app.shared.components.navbar.CollapsingHeaderScrollView
import live.collog.app.shared.navigation.LocalNavigationManager
import live.collog.app.shared.navigation.LocalTabManager
import live.collog.app.shared.navigation.Route
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.cardSurface
import live.collog.app.shared.ui.pressable

/**
 * iOS `HomeView`. 탭 루트는 항상 그려 두고(스크롤 위치·불러온 값 유지),
 * 화면 스택에 밀어 넣은 화면은 그 위에 덮어 그린다.
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
                    Route.HealthFeedbackDetail -> viewModel.healthFeedback?.let { HealthFeedbackDetailScreen(it) }
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

    var isGeneratingQuestions by remember { mutableStateOf(false) }
    var questionGenerationId by remember { mutableStateOf<UUID?>(null) }
    var questionError by remember { mutableStateOf<String?>(null) }

    val contacts = environment.family.contacts
    val selectedContact = environment.family.selectedContact
    val selectedQuestions = environment.family.questions(selectedContact)

    suspend fun refresh() {
        environment.family.refresh(environment)
        viewModel.refresh(environment, environment.family.selectedContact)
    }

    fun completeQuestionGeneration(questions: List<String>, contact: FamilyContact, generationId: UUID) {
        if (questionGenerationId != generationId || environment.family.selectedContactId != contact.id) return
        environment.family.saveQuestions(questions, contact)
        questionGenerationId = null
        isGeneratingQuestions = false
        Haptics.commit()
    }

    fun generateQuestions() {
        val contact = selectedContact ?: return
        if (isGeneratingQuestions) return
        val generationId = UUID.randomUUID()
        questionGenerationId = generationId
        isGeneratingQuestions = true
        questionError = null
        val existing = selectedQuestions.map { it.text }
        val userId = environment.session.user?.id

        if (!environment.settings.isGuestMode) {
            scope.launch {
                try {
                    val parentId = if (environment.session.user?.role == "PARENT") userId else contact.userId
                    if (parentId == null) throw ApiError.Unauthenticated
                    val questions = environment.api.dailyQuestions(parentId)
                    if (environment.session.user?.id != userId) return@launch
                    completeQuestionGeneration(questions.map { it.text }, contact, generationId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (questionGenerationId != generationId || environment.session.user?.id != userId) return@launch
                    questionError = e.message
                    questionGenerationId = null
                    isGeneratingQuestions = false
                }
            }
            return
        }

        scope.launch {
            val generated = QuestionGenerator.generate(contact.name, existing)
            completeQuestionGeneration(generated, contact, generationId)
        }
        // 6초 안에 못 만들면 준비된 질문으로 채운다
        scope.launch {
            delay(6_000)
            completeQuestionGeneration(QuestionGenerator.fallback(existing), contact, generationId)
        }
    }

    fun select(contact: FamilyContact) {
        if (environment.family.selectedContactId == contact.id) return
        Haptics.focus()
        questionGenerationId = null
        isGeneratingQuestions = false
        questionError = null
        environment.family.selectContact(contact)
        scope.launch { refresh() }
    }

    LaunchedEffect(Unit) { refresh() }

    CollapsingHeaderScrollView(
        title = selectedContact?.name ?: "가족",
        onRefresh = { refresh() },
        scrollReset = tabManager.reselectionCount,
        large = { LargeTitle(contacts, selectedContact, viewModel.lastCallText, ::select) },
        trailing = {
            CollogIcon(
                "bell",
                Modifier
                    .size(40.dp)
                    .pressable { navigation.push(Route.Notifications) },
                color = CollogColors.gray900,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.x5)
                .padding(bottom = Spacing.x8),
        ) {
            val summary = viewModel.healthSummary
            val error = viewModel.loadError ?: viewModel.recentCallsError ?: environment.family.loadError
            if (summary != null) {
                HealthStatusCard(
                    summary = summary,
                    isLoaded = viewModel.isLoaded,
                    modifier = Modifier.padding(bottom = Spacing.x5),
                    onTap = { navigation.push(Route.FamilyHealthOverview) },
                )
            } else if (!viewModel.isLoaded) {
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
                Spacer(Modifier.size(Spacing.x5))
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
                Spacer(Modifier.size(Spacing.x5))
            }

            QuestionList(
                questions = selectedQuestions,
                modifier = Modifier.padding(bottom = Spacing.x4),
                isGenerating = isGeneratingQuestions,
                onTap = ::generateQuestions,
            )

            questionError?.let {
                Text(
                    it,
                    style = CollogType.caption01Medium,
                    color = CollogColors.red500,
                    modifier = Modifier.padding(bottom = Spacing.x4),
                )
            }
            viewModel.healthFeedback?.let { FeedbackRow(it) { navigation.push(Route.HealthFeedbackDetail) } }
        }
    }
}

@Composable
private fun LargeTitle(
    contacts: List<FamilyContact>,
    selected: FamilyContact?,
    lastCallText: String,
    onSelect: (FamilyContact) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        val name = selected?.name ?: "가족"
        if (contacts.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.pressable { expanded = true },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.x1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = CollogType.headline02, color = CollogColors.gray900)
                    CollogIcon("chevron.down", size = 18.dp, color = CollogColors.gray700)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = CollogColors.gray00,
                ) {
                    contacts.forEach { contact ->
                        DropdownMenuItem(
                            text = { Text(contact.name, style = CollogType.body02Medium, color = CollogColors.gray900) },
                            onClick = {
                                expanded = false
                                onSelect(contact)
                            },
                        )
                    }
                }
            }
        } else {
            Text(name, style = CollogType.headline02, color = CollogColors.gray900)
        }

        Text(lastCallText, style = CollogType.body03Medium, color = CollogColors.gray700)
    }
}

@Composable
private fun FeedbackRow(feedback: HealthFeedback, onClick: () -> Unit) {
    Row(
        Modifier.pressable(onClick = onClick).cardSurface(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
            Text(feedback.title, style = CollogType.caption01Medium, color = CollogColors.gray800)
            Text(feedback.headline, style = CollogType.body02Medium, color = CollogColors.gray900)
        }

        Spacer(Modifier.size(Spacing.x2))

        CollogIcon("chevron.right", size = 14.dp, color = CollogColors.gray500)
    }
}
