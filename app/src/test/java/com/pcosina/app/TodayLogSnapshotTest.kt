package com.pcosina.app

import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.util.TodayMealDescriptor
import com.pcosina.app.ui.util.buildTodayLogSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayLogSnapshotTest {

    @Test
    fun snapshot_countsCompletedAndNextMeal_fromMealKeys() {
        val meals = listOf(
            TodayMealDescriptor("Breakfast", "Oats", "r1"),
            TodayMealDescriptor("Lunch", "Tinola", "r2"),
            TodayMealDescriptor("Dinner", "Fish", "r3")
        )
        val completed = listOf(
            ProgressViewModel.buildMealKey("Breakfast", "r1")
        )

        val snapshot = buildTodayLogSnapshot(meals, completed)

        assertEquals(3, snapshot.plannedCount)
        assertEquals(1, snapshot.completedCount)
        assertEquals("r2", snapshot.nextMeal?.recipeId)
    }

    @Test
    fun snapshot_supportsLegacyRecipeOnlyKeys() {
        val meals = listOf(
            TodayMealDescriptor("Breakfast", "Oats", "r1"),
            TodayMealDescriptor("Lunch", "Tinola", "r2")
        )

        val snapshot = buildTodayLogSnapshot(meals, completedMealIds = listOf("r1"))

        assertEquals(2, snapshot.plannedCount)
        assertEquals(1, snapshot.completedCount)
        assertEquals("r2", snapshot.nextMeal?.recipeId)
    }

    @Test
    fun snapshot_returnsNoNextMeal_whenComplete() {
        val meals = listOf(
            TodayMealDescriptor("Breakfast", "Oats", "r1")
        )
        val completed = listOf(ProgressViewModel.buildMealKey("Breakfast", "r1"))

        val snapshot = buildTodayLogSnapshot(meals, completed)

        assertEquals(1, snapshot.completedCount)
        assertNull(snapshot.nextMeal)
    }

    @Test
    fun snapshot_skipsDoNotCountAsLoggedButMoveNextMealForward() {
        val meals = listOf(
            TodayMealDescriptor("Breakfast", "Oats", "r1"),
            TodayMealDescriptor("Lunch", "Tinola", "r2"),
            TodayMealDescriptor("Dinner", "Fish", "r3")
        )
        val skipped = listOf(
            ProgressViewModel.buildMealKey("Breakfast", "r1")
        )

        val snapshot = buildTodayLogSnapshot(
            todayMeals = meals,
            completedMealIds = emptyList(),
            skippedMealIds = skipped
        )

        assertEquals(3, snapshot.plannedCount)
        assertEquals(0, snapshot.completedCount)
        assertEquals("Lunch", snapshot.nextMeal?.mealLabel)
    }

    @Test
    fun snapshot_countsDuplicateRecipeSlotsIndividually() {
        val meals = listOf(
            TodayMealDescriptor("Breakfast", "Tinola", "r1"),
            TodayMealDescriptor("Lunch", "Veggies", "r2"),
            TodayMealDescriptor("Dinner", "Tinola", "r1")
        )
        val completed = listOf(
            ProgressViewModel.buildMealKey("Breakfast", "r1"),
            ProgressViewModel.buildMealKey("Lunch", "r2")
        )

        val snapshot = buildTodayLogSnapshot(meals, completed)

        assertEquals(3, snapshot.plannedCount)
        assertEquals(2, snapshot.completedCount)
        assertEquals("Dinner", snapshot.nextMeal?.mealLabel)
        assertEquals("r1", snapshot.nextMeal?.recipeId)
    }
}
