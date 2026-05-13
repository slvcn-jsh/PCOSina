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
    fun evaluate_blocksLunchUntilBreakfastIsLoggedOrSkipped() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(12, 0),
            mealLabel = "Lunch",
            completedMealLabels = emptyList(),
            skippedMealLabels = emptyList(),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertFalse(decision.allowed)
        assertEquals("Log or skip breakfast before lunch.", decision.reason)
    }

    @Test
    fun evaluate_allowsLunchAfterBreakfastIsLogged() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(1, 0),
            mealLabel = "Lunch",
            completedMealLabels = listOf("Breakfast"),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertTrue(decision.allowed)
        assertEquals("", decision.reason)
    }

    @Test
    fun evaluate_allowsLunchAfterBreakfastIsSkipped() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(1, 0),
            mealLabel = "Lunch",
            completedMealLabels = emptyList(),
            skippedMealLabels = listOf("Breakfast"),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertTrue(decision.allowed)
        assertEquals("", decision.reason)
    }

    @Test
    fun evaluate_blocksDinnerUntilPriorMealsAreLoggedOrSkipped() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(23, 0),
            mealLabel = "Dinner",
            completedMealLabels = listOf("Breakfast"),
            skippedMealLabels = emptyList(),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertFalse(decision.allowed)
        assertEquals("Log or skip lunch before dinner.", decision.reason)
    }

    @Test
    fun evaluate_allowsDinnerAfterBreakfastLoggedAndLunchSkipped() {
        val decision = useCase.evaluate(
            date = LocalDate.of(2026, 5, 2),
            now = LocalDate.of(2026, 5, 2),
            currentTime = LocalTime.of(23, 0),
            mealLabel = "Dinner",
            completedMealLabels = listOf("Breakfast"),
            skippedMealLabels = listOf("Lunch"),
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
            skippedMealLabels = listOf("Breakfast", "Lunch"),
            plannedMealLabels = listOf("Breakfast", "Lunch", "Dinner")
        )

        assertFalse(decision.allowed)
        assertEquals(
            "Past-day logging is locked. Log meals on the same day to keep insights accurate.",
            decision.reason
        )
    }

    @Test
    fun timeWindowLockReason_isDisabledForRespondentDemo() {
        assertEquals("", useCase.timeWindowLockReason("Breakfast", LocalTime.of(1, 0)))
        assertEquals("", useCase.timeWindowLockReason("Dinner", LocalTime.of(23, 0)))
    }
}
