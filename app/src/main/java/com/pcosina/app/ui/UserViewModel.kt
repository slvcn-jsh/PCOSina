package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    init {
        viewModelScope.launch {
            repository.userProfileFlow.collectLatest { profile ->
                _userProfile.value = profile
            }
        }
    }

    private fun saveProfile() {
        viewModelScope.launch {
            repository.updateProfile(_userProfile.value)
        }
    }

    fun updatePersonalDetails(age: Int, weight: Int, height: Int, activity: String) {
        _userProfile.update { 
            it.copy(
                age = age,
                weightKg = weight,
                heightCm = height,
                activityLevel = activity
            )
        }
        saveProfile()
    }

    fun updatePcosDetails(insulin: String, symptoms: List<String>, comorbidities: List<String>) {
        _userProfile.update {
            it.copy(
                insulinResistanceLevel = insulin,
                symptoms = symptoms,
                comorbidities = comorbidities
            )
        }
        saveProfile()
    }

    fun updateDietaryRestrictions(restrictions: List<String>) {
        _userProfile.update {
            it.copy(dietaryRestrictions = restrictions)
        }
        saveProfile()
    }

    fun updateBudget(budget: Int) {
        _userProfile.update {
            it.copy(weeklyBudgetPhp = budget)
        }
        saveProfile()
    }

    fun updateGoal(goal: String) {
        _userProfile.update {
            it.copy(goal = goal)
        }
        saveProfile()
    }

    val dailyCalorieTarget: Int
        get() {
            val profile = _userProfile.value
            val bmr = (10 * profile.weightKg) + (6.25 * profile.heightCm) - (5 * profile.age) - 161
            val activityMultiplier = when (profile.activityLevel) {
                "Sedentary" -> 1.2
                "Lightly Active" -> 1.375
                "Moderately Active" -> 1.55
                "Very Active" -> 1.725
                else -> 1.375
            }
            val maintenance = (bmr * activityMultiplier).toInt()
            
            return when {
                profile.goal.contains("Weight Loss", true) -> maintenance - 500
                else -> maintenance
            }
        }

    class Factory(private val repository: UserPreferencesRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UserViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
