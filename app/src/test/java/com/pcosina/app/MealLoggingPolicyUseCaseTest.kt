package com.pcosina.app

import com.pcosina.app.domain.MealLoggingPolicyUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MealLoggingPolicyUseCaseTest {

    private val useCase = MealLoggingPolicyUseCase()

    @Test
    fun evaluate_blocksDinnerWhenLunchNotLogged() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            mealLabel = "Dinner",
            completedMealLabels = listOf("Breakfast"),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertFalse(decision.allowed)
        assertEquals("Log lunch before dinner.", decision.reason)
    }

    @Test
    fun evaluate_allowsLunchAfterBreakfast() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            mealLabel = "Lunch",
            completedMealLabels = listOf("Breakfast"),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertTrue(decision.allowed)
        assertEquals("", decision.reason)
    }

    @Test
    fun evaluate_blocksPastDatesBeforeSequenceChecks() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 1),
            now = LocalDate.of(2026, 5, 2),
            mealLabel = "Dinner",
            completedMealLabels = emptyList(),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertFalse(decision.allowed)
        assertEquals(
            "Past-day logging is locked. Log meals on the same day to keep insights accurate.",
            decision.reason
        )
    }
}
