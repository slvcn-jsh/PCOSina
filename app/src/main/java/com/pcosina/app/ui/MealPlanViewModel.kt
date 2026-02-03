package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.model.DummyData
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
    val avgFiber: Int = 0
)

class MealPlanViewModel(
    private val repository: MealPlanRepository,
    private val userPrefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<MealPlanUiState>(MealPlanUiState.Idle)
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    // Fixed: Expose the missing recipe state
    private val _recipeState = MutableStateFlow<RecipeDetailsUiState>(RecipeDetailsUiState.Idle)
    val recipeState: StateFlow<RecipeDetailsUiState> = _recipeState.asStateFlow()

    private val _planMetrics = MutableStateFlow(PlanMetrics())
    val planMetrics: StateFlow<PlanMetrics> = _planMetrics.asStateFlow()

    private var currentUserEmail: String = ""
    private val gson = Gson()

    fun loadSavedPlan(email: String) {
        currentUserEmail = email
        viewModelScope.launch {
            _uiState.value = MealPlanUiState.Idle 
            val savedJson = userPrefsRepository.getSavedPlanJson(email).first()
            val savedTimestamp = userPrefsRepository.getSavedPlanTimestamp(email).first()
            
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
            val recipeIds = response.days.flatMap { it.meals }.map { it.recipeId }.distinct()
            val deferredDetails = recipeIds.map { id -> async { repository.getRecipeDetails(id).getOrNull() } }
            val allDetails = deferredDetails.awaitAll().filterNotNull()
            
            if (allDetails.isNotEmpty()) {
                val totalP = allDetails.sumOf { it.proteinGrams ?: 0 }
                val totalC = allDetails.sumOf { it.carbsGrams ?: 0 }
                val totalF = allDetails.sumOf { it.fiberGrams ?: 0 }
                _planMetrics.value = PlanMetrics(
                    avgProtein = (totalP / 7),
                    avgCarbs = (totalC / 7),
                    avgFiber = (totalF / 7)
                )
            }
        }
    }

    fun reset() {
        currentUserEmail = ""
        _uiState.value = MealPlanUiState.Idle
        _recipeState.value = RecipeDetailsUiState.Idle
        _planMetrics.value = PlanMetrics()
    }

    fun generateMealPlan(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = MealPlanUiState.Loading
            // Warm up the backend (helps with Render cold starts).
            repository.warmup()
            val result = repository.generatePlan(profile)
            result.onSuccess { response ->
                val now = System.currentTimeMillis()
                _uiState.value = MealPlanUiState.Success(response, now)
                calculateMetrics(response)
                if (currentUserEmail.isNotBlank()) {
                    userPrefsRepository.savePlanJson(currentUserEmail, gson.toJson(response), now)
                }
            }.onFailure { error ->
                _uiState.value = MealPlanUiState.Error(error.message ?: "Failed to connect to MILP engine")
            }
        }
    }

    fun showError(message: String) {
        _uiState.value = MealPlanUiState.Error(message)
    }

    /**
     * Managed fetch logic for recipe details.
     */
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
