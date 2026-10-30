package live.collog.app.features.auth.views

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.viewmodels.TermsAgreement
import live.collog.app.features.auth.viewmodels.TermsItem
import live.collog.app.features.auth.viewmodels.grantConsent
import live.collog.app.features.auth.views.components.AuthBlockButton
import live.collog.app.features.auth.views.components.AuthCheckBox
import live.collog.app.features.auth.views.components.InfoIconRow
import live.collog.app.features.settings.views.components.SettingsAlert
import live.collog.app.features.settings.views.components.SettingsBullet
import live.collog.app.features.settings.views.components.SettingsCallout
import live.collog.app.features.settings.views.components.SettingsDetailHeader
import live.collog.app.features.settings.views.components.SystemTextButton
import live.collog.app.shared.services.api.ConsentDocument
import live.collog.app.shared.services.api.ConsentItemLabel
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.designsystem.pretendard
import live.collog.app.shared.ui.pressable

/** 서비스 이용약관 주소. 생기면 넣는다(그 전까지 「보기」를 숨긴다). */
private val SERVICE_TERMS_URL: String? = null
private const val PRIVACY_URL = "https://privacy.collog.live"

/**
 * 피그마 「최종본」 약관 동의(269:3944, 펼친 모양 269:3954). 위치정보 약관은 위치를 모으지 않아 뺐다.
 * 개인정보 동의서는 서버 동의 문서를 피그마의 세 덩어리로 펼쳐 보이고, 서버 규칙대로 끝까지 내려 읽어야 넘어간다.
 * 「다음으로」는 서버에 분석 동의(GRANT)를 남긴다. 뒤로 가면 로그아웃을 묻는다.
 */
@Composable
fun TermsScreen(onAgreed: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var document by remember { mutableStateOf<ConsentDocument?>(null) }
    var agreement by remember { mutableStateOf(TermsAgreement()) }
    var showsDocument by remember { mutableStateOf(false) }
    var hasReachedEnd by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmsSignOut by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    suspend fun load() {
        isLoading = true
        errorMessage = null
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

    fun update(next: TermsAgreement) {
        // 피그마 05: 동의서에 체크하면 내용이 펼쳐진다
        if (next.isChecked(TermsItem.Privacy) && !agreement.isChecked(TermsItem.Privacy)) showsDocument = true
        agreement = next
    }

    fun submit() {
        val current = document ?: return
        if (!agreement.allChecked || !hasReachedEnd || isSubmitting) return
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                if (grantConsent(environment, current)) onAgreed()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message
            } finally {
                isSubmitting = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }
    // 동의서를 펼친 뒤 화면 끝까지 내려갔는지(한 화면에 다 들어오면 바로) 본다. 서버는 끝까지 읽지 않은 동의를 받지 않는다.
    LaunchedEffect(showsDocument, document) {
        if (!showsDocument || document == null) return@LaunchedEffect
        withFrameNanos { }
        snapshotFlow { scroll.value >= scroll.maxValue - 8 }.collect { if (it) hasReachedEnd = true }
    }
    BackHandler { confirmsSignOut = true }

    val canSubmit = agreement.allChecked && hasReachedEnd && document != null && !isSubmitting
    Column(Modifier.fillMaxSize().background(CollogColors.gray00).statusBarsPadding()) {
        SettingsDetailHeader("약관 동의", onBack = { confirmsSignOut = true })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.x5)
                .padding(top = Spacing.x5, bottom = Spacing.x5),
            verticalArrangement = Arrangement.spacedBy(Spacing.x5),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x4)) {
                CheckRow("전체 동의하기", agreement.allChecked, isAll = true) { update(agreement.togglingAll()) }
                TermsItem.entries.forEach { item ->
                    val link: (() -> Unit)? = when (item) {
                        TermsItem.Service -> SERVICE_TERMS_URL?.let { url -> { context.open(url) } }
                        TermsItem.Privacy -> ({ showsDocument = !showsDocument })
                        TermsItem.Adult -> null
                    }
                    CheckRow(
                        item.title,
                        agreement.isChecked(item),
                        linkText = if (item == TermsItem.Privacy && showsDocument) "접기" else "보기",
                        onLink = link,
                    ) { update(agreement.toggling(item)) }
                }
            }

            if (showsDocument) {
                val shown = document
                if (shown != null) {
                    ConsentDocumentCallout(shown) { context.open(PRIVACY_URL) }
                } else if (isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CollogProgress() }
                }
            }

            if (document == null && !isLoading) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
                    Text("동의 내용을 불러오지 못했어요.", style = CollogType.body02Medium, color = CollogColors.gray900)
                    SystemTextButton("다시 시도") { scope.launch { load() } }
                }
            }
            errorMessage?.let { Text(it, style = CollogType.caption01Medium, color = CollogColors.red500) }
        }

        AuthBlockButton(
            text = if (agreement.allChecked && document != null && !hasReachedEnd) "끝까지 읽어주세요" else "다음으로",
            enabled = canSubmit,
            onClick = ::submit,
        )
    }

    if (confirmsSignOut) {
        SettingsAlert(
            title = "로그아웃할까요?",
            message = "처음 화면으로 돌아가요. 다시 로그인하면 여기서부터 이어서 할 수 있어요.",
            confirmText = "로그아웃",
            onConfirm = { scope.launch { environment.signOut() } },
            onDismiss = { confirmsSignOut = false },
        )
    }
}

/** 피그마 Checkbox group(269:3950): 전체 동의는 굵게, 항목은 32dp 들여 쓴다. [onLink]가 있으면 오른쪽에 「보기」. */
@Composable
private fun CheckRow(
    title: String,
    checked: Boolean,
    isAll: Boolean = false,
    linkText: String = "보기",
    onLink: (() -> Unit)? = null,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (isAll) 0.dp else 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .semantics {
                    role = Role.Checkbox
                    stateDescription = if (checked) "선택됨" else "선택 안 됨"
                }
                .pressable(onClick = onToggle),
            horizontalArrangement = Arrangement.spacedBy(Spacing.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuthCheckBox(checked)
            Text(
                title,
                style = pretendard(if (isAll) Pretendard.SemiBold else Pretendard.Regular, 16f),
                color = if (isAll) CollogColors.gray1000 else CollogColors.gray900,
            )
        }
        if (onLink != null) {
            Text(
                linkText,
                style = pretendard(Pretendard.Regular, 14f).copy(textDecoration = TextDecoration.Underline),
                color = CollogColors.gray700,
                modifier = Modifier.padding(start = Spacing.x2).pressable(onClick = onLink),
            )
        }
    }
}

/**
 * 피그마 05 Call Out(269:3962)의 세 덩어리에 서버 동의 문서를 나눠 담는다:
 * 수집·이용 목적(목적, 수집 항목) / 원본 오디오 처리 원칙(원본 음성, 보관 기간) / 동의 범위 및 권리(동의 항목, 안내 전문).
 */
@Composable
private fun ConsentDocumentCallout(document: ConsentDocument, onOpenPolicy: () -> Unit) {
    SettingsCallout(spacing = 36.dp) {
        DocumentBlock("수집·이용 목적", document.purpose, "수집 항목: " + document.collectedItems.joinToString(", "))
        DocumentBlock("원본 오디오 처리 원칙", document.rawAudioPolicy, document.retentionPeriod)
        DocumentBlock(
            "동의 범위 및 권리",
            "동의 항목: " + document.requiredItems.joinToString(", ") { ConsentItemLabel.korean(it) },
            document.fullText,
        )
        Text(
            "개인정보처리방침 전문 보기",
            style = pretendard(Pretendard.Regular, 14f).copy(textDecoration = TextDecoration.Underline),
            color = CollogColors.gray800,
            modifier = Modifier.padding(start = 28.dp).pressable(onClick = onOpenPolicy),
        )
    }
}

/** 피그마 05 한 덩어리: 안내 아이콘과 제목, 글머리표 문단, 그 아래 같은 들여쓰기의 문단. */
@Composable
private fun DocumentBlock(title: String, lead: String, detail: String) {
    InfoIconRow {
        Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
            Text(title, style = pretendard(Pretendard.SemiBold, 14f), color = CollogColors.gray800)
        }
        Column(Modifier.padding(top = Spacing.x4), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SettingsBullet(lead)
            Text(detail, style = CollogType.body02Regular, color = CollogColors.gray800, modifier = Modifier.padding(start = 22.dp))
        }
    }
}

private fun android.content.Context.open(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
