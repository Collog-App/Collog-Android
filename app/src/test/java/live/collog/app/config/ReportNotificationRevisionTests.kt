package live.collog.app.config

import live.collog.app.TestEnvironment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 분석 완료 알림은 누르지 않아도 리포트를 다시 읽게 하고, 누를 때만 리포트를 연다. */
class ReportNotificationRevisionTests {
    private lateinit var fixture: TestEnvironment

    @Before
    fun setUp() {
        fixture = TestEnvironment()
        assertTrue(fixture.environment.session.isAuthenticated)
    }

    @After
    fun tearDown() = fixture.cleanUp()

    @Test
    fun bannerNotificationRefreshesDataWithoutOpeningTheReport() {
        val environment = fixture.environment
        environment.receiveReportNotification("call-1", "분석 완료", "확인해 주세요", openReport = false)
        assertEquals(1, environment.reportDataRevision)
        assertEquals(0, environment.reportNotificationRevision)
    }

    @Test
    fun tappedNotificationRefreshesDataAndOpensTheReport() {
        val environment = fixture.environment
        environment.receiveReportNotification("call-1", "분석 완료", "확인해 주세요", openReport = true)
        assertEquals(1, environment.reportDataRevision)
        assertEquals(1, environment.reportNotificationRevision)
        assertEquals("call-1", environment.openedReportCallId)
    }
}
