package com.pcosina.app.data.api

import com.pcosina.app.data.model.Ingredient
import com.pcosina.app.data.model.PlannerRecipeDetail
import com.pcosina.app.data.model.PlannerRecipeSummary

fun RecipeDetailDto.toPlannerRecipeDetail(): PlannerRecipeDetail =
    PlannerRecipeDetail(
        id = id,
        title = title,
        mealType = mealType,
        calories = calories,
        tags = tags,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatsGrams = fatsGrams,
        fiberGrams = fiberGrams,
        minutes = minutes,
        ingredients = ingredients.map { Ingredient(name = it.name, quantity = it.quantity) },
        steps = steps,
        nutritionCorrectionId = nutritionCorrectionId,
        nutritionDataSource = nutritionDataSource,
        nutritionConfidence = nutritionConfidence,
        nutritionReviewStatus = nutritionReviewStatus,
        nutritionNotes = nutritionNotes,
    )

fun RecipeSummaryDto.toPlannerRecipeSummary(): PlannerRecipeSummary =
    PlannerRecipeSummary(
        id = id,
        title = title,
        mealType = mealType,
        minutes = minutes,
    )
