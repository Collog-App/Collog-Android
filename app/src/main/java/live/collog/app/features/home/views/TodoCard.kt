package live.collog.app.features.home.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import live.collog.app.config.LocalAppEnvironment
import live.collog.app.features.home.models.canAddTodo
import live.collog.app.features.home.models.limitTodoInput
import live.collog.app.features.home.viewmodels.TodoViewModel
import live.collog.app.shared.services.api.Todo
import live.collog.app.shared.ui.designsystem.Pretendard
import live.collog.app.shared.ui.designsystem.SpecColors
import live.collog.app.shared.ui.designsystem.specCard
import live.collog.app.shared.ui.designsystem.specText
import live.collog.app.shared.ui.pressable

/**
 * 오늘 할 일 카드(피그마 없음, 오늘의 질문 카드 모양을 따른다). 내 할 일만 보이고 가족과 공유하지 않는다.
 * 줄: 둥근 체크 · 제목(끝내면 흐리게+취소선) · 추천 태그 · 삭제. 아래에 직접 추가 입력칸과 안내 문구.
 */
@Composable
internal fun TodoCard(viewModel: TodoViewModel) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }
    val canAdd = canAddTodo(input) && !isAdding

    fun submit() {
        if (!canAdd) return
        isAdding = true
        scope.launch {
            try {
                if (viewModel.add(environment, input)) input = ""
            } finally {
                isAdding = false
            }
        }
    }

    Column(Modifier.specCard(padding = 0.dp).padding(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 20.dp)) {
        Text("오늘 할 일", style = specText(Pretendard.Bold, 14f))
        Spacer(Modifier.height(16.dp))

        when {
            viewModel.loadFailed -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("할 일을 불러오지 못했어요", style = specText(Pretendard.Medium, 12f, SpecColors.muted), modifier = Modifier.weight(1f))
                Text(
                    "다시 시도",
                    style = specText(Pretendard.Bold, 12f, SpecColors.green),
                    modifier = Modifier.pressable { scope.launch { viewModel.load(environment) } }.padding(8.dp),
                )
            }
            viewModel.items.isEmpty() ->
                if (viewModel.isLoading) {
                    Text("할 일을 불러오고 있어요", style = specText(Pretendard.Medium, 12f, SpecColors.muted))
                } else {
                    Text("오늘 할 일이 없어요", style = specText(Pretendard.Medium, 12f, SpecColors.muted))
                }
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                viewModel.items.forEach { todo ->
                    TodoRow(
                        todo,
                        onToggle = { scope.launch { viewModel.toggle(environment, todo) } },
                        onDelete = { scope.launch { viewModel.delete(environment, todo) } },
                    )
                }
            }
        }

        viewModel.actionError?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = specText(Pretendard.Medium, 11f, SpecColors.destructive))
        }

        // 불러오기에 실패한 동안은 추가도 실패할 가능성이 커서 입력칸을 숨긴다
        if (!viewModel.loadFailed) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(SpecColors.chip, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (input.isEmpty()) {
                        Text("할 일을 직접 추가해 보세요", style = specText(Pretendard.Regular, 12f, SpecColors.muted))
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = limitTodoInput(it) },
                        singleLine = true,
                        textStyle = specText(Pretendard.Regular, 12f),
                        cursorBrush = SolidColor(SpecColors.green),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier
                        .height(40.dp)
                        .background(if (canAdd) SpecColors.green else SpecColors.tag, RoundedCornerShape(12.dp))
                        .pressable(enabled = canAdd, onClick = ::submit)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("추가", style = specText(Pretendard.Bold, 12f, if (canAdd) Color.White else SpecColors.muted))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "추천 활동은 등록한 질환과 걸음 수를 보고 골라요. 의료 조언이 아니에요.",
            style = specText(Pretendard.Regular, 10f, SpecColors.muted),
        )
    }
}

@Composable
private fun TodoRow(todo: Todo, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 누르는 면은 40dp, 그려지는 원은 22dp
        Box(
            Modifier
                .size(40.dp)
                .pressable(onClick = onToggle)
                .semantics { contentDescription = if (todo.done) "완료 취소" else "완료" },
            contentAlignment = Alignment.Center,
        ) {
            val circle = Modifier.size(22.dp)
            if (todo.done) {
                Box(circle.background(SpecColors.green, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            } else {
                Box(circle.border(1.5.dp, SpecColors.tickNeutral, CircleShape))
            }
        }
        Text(
            todo.title,
            style = specText(Pretendard.Medium, 13f, if (todo.done) SpecColors.muted else SpecColors.ink)
                .copy(textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (todo.isSuggested) {
            Spacer(Modifier.size(8.dp))
            Text(
                "추천",
                style = specText(Pretendard.Bold, 10f, SpecColors.green),
                modifier = Modifier.background(SpecColors.greenSurface, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(40.dp)
                .pressable(onClick = onDelete)
                .semantics { contentDescription = "삭제" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = SpecColors.muted, modifier = Modifier.size(16.dp))
        }
    }
}
