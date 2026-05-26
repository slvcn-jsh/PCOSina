package com.pcosina.app

import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.domain.WeightGoalGuardrailUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightGoalGuardrailUseCaseTest {

    private val useCase = WeightGoalGuardrailUseCase()

    @Test
    fun build_returnsDirectionOnlyWhenTargetDateIsMissing() {
        val summary = useCase.build(
            UserProfile(weightKg = 72, targetWeightKg = 68),
            LocalDate.of(2026, 5, 26)
        )

        assertTrue(summary.hasTarget)
        assertEquals("loss", summary.direction)
        assertEquals("direction_only", summary.status)
        assertNull(summary.weeklyChangeKg)
    }

    @Test
    fun build_flagsAggressivePaceForReview() {
        val summary = useCase.build(
            UserProfile(weightKg = 72, targetWeightKg = 66, targetDate = "2026-06-09"),
            LocalDate.of(2026, 5, 26)
        )

        assertEquals("review_pace", summary.status)
        assertTrue(kotlin.math.abs(summary.weeklyChangeKg ?: 0.0) > 1.0)
    }

    @Test
    fun build_acceptsModeratePaceWithinGuardrail() {
        val summary = useCase.build(
            UserProfile(weightKg = 72, targetWeightKg = 68, targetDate = "2026-07-21"),
            LocalDate.of(2026, 5, 26)
        )

        assertEquals("within_guardrail", summary.status)
        assertTrue(kotlin.math.abs(summary.weeklyChangeKg ?: 0.0) <= 1.0)
    }
}
