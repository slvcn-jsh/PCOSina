package com.pcosina.app.data.model

data class DailyLog(
    val date: String, // YYYY-MM-DD
    val completedMealIds: List<String> = emptyList(), // mealLabel::recipeId
    val skippedMealIds: List<String> = emptyList(), // mealLabel::recipeId
    val mealCheckIns: List<MealCheckIn> = emptyList(),
    val weightKg: Float? = null,
    val weightNote: String? = null,
    val energyLevel: Int? = null, // 1-5
    val cravingsLevel: Int? = null, // 1-5
    val moodLevel: Int? = null, // 1-5
    val symptomTags: List<String> = emptyList(),
    val symptomsNote: String? = null,
    val journalText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
