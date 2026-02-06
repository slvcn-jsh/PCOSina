package com.pcosina.app.data.api

import com.pcosina.app.data.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class PlannedMealDto(
    val mealLabel: String,
    val recipeId: String,
    val title: String
)

data class DayPlanDto(
    val dayLabel: String,
    val meals: List<PlannedMealDto>,
    val totalCalories: Int
)

data class GeneratePlanRequest(
    val profile: UserProfile,
    val days: Int = 7,
    val mealsPerDay: Int = 3
)

data class GeneratePlanResponse(
    val weekLabel: String,
    val days: List<DayPlanDto>,
    val status: String,
    val message: String,
    val explanation: PlanExplanation? = null
)

data class PlanExplanation(
    val targetCalories: Int? = null,
    val avgCalories: Int? = null,
    val avgCaloriesDeviation: Int? = null,
    val targetProtein: Int? = null,
    val avgProtein: Int? = null,
    val targetCarbs: Int? = null,
    val avgCarbs: Int? = null,
    val targetFats: Int? = null,
    val avgFats: Int? = null,
    val toleranceUsed: Double? = null,
    val maxPerWeek: Int? = null,
    val pantryMatches: Int? = null,
    val uniqueVegTokens: Int? = null,
    val budgetWeekly: Double? = null,
    val estimatedWeeklyCost: Int? = null,
    val restrictionCount: Int? = null
)

data class HealthResponse(
    val status: String
)

interface PcosinaApiService {
    @GET("health")
    suspend fun health(): HealthResponse

    @POST("generate-plan")
    suspend fun generatePlan(@Body request: GeneratePlanRequest): GeneratePlanResponse

    @GET("recipe/{id}")
    suspend fun getRecipe(@Path("id") recipeId: String): RecipeDetailDto
}
