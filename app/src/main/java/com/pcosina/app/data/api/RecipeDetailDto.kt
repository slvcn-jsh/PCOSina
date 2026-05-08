package com.pcosina.app.data.api

data class IngredientDto(
    val name: String,
    val quantity: String
)

data class RecipeDetailDto(
    val id: String,
    val title: String,
    val mealType: String? = null,
    val calories: Int? = null,
    val tags: List<String> = emptyList(),
    
    // Detailed nutritional and instruction data
    val proteinGrams: Int? = null,
    val carbsGrams: Int? = null,
    val fatsGrams: Int? = null,
    val fiberGrams: Int? = null,
    val minutes: Int? = null,
    val ingredients: List<IngredientDto> = emptyList(),
    val steps: List<String> = emptyList(),
    val nutritionCorrectionId: String? = null,
    val nutritionDataSource: String? = null,
    val nutritionConfidence: String? = null,
    val nutritionReviewStatus: String? = null,
    val nutritionNotes: String? = null
)
