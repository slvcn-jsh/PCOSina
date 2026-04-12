package com.pcosina.app.data.api

import com.pcosina.app.data.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

data class SwapOptionsRequestDto(
    val profile: UserProfile,
    val mealLabel: String,
    val currentRecipeId: String? = null,
    val activeRecipeIds: List<String> = emptyList(),
    val limit: Int = 20
)

data class GeneratePlanResponse(
    val weekLabel: String,
    val days: List<DayPlanDto>,
    val status: String,
    val message: String,
    val explanation: PlanExplanation? = null,
    val requestId: String? = null,
    val planId: String? = null,
    val policyVersion: String? = null,
    val machineReasonCodes: List<String> = emptyList(),
    val humanGuidance: List<String> = emptyList(),
    val suggestedRelaxations: List<String> = emptyList(),
    val diagnosticsReference: String? = null,
    val timestamps: PlannerTimestamps? = null
)

data class PlannerTimestamps(
    val requestedAtMs: Long? = null,
    val completedAtMs: Long? = null
)

data class GeneratePlanAsyncResponse(
    val jobId: String,
    val status: String,
    val executionMode: String? = null,
    val reused: Boolean? = null,
    val queueBackend: String? = null,
    val brokerSignalPublished: Boolean? = null
)

data class PlanJobDto(
    val id: String,
    val status: String,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val error: String? = null,
    val idempotencyKey: String? = null,
    val workerId: String? = null,
    val ownerUid: String? = null,
    val attemptCount: Int? = null,
    val nextAttemptAt: Long? = null,
    val result: GeneratePlanResponse? = null
)

data class PlanExplanation(
    val confidenceScore: Int? = null,
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

data class RecipeSummaryDto(
    val id: String,
    val title: String,
    val mealType: String? = null,
    val minutes: Int? = null
)

data class MlClientEventRequestDto(
    val eventName: String,
    val requestId: String? = null,
    val payload: Map<String, Any> = emptyMap()
)

data class MlClientEventResponseDto(
    val status: String,
    val eventName: String? = null,
    val requestId: String? = null
)

interface PcosinaApiService {
    @GET("health")
    suspend fun health(): HealthResponse

    @POST("generate-plan")
    suspend fun generatePlan(@Body request: GeneratePlanRequest): GeneratePlanResponse

    @POST("generate-plan-async")
    suspend fun generatePlanAsync(@Body request: GeneratePlanRequest): GeneratePlanAsyncResponse

    @GET("plan-jobs/{jobId}")
    suspend fun getPlanJob(@Path("jobId") jobId: String): PlanJobDto

    @GET("recipe/{id}")
    suspend fun getRecipe(@Path("id") recipeId: String): RecipeDetailDto

    @GET("recipes/summary")
    suspend fun getRecipeSummaries(
        @Query("meal_type") mealType: String? = null,
        @Query("limit") limit: Int = 50
    ): List<RecipeSummaryDto>

    @POST("recipes/swap-options")
    suspend fun getSwapOptions(@Body request: SwapOptionsRequestDto): List<RecipeSummaryDto>

    @POST("ml/events")
    suspend fun postMlEvent(@Body request: MlClientEventRequestDto): MlClientEventResponseDto
}
