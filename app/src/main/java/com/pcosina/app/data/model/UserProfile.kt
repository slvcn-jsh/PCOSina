package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserProfile(
    val displayName: String,
    val age: Int,
    val heightCm: Int,
    val weightKg: Int,
    val goal: String,
    val activityLevel: String,
)

