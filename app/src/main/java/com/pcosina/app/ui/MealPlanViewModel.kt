package com.pcosina.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pcosina.app.data.api.DayPlanDto
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.api.PlanExplanation
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.model.PlanInstance
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.data.model.DemoWeekSeed
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.util.goalTextForApi
import com.pcosina.app.ui.util.hasGoalSelection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

sealed class MealPlanUiState {
    object Idle : MealPlanUiState()
    object Loading : MealPlanUiState()
    data class Success(val response: GeneratePlanResponse, val timestamp: Long) : MealPlanUiState()
    data class Error(val message: String) : MealPlanUiState()
}

sealed class RecipeDetailsUiState {
    object Idle : RecipeDetailsUiState()
    object Loading : RecipeDetailsUiState()
    data class Success(val recipe: RecipeDetailDto) : RecipeDetailsUiState()
    data class Error(val message: String) : RecipeDetailsUiState()
}

sealed class MealPlanGenerationNotice {
    data class NoSafePlan(
        val message: String,
        val guidance: List<String>,
        val diagnosticsReference: String?,
        val continuityPlanAvailable: Boolean
    ) : MealPlanGenerationNotice()
}

data class PlanMetrics(
    val avgProtein: Int = 0,
    val avgCarbs: Int = 0,
    val avgFiber: Int = 0,
    val avgFats: Int = 0
)

class MealPlanViewModel(
    private val repository: MealPlanRepository,
    private val userPrefsRepository: UserPreferencesRepository
) : ViewModel() {

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

    private data class ContinuityPlanSnapshot(
        val response: GeneratePlanResponse,
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
                val savedJson = userPrefsRepository.getSavedPlanJson(userId).first()
                val savedTimestamp = userPrefsRepository.getSavedPlanTimestamp(userId).first()
                if (!savedJson.isNullOrBlank()) {
                    try {
                        val response = gson.fromJson(savedJson, GeneratePlanResponse::class.java)
                        val start = weekStartDate(savedTimestamp)
                        val end = start.plusDays(6)
                        val id = start.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val instance = PlanInstance(
                            id = id,
                            weekStart = id,
                            weekEnd = end.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            generatedAt = if (savedTimestamp > 0) savedTimestamp else System.currentTimeMillis(),
                            response = normalizeResponse(response.copy(weekLabel = weekLabelFor(start)))
                        )
                        history = listOf(instance)
                        savePlanHistory(history)
                        userPrefsRepository.saveActivePlanId(userId, id)
                    } catch (_: Exception) {
                        history = emptyList()
                    }
                }
            }
            val normalizedHistory = history.map { it.copy(response = normalizeResponse(it.response)) }
            if (normalizedHistory != history) {
                savePlanHistory(normalizedHistory)
            }
            _planHistory.value = normalizedHistory
            val activeId = userPrefsRepository.getActivePlanId(userId).first()
            val reviewed = userPrefsRepository.getLastReviewedWeek(userId).first()
            _lastReviewedWeek.value = reviewed
            val currentWeekId = weekStartDate(System.currentTimeMillis()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val active = when {
                normalizedHistory.any { it.id == currentWeekId } -> normalizedHistory.first { it.id == currentWeekId }
                !activeId.isNullOrBlank() -> normalizedHistory.firstOrNull { it.id == activeId }
                else -> normalizedHistory.maxByOrNull { it.generatedAt }
            }
            val expired = active?.let { isExpired(it) } ?: false
            _planExpired.value = expired && (normalizedHistory.none { it.id == currentWeekId })
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

    private fun calculateMetrics(response: GeneratePlanResponse) {
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
    }

    fun generateMealPlan(profile: UserProfile) {
        viewModelScope.launch {
            val continuityPlan = continuityPlanSnapshot()
            _generationNotice.value = null
            _uiState.value = MealPlanUiState.Loading
            repository.warmup()
            val effectiveProfile = resolveProfile(profile)
            val tunedProfile = applyFeedbackTuning(effectiveProfile)
            val apiProfile = tunedProfile.copy(goal = goalTextForApi(tunedProfile.goal))
            val result = repository.generatePlan(apiProfile)
            result.onSuccess { response ->
                if (response.status.equals("no-safe-plan", ignoreCase = true) || response.days.isEmpty()) {
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
                val now = System.currentTimeMillis()
                val start = weekStartDate(now)
                val end = start.plusDays(6)
                val id = start.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val withLabel = normalizeResponse(response.copy(weekLabel = weekLabelFor(start)))
                val instance = PlanInstance(
                    id = id,
                    weekStart = id,
                    weekEnd = end.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    generatedAt = now,
                    response = withLabel
                )
                _generationNotice.value = null
                _uiState.value = MealPlanUiState.Success(withLabel, now)
                calculateMetrics(withLabel)
                if (currentUserId.isNotBlank()) {
                    upsertPlanInstance(instance)
                    userPrefsRepository.savePlanJson(currentUserId, gson.toJson(withLabel), now)
                    userPrefsRepository.saveActivePlanId(currentUserId, id)
                    _activePlanId.value = id
                    _activeWeekStart.value = instance.weekStart
                    _activeWeekEnd.value = instance.weekEnd
                    _planExpired.value = false
                }
                val planTelemetryId = withLabel.planId ?: id
                val slotCount = withLabel.days.sumOf { it.meals.size }
                emitMlEventSafe(
                    eventName = "plan_generated",
                    requestId = withLabel.requestId,
                    payload = mapOf(
                        "status" to "success",
                        "plan_id" to planTelemetryId,
                        "slot_count" to slotCount
                    )
                )
                emitMlEventSafe(
                    eventName = "plan_viewed",
                    requestId = withLabel.requestId,
                    payload = mapOf("plan_id" to planTelemetryId, "source" to "fresh_generation")
                )
            }.onFailure { error ->
                val raw = error.message ?: "Failed to connect to MILP engine"
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

    private suspend fun applyFeedbackTuning(profile: UserProfile): UserProfile {
        if (currentUserId.isBlank()) return profile
        val tags = userPrefsRepository.getPlanFeedbackTags(currentUserId).first()
        if (tags.isEmpty()) return profile
        var tuned = profile
        if (tags.any { it.equals("Too repetitive", true) }) {
            tuned = tuned.copy(varietyPreference = "High")
        }
        if (tags.any { it.equals("Too expensive", true) }) {
            val lowered = (tuned.weeklyBudgetPhp * 0.9f).toInt()
            tuned = tuned.copy(weeklyBudgetPhp = lowered.coerceAtLeast(0))
        }
        if (tags.any { it.equals("Too hard to cook", true) }) {
            tuned = tuned.copy(maxCookingTimeMinutes = (tuned.maxCookingTimeMinutes - 10).coerceAtLeast(10))
        }
        return tuned
    }

    private suspend fun resolveProfile(profile: UserProfile): UserProfile {
        if (isProfileValid(profile)) return profile
        return try {
            if (currentUserId.isBlank()) profile
            else userPrefsRepository.getUserProfile(currentUserId).first()
        } catch (_: Exception) {
            profile
        }
    }

    private fun isProfileValid(profile: UserProfile): Boolean {
        return profile.age > 0 && profile.heightCm > 0 && profile.weightKg > 0 &&
            profile.activityLevel.isNotBlank() && hasGoalSelection(profile.goal)
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

    suspend fun getRecipeDetails(recipeId: String): Result<RecipeDetailDto> {
        return repository.getRecipeDetails(recipeId)
    }

    suspend fun getSwapOptions(mealLabel: String, limit: Int = 30): Result<List<RecipeSummaryDto>> {
        return repository.getRecipeSummaries(mealLabel, limit)
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
                userPrefsRepository.savePlanJson(currentUserId, gson.toJson(updated), currentState.timestamp)
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
                userPrefsRepository.saveActivePlanId(currentUserId, plan.id)
            }
        }
    }

    fun clearPlanHistory() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            userPrefsRepository.clearPlanHistory(currentUserId)
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
            userPrefsRepository.saveLastReviewedWeek(currentUserId, key)
        }
    }

    fun seedDemoWeeks(profile: UserProfile): List<DemoWeekSeed> {
        if (currentUserId.isBlank()) return emptyList()
        val basePlan = when (val state = _uiState.value) {
            is MealPlanUiState.Success -> state.response
            else -> _planHistory.value.maxByOrNull { it.generatedAt }?.response
        } ?: buildFallbackPlan(profile)

        val baseStart = weekStartDate(System.currentTimeMillis())
        val weeks = listOf(0L, 1L, 2L).map { baseStart.minusWeeks(it) }
        val seeds = weeks.mapIndexed { index, start ->
            val response = buildVariantPlan(basePlan, start, index)
            val id = start.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val end = start.plusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val instance = PlanInstance(
                id = id,
                weekStart = id,
                weekEnd = end,
                generatedAt = System.currentTimeMillis() - (index * 7L * 24 * 60 * 60 * 1000),
                response = response
            )
            DemoWeekSeed(
                planInstance = instance,
                dailyLogs = buildDemoLogs(instance, index),
                weeklyJournal = demoJournalText(index),
                weeklySpend = demoWeeklySpend(index, response)
            )
        }

        val history = seeds.map { it.planInstance }.sortedBy { it.weekStart }
        _planHistory.value = history
        savePlanHistory(history)
        val active = history.maxByOrNull { it.generatedAt } ?: history.last()
        _activePlanId.value = active.id
        _activeWeekStart.value = active.weekStart
        _activeWeekEnd.value = active.weekEnd
        _planExpired.value = isExpired(active) && (history.none { it.id == weekStartDate(System.currentTimeMillis()).format(DateTimeFormatter.ISO_LOCAL_DATE) })
        _uiState.value = MealPlanUiState.Success(active.response, active.generatedAt)
        calculateMetrics(active.response)
        viewModelScope.launch {
            userPrefsRepository.saveActivePlanId(currentUserId, active.id)
        }
        return seeds
    }

    private fun buildFallbackPlan(profile: UserProfile): GeneratePlanResponse {
        val meals = listOf(
            Pair("Breakfast", "Demo Oatmeal Bowl"),
            Pair("Lunch", "Demo Chicken Tinola"),
            Pair("Dinner", "Demo Veggie Stir-fry")
        )
        val days = dayOrder.mapIndexed { idx, label ->
            val plannedMeals = meals.mapIndexed { mIndex, (mealLabel, title) ->
                com.pcosina.app.data.api.PlannedMealDto(
                    mealLabel = mealLabel,
                    recipeId = "demo_${idx}_$mIndex",
                    title = title
                )
            }
            val kcal = 1600 + (idx * 10)
            com.pcosina.app.data.api.DayPlanDto(
                dayLabel = label,
                meals = plannedMeals,
                totalCalories = kcal
            )
        }
        val explanation = PlanExplanation(
            targetCalories = profile.age.takeIf { it > 0 }?.let { 1800 } ?: null,
            avgCalories = days.sumOf { it.totalCalories } / days.size,
            avgProtein = 85,
            avgCarbs = 210,
            avgFats = 60,
            estimatedWeeklyCost = 1500,
            pantryMatches = profile.pantryItems.size.takeIf { it > 0 } ?: 0
        )
        return GeneratePlanResponse(
            weekLabel = weekLabelFor(weekStartDate(System.currentTimeMillis())),
            days = days,
            status = "demo",
            message = "Demo plan generated locally.",
            explanation = explanation
        )
    }

    private fun buildVariantPlan(base: GeneratePlanResponse, start: LocalDate, index: Int): GeneratePlanResponse {
        val calorieDelta = when (index) {
            1 -> 40
            2 -> -30
            else -> 0
        }
        val adjustedDays = base.days.mapIndexed { dayIndex, day ->
            val meals = day.meals.toMutableList()
            if (index > 0 && meals.size >= 2 && dayIndex % 2 == index % 2) {
                val tmp = meals.first()
                meals[0] = meals.last()
                meals[meals.lastIndex] = tmp
            }
            day.copy(
                meals = meals,
                totalCalories = (day.totalCalories + calorieDelta).coerceAtLeast(0)
            )
        }
        val baseExplain = base.explanation
        val estimated = baseExplain?.estimatedWeeklyCost?.plus(index * 60)
            ?: (1500 + index * 60)
        val explanation = baseExplain?.copy(
            avgCalories = adjustedDays.sumOf { it.totalCalories } / adjustedDays.size,
            estimatedWeeklyCost = estimated,
            pantryMatches = (baseExplain.pantryMatches ?: 0) + index
        ) ?: PlanExplanation(
            avgCalories = adjustedDays.sumOf { it.totalCalories } / adjustedDays.size,
            avgProtein = 85,
            avgCarbs = 210,
            avgFats = 60,
            estimatedWeeklyCost = estimated,
            pantryMatches = index
        )
        return base.copy(
            weekLabel = weekLabelFor(start),
            days = normalizeResponse(base.copy(days = adjustedDays)).days,
            explanation = explanation
        )
    }

    private fun buildDemoLogs(instance: PlanInstance, index: Int): List<DailyLog> {
        val start = LocalDate.parse(instance.weekStart, DateTimeFormatter.ISO_LOCAL_DATE)
        val logs = mutableListOf<DailyLog>()
        instance.response.days.forEachIndexed { dayIndex, day ->
            val date = start.plusDays(dayIndex.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val completedMeals = day.meals.filterIndexed { mealIndex, _ ->
                (dayIndex + mealIndex + index) % 2 == 0
            }.map { meal ->
                ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)
            }
            val weight = when (index) {
                0 -> 65f
                1 -> 64.5f
                else -> 64f
            } + (dayIndex * 0.05f)
            logs.add(
                DailyLog(
                    date = date,
                    completedMealIds = completedMeals,
                    weightKg = weight
                )
            )
        }
        return logs
    }

    private fun demoJournalText(index: Int): String {
        return when (index) {
            0 -> "Baseline week. Focused on getting used to the plan."
            1 -> "Week 2 felt more consistent. Cooking felt easier."
            else -> "Week 3: better routine and improved meal prep."
        }
    }

    private fun demoWeeklySpend(index: Int, response: GeneratePlanResponse): Int? {
        val base = response.explanation?.estimatedWeeklyCost ?: 1500
        return (base + index * 40).coerceAtLeast(0)
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
        val date = java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        return date.with(TemporalAdjusters.previousOrSame(firstDay))
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

    private suspend fun loadPlanHistory(userId: String): List<PlanInstance> {
        val json = userPrefsRepository.getPlanHistoryJson(userId).first()
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
            userPrefsRepository.savePlanHistoryJson(currentUserId, gson.toJson(history))
        }
    }

    private fun upsertPlanInstance(instance: PlanInstance) {
        val updated = _planHistory.value.toMutableList()
        val idx = updated.indexOfFirst { it.id == instance.id }
        if (idx >= 0) updated[idx] = instance else updated.add(instance)
        _planHistory.value = updated.sortedBy { it.weekStart }
        savePlanHistory(_planHistory.value)
    }

    private fun updateActivePlanResponse(updated: GeneratePlanResponse) {
        val activeId = _activePlanId.value ?: return
        val normalized = normalizeResponse(updated)
        val updatedHistory = _planHistory.value.map { plan ->
            if (plan.id == activeId) plan.copy(response = normalized) else plan
        }
        _planHistory.value = updatedHistory
        savePlanHistory(updatedHistory)
    }

    private fun normalizeResponse(response: GeneratePlanResponse): GeneratePlanResponse {
        val byCanonical = response.days.mapNotNull { day ->
            val canonical = canonicalDayLabel(day.dayLabel) ?: return@mapNotNull null
            canonical to day.copy(dayLabel = canonical)
        }.toMap()
        val normalized = dayOrder.map { label ->
            byCanonical[label] ?: DayPlanDto(label, emptyList(), 0)
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
        }
        val requiredFields = when (normalizedName) {
            "meal_accepted", "meal_skipped" -> listOf("plan_id", "slot_index", "recipe_id")
            "cook_completed" -> listOf("plan_id", "recipe_id")
            "grocery_completed" -> listOf("plan_id")
            "pantry_item_added", "pantry_item_removed", "pantry_item_expired" -> listOf("item_token")
            "manual_override_attempted" -> listOf("override_type")
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

    class Factory(
        private val repository: MealPlanRepository,
        private val userPrefsRepository: UserPreferencesRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MealPlanViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MealPlanViewModel(repository, userPrefsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
