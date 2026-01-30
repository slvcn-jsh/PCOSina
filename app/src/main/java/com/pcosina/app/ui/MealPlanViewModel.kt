package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.data.repository.MealPlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MealPlanUiState {
    object Idle : MealPlanUiState()
    object Loading : MealPlanUiState()
    data class Success(val response: GeneratePlanResponse) : MealPlanUiState()
    data class Error(val message: String) : MealPlanUiState()
}

class MealPlanViewModel(private val repository: MealPlanRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<MealPlanUiState>(MealPlanUiState.Idle)
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    fun generateMealPlan(profile: UserProfile) {
        viewModelScope.launch {
            _uiState.value = MealPlanUiState.Loading
            val result = repository.generatePlan(profile)
            result.onSuccess { response ->
                _uiState.value = MealPlanUiState.Success(response)
            }.onFailure { error ->
                _uiState.value = MealPlanUiState.Error(error.message ?: "Failed to connect to MILP engine")
            }
        }
    }

    class Factory(private val repository: MealPlanRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MealPlanViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MealPlanViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
