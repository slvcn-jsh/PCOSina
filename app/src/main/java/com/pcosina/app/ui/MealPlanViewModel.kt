package com.pcosina.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pcosina.app.data.api.AdminPriceRuleDto
import com.pcosina.app.data.api.AdminPriceRuleUpsertDto
import com.pcosina.app.data.api.AdminRecipeUpsertDto
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.model.PlanInstance
import com.pcosina.app.data.model.PlannerDayPlan
import com.pcosina.app.data.model.PlannerPlanExplanation
import com.pcosina.app.data.model.PlannerPlanResponse
import com.pcosina.app.data.model.PlannerPlannedMeal
import com.pcosina.app.data.model.PlannerRecipeDetail
import com.pcosina.app.data.model.PlannerRecipeSummary
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.PlannerLocalRepository
import com.pcosina.app.data.repository.UserPreferencesPlannerLocalRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.domain.PlannerProfilePreparationUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class MealPlanUiState {
    object Idle : MealPlanUiState()
    object Loading : MealPlanUiState()
    data class Success(val response: PlannerPlanResponse, val timestamp: Long) : MealPlanUiState()
    data class Error(val message: String) : MealPlanUiState()
}

sealed class RecipeDetailsUiState {
    object Idle : RecipeDetailsUiState()
    object Loading : RecipeDetailsUiState()
    data class Success(val recipe: PlannerRecipeDetail) : RecipeDetailsUiState()
    data class Error(val message: String) : RecipeDetailsUiState()
}

sealed class MealPlanGenerationNotice {
    data class NoSafePlan(
        val message: String,
        val guidance: List<String>,
        val diagnosticsReference: String?,
        val continuityPlanAvailable: Boolean
    ) : MealPlanGenerationNotice()

    data class ContinuityFallback(
        val message: String,
        val continuityPlanAvailable: Boolean
    ) : MealPlanGenerationNotice()
}

data class PlanMetrics(
    val avgProtein: Int = 0,
    val avgCarbs: Int = 0,
    val avgFiber: Int = 0,
    val avgFats: Int = 0
)

private data class PendingGenerateRequest(
    val attempt: MealPlanRepository.GeneratePlanAttempt
)

class MealPlanViewModel(
    private val repository: MealPlanRepository,
    private val plannerLocalRepository: PlannerLocalRepository
) : ViewModel() {
    constructor(
        repository: MealPlanRepository,
        userPreferencesRepository: UserPreferencesRepository
    ) : this(repository, UserPreferencesPlannerLocalRepository(userPreferencesRepository))


    private val _uiState = MutableStateFlow<MealPlanUiState>(MealPlanUiState.Idle)
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    private val _recipeState = MutableStateFlow<RecipeDetailsUiState>(RecipeDetailsUiState.Idle)
    val recipeState: StateFlow<RecipeDetailsUiState> = _recipeState.asStateFlow()

    private val _generationNotice = MutableStateFlow<MealPlanGenerationNotice?>(null)
    val generationNotice: StateFlow<MealPlanGenerationNotice?> = _generationNotice.asStateFlow()

    private val _planMetrics = MutableStateFlow(PlanMetrics())
    val planMetrics: StateFlow<PlanMetrics> = _planMetrics.asStateFlow()

    private val _planHistory = MutableStateFlow<List<PlanInstance>>(emptyList())
    val planHistory: StateFlow<List<PlanInstance>> = _planHistory.asStateFlow()

    private val _activePlanId = MutableStateFlow<String?>(null)
    val activePlanId: StateFlow<String?> = _activePlanId.asStateFlow()

    private val _activeWeekStart = MutableStateFlow<String?>(null)
    val activeWeekStart: StateFlow<String?> = _activeWeekStart.asStateFlow()

    private val _activeWeekEnd = MutableStateFlow<String?>(null)
    val activeWeekEnd: StateFlow<String?> = _activeWeekEnd.asStateFlow()

    private val _planExpired = MutableStateFlow(false)
    val planExpired: StateFlow<Boolean> = _planExpired.asStateFlow()
    private val _lastReviewedWeek = MutableStateFlow<String?>(null)
    val lastReviewedWeek: StateFlow<String?> = _lastReviewedWeek.asStateFlow()

    private var currentUserId: String = ""
    private val gson = Gson()
    private val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private var pendingGenerateRequest: PendingGenerateRequest? = null
    private val plannerProfilePreparationUseCase = PlannerProfilePreparationUseCase()

    private data class ContinuityPlanSnapshot(
        val response: PlannerPlanResponse,
        val timestamp: Long,
        val activePlanId: String?,
        val weekStart: String?,
        val weekEnd: String?,
        val expired: Boolean
    )

    fun loadSavedPlan(userId: String) {
        currentUserId = userId
        viewModelScope.launch {
            _generationNotice.value = null
            _uiState.value = MealPlanUiState.Idle 
            var history = loadPlanHistory(userId)
            if (history.isEmpty()) {
                val savedJson = plannerLocalRepository.getSavedPlanJson(userId).first()
                val savedTimestamp = plannerLocalRepository.getSavedPlanTimestamp(userId).first()
                if (!savedJson.isNullOrBlank()) {
                    try {
                        val response = gson.fromJson(savedJson, PlannerPlanResponse::class.java)
                        val generatedAt = if (savedTimestamp > 0) savedTimestamp else System.currentTimeMillis()
                        val start = weekStartDate(generatedAt)
                        val end = start.plusDays(6)
                        val id = start.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val instance = PlanInstance(
                            id = id,
                            weekStart = id,
                            weekEnd = end.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            generatedAt = generatedAt,
                            response = normalizeResponse(response.copy(weekLabel = weekLabelFor(start)), start)
                        )
                        history = listOf(instance)
                        savePlanHistory(history)
                        plannerLocalRepository.saveActivePlanId(userId, id)
                    } catch (_: Exception) {
                        history = emptyList()
                    }
                }
            }
            val normalizedHistory = history.map { plan ->
                val start = parsePlanDate(plan.weekStart) ?: weekStartDate(plan.generatedAt)
                plan.copy(response = normalizeResponse(plan.response, start))
            }
            if (normalizedHistory != history) {
                savePlanHistory(normalizedHistory)
            }
            _planHistory.value = normalizedHistory
            val activeId = plannerLocalRepository.getActivePlanId(userId).first()
            val reviewed = plannerLocalRepository.getLastReviewedWeek(userId).first()
            _lastReviewedWeek.value = reviewed
            val today = LocalDate.now()
            val overlappingPlans = normalizedHistory.filter { containsDate(it, today) }
            val currentPlan = when {
                overlappingPlans.isEmpty() -> null
                !activeId.isNullOrBlank() -> overlappingPlans
                    .filter { it.id == activeId }
                    .maxByOrNull { it.generatedAt }
                    ?: overlappingPlans.maxByOrNull { it.generatedAt }
                else -> overlappingPlans.maxByOrNull { it.generatedAt }
            }
            val active = when {
                currentPlan != null -> currentPlan
                !activeId.isNullOrBlank() -> normalizedHistory.firstOrNull { it.id == activeId }
                else -> normalizedHistory.maxByOrNull { it.generatedAt }
            }
            val expired = active?.let { isExpired(it) } ?: false
            _planExpired.value = expired && normalizedHistory.none { containsDate(it, today) }
            _activePlanId.value = active?.id
            _activeWeekStart.value = active?.weekStart
            _activeWeekEnd.value = active?.weekEnd
            if (active != null && !expired) {
                _uiState.value = MealPlanUiState.Success(active.response, active.generatedAt)
                calculateMetrics(active.response)
                emitMlEventSafe(
                    eventName = "plan_viewed",
                    requestId = active.response.requestId,
                    payload = mapOf(
                        "plan_id" to active.id,
                        "source" to "local_cache"
                    )
                )
            } else {
                _uiState.value = MealPlanUiState.Idle
            }
        }
    }

    private fun calculateMetrics(response: PlannerPlanResponse) {
        viewModelScope.launch {
            val mealIds = response.days.flatMap { it.meals }.mapNotNull { it.recipeId }
            val countsById = mealIds.groupingBy { it }.eachCount()
            val deferredDetails = countsById.keys.map { id -> async { repository.getRecipeDetails(id).getOrNull() } }
            val allDetails = deferredDetails.awaitAll().filterNotNull()
            
            if (allDetails.isNotEmpty()) {
                val totalP = allDetails.sumOf { (it.proteinGrams ?: 0) * (countsById[it.id] ?: 1) }
                val totalC = allDetails.sumOf { (it.carbsGrams ?: 0) * (countsById[it.id] ?: 1) }
                val totalF = allDetails.sumOf { (it.fiberGrams ?: 0) * (countsById[it.id] ?: 1) }
                val totalFat = allDetails.sumOf { (it.fatsGrams ?: 0) * (countsById[it.id] ?: 1) }
                val dayDivisor = if (response.days.isNotEmpty()) response.days.size else 7
                _planMetrics.value = PlanMetrics(
                    avgProtein = (totalP / dayDivisor),
                    avgCarbs = (totalC / dayDivisor),
                    avgFiber = (totalF / dayDivisor),
                    avgFats = (totalFat / dayDivisor)
                )
            }
        }
    }

    fun reset() {
        currentUserId = ""
        _uiState.value = MealPlanUiState.Idle
        _recipeState.value = RecipeDetailsUiState.Idle
        _generationNotice.value = null
        _planMetrics.value = PlanMetrics()
        _planHistory.value = emptyList()
        _activePlanId.value = null
        _activeWeekStart.value = null
        _activeWeekEnd.value = null
        _planExpired.value = false
        pendingGenerateRequest = null
    }

    fun generateMealPlan(profile: UserProfile) {
        viewModelScope.launch {
            val continuityPlan = continuityPlanSnapshot()
            _generationNotice.value = null
            _uiState.value = MealPlanUiState.Loading
            val warmupResult = repository.warmup()
            val warmupError = warmupResult.exceptionOrNull()
            if (warmupError != null) {
                pendingGenerateRequest = null
                val message = warmupError.message ?: "Cannot reach planner service right now."
                if (continuityPlan != null) {
                    presentContinuityFallback(message, continuityPlan)
                } else {
                    _generationNotice.value = null
                    _uiState.value = MealPlanUiState.Error(message)
                }
                return@launch
            }
            val storedProfile = if (currentUserId.isBlank() || plannerProfilePreparationUseCase.isProfileValid(profile)) {
                null
            } else {
                runCatching { plannerLocalRepository.getUserProfile(currentUserId).first() }.getOrNull()
            }
            val feedbackTags = if (currentUserId.isBlank()) {
                emptyList()
            } else {
                plannerLocalRepository.getPlanFeedbackTags(currentUserId).first()
            }
            val apiProfile = plannerProfilePreparationUseCase(
                requestedProfile = profile,
                storedProfile = storedProfile,
                feedbackTags = feedbackTags
            ).plannerProfile
            val activeAttempt = pendingGenerateRequest?.attempt ?: repository.createGeneratePlanAttempt().also {
                pendingGenerateRequest = PendingGenerateRequest(it)
            }
            val result = repository.generatePlan(apiProfile, activeAttempt)
            result.onSuccess { response ->
                if (response.status.equals("no-safe-plan", ignoreCase = true) || response.days.isEmpty()) {
                    pendingGenerateRequest = null
                    val reasonCodes = if (response.machineReasonCodes.isNotEmpty()) {
                        response.machineReasonCodes
                    } else {
                        listOf("UNKNOWN_INFEASIBILITY")
                    }
                    emitMlEventSafe(
                        eventName = "no_safe_plan_encountered",
                        requestId = response.requestId,
                        payload = mapOf(
                            "reason_codes" to reasonCodes,
                            "status" to "no-safe-plan"
                        )
                    )
                    val guidance = (response.humanGuidance + response.suggestedRelaxations)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                    val combined = guidance.firstOrNull()
                        ?: response.message.trim().takeIf { it.isNotBlank() }
                        ?: "No safe plan could be generated. Adjust non-safety preferences and retry."
                    presentNoSafePlan(
                        message = combined,
                        guidance = guidance,
                        diagnosticsReference = response.diagnosticsReference,
                        continuityPlan = continuityPlan
                    )
                    return@onSuccess
                }
                pendingGenerateRequest = null
                val completedAtMs = response.timestamps?.completedAtMs
                    ?.takeIf { it > 0 }
                    ?: System.currentTimeMillis()
                val startAnchorMs = response.timestamps?.requestedAtMs
                    ?.takeIf { it > 0 }
                    ?: completedAtMs
                val start = weekStartDate(startAnchorMs)
                val end = start.plusDays(6)
                val id = start.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val withLabel = normalizeResponse(response.copy(weekLabel = weekLabelFor(start)), start)
                val instance = PlanInstance(
                    id = id,
                    weekStart = id,
                    weekEnd = end.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    generatedAt = completedAtMs,
                    response = withLabel
                )
                _generationNotice.value = null
                _uiState.value = MealPlanUiState.Success(withLabel, completedAtMs)
                calculateMetrics(withLabel)
                if (currentUserId.isNotBlank()) {
                    upsertPlanInstance(instance)
                    plannerLocalRepository.savePlanJson(currentUserId, gson.toJson(withLabel), completedAtMs)
                    plannerLocalRepository.saveActivePlanId(currentUserId, id)
                    _activePlanId.value = id
                    _activeWeekStart.value = instance.weekStart
                    _activeWeekEnd.value = instance.weekEnd
                    _planExpired.value = false
                }
                val planTelemetryId = withLabel.planId ?: id
                emitMlEventSafe(
                    eventName = "plan_generated",
                    requestId = withLabel.requestId,
                    payload = mapOf(
                        "status" to "success",
                        "plan_id" to planTelemetryId,
                        "slot_count" to withLabel.days.sumOf { day -> day.meals.size },
                        "source" to "mobile_generation_flow"
                    )
                )
                emitMlEventSafe(
                    eventName = "plan_viewed",
                    requestId = withLabel.requestId,
                    payload = mapOf("plan_id" to planTelemetryId, "source" to "fresh_generation")
                )
            }.onFailure { error ->
                val raw = error.message ?: "Failed to connect to MILP engine"
                if (!shouldKeepPendingGenerateRequest(raw)) {
                    pendingGenerateRequest = null
                }
                val message = if (raw.contains("Profile invalid", ignoreCase = true)) {
                    "Profile incomplete. Please open Profile or Settings and save your age, height, and weight."
                } else if (
                    raw.contains("Infeasible", ignoreCase = true) ||
                    raw.contains("No feasible", ignoreCase = true) ||
                    raw.contains("No safe recipes", ignoreCase = true)
                ) {
                    "No feasible plan found for your current settings. " +
                        "Try reducing restrictions/allergies, increasing max cooking time, or relaxing budget."
                } else {
                    raw
                }
                _generationNotice.value = null
                _uiState.value = MealPlanUiState.Error(message)
            }
        }
    }

    fun generateMealPlanFresh(profile: UserProfile) {
        pendingGenerateRequest = null
        generateMealPlan(profile)
    }

    private fun shouldKeepPendingGenerateRequest(message: String): Boolean {
        val normalized = message.trim().lowercase(Locale.US)
        return normalized.contains("plan generation is still running") ||
            normalized.contains("keep waiting for the same request")
    }

    fun showError(message: String) {
        _generationNotice.value = null
        _uiState.value = MealPlanUiState.Error(message)
    }

    fun showNoSafePlan(
        message: String,
        guidance: List<String> = emptyList(),
        diagnosticsReference: String? = null
    ) {
        presentNoSafePlan(
            message = message,
            guidance = guidance,
            diagnosticsReference = diagnosticsReference,
            continuityPlan = continuityPlanSnapshot()
        )
    }

    fun loadRecipeDetails(recipeId: String) {
        viewModelScope.launch {
            _recipeState.value = RecipeDetailsUiState.Loading
            val result = repository.getRecipeDetails(recipeId)
            result.onSuccess { dto ->
                _recipeState.value = RecipeDetailsUiState.Success(dto)
                emitMlEventSafe(
                    eventName = "recipe_opened",
                    requestId = (_uiState.value as? MealPlanUiState.Success)?.response?.requestId,
                    payload = mapOf("recipe_id" to dto.id)
                )
            }.onFailure { e ->
                _recipeState.value = RecipeDetailsUiState.Error(e.message ?: "Failed to load recipe")
            }
        }
    }

    suspend fun getRecipeDetails(recipeId: String): Result<PlannerRecipeDetail> {
        return repository.getRecipeDetails(recipeId)
    }

    suspend fun getSwapOptions(
        profile: UserProfile,
        mealLabel: String,
        currentRecipeId: String,
        activeRecipeIds: List<String>,
        limit: Int = 30
    ): Result<List<PlannerRecipeSummary>> {
        return repository.getSwapOptions(
            profile = profile,
            mealLabel = mealLabel,
            currentRecipeId = currentRecipeId,
            activeRecipeIds = activeRecipeIds,
            limit = limit
        )
    }

    suspend fun getSwapOptions(mealLabel: String, limit: Int = 30): Result<List<PlannerRecipeSummary>> {
        return repository.getRecipeSummaries(mealLabel, limit)
    }

    suspend fun getAdminRecipes(limit: Int = 250): Result<List<RecipeDetailDto>> {
        return repository.getAdminRecipes(limit)
    }

    suspend fun getAdminPriceRules(limit: Int = 250): Result<List<AdminPriceRuleDto>> {
        return repository.getAdminPriceRules(limit)
    }

    suspend fun saveAdminRecipe(request: AdminRecipeUpsertDto): Result<RecipeDetailDto> {
        return repository.saveAdminRecipe(request)
    }

    suspend fun deleteAdminRecipe(recipeId: String): Result<Unit> {
        return repository.deleteAdminRecipe(recipeId)
    }

    suspend fun saveAdminPriceRule(request: AdminPriceRuleUpsertDto): Result<AdminPriceRuleDto> {
        return repository.saveAdminPriceRule(request)
    }

    suspend fun deleteAdminPriceRule(ruleId: String): Result<Unit> {
        return repository.deleteAdminPriceRule(ruleId)
    }

    fun swapMeal(dayIndex: Int, mealIndex: Int, newRecipeId: String, newTitle: String) {
        val currentState = _uiState.value
        if (currentState !is MealPlanUiState.Success) return
        viewModelScope.launch {
            val response = currentState.response
            val days = response.days.toMutableList()
            val day = days.getOrNull(dayIndex) ?: return@launch
            val meals = day.meals.toMutableList()
            val oldMeal = meals.getOrNull(mealIndex) ?: return@launch
            
            
            if (oldMeal.recipeId == newRecipeId) return@launch

            val oldDetail = repository.getRecipeDetails(oldMeal.recipeId).getOrNull()
            val newDetail = repository.getRecipeDetails(newRecipeId).getOrNull()
            val oldCal = oldDetail?.calories ?: 0
            val newCal = newDetail?.calories ?: 0

            meals[mealIndex] = oldMeal.copy(recipeId = newRecipeId, title = newTitle)
            val newTotal = (day.totalCalories - oldCal + newCal).coerceAtLeast(0)
            days[dayIndex] = day.copy(meals = meals, totalCalories = newTotal)

            val updated = response.copy(days = days)
            _uiState.value = MealPlanUiState.Success(updated, currentState.timestamp)
            calculateMetrics(updated)
            if (currentUserId.isNotBlank()) {
                plannerLocalRepository.savePlanJson(currentUserId, gson.toJson(updated), currentState.timestamp)
                updateActivePlanResponse(updated)
            }
            val planIdForEvent = _activePlanId.value ?: response.planId ?: response.weekLabel
            emitMlEventSafe(
                eventName = "meal_replaced",
                requestId = response.requestId,
                payload = mapOf(
                    "plan_id" to planIdForEvent,
                    "slot_index" to ((dayIndex * 3) + mealIndex),
                    "old_recipe_id" to oldMeal.recipeId,
                    "new_recipe_id" to newRecipeId
                )
            )
        }
    }

    suspend fun getGrocerySourcesForRecipe(recipeId: String): Result<List<GroceryItemSource>> {
        val detail = repository.getRecipeDetails(recipeId).getOrNull()
            ?: return Result.failure(IllegalStateException("Recipe details unavailable"))
        return Result.success(detail.ingredients.map { GroceryItemSource(it.name, it.quantity) })
    }

    fun extractGrocerySourcesForPlan(onComplete: (Map<String, List<GroceryItemSource>>) -> Unit) {
        val currentState = _uiState.value
        if (currentState !is MealPlanUiState.Success) return
        viewModelScope.launch {
            val plan = currentState.response
            val recipeIds = plan.days.flatMap { it.meals }.mapNotNull { it.recipeId }.distinct()
            val deferredDetails = recipeIds.map { id -> async { repository.getRecipeDetails(id).getOrNull() } }
            val allDetails = deferredDetails.awaitAll().filterNotNull()
            val detailsById = allDetails.associateBy { it.id }
            val sources = mutableMapOf<String, List<GroceryItemSource>>()
            val planId = _activePlanId.value ?: plan.weekLabel
            plan.days.forEachIndexed { dayIndex, day ->
                day.meals.forEachIndexed { mealIndex, meal ->
                    val detail = detailsById[meal.recipeId] ?: return@forEachIndexed
                    val mealId = buildMealInstanceId(planId, dayIndex, mealIndex, meal.mealLabel)
                    sources[mealId] = detail.ingredients.map { GroceryItemSource(it.name, it.quantity) }
                }
            }
            onComplete(sources)
        }
    }

    fun extractAllGroceryItems(onComplete: (List<DummyData.GroceryItem>) -> Unit) {
        val currentState = _uiState.value
        if (currentState is MealPlanUiState.Success) {
            viewModelScope.launch {
                val recipeIds = currentState.response.days.flatMap { it.meals }.mapNotNull { it.recipeId }.distinct()
                val deferredDetails = recipeIds.map { id -> async { repository.getRecipeDetails(id).getOrNull() } }
                val allDetails = deferredDetails.awaitAll().filterNotNull()

                val allPlannedItems = allDetails.flatMap { detail ->
                    detail.ingredients.map { ing ->
                        DummyData.GroceryItem(ing.name, ing.quantity, 0, detail.mealType ?: "Required")
                    }
                }
                val consolidated = allPlannedItems
                    .groupBy { normalizeGroceryItemName(it.name) }
                    .mapNotNull { (normalizedName, group) ->
                        if (normalizedName.isBlank()) return@mapNotNull null
                        val displayName = group.firstNotNullOfOrNull { item ->
                            item.name.trim().takeIf { it.isNotBlank() }
                        } ?: return@mapNotNull null
                        DummyData.GroceryItem(
                            displayName,
                            group.joinToString(", ") { it.quantity.trim() },
                            0,
                            "Consolidated"
                        )
                    }
                onComplete(consolidated)
            }
        }
    }

    fun buildMealInstanceId(weekLabel: String, dayIndex: Int, mealIndex: Int, mealLabel: String): String {
        return "${weekLabel}_d${dayIndex}_m${mealIndex}_$mealLabel"
    }

    fun selectPlan(planId: String) {
        val plan = _planHistory.value.firstOrNull { it.id == planId } ?: return
        _activePlanId.value = plan.id
        _activeWeekStart.value = plan.weekStart
        _activeWeekEnd.value = plan.weekEnd
        _planExpired.value = isExpired(plan)
        _uiState.value = MealPlanUiState.Success(plan.response, plan.generatedAt)
        calculateMetrics(plan.response)
        emitMlEventSafe(
            eventName = "plan_viewed",
            requestId = plan.response.requestId,
            payload = mapOf("plan_id" to plan.id, "source" to "history_select")
        )
        if (currentUserId.isNotBlank()) {
            viewModelScope.launch {
                plannerLocalRepository.saveActivePlanId(currentUserId, plan.id)
            }
        }
    }

    fun clearPlanHistory() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            plannerLocalRepository.clearPlanHistory(currentUserId)
            _planHistory.value = emptyList()
            _activePlanId.value = null
            _activeWeekStart.value = null
            _activeWeekEnd.value = null
            _planExpired.value = false
            _lastReviewedWeek.value = null
            _uiState.value = MealPlanUiState.Idle
            _planMetrics.value = PlanMetrics()
        }
    }

    fun markWeekReviewed(weekStart: String?) {
        val key = weekStart?.trim().orEmpty()
        if (key.isBlank() || currentUserId.isBlank()) return
        _lastReviewedWeek.value = key
        viewModelScope.launch {
            plannerLocalRepository.saveLastReviewedWeek(currentUserId, key)
        }
    }

    private fun isExpired(plan: PlanInstance): Boolean {
        return try {
            val end = LocalDate.parse(plan.weekEnd, DateTimeFormatter.ISO_LOCAL_DATE)
            end.isBefore(LocalDate.now())
        } catch (_: Exception) {
            false
        }
    }

    private fun weekStartDate(timestamp: Long): LocalDate {
        return java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
    }

    private fun weekLabelFor(start: LocalDate): String {
        val end = start.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val fmtYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
        return if (start.year == end.year) {
            "${start.format(fmt)} – ${end.format(fmtYear)}"
        } else {
            "${start.format(fmtYear)} – ${end.format(fmtYear)}"
        }
    }

    private fun orderedDayLabels(start: LocalDate): List<String> =
        (0..6).map { offset ->
            start.plusDays(offset.toLong()).format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
        }

    private fun rotatedDayOrder(startLabel: String): List<String> {
        val canonical = canonicalDayLabel(startLabel) ?: return dayOrder
        val startIndex = dayOrder.indexOf(canonical)
        if (startIndex < 0) return dayOrder
        return dayOrder.drop(startIndex) + dayOrder.take(startIndex)
    }

    private fun parsePlanDate(raw: String?): LocalDate? =
        raw?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }

    private fun containsDate(plan: PlanInstance, date: LocalDate): Boolean {
        val start = parsePlanDate(plan.weekStart) ?: return false
        val end = parsePlanDate(plan.weekEnd) ?: start.plusDays(6)
        return !date.isBefore(start) && !date.isAfter(end)
    }

    private suspend fun loadPlanHistory(userId: String): List<PlanInstance> {
        val json = plannerLocalRepository.getPlanHistoryJson(userId).first()
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<PlanInstance>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePlanHistory(history: List<PlanInstance>) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            plannerLocalRepository.savePlanHistoryJson(currentUserId, gson.toJson(history))
        }
    }

    private fun upsertPlanInstance(instance: PlanInstance) {
        val updated = _planHistory.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == instance.id }
        if (idx >= 0) updated[idx] = instance else updated.add(instance)
        _planHistory.value = updated.sortedBy { it.weekStart }
        savePlanHistory(_planHistory.value)
    }

    private fun updateActivePlanResponse(updated: PlannerPlanResponse) {
        val activeId = _activePlanId.value ?: return
        val activeStart = _activeWeekStart.value?.let(::parsePlanDate)
            ?: _planHistory.value.firstOrNull { it.id == activeId }?.weekStart?.let(::parsePlanDate)
        val normalized = normalizeResponse(updated, activeStart)
        val updatedHistory = _planHistory.value.map { plan ->
            if (plan.id == activeId) plan.copy(response = normalized) else plan
        }
        _planHistory.value = updatedHistory
        savePlanHistory(updatedHistory)
    }

    private fun normalizeResponse(
        response: PlannerPlanResponse,
        startDate: LocalDate? = null
    ): PlannerPlanResponse {
        val byCanonical = response.days.mapNotNull { day ->
            val canonical = canonicalDayLabel(day.dayLabel) ?: return@mapNotNull null
            canonical to day.copy(dayLabel = canonical)
        }.toMap()
        val orderedLabels = when {
            startDate != null -> orderedDayLabels(startDate)
            response.days.isNotEmpty() -> rotatedDayOrder(response.days.first().dayLabel)
            else -> dayOrder
        }
        val normalized = orderedLabels.map { label ->
            byCanonical[label] ?: PlannerDayPlan(label, emptyList(), 0)
        }
        return response.copy(days = normalized)
    }

    private fun canonicalDayLabel(label: String): String? {
        val raw = label.trim().lowercase(Locale.ENGLISH)
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("mon") || raw.startsWith("monday") || raw.startsWith("lun") || raw.startsWith("lunes") -> "Mon"
            raw.startsWith("tue") || raw.startsWith("tues") || raw.startsWith("tuesday") || raw.startsWith("mar") || raw.startsWith("martes") -> "Tue"
            raw.startsWith("wed") || raw.startsWith("weds") || raw.startsWith("wednesday") || raw.startsWith("miy") || raw.startsWith("miyerkules") -> "Wed"
            raw.startsWith("thu") || raw.startsWith("thur") || raw.startsWith("thurs") || raw.startsWith("thursday") || raw.startsWith("huw") || raw.startsWith("huwebes") -> "Thu"
            raw.startsWith("fri") || raw.startsWith("friday") || raw.startsWith("biy") || raw.startsWith("biyernes") -> "Fri"
            raw.startsWith("sat") || raw.startsWith("saturday") || raw.startsWith("sab") || raw.startsWith("sabado") -> "Sat"
            raw.startsWith("sun") || raw.startsWith("sunday") || raw.startsWith("lin") || raw.startsWith("linggo") -> "Sun"
            else -> null
        }
    }

    private fun emitMlEventSafe(
        eventName: String,
        requestId: String? = null,
        payload: Map<String, Any> = emptyMap()
    ) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            repository.emitMlEvent(eventName = eventName, requestId = requestId, payload = payload)
                .onFailure { error ->
                    Log.w("MealPlanViewModel", "ML event emit failed for $eventName: ${error.message}")
                }
        }
    }

    private fun currentPlanTelemetryId(): String? {
        val response = (_uiState.value as? MealPlanUiState.Success)?.response
        return _activePlanId.value
            ?: response?.planId?.takeIf { it.isNotBlank() }
            ?: response?.weekLabel?.takeIf { it.isNotBlank() }
    }

    private fun isMissingMlField(value: Any?): Boolean {
        return when (value) {
            null -> true
            is String -> value.isBlank()
            else -> false
        }
    }

    private fun normalizeMlItemToken(raw: Any?): String? {
        val text = raw?.toString()?.trim()?.lowercase(Locale.ENGLISH).orEmpty()
        if (text.isBlank()) return null
        return text
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .takeIf { it.isNotBlank() }
    }

    private fun normalizeGroceryItemName(raw: String): String =
        raw.trim().lowercase(Locale.ENGLISH)

    private fun normalizeTrackedMlPayload(
        eventName: String,
        payload: Map<String, Any>
    ): Map<String, Any>? {
        val normalizedName = eventName.trim()
        if (normalizedName.isBlank()) return null
        val normalized = payload.toMutableMap()
        val planId = currentPlanTelemetryId()
        when (normalizedName) {
            "meal_accepted", "meal_skipped" -> {
                planId?.let { normalized.putIfAbsent("plan_id", it) }
                if (isMissingMlField(normalized["slot_index"])) {
                    val fallbackSlotIndex = (payload["slot_index"] as? Number)?.toInt()
                        ?: (payload["meal_index"] as? Number)?.toInt()
                        ?: -1
                    normalized["slot_index"] = fallbackSlotIndex
                }
            }
            "cook_completed", "grocery_completed" -> {
                planId?.let { normalized.putIfAbsent("plan_id", it) }
            }
            "plan_generated" -> {
                planId?.let { normalized.putIfAbsent("plan_id", it) }
                normalized.putIfAbsent("status", "success")
                if (isMissingMlField(normalized["slot_count"])) {
                    val slotCount = (_uiState.value as? MealPlanUiState.Success)
                        ?.response
                        ?.days
                        ?.sumOf { day -> day.meals.size }
                        ?: 0
                    normalized["slot_count"] = slotCount
                }
            }
            "pantry_item_added", "pantry_item_removed", "pantry_item_expired" -> {
                val itemToken = normalizeMlItemToken(
                    payload["item_token"] ?: payload["item_name"] ?: payload["itemName"]
                )
                if (itemToken != null) {
                    normalized["item_token"] = itemToken
                }
            }
            "manual_override_attempted" -> {
                normalized.putIfAbsent("override_type", "meal_swap")
            }
            "why_replaced_submitted", "why_skipped_submitted" -> {
                planId?.let { normalized.putIfAbsent("plan_id", it) }
                if (isMissingMlField(normalized["slot_index"])) {
                    val fallbackSlotIndex = (payload["slot_index"] as? Number)?.toInt()
                        ?: (payload["meal_index"] as? Number)?.toInt()
                        ?: -1
                    normalized["slot_index"] = fallbackSlotIndex
                }
            }
        }
        val requiredFields = when (normalizedName) {
            "plan_generated" -> listOf("status", "plan_id", "slot_count")
            "meal_accepted", "meal_skipped" -> listOf("plan_id", "slot_index", "recipe_id")
            "cook_completed" -> listOf("plan_id", "recipe_id")
            "grocery_completed" -> listOf("plan_id")
            "pantry_item_added", "pantry_item_removed", "pantry_item_expired" -> listOf("item_token")
            "manual_override_attempted" -> listOf("override_type")
            "why_replaced_submitted", "why_skipped_submitted" -> listOf("plan_id", "slot_index")
            else -> emptyList()
        }
        val missing = requiredFields.filter { key -> isMissingMlField(normalized[key]) }
        if (missing.isNotEmpty()) {
            Log.w(
                "MealPlanViewModel",
                "Skipping ML event $normalizedName due to missing required fields: ${missing.joinToString(",")}"
            )
            return null
        }
        return normalized
    }

    fun currentRequestId(): String? {
        return (_uiState.value as? MealPlanUiState.Success)?.response?.requestId
    }

    fun trackMlEvent(
        eventName: String,
        payload: Map<String, Any> = emptyMap(),
        requestId: String? = currentRequestId()
    ) {
        val normalizedPayload = normalizeTrackedMlPayload(eventName, payload) ?: return
        emitMlEventSafe(eventName = eventName, requestId = requestId, payload = normalizedPayload)
    }

    private fun continuityPlanSnapshot(): ContinuityPlanSnapshot? {
        val currentSuccess = _uiState.value as? MealPlanUiState.Success
        if (currentSuccess != null) {
            return ContinuityPlanSnapshot(
                response = currentSuccess.response,
                timestamp = currentSuccess.timestamp,
                activePlanId = _activePlanId.value,
                weekStart = _activeWeekStart.value,
                weekEnd = _activeWeekEnd.value,
                expired = _planExpired.value
            )
        }
        val fallback = _planHistory.value.firstOrNull { it.id == _activePlanId.value }
            ?: _planHistory.value.maxByOrNull { it.generatedAt }
            ?: return null
        return ContinuityPlanSnapshot(
            response = fallback.response,
            timestamp = fallback.generatedAt,
            activePlanId = fallback.id,
            weekStart = fallback.weekStart,
            weekEnd = fallback.weekEnd,
            expired = isExpired(fallback)
        )
    }

    private fun presentNoSafePlan(
        message: String,
        guidance: List<String>,
        diagnosticsReference: String?,
        continuityPlan: ContinuityPlanSnapshot?
    ) {
        val normalizedMessage = message.ifBlank {
            "No safe plan could be generated. Adjust non-safety preferences and retry."
        }
        _generationNotice.value = MealPlanGenerationNotice.NoSafePlan(
            message = normalizedMessage,
            guidance = guidance
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(normalizedMessage, ignoreCase = true) }
                .distinct(),
            diagnosticsReference = diagnosticsReference?.trim()?.takeIf { it.isNotBlank() },
            continuityPlanAvailable = continuityPlan != null
        )
        if (continuityPlan == null) {
            _planMetrics.value = PlanMetrics()
            _uiState.value = MealPlanUiState.Error(normalizedMessage)
            return
        }
        _activePlanId.value = continuityPlan.activePlanId
        _activeWeekStart.value = continuityPlan.weekStart
        _activeWeekEnd.value = continuityPlan.weekEnd
        _planExpired.value = continuityPlan.expired
        _uiState.value = MealPlanUiState.Success(continuityPlan.response, continuityPlan.timestamp)
        calculateMetrics(continuityPlan.response)
    }

    private fun presentContinuityFallback(
        message: String,
        continuityPlan: ContinuityPlanSnapshot
    ) {
        val normalizedMessage = message.ifBlank {
            "Planner service is unavailable. Showing your latest saved plan."
        }
        _generationNotice.value = MealPlanGenerationNotice.ContinuityFallback(
            message = normalizedMessage,
            continuityPlanAvailable = true
        )
        _activePlanId.value = continuityPlan.activePlanId
        _activeWeekStart.value = continuityPlan.weekStart
        _activeWeekEnd.value = continuityPlan.weekEnd
        _planExpired.value = continuityPlan.expired
        _uiState.value = MealPlanUiState.Success(continuityPlan.response, continuityPlan.timestamp)
        calculateMetrics(continuityPlan.response)
    }

    fun seedDemoWeeks(profile: UserProfile): PlannerPlanResponse {
        val today = LocalDate.now()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek))
        val weekEnd = weekStart.plusDays(6)
        val meals = listOf(
            "Breakfast" to ("pcosina_demo_breakfast" to "Protein Oats with Saba"),
            "Lunch" to ("pcosina_demo_lunch" to "Chicken Tinola Brown Rice Bowl"),
            "Dinner" to ("pcosina_demo_dinner" to "Ginger Fish and Monggo Plate")
        )
        val days = dayOrder.mapIndexed { index, day ->
            PlannerDayPlan(
                dayLabel = day,
                meals = meals.map { (label, recipe) ->
                    PlannerPlannedMeal(
                        mealLabel = label,
                        recipeId = "${recipe.first}_${index + 1}",
                        title = recipe.second
                    )
                },
                totalCalories = 1550 + (index * 10)
            )
        }
        val response = PlannerPlanResponse(
            weekLabel = "${weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)} to ${weekEnd.format(DateTimeFormatter.ISO_LOCAL_DATE)}",
            days = days,
            status = "success",
            message = "Demo plan seeded for instrumentation.",
            planId = weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
            explanation = PlannerPlanExplanation(
                targetCalories = 1600,
                avgCalories = days.map { it.totalCalories }.average().toInt(),
                avgProtein = 96,
                avgCarbs = 168,
                avgFats = 54,
                fiberMinTarget = 30
            )
        )
        val generatedAt = System.currentTimeMillis()
        val planId = response.planId ?: weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val instance = PlanInstance(
            id = planId,
            weekStart = weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE),
            weekEnd = weekEnd.format(DateTimeFormatter.ISO_LOCAL_DATE),
            generatedAt = generatedAt,
            response = response
        )
        _planHistory.value = listOf(instance)
        _activePlanId.value = planId
        _activeWeekStart.value = instance.weekStart
        _activeWeekEnd.value = instance.weekEnd
        _planExpired.value = false
        _uiState.value = MealPlanUiState.Success(response, generatedAt)
        calculateMetrics(response)
        return response
    }

    class Factory(
        private val repository: MealPlanRepository,
        private val plannerLocalRepository: PlannerLocalRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MealPlanViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MealPlanViewModel(repository, plannerLocalRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
