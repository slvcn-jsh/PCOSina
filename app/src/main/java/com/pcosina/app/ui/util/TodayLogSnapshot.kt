package com.pcosina.app.ui.util

import com.pcosina.app.ui.ProgressViewModel

data class TodayMealDescriptor(
    val mealLabel: String,
    val title: String,
    val recipeId: String
)

data class TodayLogSnapshot(
    val plannedCount: Int,
    val completedCount: Int,
    val nextMeal: TodayMealDescriptor?
) {
    val completionRatio: Float
        get() = if (plannedCount > 0) {
            (completedCount.toFloat() / plannedCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

fun buildTodayLogSnapshot(
    todayMeals: List<TodayMealDescriptor>,
    completedMealIds: List<String>
): TodayLogSnapshot {
    val remaining = remainingTodayMealSlots(todayMeals, completedMealIds)
    val completedCount = (todayMeals.size - remaining.size).coerceAtLeast(0)
    val nextMeal = remaining.firstOrNull()
    return TodayLogSnapshot(
        plannedCount = todayMeals.size,
        completedCount = completedCount,
        nextMeal = nextMeal
    )
}

fun remainingTodayMealSlots(
    todayMeals: List<TodayMealDescriptor>,
    completedMealIds: List<String>
): List<TodayMealDescriptor> {
    val remaining = todayMeals.toMutableList()
    completedMealIds.forEach { loggedId ->
        if (remaining.isEmpty()) return@forEach

        val loggedRecipeId = ProgressViewModel.extractRecipeId(loggedId)
        val loggedMealLabel = normalizeMealLabel(ProgressViewModel.extractMealLabel(loggedId))

        val exactSlotIndex = if (loggedMealLabel.isNotBlank()) {
            remaining.indexOfFirst { slot ->
                slot.recipeId == loggedRecipeId &&
                    normalizeMealLabel(slot.mealLabel) == loggedMealLabel
            }
        } else {
            -1
        }

        val matchIndex = if (exactSlotIndex >= 0) {
            exactSlotIndex
        } else {
            remaining.indexOfFirst { slot -> slot.recipeId == loggedRecipeId }
        }

        if (matchIndex >= 0) {
            remaining.removeAt(matchIndex)
        }
    }
    return remaining
}
