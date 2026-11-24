package live.collog.app.features.timeline

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import live.collog.app.TestEnvironment
import live.collog.app.features.timeline.models.TimelineWeekPage
import live.collog.app.features.timeline.viewmodels.TimelineViewModel
import live.collog.app.shared.services.AppEventCode
import live.collog.app.shared.services.AppEvents
import live.collog.app.shared.services.api.AppEventDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `report_view`: 사람이 리포트에 들어온 순간과 보는 사람을 바꾼 순간만. 자동 새로 고침·주 넘기기는 남기지 않는다. */
class ReportViewEventTests {
    private val originalToday = TimelineWeekPage.today

    @Before
    fun fixToday() {
        TimelineWeekPage.today = { LocalDate.parse("2026-10-24") }
    }

    @After
    fun restoreToday() {
        TimelineWeekPage.today = originalToday
    }

    private fun withFixture(familyId: String? = "family", body: suspend (TestEnvironment) -> Unit) = runTest {
        val fixture = TestEnvironment(familyId = familyId, recordsEvents = true)
        // 가족은 엄마 한 명. 리포트 내용은 이 테스트와 상관없어 모두 404 로 둔다
        fixture.respond = { request ->
            if (request.url.encodedPath.endsWith("/members")) {
                200 to """{"members":[{"memberId":"m1","userId":"mom","name":"엄마","relation":"MOTHER","status":"ACTIVE"}]}"""
            } else {
                404 to "{}"
            }
        }
        try {
            body(fixture)
        } finally {
            fixture.cleanUp()
        }
    }

    private fun TestEnvironment.views(): List<AppEventDto> =
        environment.events.queued.map { it.event }.filter { it.name == AppEvents.REPORT_VIEW }

    @Test
    fun enteringTheReportRecordsWhoIsBeingViewed() = withFixture { fixture ->
        val environment = fixture.environment
        environment.family.refresh(environment)
        val model = TimelineViewModel(selectedTabIndex = 0)

        model.recordReportView(environment, AppEventCode.DIRECT)

        val view = fixture.views().single()
        assertEquals("DIRECT", view.code)
        assertEquals("mom", view.subjectId)
        assertEquals(null, view.callId)
    }

    @Test
    fun refreshesAndWeekPagingRecordNothing() = withFixture { fixture ->
        val environment = fixture.environment
        environment.family.refresh(environment)
        val model = TimelineViewModel(selectedTabIndex = 0)
        model.recordReportView(environment, AppEventCode.DIRECT)

        // 들어올 때 불러오기, 당겨서 새로 고침, 주 넘기기, 분석 완료 알림·건강 동기화 뒤 다시 읽기, 앱 복귀 재확인
        model.refresh(environment)
        model.refresh(environment, forceReload = true)
        model.setWeek(-1)
        model.refresh(environment)
        model.loadPage(-2, environment)
        environment.receiveReportNotification("call-1", "Collog", "새 통화 기록", openReport = false)
        model.revalidateSharing(environment)
        model.recordPendingReportView(environment)

        assertEquals(1, fixture.views().size)
    }

    @Test
    fun changingThePersonRecordsTheNewPerson() = withFixture { fixture ->
        val environment = fixture.environment
        environment.family.refresh(environment)
        val model = TimelineViewModel(selectedTabIndex = 0)
        model.recordReportView(environment, AppEventCode.DIRECT)

        val self = model.subjects(environment).single { it.id == TimelineViewModel.SelfContactId }
        model.selectMember(self)
        model.recordReportView(environment, AppEventCode.DIRECT)

        assertEquals(listOf("mom", "me"), fixture.views().map { it.subjectId })
    }

    @Test
    fun waitsForTheFamilyListInsteadOfRecordingTheTemporarySelf() = withFixture { fixture ->
        val environment = fixture.environment
        val model = TimelineViewModel(selectedTabIndex = 0)

        // 꺼진 앱에서 리포트 알림을 눌러 가족 목록보다 리포트가 먼저 열렸다
        model.recordReportView(environment, AppEventCode.NOTIFICATION)
        assertTrue(fixture.views().isEmpty())

        environment.family.refresh(environment)
        model.recordPendingReportView(environment)

        val view = fixture.views().single()
        assertEquals("NOTIFICATION", view.code)
        assertEquals("mom", view.subjectId)
    }

    @Test
    fun withoutFamilyMyOwnReportIsRecorded() = withFixture(familyId = null) { fixture ->
        val environment = fixture.environment
        environment.family.refresh(environment)

        TimelineViewModel(selectedTabIndex = 0).recordReportView(environment, AppEventCode.DIRECT)

        assertEquals("me", fixture.views().single().subjectId)
    }

    @Test
    fun timelineTabIsNotAReportView() = withFixture { fixture ->
        val environment = fixture.environment
        environment.family.refresh(environment)

        TimelineViewModel(selectedTabIndex = 1).recordReportView(environment, AppEventCode.DIRECT)

        assertTrue(fixture.views().isEmpty())
    }

    @Test
    fun onlyATappedReportNotificationMarksTheEntryAsNotification() = withFixture { fixture ->
        val environment = fixture.environment
        environment.receiveReportNotification("call-1", "Collog", "새 통화 기록", openReport = false)
        assertFalse(environment.reportOpenedFromNotification)

        environment.receiveReportNotification("call-2", "Collog", "새 통화 기록", openReport = true)
        assertTrue(environment.reportOpenedFromNotification)
    }
}
