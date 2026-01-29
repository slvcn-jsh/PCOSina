package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Recipe(
    val id: String,
    val title: String,
    val subtitle: String,
    val minutes: Int,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatsGrams: Int,
    val fiberGrams: Int,
    val tags: List<String> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
)

