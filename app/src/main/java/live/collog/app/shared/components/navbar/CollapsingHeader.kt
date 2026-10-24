package live.collog.app.shared.components.navbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import live.collog.app.shared.ui.CollogProgress
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.OnChange
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

private val CollapseDistance = 44.dp
private val BarHeight = 52.dp
private val RefreshThreshold = 68.dp
private val RefreshHoldHeight = 44.dp
private val ScrollToTopShowOffset = 520.dp

/** iOS `CollapsingLargeTitle`. */
@Composable
fun CollapsingLargeTitle(title: String) {
    Text(title, style = CollogType.headline02, color = CollogColors.gray900)
}

/**
 * iOS `CollapsingHeaderScrollView`: 큰 제목이 스크롤하면 위쪽 막대의 작은 제목으로 바뀐다.
 *
 * - [large]: 본문 맨 위에 놓는 큰 머리말(기본은 [title] 큰 글씨). 스크롤하면 흐려진다.
 * - [trailing]: 막대 오른쪽 끝에 놓는 내용.
 * - [sticky] / [pinnedSticky]: 둘 다 주면 [sticky]가 막대 아래에 닿을 때 [pinnedSticky]가 막대 밑에 고정된다.
 * - [onRefresh]: 맨 위에서 끌어내리면(임계값 68dp) 불린다. 새로고침 표시는 최소 0.65초 보인다.
 * - [scrollReset]: 값이 바뀌면 맨 위로 되돌린다.
 *
 * 상태 표시줄 높이는 막대 안에서 처리한다(iOS의 안전 영역 대응).
 */
@Composable
fun CollapsingHeaderScrollView(
    title: String,
    modifier: Modifier = Modifier,
    onRefresh: (suspend () -> Unit)? = null,
    scrollReset: Int = 0,
    showsScrollToTopButton: Boolean = false,
    large: @Composable () -> Unit = { CollapsingLargeTitle(title) },
    trailing: @Composable RowScope.() -> Unit = {},
    sticky: (@Composable () -> Unit)? = null,
    pinnedSticky: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val statusBarDp = with(density) { statusBarPx.toDp() }
    val barPx = with(density) { BarHeight.toPx() }
    val collapsePx = with(density) { CollapseDistance.toPx() }
    val thresholdPx = with(density) { RefreshThreshold.toPx() }
    val scrollToTopPx = with(density) { ScrollToTopShowOffset.toPx() }

    var isRefreshing by remember { mutableStateOf(false) }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var isRefreshArmed by remember { mutableStateOf(false) }
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var stickyTop by remember { mutableFloatStateOf(Float.MAX_VALUE) }

    fun collapseProgress(): Float = (scrollState.value / collapsePx).coerceIn(0f, 1f)

    val isSticky = sticky != null && stickyTop - viewportTop <= statusBarPx + barPx
    val shouldShowScrollToTopButton = showsScrollToTopButton && scrollState.value > scrollToTopPx && !isRefreshing

    fun finishPull() {
        if (isRefreshing) return
        pullDistance = 0f
        val refresh = currentOnRefresh
        if (!isRefreshArmed || refresh == null) {
            isRefreshArmed = false
            return
        }
        isRefreshArmed = false
        isRefreshing = true
        scope.launch {
            try {
                val minimumDuration = launch { delay(650) }
                refresh()
                minimumDuration.join()
            } finally {
                isRefreshing = false
            }
        }
    }

    // iOS는 맨 위 고무줄 늘어남(음수 오프셋)으로 당김을 재지만, 안드로이드는 맨 위에서 남는 끌기량으로 대신한다.
    // 손가락 움직임의 절반만 늘어남으로 센다(고무줄 저항).
    val pullConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullDistance <= 0f || available.y >= 0f) return Offset.Zero
                val consumed = min(-available.y, pullDistance * 2)
                pullDistance -= consumed / 2
                return Offset(0f, -consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                if (currentOnRefresh == null || isRefreshing || scrollState.value > 0) return Offset.Zero
                pullDistance += available.y / 2
                if (pullDistance >= thresholdPx && !isRefreshArmed) {
                    isRefreshArmed = true
                    Haptics.focus()
                }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDistance > 0f || isRefreshArmed) finishPull()
                return Velocity.Zero
            }
        }
    }

    OnChange(scrollReset) { _, _ -> scrollState.animateScrollTo(0, tween(320)) }

    Box(
        modifier
            .fillMaxSize()
            .background(CollogColors.gray50)
            .onGloballyPositioned { viewportTop = it.positionInRoot().y }
            .nestedScroll(pullConnection),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pullDistance }
                .verticalScroll(scrollState)
                .padding(top = statusBarDp + BarHeight),
        ) {
            val indicatorHeight by animateDpAsState(
                if (isRefreshing) RefreshHoldHeight else 0.dp,
                tween(240),
                label = "refreshHold",
            )
            Box(Modifier.fillMaxWidth().height(indicatorHeight), contentAlignment = Alignment.Center) {
                if (isRefreshing) CollogProgress()
            }

            Box(
                Modifier
                    .padding(start = Spacing.x5, end = Spacing.x5, top = Spacing.x2, bottom = Spacing.x6)
                    .graphicsLayer { alpha = 1f - collapseProgress() },
            ) { large() }

            if (sticky != null) {
                Box(
                    Modifier
                        .background(CollogColors.gray50)
                        .onGloballyPositioned { stickyTop = it.positionInRoot().y }
                        .graphicsLayer { alpha = if (isSticky) 0f else 1f },
                ) { sticky() }
            }

            content()
        }

        if (pinnedSticky != null) {
            AnimatedVisibility(
                visible = isSticky,
                modifier = Modifier.align(Alignment.TopStart).padding(top = statusBarDp + BarHeight),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(Modifier.background(CollogColors.gray50)) { pinnedSticky() }
            }
        }

        // 위쪽 막대: 작은 제목은 스크롤에 따라 나타난다
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().background(CollogColors.gray50).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = Spacing.x5),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = CollogType.body01Semibold,
                    color = CollogColors.gray900,
                    modifier = Modifier.graphicsLayer { alpha = collapseProgress() },
                )
                Spacer(Modifier.weight(1f).padding(start = Spacing.x2))
                trailing()
            }
        }

        AnimatedVisibility(
            visible = shouldShowScrollToTopButton,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.x5, bottom = Spacing.x5),
            enter = scaleIn(tween(220), initialScale = 0.72f) + fadeIn(tween(220)),
            exit = scaleOut(tween(220), targetScale = 0.72f) + fadeOut(tween(220)),
        ) {
            Box(
                Modifier
                    .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                    .size(48.dp)
                    .background(CollogColors.gray900.copy(alpha = 0.94f), CircleShape)
                    .semantics { contentDescription = "최상단으로 이동" }
                    .pressable {
                        Haptics.press()
                        scope.launch { scrollState.animateScrollTo(0, tween(400)) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                CollogIcon("chevron.up", size = 20.dp, color = CollogColors.gray00)
            }
        }
    }
}
