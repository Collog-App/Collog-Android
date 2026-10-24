package live.collog.app.features.auth.views

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.shared.services.api.ConsentDocument
import live.collog.app.shared.services.api.ConsentItemLabel
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendardPlain
import live.collog.app.shared.ui.pressable

/** iOS `ConsentView`. 문서 끝까지 스크롤하고 필수 항목을 모두 체크해야 동의할 수 있다. */
@Composable
fun ConsentScreen(onAgreed: () -> Unit, showsAccountActions: Boolean = true) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()

    var document by remember { mutableStateOf<ConsentDocument?>(null) }
    var agreedItems by remember { mutableStateOf(emptySet<String>()) }
    var hasReachedEnd by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val allAgreed = document?.let { agreedItems.containsAll(it.requiredItems) } ?: false
    val canSubmit = hasReachedEnd && allAgreed && !isSubmitting && !isLoading

    suspend fun load() {
        isLoading = true
        errorMessage = null
        hasReachedEnd = false
        agreedItems = emptySet()
        document = null
        try {
            document = environment.api.consentDocument()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = error.message
        } finally {
            isLoading = false
        }
    }

    fun submit(granted: Boolean) {
        val current = document ?: return
        if (isSubmitting) return
        if (granted && !canSubmit) return
        isSubmitting = true
        errorMessage = null
        val userId = environment.session.user?.id
        scope.launch {
            try {
                environment.api.submitConsent(
                    documentVersion = current.version,
                    agreedItems = if (granted) agreedItems.sorted() else emptyList(),
                    decision = if (granted) "GRANT" else "DENY",
                    scrolledToEnd = granted && hasReachedEnd,
                )
                if (environment.session.user?.id != userId) {
                    isSubmitting = false
                    return@launch
                }
                onAgreed()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message
            }
            isSubmitting = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // iOS `.id(document?.version)`: 문서가 바뀌면 스크롤 상태를 새로 만든다.
    val scroll = remember(document?.version) { ScrollState(0) }
    // 레이아웃이 끝난 뒤 끝까지 내려갔는지(또는 한 화면에 다 들어오는지) 본다.
    LaunchedEffect(document?.version, isLoading) {
        if (document == null || isLoading) return@LaunchedEffect
        withFrameNanos { }
        snapshotFlow { scroll.value >= scroll.maxValue - 8 }.collect { reachedEnd ->
            if (reachedEnd) hasReachedEnd = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray50)
            .safeDrawingPadding()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(Spacing.x4),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.x5).padding(top = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x2),
        ) {
            Text("통화 분석을 사용할까요?", style = CollogType.headline02, color = CollogColors.gray900)
            Text(
                "두 사람 모두 동의한 통화만 녹음하고 분석해요. 동의 없이도 일반 통화는 이용할 수 있어요.",
                style = CollogType.body02Medium,
                color = CollogColors.gray800,
            )
        }

        Column(
            Modifier
                .padding(horizontal = Spacing.x5)
                .fillMaxWidth()
                .background(CollogColors.gray00, RoundedCornerShape(Radius.card))
                .padding(Spacing.x4),
            verticalArrangement = Arrangement.spacedBy(Spacing.x4),
        ) {
            val shown = document
            if (shown != null) {
                Text(shown.fullText, style = CollogType.body02Medium, color = CollogColors.gray900)
                InfoRow("수집 항목", shown.collectedItems.joinToString(", "))
                InfoRow("이용 목적", shown.purpose)
                InfoRow("보관 기간", shown.retentionPeriod)
                InfoRow("원본 오디오", shown.rawAudioPolicy)
            } else if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CollogProgress() }
            } else {
                Text("동의 내용을 불러오지 못했어요.", style = CollogType.body02Medium, color = CollogColors.gray900)
                Text(
                    "다시 시도",
                    style = pretendardPlain(Pretendard.Medium, 16f, CollogColors.blue600),
                    modifier = Modifier.pressable { scope.launch { load() } },
                )
            }
        }

        Column(
            Modifier.padding(horizontal = Spacing.x5).padding(bottom = Spacing.x8),
            verticalArrangement = Arrangement.spacedBy(Spacing.x3),
        ) {
            document?.requiredItems?.forEach { item ->
                CheckRow(
                    item = item,
                    checked = item in agreedItems,
                    onToggle = { agreedItems = if (item in agreedItems) agreedItems - item else agreedItems + item },
                )
            }

            errorMessage?.let {
                Text(it, style = CollogType.caption01Medium, color = CollogColors.red500)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        if (canSubmit) CollogColors.greenNormal else CollogColors.gray500,
                        RoundedCornerShape(Radius.btnSmall),
                    )
                    .pressable(enabled = canSubmit) { submit(granted = true) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (hasReachedEnd) "동의하고 시작하기" else "끝까지 읽어주세요",
                    style = CollogType.body01Semibold,
                    color = CollogColors.gray00,
                )
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PlainTextAction(
                    "동의 없이 통화 이용하기",
                    enabled = document != null && !isSubmitting && !isLoading,
                ) { submit(granted = false) }
            }

            if (showsAccountActions) {
                OnboardingAccountActions(enabled = !isSubmitting)
            }
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.x1)) {
        Text(title, style = CollogType.caption01Medium, color = CollogColors.gray700)
        Text(value, style = CollogType.body03Medium, color = CollogColors.gray900)
    }
}

@Composable
private fun CheckRow(item: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onToggle)
            .semantics { stateDescription = if (checked) "선택됨" else "선택 안 됨" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (checked) CollogColors.greenNormal else CollogColors.gray400, CircleShape),
        )
        Text(ConsentItemLabel.korean(item), style = CollogType.body02Medium, color = CollogColors.gray900)
    }
}
