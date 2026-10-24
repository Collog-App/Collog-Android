package live.collog.app.features.call

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import live.collog.app.features.call.viewmodels.CallLauncherModel
import live.collog.app.features.call.viewmodels.CallLauncherModel.Mode
import live.collog.app.features.home.models.FamilyContact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallLauncherModelTests {
    private val dispatcher = StandardTestDispatcher()

    private val contacts = listOf(
        FamilyContact("a", "ua", "어머니", "MOTHER", ""),
        FamilyContact("b", "ub", "아버지", "FATHER", ""),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun model(targets: List<FamilyContact> = contacts) =
        CallLauncherModel(dispatcher).apply { configure(targets, listOf("q")) }

    @Test
    fun angleRangesSplitSemicircleEvenly() {
        val m = model()
        assertEquals(-180.0..-90.0, m.angleRange(0))
        assertEquals(-90.0..0.0, m.angleRange(1))
        assertEquals(-180.0..0.0, model(contacts.take(1)).angleRange(0))
        // 대상이 없어도 0으로 나누지 않는다
        assertEquals(-180.0..0.0, model(emptyList()).angleRange(0))
    }

    @Test
    fun offsetsSitOnArcCentre() {
        val m = model()
        assertEquals(-86.27f, m.offset(0).x, 0.05f)
        assertEquals(-86.27f, m.offset(0).y, 0.05f)
        assertEquals(86.27f, m.offset(1).x, 0.05f)
        assertEquals(-86.27f, m.offset(1).y, 0.05f)
    }

    @Test
    fun selectionIndexFollowsDragDirection() {
        val m = model()
        assertEquals(0, m.selectionIndex(Offset(-80f, -80f)))
        assertEquals(1, m.selectionIndex(Offset(80f, -80f)))
    }

    @Test
    fun selectionIndexRejectsOutOfRangeAndBelow() {
        val m = model()
        assertNull(m.selectionIndex(Offset(0f, -10f))) // 안쪽 반지름 미만
        assertNull(m.selectionIndex(Offset(0f, -300f))) // 바깥 반지름 초과
        assertNull(m.selectionIndex(Offset(-80f, 80f))) // 버튼 아래쪽
        assertNull(model(emptyList()).selectionIndex(Offset(0f, -100f)))
    }

    @Test
    fun hysteresisKeepsFocusNearBoundary() = runTest(dispatcher) {
        val m = model()
        m.pressBegan()
        advanceTimeBy(221)
        runCurrent()
        m.dragChanged(Offset(-80f, -80f))
        assertEquals(0, m.focusedIndex)
        // 경계를 살짝 넘어도 여유 폭 안이면 그대로
        m.dragChanged(Offset(5f, -100f))
        assertEquals(0, m.focusedIndex)
        // 충분히 넘으면 바뀐다
        m.dragChanged(Offset(80f, -80f))
        assertEquals(1, m.focusedIndex)
    }

    @Test
    fun holdActivatesDraggingAfter220ms() = runTest(dispatcher) {
        val m = model()
        m.pressBegan()
        advanceTimeBy(219)
        runCurrent()
        assertEquals(Mode.Idle, m.mode)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(Mode.Dragging, m.mode)
    }

    @Test
    fun holdWithoutTargetsFallsBackToSticky() = runTest(dispatcher) {
        val m = model(emptyList())
        m.pressBegan()
        advanceTimeBy(221)
        runCurrent()
        assertEquals(Mode.Sticky, m.mode)
    }

    @Test
    fun quickTapPresentsStickySelection() = runTest(dispatcher) {
        val m = model()
        m.pressBegan()
        advanceTimeBy(100)
        assertNull(m.pressEnded(Offset.Zero))
        assertEquals(Mode.Sticky, m.mode)
        advanceTimeBy(500) // 취소된 타이머가 모드를 바꾸지 않는다
        runCurrent()
        assertEquals(Mode.Sticky, m.mode)
    }

    @Test
    fun tapWhileStickyDismisses() = runTest(dispatcher) {
        val m = model()
        m.presentSelection()
        m.pressBegan() // sticky에서는 무시
        assertNull(m.pressEnded(Offset.Zero))
        assertEquals(Mode.Idle, m.mode)
    }

    @Test
    fun releaseOnTargetAfterHoldReturnsContact() = runTest(dispatcher) {
        val m = model()
        m.pressBegan()
        advanceTimeBy(221)
        runCurrent()
        m.dragChanged(Offset(80f, -80f))
        assertEquals(contacts[1], m.pressEnded(Offset(80f, -80f)))
        assertEquals(Mode.Idle, m.mode)
        assertNull(m.focusedIndex)
    }

    @Test
    fun releaseOutsideAfterHoldCancels() = runTest(dispatcher) {
        val m = model()
        m.pressBegan()
        advanceTimeBy(221)
        runCurrent()
        m.dragChanged(Offset(-80f, -80f))
        assertNull(m.pressEnded(Offset(0f, 100f)))
        assertEquals(Mode.Idle, m.mode)
        // 다음 짧은 탭은 다시 sticky
        m.pressBegan()
        m.pressEnded(Offset.Zero)
        assertEquals(Mode.Sticky, m.mode)
    }

    @Test
    fun selectReturnsContactAndCloses() {
        val m = model()
        m.presentSelection()
        assertEquals(contacts[0], m.select(0))
        assertEquals(Mode.Idle, m.mode)
        m.presentSelection()
        assertNull(m.select(5))
        assertEquals(Mode.Sticky, m.mode)
    }
}
