package live.collog.app.shared.components.tabbar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import live.collog.app.features.call.viewmodels.CallLauncherModel
import live.collog.app.features.call.views.responseSpring
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.shared.navigation.MainTab
import live.collog.app.shared.ui.Haptics
import live.collog.app.shared.ui.designsystem.CollogColors
import live.collog.app.shared.ui.designsystem.CollogIcon
import live.collog.app.shared.ui.designsystem.CollogType
import live.collog.app.shared.ui.designsystem.IconSize
import live.collog.app.shared.ui.designsystem.Spacing
import live.collog.app.shared.ui.pressable

object BottomNavBar {
    val barHeight = 64.dp
    val buttonLift = 14.dp
    val anchorInset = barHeight / 2 + buttonLift

    // 전화 버튼이 막대 위로 튀어나온 만큼 위쪽에 투명한 터치 영역이 필요하다(Compose는 부모 밖 터치를 못 받는다)
    private val overhang = 16.dp
    internal val topPadding = overhang
}

/** iOS `BottomNavBarView`. 탭 네 개와 가운데 전화 버튼(길게 눌러 드래그하거나 눌러서 선택). */
@Composable
fun BottomNavBar(
    selection: MainTab,
    onSelectionChange: (MainTab) -> Unit,
    launcher: CallLauncherModel,
    onLaunch: (FamilyContact) -> Unit,
    onReselect: (MainTab) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hideOffset by animateDpAsState(
        if (launcher.isPresented) BottomNavBar.barHeight + Spacing.x8 + navigationInset else 0.dp,
        responseSpring(0.24f, 0.9f),
        label = "barHide",
    )

    Box(modifier.height(BottomNavBar.topPadding + BottomNavBar.barHeight + navigationInset)) {
        // 배경은 시스템 내비게이션 바 아래까지 뻗는다
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = hideOffset)
                .fillMaxWidth()
                .height(BottomNavBar.barHeight + navigationInset)
                .background(CollogColors.gray00.copy(alpha = 0.92f)),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(CollogColors.gray200))
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = BottomNavBar.topPadding)
                .offset(y = hideOffset)
                .fillMaxWidth()
                .height(BottomNavBar.barHeight)
                .padding(horizontal = Spacing.x3),
        ) {
            TabButton(MainTab.Home, selection, Modifier.weight(1f)) { tap(it, selection, launcher, onSelectionChange, onReselect) }
            TabButton(MainTab.Report, selection, Modifier.weight(1f)) { tap(it, selection, launcher, onSelectionChange, onReselect) }
            Box(Modifier.weight(1f))
            TabButton(MainTab.Timeline, selection, Modifier.weight(1f)) { tap(it, selection, launcher, onSelectionChange, onReselect) }
            TabButton(MainTab.Settings, selection, Modifier.weight(1f)) { tap(it, selection, launcher, onSelectionChange, onReselect) }
        }

        CallButton(
            launcher = launcher,
            onLaunch = onLaunch,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = BottomNavBar.topPadding + (BottomNavBar.barHeight - 68.dp) / 2 - BottomNavBar.buttonLift),
        )
    }
}

private fun tap(
    tab: MainTab,
    selection: MainTab,
    launcher: CallLauncherModel,
    onSelectionChange: (MainTab) -> Unit,
    onReselect: (MainTab) -> Unit,
) {
    launcher.dismiss()
    if (selection == tab) onReselect(tab) else onSelectionChange(tab)
    Haptics.press()
}

@Composable
private fun TabButton(tab: MainTab, selection: MainTab, modifier: Modifier, onTap: (MainTab) -> Unit) {
    val color = if (selection == tab) CollogColors.greenNormal else CollogColors.gray600
    Column(
        modifier.fillMaxSize().pressable { onTap(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        CollogIcon(tab.symbol, size = IconSize.medium, color = color)
        Text(tab.title, style = CollogType.caption01Medium, color = color)
    }
}

@Composable
private fun CallButton(launcher: CallLauncherModel, onLaunch: (FamilyContact) -> Unit, modifier: Modifier = Modifier) {
    var isPressing by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current

    val target = if (launcher.isPresented) 1.1f else if (isPressing) 0.94f else 1f
    val scale by animateFloatAsState(target, responseSpring(0.3f, 0.7f), label = "callScale")
    val shadowColor = CollogColors.greenNormal.copy(alpha = if (launcher.isPresented) 0.45f else 0.28f)
    val shadowElevation by animateDpAsState(if (launcher.isPresented) 22.dp else 12.dp, label = "callShadow")

    Box(
        modifier
            .size(68.dp)
            .scale(scale)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(launcher) {
                // SwiftUI DragGesture(minimumDistance: 0): 닿는 순간부터 끝까지, 버튼 중심 기준 dp 벡터
                fun vector(local: Offset): Offset {
                    val node = coordinates ?: return Offset.Zero
                    val center = node.localToWindow(Offset(node.size.width / 2f, node.size.height / 2f))
                    val point = node.localToWindow(local)
                    return Offset((point.x - center.x) / density.density, (point.y - center.y) / density.density)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressing = true
                    launcher.pressBegan()
                    launcher.dragChanged(vector(down.position))
                    var last = down.position
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        change.consume()
                        last = change.position
                        if (change.changedToUp() || !change.pressed) break
                        launcher.dragChanged(vector(last))
                    }
                    isPressing = false
                    launcher.pressEnded(vector(last))?.let(onLaunch)
                }
            }
            .semantics { contentDescription = "가족에게 전화" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(68.dp).background(CollogColors.gray50, CircleShape))
        Box(
            Modifier
                .size(58.dp)
                .shadow(shadowElevation, CircleShape, ambientColor = shadowColor, spotColor = shadowColor)
                .background(CollogColors.greenNormal, CircleShape),
        )
        CollogIcon("phone.fill", size = 26.dp, color = Color.White)
    }
}
