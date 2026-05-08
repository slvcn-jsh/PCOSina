package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlannerRecipeDetail(
    val id: String,
    val title: String,
    val mealType: String? = null,
    val calories: Int? = null,
    val tags: List<String> = emptyList(),
    val proteinGrams: Int? = null,
    val carbsGrams: Int? = null,
    val fatsGrams: Int? = null,
    val fiberGrams: Int? = null,
    val minutes: Int? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val nutritionCorrectionId: String? = null,
    val nutritionDataSource: String? = null,
    val nutritionConfidence: String? = null,
    val nutritionReviewStatus: String? = null,
    val nutritionNotes: String? = null,
)

@Immutable
data class PlannerRecipeSummary(
    val id: String,
    val title: String,
    val mealType: String? = null,
    val minutes: Int? = null,
)
