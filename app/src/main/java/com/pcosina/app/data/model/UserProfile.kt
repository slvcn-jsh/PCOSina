package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val displayName: String = "Maria",
    val age: Int = 25,
    val heightCm: Int = 160,
    val weightKg: Int = 65,
    val activityLevel: String = "Lightly Active",
    val goal: String = "Support PCOS symptom management",
    val insulinResistanceLevel: String = "Mild",
    val symptoms: List<String> = emptyList(),
    val comorbidities: List<String> = emptyList(),
    val dietaryRestrictions: List<String> = emptyList(),
    val weeklyBudgetPhp: Int = 2000
)
