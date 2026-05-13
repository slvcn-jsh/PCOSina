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
    completedMealIds: List<String>,
    skippedMealIds: List<String> = emptyList()
): TodayLogSnapshot {
    val completedRemaining = remainingTodayMealSlots(todayMeals, completedMealIds)
    val remaining = remainingTodayMealSlots(
        todayMeals = todayMeals,
        completedMealIds = completedMealIds,
        skippedMealIds = skippedMealIds
    )
    val completedCount = (todayMeals.size - completedRemaining.size).coerceAtLeast(0)
    val nextMeal = remaining.firstOrNull()
    return TodayLogSnapshot(
        plannedCount = todayMeals.size,
        completedCount = completedCount,
        nextMeal = nextMeal
    )
}

fun remainingTodayMealSlots(
    todayMeals: List<TodayMealDescriptor>,
    completedMealIds: List<String>,
    skippedMealIds: List<String> = emptyList()
): List<TodayMealDescriptor> {
    val remaining = todayMeals.toMutableList()
    (completedMealIds + skippedMealIds).forEach { handledId ->
        removeHandledMealSlot(remaining, handledId)
    }
    return remaining
}

private fun removeHandledMealSlot(
    remaining: MutableList<TodayMealDescriptor>,
    handledId: String
) {
    if (remaining.isEmpty()) return

    val handledRecipeId = ProgressViewModel.extractRecipeId(handledId)
    val handledMealLabel = normalizeMealLabel(ProgressViewModel.extractMealLabel(handledId))

    val exactSlotIndex = if (handledMealLabel.isNotBlank()) {
        remaining.indexOfFirst { slot ->
            slot.recipeId == handledRecipeId &&
                normalizeMealLabel(slot.mealLabel) == handledMealLabel
        }
    } else {
        -1
    }

    val matchIndex = if (exactSlotIndex >= 0) {
        exactSlotIndex
    } else {
        remaining.indexOfFirst { slot -> slot.recipeId == handledRecipeId }
    }

    if (matchIndex >= 0) {
        remaining.removeAt(matchIndex)
    }
}
