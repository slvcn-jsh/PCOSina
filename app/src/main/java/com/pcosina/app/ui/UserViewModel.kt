package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.domain.CalorieTargetBreakdown
import com.pcosina.app.domain.HealthMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    private val _adminMode = MutableStateFlow(false)
    val adminMode: StateFlow<Boolean> = _adminMode.asStateFlow()

    private var profileJob: Job? = null
    private var currentUserId: String = ""
    private var pendingProfile: UserProfile? = null

    init {
        viewModelScope.launch {
            repository.getAdminMode().collectLatest { enabled ->
                _adminMode.value = enabled
            }
        }
    }

    fun loadProfileForUser(userId: String) {
        if (currentUserId == userId) {
            flushPendingProfile()
            return
        }
        currentUserId = userId
        _userProfile.value = pendingProfile ?: UserProfile()
        _isProfileLoading.value = true
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            repository.getUserProfile(userId).collectLatest { profile ->
                val pending = pendingProfile
                if (pending != null) {
                    if (profile == pending) {
                        pendingProfile = null
                        _userProfile.value = profile
                    } else {
                        _userProfile.value = pending
                    }
                } else {
                    _userProfile.value = profile
                }
                _isProfileLoading.value = false
            }
        }
        flushPendingProfile()
    }

    /**
     * RESTORED: This was causing the "Unresolved Reference" crash.
     */
    fun updateProfileName(name: String) {
        _userProfile.update { it.copy(displayName = name) }
        saveProfile()
    }

    fun reset() {
        currentUserId = ""
        profileJob?.cancel()
        _userProfile.value = UserProfile()
        _isProfileLoading.value = false
        pendingProfile = null
    }

    fun toggleAdminMode() {
        viewModelScope.launch {
            repository.setAdminMode(!_adminMode.value)
        }
    }

    private fun saveProfile() {
        if (currentUserId.isBlank()) {
            pendingProfile = _userProfile.value
            return
        }
        viewModelScope.launch {
            repository.updateProfile(currentUserId, _userProfile.value)
        }
    }

    private fun flushPendingProfile() {
        val pending = pendingProfile ?: return
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            repository.updateProfile(currentUserId, pending)
        }
    }

    fun updatePersonalDetails(age: Int, weight: Int, height: Int, activity: String) {
        _userProfile.update { 
            it.copy(age = age, weightKg = weight, heightCm = height, activityLevel = activity)
        }
        saveProfile()
    }

    fun updatePcosDetails(insulin: String, symptoms: List<String>, comorbidities: List<String>) {
        _userProfile.update {
            it.copy(insulinResistanceLevel = insulin, symptoms = symptoms, comorbidities = comorbidities)
        }
        saveProfile()
    }

    fun updateDietaryRestrictions(restrictions: List<String>) {
        _userProfile.update { it.copy(dietaryRestrictions = restrictions) }
        saveProfile()
    }

    fun updateBudget(budget: Int) {
        _userProfile.update { it.copy(weeklyBudgetPhp = budget) }
        saveProfile()
    }

    fun updateGoal(goal: String) {
        _userProfile.update { it.copy(goal = goal) }
        saveProfile()
    }

    fun setProfileCompleted(completed: Boolean) {
        _userProfile.update { it.copy(isProfileCompleted = completed) }
        saveProfile()
    }

    val dailyCalorieTarget: Int
        get() {
            val profile = _userProfile.value
            return HealthMetrics.targetCaloriesPerDay(
                weightKg = profile.weightKg,
                heightCm = profile.heightCm,
                age = profile.age,
                activityLevel = profile.activityLevel,
                goal = profile.goal
            ).target
        }

    val calorieTargetBreakdown: CalorieTargetBreakdown
        get() {
            val profile = _userProfile.value
            return HealthMetrics.targetCaloriesPerDay(
                weightKg = profile.weightKg,
                heightCm = profile.heightCm,
                age = profile.age,
                activityLevel = profile.activityLevel,
                goal = profile.goal
            )
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
