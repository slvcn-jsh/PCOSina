package com.pcosina.app

import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.ui.screens.TodayTimelineState
import com.pcosina.app.ui.screens.buildTodayTimelineSteps
import com.pcosina.app.ui.screens.isSundayCloseoutReady
import com.pcosina.app.ui.ProgressViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTodayTimelineTest {

    @Test
    fun timeline_marksDoneNowPendingAndLockedStates() {
        val todayMeals = listOf(
            "Breakfast" to "r_breakfast",
            "Lunch" to "r_lunch"
        )
        val completed = listOf(
            ProgressViewModel.buildMealKey("Breakfast", "r_breakfast")
        )

        val steps = buildTodayTimelineSteps(
            todayMeals = todayMeals,
            completedIds = completed,
            nextMealSlot = "Lunch" to "r_lunch"
        )

        assertEquals(TodayTimelineState.Done, steps.first { it.label == "Breakfast" }.state)
        assertEquals(TodayTimelineState.Now, steps.first { it.label == "Lunch" }.state)
        assertEquals(TodayTimelineState.Locked, steps.first { it.label == "Dinner" }.state)
    }

    @Test
    fun timeline_marksPendingWhenMealExistsButNotDoneOrNext() {
        val todayMeals = listOf(
            "Breakfast" to "r_breakfast",
            "Lunch" to "r_lunch",
            "Dinner" to "r_dinner"
        )

        val steps = buildTodayTimelineSteps(
            todayMeals = todayMeals,
            completedIds = emptyList(),
            nextMealSlot = "Lunch" to "r_lunch"
        )

        assertEquals(TodayTimelineState.Pending, steps.first { it.label == "Dinner" }.state)
    }

    @Test
    fun timeline_treatsSkippedMealAsHandledForNextState() {
        val todayMeals = listOf(
            "Breakfast" to "r_breakfast",
            "Lunch" to "r_lunch"
        )
        val skipped = listOf(
            ProgressViewModel.buildMealKey("Breakfast", "r_breakfast")
        )

        val steps = buildTodayTimelineSteps(
            todayMeals = todayMeals,
            completedIds = emptyList(),
            skippedIds = skipped,
            nextMealSlot = "Lunch" to "r_lunch"
        )

        assertEquals(TodayTimelineState.Done, steps.first { it.label == "Breakfast" }.state)
        assertEquals(TodayTimelineState.Now, steps.first { it.label == "Lunch" }.state)
    }


    @Test
    fun sundayCloseout_readyOnlyWhenAllPlannedMealsCompleted() {
        val sundayKey = "2026-02-15"
        val planned = listOf(
            "Breakfast" to "r1",
            "Lunch" to "r2",
            "Dinner" to "r3"
        )
        val partialLogs = mapOf(
            sundayKey to DailyLog(
                date = sundayKey,
                completedMealIds = listOf(
                    ProgressViewModel.buildMealKey("Breakfast", "r1"),
                    ProgressViewModel.buildMealKey("Lunch", "r2")
                )
            )
        )

        assertFalse(
            isSundayCloseoutReady(
                sundayKey = sundayKey,
                sundayPlanMeals = planned,
                logs = partialLogs
            )
        )

        val fullLogs = mapOf(
            sundayKey to DailyLog(
                date = sundayKey,
                completedMealIds = listOf(
                    ProgressViewModel.buildMealKey("Breakfast", "r1"),
                    ProgressViewModel.buildMealKey("Lunch", "r2"),
                    ProgressViewModel.buildMealKey("Dinner", "r3")
                )
            )
        )

        assertTrue(
            isSundayCloseoutReady(
                sundayKey = sundayKey,
                sundayPlanMeals = planned,
                logs = fullLogs
            )
        )
    }

    @Test
    fun sundayCloseout_requiresAllRepeatedRecipeSlots() {
        val sundayKey = "2026-02-22"
        val planned = listOf(
            "Breakfast" to "r1",
            "Lunch" to "r2",
            "Dinner" to "r1"
        )
        val singleR1Logged = mapOf(
            sundayKey to DailyLog(
                date = sundayKey,
                completedMealIds = listOf(
                    ProgressViewModel.buildMealKey("Breakfast", "r1"),
                    ProgressViewModel.buildMealKey("Lunch", "r2")
                )
            )
        )

        assertFalse(
            isSundayCloseoutReady(
                sundayKey = sundayKey,
                sundayPlanMeals = planned,
                logs = singleR1Logged
            )
        )
    }

    @Test
    fun sundayCloseout_acceptsSkippedMealsAsHandled() {
        val sundayKey = "2026-02-22"
        val planned = listOf(
            "Breakfast" to "r1",
            "Lunch" to "r2",
            "Dinner" to "r3"
        )
        val logs = mapOf(
            sundayKey to DailyLog(
                date = sundayKey,
                completedMealIds = listOf(ProgressViewModel.buildMealKey("Breakfast", "r1")),
                skippedMealIds = listOf(
                    ProgressViewModel.buildMealKey("Lunch", "r2"),
                    ProgressViewModel.buildMealKey("Dinner", "r3")
                )
            )
        )

        assertTrue(
            isSundayCloseoutReady(
                sundayKey = sundayKey,
                sundayPlanMeals = planned,
                logs = logs
            )
        )
    }
}
