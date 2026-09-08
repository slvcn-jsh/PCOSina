package com.pcosina.app.data.model

data class MealCheckIn(
    val mealKey: String,
    val recipeId: String,
    val mealLabel: String,
    val energyLevel: Int? = null,
    val fullnessLevel: Int? = null,
    val cravingsLevel: Int? = null,
    val satisfactionLevel: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
