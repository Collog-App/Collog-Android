package live.collog.app.features.auth.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.shared.models.HealthCondition
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Radius
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

/** iOS `HealthProfileSetupView`. */
@Composable
fun HealthProfileSetupScreen(onCompleted: () -> Unit) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf(emptySet<HealthCondition>()) }
    var hasNoConditions by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canSubmit = (selected.isNotEmpty() || hasNoConditions) && !isSubmitting

    fun submit() {
        val parentId = environment.session.user?.id
        if (!canSubmit || parentId == null) return
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                environment.api.updateProfile(
                    parentId = parentId,
                    conditions = HealthCondition.entries.filter { it in selected }.map { it.rawValue },
                )
                onCompleted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message
            }
            isSubmitting = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CollogColors.gray50)
            .safeDrawingPadding()
            .padding(start = Spacing.x5, end = Spacing.x5, top = Spacing.x8, bottom = Spacing.x8),
        verticalArrangement = Arrangement.spacedBy(Spacing.x6),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            Text("관리 중인 질환을 알려주세요", style = CollogType.headline02, color = CollogColors.gray900)
            Text(
                "등록한 질환에 맞춰 오늘의 질문을 준비해드려요. 나중에 설정에서 바꿀 수 있어요.",
                style = CollogType.body02Medium,
                color = CollogColors.gray800,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2)) {
            HealthCondition.entries.forEach { condition ->
                ConditionRow(
                    title = condition.title,
                    isSelected = condition in selected,
                    enabled = !isSubmitting,
                ) {
                    hasNoConditions = false
                    selected = if (condition in selected) selected - condition else selected + condition
                }
            }
            ConditionRow(title = "해당 없음", isSelected = hasNoConditions, enabled = !isSubmitting) {
                selected = emptySet()
                hasNoConditions = !hasNoConditions
            }
        }

        errorMessage?.let {
            Text(it, style = CollogType.caption01Medium, color = CollogColors.red500)
        }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    if (!canSubmit) CollogColors.gray500 else CollogColors.greenNormal,
                    RoundedCornerShape(Radius.btnSmall),
                )
                .pressable(enabled = canSubmit) { submit() },
            contentAlignment = Alignment.Center,
        ) {
            Text("완료", style = CollogType.body01Semibold, color = CollogColors.gray00)
        }

        OnboardingAccountActions(enabled = !isSubmitting)
    }
}

@Composable
private fun ConditionRow(title: String, isSelected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.btnSmall)
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(CollogColors.gray00, shape)
            .border(1.dp, if (isSelected) CollogColors.greenNormal else Color.Transparent, shape)
            .pressable(enabled = enabled, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = Spacing.x4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = CollogType.body02Medium,
            color = if (isSelected) CollogColors.gray900 else CollogColors.gray800,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(20.dp)
                .background(if (isSelected) CollogColors.greenNormal else CollogColors.gray300, CircleShape),
        )
    }
}
