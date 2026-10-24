package live.collog.app.features.auth.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.viewmodels.LoginViewModel
import live.collog.app.shared.models.UserRoleOption
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.SegmentedPicker
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendardPlain

/** iOS `LoginView`. Apple 버튼 자리에 Google 버튼을 둔다. */
@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val viewModel = remember { LoginViewModel() }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

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

    Box(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray50)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focus.clearFocus()
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.x5, vertical = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x6),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                Text("Google 계정으로 시작하기", style = CollogType.headline02, color = CollogColors.gray900)
                Text("역할을 선택하고 가족과 통화를 시작하세요.", style = CollogType.body02Medium, color = CollogColors.gray800)
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
                Text("역할", style = CollogType.caption01Medium, color = CollogColors.gray800)
                SegmentedPicker(
                    options = UserRoleOption.entries,
                    selection = viewModel.role,
                    onSelect = { viewModel.role = it },
                    label = { it.title },
                    enabled = !viewModel.isSubmitting,
                )

                Text("가족에게 표시할 이름", style = CollogType.caption01Medium, color = CollogColors.gray800)
                BasicTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it },
                    enabled = !viewModel.isSubmitting,
                    singleLine = true,
                    textStyle = pretendardPlain(Pretendard.Medium, 16f),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    decorationBox = { field ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(CollogColors.gray00, RoundedCornerShape(Radius.btnSmall))
                                .padding(horizontal = Spacing.x4),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (viewModel.name.isEmpty()) {
                                Text(
                                    "비워두면 Google 계정 이름을 사용해요",
                                    style = pretendardPlain(Pretendard.Medium, 16f, CollogColors.gray600),
                                )
                            }
                            field()
                        }
                    },
                )
            }

            (viewModel.errorMessage ?: environment.session.storageError)?.let {
                Text(it, style = CollogType.caption01Medium, color = CollogColors.red500)
            }

            GoogleSignInButton(enabled = viewModel.canSignIn) {
                focus.clearFocus()
                scope.launch {
                    if (viewModel.signIn(context, environment)) onSignedIn()
                }
            }

            if (viewModel.isLoadingChallenge || viewModel.isSubmitting) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CollogProgress() }
            } else if (!viewModel.canSignIn) {
                TextButton(
                    onClick = { scope.launch { viewModel.prepareChallenge(environment) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("로그인 다시 준비하기", style = CollogType.body02Semibold, color = CollogColors.blue600)
                }
            }

            GuestButton(enabled = !viewModel.isSubmitting) {
                focus.clearFocus()
                environment.settings.isGuestMode = true
                onSignedIn()
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(if (enabled) Color.Black else CollogColors.gray500, RoundedCornerShape(Radius.btnSmall))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("Google로 계속하기", style = CollogType.body01Semibold, color = CollogColors.gray00)
    }
}

@Composable
private fun GuestButton(enabled: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(CollogColors.gray00, RoundedCornerShape(Radius.btnSmall))
                .border(1.dp, CollogColors.gray300, RoundedCornerShape(Radius.btnSmall))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("가입 없이 둘러보기", style = CollogType.body02Semibold, color = CollogColors.gray800)
        }
        Text(
            "예시 데이터로 앱을 먼저 둘러볼 수 있어요",
            style = CollogType.caption01Medium,
            color = CollogColors.gray700,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
