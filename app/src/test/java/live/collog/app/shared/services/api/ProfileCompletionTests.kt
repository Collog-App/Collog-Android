package live.collog.app.shared.services.api

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `ProfileCompletionTests`. */
class ProfileCompletionTests {
    @Test
    fun completedProfileCanHaveNoConditions() {
        val profile = ProfileDto(parentId = "parent", conditions = emptyList(), isCompleted = true, updatedAt = null)
        assertTrue(profile.hasCompletedSetup)
    }

    @Test
    fun explicitIncompleteStatusOverridesLegacyFields() {
        val profile = ProfileDto("parent", listOf("HYPERTENSION"), isCompleted = false, updatedAt = Instant.now())
        assertFalse(profile.hasCompletedSetup)
    }

    @Test
    fun legacySavedEmptyProfileIsComplete() {
        val profile = ProfileDto("parent", emptyList(), isCompleted = null, updatedAt = Instant.now())
        assertTrue(profile.hasCompletedSetup)
    }

    @Test
    fun untouchedProfileNeedsSetup() {
        val profile = ProfileDto("parent", emptyList(), isCompleted = null, updatedAt = null)
        assertFalse(profile.hasCompletedSetup)
    }
}
