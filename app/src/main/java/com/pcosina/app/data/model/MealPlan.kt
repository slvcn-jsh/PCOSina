package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class MealPlan(
    val weekLabel: String,
    val days: List<DayPlan>,
)

@Immutable
data class DayPlan(
    val dayLabel: String,
    val meals: List<PlannedMeal>,
)

@Immutable
data class PlannedMeal(
    val mealLabel: String, // Breakfast/Lunch/Dinner/Snack
    val recipeId: String,
)

