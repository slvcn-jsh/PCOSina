package com.pcosina.app.ui.screens

import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.ui.util.TodayMealDescriptor
import com.pcosina.app.ui.util.buildTodayLogSnapshot
import com.pcosina.app.ui.util.remainingTodayMealSlots

enum class TodayTimelineState(val label: String) {
    Done("Done"),
    Now("Now"),
    Pending("Later"),
    Locked("Locked")
}

data class TodayTimelineStep(
    val label: String,
    val state: TodayTimelineState
)

fun buildTodayTimelineSteps(
    todayMeals: List<Pair<String, String>>,
    completedIds: List<String>,
    nextMealSlot: Pair<String, String>?
): List<TodayTimelineStep> {
    val canonical = listOf("Breakfast", "Lunch", "Dinner")
    val todayDescriptors = todayMeals.map { (mealLabel, recipeId) ->
        TodayMealDescriptor(
            mealLabel = mealLabel,
            title = mealLabel,
            recipeId = recipeId
        )
    }
    val remaining = remainingTodayMealSlots(todayDescriptors, completedIds)
    return canonical.map { label ->
        val meal = todayMeals.firstOrNull { it.first.equals(label, ignoreCase = true) }
        val state = when {
            meal == null -> TodayTimelineState.Locked
            remaining.none { slot ->
                slot.mealLabel.equals(meal.first, ignoreCase = true) &&
                    slot.recipeId == meal.second
            } -> TodayTimelineState.Done
            nextMealSlot != null &&
                meal.first.equals(nextMealSlot.first, ignoreCase = true) &&
                meal.second == nextMealSlot.second -> TodayTimelineState.Now
            else -> TodayTimelineState.Pending
        }
        TodayTimelineStep(label = label, state = state)
    }
}

fun isSundayCloseoutReady(
    sundayKey: String?,
    sundayPlanMeals: List<Pair<String, String>>,
    logs: Map<String, DailyLog>
): Boolean {
    if (sundayKey.isNullOrBlank()) return false
    if (sundayPlanMeals.isEmpty()) return false
    val descriptors = sundayPlanMeals.map { (mealLabel, recipeId) ->
        TodayMealDescriptor(
            mealLabel = mealLabel,
            title = mealLabel,
            recipeId = recipeId
        )
    }
    val snapshot = buildTodayLogSnapshot(
        todayMeals = descriptors,
        completedMealIds = logs[sundayKey]?.completedMealIds.orEmpty()
    )
    return snapshot.completedCount >= snapshot.plannedCount
}
