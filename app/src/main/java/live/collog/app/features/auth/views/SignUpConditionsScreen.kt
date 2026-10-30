package live.collog.app.features.auth.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.auth.viewmodels.saveOwnConditions
import live.collog.app.features.settings.models.HealthSelection
import live.collog.app.features.settings.views.HealthProfileEditor
import live.collog.app.shared.ui.designsystem.CollogColors

/**
 * 피그마 「최종본」 나의 질환(269:3917, 고른 질환 설명 277:5137). 설정의 질환 편집기를 가입 제목·버튼으로 쓴다.
 * 뒤로 가면 회원가입 정보로 돌아간다(저장된 값이 채워져 있다).
 */
@Composable
fun SignUpConditionsScreen(onBack: () -> Unit, onCompleted: () -> Unit) {
    val environment = LocalAppEnvironment.current
    BackHandler(onBack = onBack)
    HealthProfileEditor(
        initialSelection = HealthSelection(),
        initialConcerns = "",
        onCancel = onBack,
        onSave = { selection, concerns -> if (saveOwnConditions(environment, selection, concerns)) onCompleted() },
        modifier = Modifier.fillMaxSize().background(CollogColors.gray00).statusBarsPadding().navigationBarsPadding(),
        title = "회원가입",
        saveLabel = "콜록 시작하기",
    )
}
