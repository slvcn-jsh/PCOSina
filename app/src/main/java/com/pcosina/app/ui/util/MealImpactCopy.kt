package com.pcosina.app.ui.util

import java.util.Locale

fun mealImpactNextSuggestion(
    completedMealLabel: String?,
    nextMeal: String?,
    noNextFallback: String = "add a short reflection for today."
): String {
    val base = when (completedMealLabel?.lowercase(Locale.getDefault()).orEmpty()) {
        "breakfast" -> "Great start. Keep lunch balanced with protein + fiber."
        "lunch" -> "Nice midday progress. Keep dinner lighter and veggie-forward."
        "dinner" -> "Strong finish. Keep hydration and rest consistent tonight."
        else -> "Keep momentum with your next planned meal."
    }
    return if (!nextMeal.isNullOrBlank()) {
        "$base Next: $nextMeal."
    } else {
        "$base Next: $noNextFallback"
    }
}
