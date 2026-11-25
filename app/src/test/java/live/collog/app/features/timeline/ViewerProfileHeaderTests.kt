package live.collog.app.features.timeline

import live.collog.app.R
import live.collog.app.features.home.models.FamilyContact
import live.collog.app.features.timeline.viewmodels.TimelineViewModel
import live.collog.app.features.timeline.views.components.avatarAsset
import live.collog.app.features.timeline.views.components.viewerRelationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `ViewerProfileHeader`·`ReportTimelineView.displayTitle`: 리포트·타임라인이 누구의 기록인지 알리는 값. */
class ViewerProfileHeaderTests {
    private val self = FamilyContact(TimelineViewModel.SelfContactId, "me", "나", "", "")
    private val mother = FamilyContact("m1", "mom", "엄마", "MOTHER", "")
    private val father = FamilyContact("m2", "dad", "아빠", "FATHER", "")
    private val child = FamilyContact("m3", "kid", "딸", "CHILD", "")
    private val spouse = FamilyContact("m4", "sp", "여보", "SPOUSE", "")
    private val creator = FamilyContact("m5", "cr", "가족", "", "")

    @Test
    fun relationTagNamesSelfAndFamily() {
        assertEquals("본인", viewerRelationText(self))
        assertEquals("어머니", viewerRelationText(mother))
        assertEquals("배우자", viewerRelationText(spouse))
        // 가족을 만든 사람처럼 관계가 비어 있으면 「가족」
        assertEquals("가족", viewerRelationText(creator))
    }

    @Test
    fun avatarFollowsRelationAndFallsBackToInitial() {
        assertEquals(R.drawable.avatar_child, avatarAsset(self))
        assertEquals(R.drawable.avatar_mom, avatarAsset(mother))
        assertEquals(R.drawable.avatar_dad, avatarAsset(father))
        assertEquals(R.drawable.avatar_child, avatarAsset(child))
        // 그림이 없는 관계는 이름 첫 글자 원
        assertNull(avatarAsset(spouse))
        assertNull(avatarAsset(creator))
    }

    @Test
    fun titleSaysWhoseRecordIsShown() {
        val report = TimelineViewModel(selectedTabIndex = 0)
        // 고른 사람이 없으면(가족 목록 전) 탭 이름만
        assertEquals("리포트", report.displayTitle)
        assertFalse(report.isSelfSelected)

        report.selectMember(mother)
        assertEquals("엄마님의 리포트", report.displayTitle)

        report.selectMember(self)
        assertTrue(report.isSelfSelected)
        assertEquals("나의 리포트", report.displayTitle)

        val timeline = TimelineViewModel(selectedTabIndex = 1)
        timeline.selectMember(father)
        assertEquals("아빠님의 타임라인", timeline.displayTitle)
    }
}
