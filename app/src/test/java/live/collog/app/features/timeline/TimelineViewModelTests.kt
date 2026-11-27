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
    private val originalHealthToday = TimelineWeekPage.healthToday

    @Before
    fun fixToday() {
        TimelineWeekPage.today = { LocalDate.parse("2026-10-24") }
    }

    @After
    fun restoreToday() {
        TimelineWeekPage.today = originalToday
        TimelineWeekPage.healthToday = originalHealthToday
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

    private fun call(id: String, state: String = "ANALYZED", parentId: String = "me", childId: String? = null) =
        """{"callId":"$id","parentId":"$parentId",${childId?.let { "\"childId\":\"$it\"," } ?: ""}"state":"$state","startedAt":"2026-10-20T03:00:00Z","durationSec":600,"recorded":true}"""

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
        assertEquals("평소 200", entry.summaryStats[1].note?.text)
        assertEquals(listOf("되물으심"), entry.counts.map { it.label })

        // 기준 한 번, 앞 주(-1)까지 같이 불러온다. 리포트는 현재 주·앞 주 + 추세에 필요한 주(-2..-5, 앞 주의 -6) 6개
        assertEquals(1, fixture.paths.count { it.endsWith("/baseline") })
        assertEquals(8, fixture.paths.count { it.endsWith("/reports") })
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
        val callsQuery = fixture.requests.first { it.path().endsWith("/calls") && it.url.queryParameter("from") != null }.url
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

    /** [summaries]의 통화는 요약을 담은 제외 통화 추출 결과를, 404 목록은 결과 없음을 답한다. 나머지는 [serve] 그대로. */
    private fun answerExtractions(fixture: TestEnvironment, summaries: Map<String, String>, missing: Set<String> = emptySet()) {
        val base = fixture.respond
        fixture.respond = { request ->
            val id = request.path().removePrefix("/v1/calls/").removeSuffix("/extraction")
            when {
                !request.path().endsWith("/extraction") -> base(request)
                id in missing -> 404 to """{"code":"NOT_FOUND","message":"추출 결과가 아직 없습니다"}"""
                id in summaries -> 200 to """{"callId":"$id","parseStatus":"EXCLUDED","facts":[],"summary":"${summaries.getValue(id)}"}"""
                else -> base(request)
            }
        }
    }

    @Test
    fun excludedCallWithSummaryBecomesAnEntryWithoutVoiceAnalysis() = withFixture { fixture ->
        serve(
            fixture,
            callList = {
                calls(call("a"), call("short", "ANALYSIS_EXCLUDED"), call("silent", "ANALYSIS_EXCLUDED"), call("blank", "ANALYSIS_EXCLUDED"))
            },
        )
        answerExtractions(fixture, mapOf("short" to "점심 메뉴 이야기를 나누셨어요.", "blank" to " "), missing = setOf("silent"))
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        val entries = model.page(0).entries
        // 요약이 없거나(404) 빈 제외 통화는 지금처럼 숨긴다
        assertEquals(listOf("a", "short"), entries.map { it.callId })
        assertNull(entries[0].note)
        val short = entries[1]
        assertEquals("점심 메뉴 이야기를 나누셨어요.", short.story)
        assertEquals("말씀이 짧아 목소리 분석은 하지 않았어요.", short.note)
        assertEquals(listOf("통화 길이"), short.summaryStats.map { it.label })
        assertTrue(short.counts.isEmpty())
        assertTrue(short.gauges.isEmpty())
        assertTrue(short.keywords.isEmpty())
        // 제외 통화는 음향 지표·대화 기록을 받지 않는다
        for (id in listOf("short", "silent", "blank")) {
            assertFalse(fixture.paths.any { it == "/v1/calls/$id/acoustic-features" || it == "/v1/calls/$id/transcript" })
            assertTrue("/v1/calls/$id/extraction" in fixture.paths)
        }
    }

    @Test
    fun excludedCallsAreLookedUpOnlyWhenIWasOnTheCall() = withFixture { fixture ->
        serve(
            fixture,
            callList = {
                calls(
                    call("withMe", "ANALYSIS_EXCLUDED", parentId = "mom", childId = "me"),
                    call("others", "ANALYSIS_EXCLUDED", parentId = "mom", childId = "dad"),
                    call("unknown", "ANALYSIS_EXCLUDED", parentId = "mom"),
                )
            },
        )
        answerExtractions(fixture, listOf("withMe", "others", "unknown").associateWith { "안부 인사를 나누셨어요." })
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        assertEquals(listOf("withMe"), model.page(0).entries.map { it.callId })
        // 다른 사람끼리의 통화는 403 이라 묻지 않는다
        assertEquals(listOf("/v1/calls/withMe/extraction"), fixture.paths.filter { it.endsWith("/extraction") })
    }

    @Test
    fun excludedCallsShareTheFiveEntryCap() = withFixture { fixture ->
        serve(fixture, callList = { calls(*Array(4) { call("c$it") }, *Array(3) { call("e$it", "ANALYSIS_EXCLUDED") }) })
        answerExtractions(fixture, (0 until 3).associate { "e$it" to "짧은 안부 인사를 나누셨어요." })
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        assertEquals(listOf("c0", "c1", "c2", "c3", "e0"), model.page(0).entries.map { it.callId })
        assertEquals(7, model.page(0).calls.size)
        // 다섯 개를 채우면 남은 통화는 받지 않는다
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
    fun sharingTurnedOffIsAnEmptyStateNotAFailure() = withFixture { fixture ->
        serve(fixture)
        val base = fixture.respond
        val forbidden = 403 to """{"code":"FORBIDDEN","message":"열람 권한이 없는 항목입니다"}"""
        fixture.respond = { request ->
            val path = request.path()
            if (path.endsWith("/reports") || path.endsWith("/calls")) forbidden else base(request)
        }
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        val page = model.page(0)
        assertTrue(page.isLoaded)
        assertTrue(page.reportNotShared)
        assertTrue(page.timelineNotShared)
        assertNull(page.reportError)
        assertNull(page.timelineError)
        assertTrue(page.entries.isEmpty())
    }

    @Test
    fun premiumRequiredReportIsALockNotSharingTurnedOffOrAFailure() = withFixture { fixture ->
        serve(fixture, reportStatus = 402)
        val base = fixture.respond
        fixture.respond = { request ->
            if (request.path().endsWith("/reports")) {
                402 to """{"code":"PREMIUM_REQUIRED","message":"주간 리포트는 프리미엄에서 볼 수 있어요."}"""
            } else {
                base(request)
            }
        }
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        val page = model.page(0)
        assertTrue(page.isLoaded)
        assertTrue(page.reportPremiumRequired)
        assertFalse(page.reportNotShared)
        assertNull(page.reportError)
        // 통화 목록은 서버가 막지 않는다(타임라인 탭 잠금은 앱이 요금제로 한다)
        assertFalse(page.timelineNotShared)
        assertNull(page.timelineError)
        assertEquals(1, page.entries.size)
    }

    @Test
    fun forcedRefreshAfterSharingIsTurnedBackOnDoesNotKeepOtherWeeksNotShared() = withFixture { fixture ->
        serve(fixture)
        val base = fixture.respond
        var shared = false
        fixture.respond = { request ->
            val path = request.path()
            if (!shared && (path.endsWith("/reports") || path.endsWith("/calls"))) {
                403 to """{"code":"FORBIDDEN","message":"열람 권한이 없는 항목입니다"}"""
            } else {
                base(request)
            }
        }
        val model = TimelineViewModel()
        model.refresh(fixture.environment)
        assertEquals(setOf(0, -1), model.pages.keys)
        assertTrue(model.pages.values.all { it.reportNotShared && it.timelineNotShared })

        // 가족이 공유를 다시 켜고 당겨서 새로고침: 지금 주는 다시 받고, 다른 주는 버려 보일 때 다시 받는다
        shared = true
        model.refresh(fixture.environment, forceReload = true)
        assertEquals(setOf(0), model.pages.keys)
        assertFalse(model.page(0).reportNotShared)
        assertEquals(1, model.page(0).entries.size)
        assertFalse(model.page(-1).isLoaded)
        assertFalse(model.page(-1).timelineNotShared)

        model.loadPage(-1, fixture.environment)
        assertFalse(model.page(-1).timelineNotShared)
        assertEquals(1, model.page(-1).entries.size)
    }

    private val forbiddenBody = 403 to """{"code":"FORBIDDEN","message":"열람 권한이 없는 항목입니다"}"""

    @Test
    fun revalidatingDropsEveryLoadedWeekOnceSharingIsTurnedOff() = withFixture { fixture ->
        serve(fixture)
        val base = fixture.respond
        var shared = true
        fixture.respond = { request ->
            val path = request.path()
            if (!shared && (path.endsWith("/reports") || path.endsWith("/calls"))) forbiddenBody else base(request)
        }
        val model = TimelineViewModel()
        model.refresh(fixture.environment)
        assertEquals(setOf(0, -1), model.pages.keys)
        assertFalse(model.page(0).reportNotShared)
        assertFalse(model.page(-1).reportNotShared)

        shared = false
        model.revalidateSharing(fixture.environment)

        // 공유는 주마다가 아니라 사람마다 끄므로, 불러 둔 다른 주도 남지 않는다
        for (offset in listOf(0, -1)) {
            val page = model.page(offset)
            assertTrue(page.isLoaded)
            assertTrue(page.reportNotShared)
            assertTrue(page.timelineNotShared)
            assertTrue(page.entries.isEmpty())
        }
    }

    @Test
    fun revalidatingAsksQuietlyOnceAndKeepsWeeksWhenNothingChanged() = withFixture { fixture ->
        serve(fixture)
        val model = TimelineViewModel()
        model.refresh(fixture.environment)
        val pagesBefore = model.pages
        val reportsBefore = fixture.paths.count { it.endsWith("/reports") }

        model.revalidateSharing(fixture.environment)

        // 지금 주의 리포트를 한 번 물었을 뿐, 주를 다시 읽지 않는다
        assertEquals(reportsBefore + 1, fixture.paths.count { it.endsWith("/reports") })
        assertEquals(pagesBefore, model.pages)
    }

    @Test
    fun revalidatingKeepsTheWeeksOnAnotherFailure() = withFixture { fixture ->
        serve(fixture)
        val base = fixture.respond
        var failing = false
        fixture.respond = { request ->
            val path = request.path()
            if (failing && (path.endsWith("/reports") || path.endsWith("/calls"))) {
                503 to """{"code":"ERROR","message":"잠시 후 다시 시도해주세요"}"""
            } else {
                base(request)
            }
        }
        val model = TimelineViewModel()
        model.refresh(fixture.environment)
        val pagesBefore = model.pages

        failing = true
        model.revalidateSharing(fixture.environment)

        assertEquals(pagesBefore, model.pages)
        assertFalse(model.page(0).reportNotShared)
        assertNull(model.page(0).reportError)
    }

    @Test
    fun otherServerErrorsStayLoadFailures() = withFixture { fixture ->
        serve(fixture, reportStatus = 503)
        val base = fixture.respond
        fixture.respond = { request ->
            if (request.path().endsWith("/calls")) 500 to """{"code":"SERVER","message":"잠시 뒤 다시 시도해주세요"}""" else base(request)
        }
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)

        val page = model.page(0)
        assertNotNull(page.reportError)
        assertEquals("잠시 뒤 다시 시도해주세요", page.timelineError)
        assertFalse(page.reportNotShared)
        assertFalse(page.timelineNotShared)
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
        // 처음 6개(현재 주 + 추세용 5주) 뒤에 강제 새로고침은 현재 주 하나만 더 받는다. 기준은 이미 모아 둔 것을 쓴다
        assertEquals(1, fixture.paths.count { it.endsWith("/baseline") })
        assertEquals(6 + 1, fixture.paths.count { it.endsWith("/reports") })
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
    fun subjectsAreFamilyThenMeWithoutTouchingCallContacts() = withFixture { fixture ->
        serve(fixture)
        val model = TimelineViewModel()
        val subjects = model.subjects(fixture.environment)
        assertEquals(listOf("m1", TimelineViewModel.SelfContactId), subjects.map { it.id })
        assertEquals(listOf("엄마", "나"), subjects.map { it.name })
        assertEquals("me", subjects.last().userId)
        // 통화 상대(홈·전화 걸기)에는 내가 없다
        assertEquals(listOf("m1"), fixture.environment.family.contacts.map { it.id })

        // 처음 고르는 사람은 그대로 첫 가족
        model.refresh(fixture.environment)
        assertEquals("m1", model.selectedContactId)
        assertEquals("엄마", model.selectedMember)
    }

    @Test
    fun selectingMeLoadsMyOwnRecordsAndStaysSelected() = withFixture { fixture ->
        serve(fixture)
        val model = TimelineViewModel()
        model.loadPage(0, fixture.environment)
        fixture.paths.clear()

        model.selectMember(model.subjects(fixture.environment).last())
        assertEquals("나", model.selectedMember)
        model.refresh(fixture.environment)
        assertTrue(fixture.paths.isNotEmpty())
        assertTrue(fixture.paths.all { it.startsWith("/v1/parents/me/") || it.startsWith("/v1/calls/") })
        assertEquals(TimelineViewModel.SelfContactId, model.selectedContactId)
        assertEquals(1, model.page(0).entries.size)

        // 가족 목록이 다시 와도 직접 고른 나는 그대로
        fixture.environment.family.refresh(fixture.environment)
        fixture.requests.clear()
        model.refresh(fixture.environment, forceReload = true)
        assertEquals("나", model.selectedMember)
        assertEquals("me", fixture.requests.last { it.path().endsWith("/calls") }.path().split("/")[3])
    }

    @Test
    fun withoutFamilyMeIsTheOnlySubjectAndSelected() = runTest {
        val fixture = TestEnvironment(familyId = null)
        try {
            serve(fixture)
            assertTrue(fixture.environment.family.contacts.isEmpty())
            val model = TimelineViewModel()
            assertEquals(listOf(TimelineViewModel.SelfContactId), model.subjects(fixture.environment).map { it.id })

            model.refresh(fixture.environment)
            assertEquals(TimelineViewModel.SelfContactId, model.selectedContactId)
            assertEquals("나", model.selectedMember)
            assertTrue(fixture.paths.all { it.startsWith("/v1/parents/me/") || it.startsWith("/v1/calls/") })
            val page = model.page(0)
            assertTrue(page.isLoaded)
            assertNull(page.reportError)
            assertEquals(1, page.entries.size)
        } finally {
            fixture.cleanUp()
        }
    }

    @Test
    fun meShownBeforeFamilyArrivesGivesWayToFirstFamilyMember() = withFixture { fixture ->
        serve(fixture)
        fixture.environment.family.reset()
        val model = TimelineViewModel()
        // 가족 목록보다 먼저 그려진 경우: 나를 대신 본다
        model.refresh(fixture.environment)
        assertEquals("나", model.selectedMember)

        fixture.environment.family.refresh(fixture.environment)
        model.refresh(fixture.environment)
        assertEquals("m1", model.selectedContactId)
        assertEquals("엄마", model.selectedMember)
        assertEquals("mom", fixture.requests.last { it.path().endsWith("/calls") }.path().split("/")[3])
        assertEquals(1, model.page(0).entries.size)
    }

    @Test
    fun guestSubjectsHaveNoMe() = withFixture { fixture ->
        fixture.environment.settings.isGuestMode = true
        fixture.environment.family.refresh(fixture.environment)
        val model = TimelineViewModel()
        assertEquals(FamilyContact.samples, model.subjects(fixture.environment))
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
        // 다른 화면처럼 닉네임이 있으면 닉네임(계정 이름 Tester 가 아니라)
        assertEquals("테스터", model.selectedMember)
    }

    @Test
    fun lifeRecordWindowEndsOnSeoulTodayEvenWhenTheDeviceDateIsBehind() = withFixture { fixture ->
        serve(fixture)
        val base = fixture.respond
        fixture.respond = { request ->
            if (request.path().endsWith("/health/daily")) {
                200 to """{"parentId":"mom","connected":true,"days":[]}"""
            } else {
                base(request)
            }
        }
        // 기기 날짜는 10-24(토)인데 서울은 이미 10-25(일)
        TimelineWeekPage.healthToday = { LocalDate.parse("2026-10-25") }
        val model = TimelineViewModel(0)
        model.refresh(fixture.environment)

        val health = fixture.requests.first { it.path() == "/v1/parents/mom/health/daily" }
        assertEquals("2026-09-26", health.url.queryParameter("from"))
        assertEquals("2026-10-25", health.url.queryParameter("to"))
        assertEquals("최근 7일", model.page(0).lifeRecord!!.rangeText)
        // 주 경계(리포트·타임라인)는 그대로 기기 날짜를 따른다
        assertEquals("2026-10-24", fixture.requests.first { it.path().endsWith("/reports") }.url.queryParameter("date"))
    }
}
