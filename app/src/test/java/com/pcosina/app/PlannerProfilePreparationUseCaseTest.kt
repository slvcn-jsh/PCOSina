package com.pcosina.app

import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.domain.PlannerProfilePreparationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerProfilePreparationUseCaseTest {

    private val useCase = PlannerProfilePreparationUseCase()

    @Test
    fun prepare_usesStoredProfileWhenRequestedProfileIsIncomplete() {
        val requested = UserProfile(goal = "")
        val stored = UserProfile(
            displayName = "Saved User",
            age = 29,
            heightCm = 160,
            weightKg = 64,
            activityLevel = "Lightly Active",
            goal = "Weight Loss",
        )

        val prepared = useCase(
            requestedProfile = requested,
            storedProfile = stored,
            feedbackTags = emptyList()
        )

        assertTrue(prepared.usedStoredProfileFallback)
        assertEquals(stored, prepared.resolvedProfile)
        assertEquals("Weight Loss", prepared.plannerProfile.goal)
    }

    @Test
    fun prepare_appliesFeedbackTuningAndNormalizesPlannerGoalText() {
        val requested = UserProfile(
            age = 27,
            heightCm = 158,
            weightKg = 61,
            activityLevel = "Moderately Active",
            goal = "general health improvement, support pcos symptom management",
            weeklyBudgetPhp = 2000,
            maxCookingTimeMinutes = 45,
            varietyPreference = "Balanced",
        )

        val prepared = useCase(
            requestedProfile = requested,
            feedbackTags = listOf("Too repetitive", "Too expensive", "Too hard to cook")
        )

        assertFalse(prepared.usedStoredProfileFallback)
        assertEquals("High", prepared.plannerProfile.varietyPreference)
        assertEquals(1800, prepared.plannerProfile.weeklyBudgetPhp)
        assertEquals(35, prepared.plannerProfile.maxCookingTimeMinutes)
        assertEquals("Symptom Management, General Health", prepared.plannerProfile.goal)
        assertEquals(
            listOf("Too repetitive", "Too expensive", "Too hard to cook"),
            prepared.appliedFeedbackTags
        )
    }

    @Test
    fun isProfileValid_requiresBodyMetricsActivityAndGoalSelection() {
        assertFalse(useCase.isProfileValid(UserProfile()))
        assertTrue(
            useCase.isProfileValid(
                UserProfile(
                    age = 25,
                    heightCm = 162,
                    weightKg = 59,
                    activityLevel = "Lightly Active",
                    goal = "General Health",
                )
            )
        )
    }
}
