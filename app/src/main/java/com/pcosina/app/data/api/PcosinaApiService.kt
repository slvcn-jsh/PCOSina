package com.pcosina.app.data.api

import com.pcosina.app.data.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
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
    val mealsPerDay: Int = 3,
    val startDate: String? = null
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
    val restrictionCount: Int? = null,
    val budgetHardCapApplied: Boolean? = null,
    val householdPlanningMode: String? = null,
    val goalValue: String? = null,
    val symptomSelections: List<String> = emptyList(),
    val profileRuleEffects: Map<String, List<String>>? = null,
    val symptomStrategy: List<String> = emptyList(),
    val candidateExclusionSummary: Map<String, Int>? = null,
    val selectionReasonsByRecipeId: Map<String, List<String>>? = null,
    val selectionReasonCounts: Map<String, Int>? = null,
    val fiberMinTarget: Int? = null,
    val sugarMaxTarget: Int? = null,
    val goalStrategy: List<String> = emptyList()
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

data class AdminRecipeListResponse(
    val items: List<RecipeDetailDto> = emptyList(),
    val count: Int = 0
)

data class AdminPriceRuleDto(
    val id: String,
    val keywords: List<String> = emptyList(),
    val pricePhp: Int,
    val priceMinPhp: Int? = null,
    val priceMaxPhp: Int? = null,
    val category: String,
    val unit: String? = null,
    val active: Boolean = true,
    val notes: String? = null,
    val updatedAt: Long? = null
)

data class AdminRecipeUpsertDto(
    val id: String? = null,
    val title: String,
    val mealType: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatsGrams: Int,
    val fiberGrams: Int,
    val tags: List<String> = emptyList(),
    val minutes: Int = 25,
    val ingredients: List<IngredientDto> = emptyList(),
    val steps: List<String> = emptyList()
)

data class AdminPriceRuleUpsertDto(
    val id: String? = null,
    val keywords: List<String> = emptyList(),
    val pricePhp: Int,
    val priceMinPhp: Int? = null,
    val priceMaxPhp: Int? = null,
    val category: String,
    val unit: String? = null,
    val active: Boolean = true,
    val notes: String? = null
)

data class AdminPriceRuleListResponse(
    val items: List<AdminPriceRuleDto> = emptyList(),
    val count: Int = 0
)

data class AdminDeleteResponse(
    val status: String,
    val deleted: Int = 0,
    val id: String? = null
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
    suspend fun generatePlanAsync(
        @Body request: GeneratePlanRequest,
        @Header("Idempotency-Key") idempotencyKey: String? = null
    ): GeneratePlanAsyncResponse

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

    @GET("admin/recipes")
    suspend fun getAdminRecipes(
        @Query("q") query: String? = null,
        @Query("meal_type") mealType: String? = null,
        @Query("limit") limit: Int = 250
    ): AdminRecipeListResponse

    @POST("admin/recipes")
    suspend fun createAdminRecipe(@Body request: AdminRecipeUpsertDto): RecipeDetailDto

    @PUT("admin/recipes/{recipeId}")
    suspend fun updateAdminRecipe(
        @Path("recipeId") recipeId: String,
        @Body request: AdminRecipeUpsertDto
    ): RecipeDetailDto

    @DELETE("admin/recipes/{recipeId}")
    suspend fun deleteAdminRecipe(@Path("recipeId") recipeId: String): AdminDeleteResponse

    @GET("admin/price-rules")
    suspend fun getAdminPriceRules(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 250
    ): AdminPriceRuleListResponse

    @POST("admin/price-rules")
    suspend fun createAdminPriceRule(@Body request: AdminPriceRuleUpsertDto): AdminPriceRuleDto

    @PUT("admin/price-rules/{ruleId}")
    suspend fun updateAdminPriceRule(
        @Path("ruleId") ruleId: String,
        @Body request: AdminPriceRuleUpsertDto
    ): AdminPriceRuleDto

    @DELETE("admin/price-rules/{ruleId}")
    suspend fun deleteAdminPriceRule(@Path("ruleId") ruleId: String): AdminDeleteResponse

    @POST("ml/events")
    suspend fun postMlEvent(@Body request: MlClientEventRequestDto): MlClientEventResponseDto
}
