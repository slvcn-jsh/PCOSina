package com.pcosina.app.data.api

import com.pcosina.app.data.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.POST

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
    val message: String
)

interface PcosinaApiService {
    @POST("generate-plan")
    suspend fun generatePlan(@Body request: GeneratePlanRequest): GeneratePlanResponse
}
