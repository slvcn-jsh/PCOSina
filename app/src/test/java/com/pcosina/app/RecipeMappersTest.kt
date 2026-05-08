package com.pcosina.app

import com.pcosina.app.data.api.DayPlanDto
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.IngredientDto
import com.pcosina.app.data.api.PlanExplanation
import com.pcosina.app.data.api.PlannedMealDto
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.api.toPlannerPlanResponse
import com.pcosina.app.data.api.toPlannerRecipeDetail
import com.pcosina.app.data.api.toPlannerRecipeSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeMappersTest {

    @Test
    fun recipeDetailDto_mapsToPlannerRecipeDetail() {
        val mapped = RecipeDetailDto(
            id = "recipe-1",
            title = "Chicken Tinola",
            mealType = "Lunch",
            calories = 420,
            tags = listOf("Soup"),
            proteinGrams = 32,
            carbsGrams = 18,
            fatsGrams = 14,
            fiberGrams = 4,
            minutes = 35,
            ingredients = listOf(
                IngredientDto(name = "Chicken", quantity = "500 g"),
                IngredientDto(name = "Malunggay", quantity = "1 bunch"),
            ),
            steps = listOf("Boil", "Season"),
            nutritionCorrectionId = "corr-1",
            nutritionDataSource = "nutritionist_review",
            nutritionConfidence = "high",
            nutritionReviewStatus = "reviewed",
        ).toPlannerRecipeDetail()

        assertEquals("recipe-1", mapped.id)
        assertEquals("Chicken Tinola", mapped.title)
        assertEquals("Lunch", mapped.mealType)
        assertEquals("Chicken", mapped.ingredients.first().name)
        assertEquals("500 g", mapped.ingredients.first().quantity)
        assertEquals("corr-1", mapped.nutritionCorrectionId)
        assertEquals("high", mapped.nutritionConfidence)
    }

    @Test
    fun recipeSummaryDto_mapsToPlannerRecipeSummary() {
        val mapped = RecipeSummaryDto(
            id = "swap-1",
            title = "Ginisang Monggo",
            mealType = "Dinner",
            minutes = 25,
        ).toPlannerRecipeSummary()

        assertEquals("swap-1", mapped.id)
        assertEquals("Ginisang Monggo", mapped.title)
        assertEquals("Dinner", mapped.mealType)
        assertEquals(25, mapped.minutes)
    }

    @Test
    fun generatePlanResponse_mapsToPlannerPlanResponse() {
        val mapped = GeneratePlanResponse(
            weekLabel = "May 1 - May 7",
            days = listOf(
                DayPlanDto(
                    dayLabel = "Mon",
                    meals = listOf(
                        PlannedMealDto(
                            mealLabel = "Breakfast",
                            recipeId = "recipe-1",
                            title = "Oatmeal"
                        )
                    ),
                    totalCalories = 1200
                )
            ),
            status = "success",
            message = "ok",
            explanation = PlanExplanation(
                targetCalories = 1500,
                targetProtein = 90,
                estimatedWeeklyCost = 1200
            ),
            requestId = "req-1",
            planId = "plan-1"
        ).toPlannerPlanResponse()

        assertEquals("May 1 - May 7", mapped.weekLabel)
        assertEquals("Mon", mapped.days.single().dayLabel)
        assertEquals("Breakfast", mapped.days.single().meals.single().mealLabel)
        assertEquals(1500, mapped.explanation?.targetCalories)
        assertEquals(1200, mapped.explanation?.estimatedWeeklyCost)
    }
}
