package live.collog.app.features.timeline

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.report.models.ReportState
import live.collog.app.features.timeline.models.TimelineWeekPage
import live.collog.app.features.timeline.viewmodels.TimelineViewModel
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** iOS `TimelineViewModel`에는 전용 테스트가 없다. 서버 응답은 가짜 서버로 대신한다. */
class TimelineViewModelTests {
    private val originalToday = TimelineWeekPage.today

    @Before
    fun fixToday() {
        TimelineWeekPage.today = { LocalDate.parse("2026-10-24") }
    }

    @After
    fun restoreToday() {
        TimelineWeekPage.today = originalToday
    }

    private fun withFixture(role: String = "CHILD", body: suspend (TestEnvironment) -> Unit) = runTest {
        val fixture = TestEnvironment(role = role)
        try {
            body(fixture)
        } finally {
            fixture.cleanUp()
        }
    }

    private fun report(analyzedCallCount: Int = 3) =
        """
        {"parentId":"me","period":"WEEKLY","from":"2026-10-19","to":"2026-10-25","state":"READY",
        "disclaimer":"참고용","promotedSignals":[],"acuteSignals":[],
        "conversationItems":{"sleep":["잘 주무셨다"]},
        "repeatObservation":{"count":1,"callsWithRepeat":1,"label":"되물음"},
        "acousticTrends":[],"analyzedCallCount":$analyzedCallCount}
        """.trimIndent()

    private fun call(id: String, state: String = "ANALYZED") =
        """{"callId":"$id","parentId":"me","state":"$state","startedAt":"2026-10-20T03:00:00Z","durationSec":600,"recorded":true}"""

    private fun calls(vararg items: String) = """{"calls":[${items.joinToString(",")}]}"""

    private val baselines =
        """
        {"baselines":[
        {"metric":"SPEECH_RATE","timeSlot":"ALL","kind":"ROLLING","status":"READY","sampleCount":9,"requiredCount":5,"median":200.0,"mad":10.0},
        {"metric":"PAUSE_RATIO","timeSlot":"ALL","kind":"ANCHOR","status":"READY","sampleCount":9,"requiredCount":5,"median":0.2,"mad":0.05}
        ]}
        """.trimIndent()

    private fun Request.path() = url.encodedPath

    /** 가족 한 명(엄마)을 고른 상태로 만들고, 그 사람의 리포트·통화를 답한다. 가족을 불러온 요청은 지운다. */
    private suspend fun serve(
        fixture: TestEnvironment,
        callList: (Request) -> String = { calls(call("a"), call("b", "ENDED")) },
        reportStatus: Int = 200,
    ) {
        fixture.respond = { request ->
            val path = request.path()
            when {
                path.endsWith("/members") ->
                    200 to """{"members":[{"memberId":"m1","userId":"mom","name":"엄마","relation":"MOTHER","status":"ACTIVE"}]}"""
                path.endsWith("/daily-questions") -> 200 to """{"questions":[]}"""
                path.endsWith("/baseline") -> 200 to baselines
                path.endsWith("/reports") -> reportStatus to if (reportStatus == 200) report() else "{}"
                path.endsWith("/calls") -> 200 to callList(request)
                path.endsWith("/acoustic-features") ->
                    200 to """{"callId":"a","features":[{"metric":"SPEECH_RATE","value":215.4,"unit":"음절/분","status":"OK"}]}"""
                path.endsWith("/extraction") ->
                    200 to """{"callId":"a","parseStatus":"OK","symptom":"허리가 아프다"}"""
                path.endsWith("/transcript") ->
                    200 to
                        """{"callId":"a","excluded":false,"parentSpeechSec":10,"repeatEvents":[],"repeatRequestCount":2,"repeatRequestsPerMinute":0.2}"""
                else -> 404 to "{}"
            }
        }
        fixture.environment.family.refresh(fixture.environment)
        fixture.paths.clear()
        fixture.requests.clear()
    }

    @Test
    fun titleFollowsInitialTab() {
        assertEquals("리포트", TimelineViewModel(0).title)
        assertEquals("타임라인", TimelineViewModel().title)
    }

    @Test
    fun weekCannotMoveIntoTheFuture() {
        val model = TimelineViewModel()
        assertFalse(model.canGoForward)
        model.setWeek(1)
        assertEquals(0, model.weekOffset)
        model.setWeek(-2)
        assertEquals(-2, model.weekOffset)
        assertTrue(model.canGoForward)
        assertEquals("10월 2주", model.week.title)
    }

    @Test
    fun guestRefreshLoadsSamplePagesWithoutNetwork() = withFixture { fixture ->
        fixture.environment.settings.isGuestMode = true
        val model = TimelineViewModel()
        model.refresh(fixture.environment)

        assertTrue(fixture.paths.isEmpty())
        assertEquals(setOf(0, -1), model.pages.keys)
        assertTrue(model.pages.values.all { it.isLoaded })
        assertEquals(1, model.page(0).entries.size)
        assertEquals(ReportState.Ready, model.page(0).report.state)

        model.setWeek(-5)
        model.refresh(fixture.environment)
        assertEquals(setOf(0, -1, -4, -5, -6), model.pages.keys)
        // 3주보다 먼 견본 주에는 통화 기록이 없다
        assertTrue(model.page(-5).entries.isEmpty())
        assertTrue(model.page(-4).entries.isEmpty())
        assertEquals(1, model.page(-1).entries.size)
    }

    @Test
    fun guestPageAboveCurrentWeekIsIgnored() = withFixture { fixture ->
        fixture.environment.settings.isGuestMode = true
        val model = TimelineViewModel()
        model.loadPage(1, fixture.environment)
        assertTrue(model.pages.isEmpty())
        model.loadPage(-2, fixture.environment)
        assertEquals(setOf(-2), model.pages.keys)
    }

    @Test
    fun refreshLoadsSelectedContactReportAndAnalyzedCalls() = withFixture { fixture ->
        serve(fixture)
        val model = TimelineViewModel()
        model.refresh(fixture.environment)
        assertTrue(fixture.paths.all { it.startsWith("/v1/parents/mom/") || it.startsWith("/v1/calls/") })
        assertEquals("엄마", model.selectedMember)

        val page = model.page(0)
        assertTrue(page.isLoaded)
        assertNull(page.reportError)
        assertNull(page.timelineError)
        assertEquals(ReportState.Ready, page.report.state)
        assertEquals(2, page.calls.size)
        // 분석이 끝난 통화(a)만 항목이 된다
        assertEquals(1, page.entries.size)
        val entry = page.entries[0]
        assertEquals("허리가 아프다", entry.story)
        assertEquals("215", entry.summaryStats[1].value)
        assertEquals("7.7%", entry.summaryStats[1].note?.text)
        assertEquals(listOf("되물으심"), entry.counts.map { it.label })

        // 기준 한 번, 앞 주(-1)까지 같이 불러온다
        assertEquals(1, fixture.paths.count { it.endsWith("/baseline") })
        assertEquals(2, fixture.paths.count { it.endsWith("/reports") })
        assertEquals(setOf(0, -1), model.pages.keys)
    }

    @Test
    fun reportAndCallsAskForTheWeekRange() = withFixture { fixture ->
        serve(fixture)
        val model = TimelineViewModel()
        model.loadPage(-1, fixture.environment)

        val reportQuery = fixture.requests.first { it.path().endsWith("/reports") }.url
        assertEquals("WEEKLY", reportQuery.queryParameter("period"))
        assertEquals("2026-10-17", reportQuery.queryParameter("date"))
        val callsQuery = fixture.requests.first { it.path().endsWith("/calls") }.url
        assertEquals("2026-10-12", callsQuery.queryParameter("from"))
        assertEquals("2026-10-18", callsQuery.queryParameter("to"))
    }

    @Test
    fun onlyFirstFiveAnalyzedCallsBecomeEntries() = withFixture { fixture ->
        serve(fixture, callList = { calls(*Array(7) { call("c$it") }) })
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        assertEquals(5, model.page(0).entries.size)
        assertEquals(7, model.page(0).calls.size)
        assertEquals(5, fixture.paths.count { it.endsWith("/extraction") })
    }

    @Test
    fun noAnalyzedCallsLeavesEntriesEmpty() = withFixture { fixture ->
        serve(fixture, callList = { calls(call("a", "ENDED")) })
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        assertTrue(model.page(0).entries.isEmpty())
        assertEquals(1, model.page(0).calls.size)
        assertTrue(fixture.paths.none { it.endsWith("/extraction") })
    }

    @Test
    fun reportFailureKeepsTimelineContent() = withFixture { fixture ->
        serve(fixture, reportStatus = 503)
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        val page = model.page(0)
        assertTrue(page.isLoaded)
        assertNotNull(page.reportError)
        assertNull(page.timelineError)
        assertEquals(1, page.entries.size)
        assertEquals(ReportState.Empty, page.report.state)
    }

    @Test
    fun loadedPageIsNotFetchedAgainUnlessForced() = withFixture { fixture ->
        serve(fixture)
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)
        val first = fixture.paths.size
        model.loadPage(0, fixture.environment)
        assertEquals(first, fixture.paths.size)

        model.refresh(fixture.environment, forceReload = true)
        // 강제 새로고침은 현재 주만, 기준은 이미 모아 둔 것을 쓴다
        assertEquals(1, fixture.paths.count { it.endsWith("/baseline") })
        assertEquals(2, fixture.paths.count { it.endsWith("/reports") })
        assertEquals(setOf(0), model.pages.keys)
    }

    @Test
    fun futureOffsetIsNeverLoaded() = withFixture { fixture ->
        serve(fixture)
        val model = TimelineViewModel()
        model.loadPage(1, fixture.environment)
        assertTrue(fixture.paths.isEmpty())
        assertTrue(model.pages.isEmpty())
    }

    @Test
    fun usesSelectedFamilyContactAndResetsOnSelection() = withFixture { fixture ->
        fixture.respond = { request ->
            val path = request.path()
            when {
                path.endsWith("/members") ->
                    200 to
                        """{"members":[
                        {"memberId":"m1","userId":"mom","name":"엄마","relation":"MOTHER","status":"ACTIVE","role":"PARENT"},
                        {"memberId":"m2","userId":"dad","name":"아빠","relation":"FATHER","status":"ACTIVE","role":"PARENT"}]}"""
                path.endsWith("/daily-questions") -> 200 to """{"questions":[]}"""
                path.endsWith("/baseline") -> 200 to """{"baselines":[]}"""
                path.endsWith("/reports") -> 200 to report(analyzedCallCount = 0)
                path.endsWith("/calls") -> 200 to calls()
                else -> 404 to "{}"
            }
        }
        fixture.environment.family.refresh(fixture.environment)
        fixture.paths.clear()

        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)
        assertEquals("mom", fixture.requests.last { it.path().endsWith("/calls") }.path().split("/")[3])
        assertEquals("엄마", model.selectedMember)
        assertEquals("MOTHER", model.selectedRelation)
        assertEquals("m1", model.selectedContactId)
        assertEquals(ReportState.Empty, model.page(0).report.state)

        model.setWeek(-3)
        val dad = FamilyContact("m2", "dad", "아빠", "FATHER", "아버지")
        model.selectMember(dad)
        assertEquals(0, model.weekOffset)
        assertTrue(model.pages.isEmpty())
        assertEquals("아빠", model.selectedMember)

        // 고른 사람의 기록을 다시 불러온다
        model.loadPage(0, fixture.environment)
        assertEquals("dad", fixture.requests.last { it.path().endsWith("/calls") }.path().split("/")[3])
        assertEquals(setOf(0), model.pages.keys)
    }

    @Test
    fun missingFamilyContactShowsFamilyLoadError() = withFixture(role = "PARENT") { fixture ->
        fixture.respond = { 503 to "{}" }
        fixture.environment.family.refresh(fixture.environment)
        val model = TimelineViewModel()
        model.refresh(fixture.environment)
        // 예전 부모 계정이어도 자기 기록을 대신 불러오지 않는다
        assertEquals(listOf("/v1/families/family/members"), fixture.paths)

        val page = model.page(0)
        assertTrue(page.isLoaded)
        assertNotNull(page.reportError)
        assertEquals(page.reportError, page.timelineError)
        assertEquals("Tester", model.selectedMember)
    }
}
