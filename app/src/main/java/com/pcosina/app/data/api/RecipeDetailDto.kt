package com.pcosina.app.data.api

/**
 * Data Transfer Object for detailed recipe information from the backend.
 */
data class RecipeDetailDto(
    val id: String,
    val title: String,
    val mealType: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatsGrams: Int,
    val fiberGrams: Int,
    val tags: List<String>,
    val minutes: Int,
    val ingredients: List<IngredientDto>,
    val steps: List<String>
)

data class IngredientDto(
    val name: String,
    val quantity: String
)
