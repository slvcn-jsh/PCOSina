package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pcosina.app.data.api.DayPlanDto
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.model.PlanInstance
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
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

    private var currentUserId: String = ""
    private val gson = Gson()

    fun loadSavedPlan(userId: String) {
        currentUserId = userId
        viewModelScope.launch {
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
        _planMetrics.value = PlanMetrics()
        _planHistory.value = emptyList()
        _activePlanId.value = null
        _activeWeekStart.value = null
        _activeWeekEnd.value = null
        _planExpired.value = false
    }

    fun generateMealPlan(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = MealPlanUiState.Loading
            repository.warmup()
            val effectiveProfile = resolveProfile(profile)
            val result = repository.generatePlan(effectiveProfile)
            result.onSuccess { response ->
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
            }.onFailure { error ->
                val raw = error.message ?: "Failed to connect to MILP engine"
                val message = if (raw.contains("Profile invalid", ignoreCase = true)) {
                    "Profile incomplete. Please open Profile or Settings and save your age, height, and weight."
                } else {
                    raw
                }
                _uiState.value = MealPlanUiState.Error(message)
            }
        }
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
            profile.activityLevel.isNotBlank() && profile.goal.isNotBlank()
    }

    fun showError(message: String) {
        _uiState.value = MealPlanUiState.Error(message)
    }

    fun loadRecipeDetails(recipeId: String) {
        viewModelScope.launch {
            _recipeState.value = RecipeDetailsUiState.Loading
            val result = repository.getRecipeDetails(recipeId)
            result.onSuccess { dto ->
                _recipeState.value = RecipeDetailsUiState.Success(dto)
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
                val consolidated = allPlannedItems.groupBy { it.name }.map { (name, group) ->
                    DummyData.GroceryItem(name, group.joinToString(", ") { it.quantity }, 0, "Consolidated")
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
            _uiState.value = MealPlanUiState.Idle
            _planMetrics.value = PlanMetrics()
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
        val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
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
