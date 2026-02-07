package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.GroceryItemSource
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

    private var currentUserId: String = ""
    private val gson = Gson()

    fun loadSavedPlan(userId: String) {
        currentUserId = userId
        viewModelScope.launch {
            _uiState.value = MealPlanUiState.Idle 
            val savedJson = userPrefsRepository.getSavedPlanJson(userId).first()
            val savedTimestamp = userPrefsRepository.getSavedPlanTimestamp(userId).first()
            
            if (!savedJson.isNullOrBlank()) {
                try {
                    val response = gson.fromJson(savedJson, GeneratePlanResponse::class.java)
                    _uiState.value = MealPlanUiState.Success(response, savedTimestamp)
                    calculateMetrics(response)
                } catch (e: Exception) {
                    _uiState.value = MealPlanUiState.Idle
                }
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
    }

    fun generateMealPlan(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = MealPlanUiState.Loading
            repository.warmup()
            val effectiveProfile = resolveProfile(profile)
            val result = repository.generatePlan(effectiveProfile)
            result.onSuccess { response ->
                val now = System.currentTimeMillis()
                _uiState.value = MealPlanUiState.Success(response, now)
                calculateMetrics(response)
                if (currentUserId.isNotBlank()) {
                    userPrefsRepository.savePlanJson(currentUserId, gson.toJson(response), now)
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
            }
        }
    }

    suspend fun getGrocerySourcesForRecipe(recipeId: String): List<GroceryItemSource> {
        val detail = repository.getRecipeDetails(recipeId).getOrNull() ?: return emptyList()
        return detail.ingredients.map { GroceryItemSource(it.name, it.quantity) }
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
            plan.days.forEachIndexed { dayIndex, day ->
                day.meals.forEachIndexed { mealIndex, meal ->
                    val detail = detailsById[meal.recipeId] ?: return@forEachIndexed
                    val mealId = buildMealInstanceId(plan.weekLabel, dayIndex, mealIndex, meal.mealLabel)
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
