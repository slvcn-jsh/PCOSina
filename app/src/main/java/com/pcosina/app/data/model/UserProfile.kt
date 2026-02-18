package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val displayName: String = "",
    val age: Int = 0,
    val heightCm: Int = 0,
    val weightKg: Int = 0,
    val heightUnit: String = "cm",
    val weightUnit: String = "kg",
    val activityLevel: String = "Lightly Active",
    val goal: String = "",
    val insulinResistanceLevel: String = "Mild",
    val symptoms: List<String> = emptyList(),
    val comorbidities: List<String> = emptyList(),

    // Preferences & Constraints
    val dietaryRestrictions: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val weeklyBudgetPhp: Int = 0,
    val maxCookingTimeMinutes: Int = 45,
    val varietyPreference: String = "Balanced",
    val planningPriority: String = "Balanced",

    val pantryItems: List<String> = emptyList(),
    
    val isProfileCompleted: Boolean = false
)
