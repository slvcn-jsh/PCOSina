package com.pcosina.app

import com.pcosina.app.domain.MealLoggingPolicyUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class MealLoggingPolicyUseCaseTest {

    private val useCase = MealLoggingPolicyUseCase()

    @Test
    fun evaluate_blocksLunchWhenBreakfastIsStillLoggableAndNotLogged() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(10, 45),
            mealLabel = "Lunch",
            completedMealLabels = emptyList(),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertFalse(decision.allowed)
        assertEquals("Log breakfast before lunch.", decision.reason)
    }

    @Test
    fun evaluate_allowsLunchAfterBreakfast() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(12, 0),
            mealLabel = "Lunch",
            completedMealLabels = listOf("Breakfast"),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertTrue(decision.allowed)
        assertEquals("", decision.reason)
    }

    @Test
    fun evaluate_allowsLunchWhenBreakfastWindowAlreadyClosed() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(12, 0),
            mealLabel = "Lunch",
            completedMealLabels = emptyList(),
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

    @Test
    fun evaluate_blocksMealBeforeItsTimeWindow() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(9, 0),
            mealLabel = "Dinner",
            completedMealLabels = listOf("Breakfast", "Lunch"),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertFalse(decision.allowed)
        assertEquals("Dinner logging opens at 4:30 PM.", decision.reason)
    }

    @Test
    fun evaluate_blocksMealAfterItsTimeWindow() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(23, 0),
            mealLabel = "Dinner",
            completedMealLabels = listOf("Breakfast", "Lunch"),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertFalse(decision.allowed)
        assertEquals("Dinner logging closed at 10:30 PM.", decision.reason)
    }
}
