package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.data.model.NotificationLogEntry
import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.data.repository.NotificationLocalRepository
import com.pcosina.app.data.repository.UserProfileLocalRepository
import com.pcosina.app.domain.CalorieTargetBreakdown
import com.pcosina.app.domain.HealthMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class UserViewModel(
    private val userProfileLocalRepository: UserProfileLocalRepository,
    private val notificationLocalRepository: NotificationLocalRepository
) : ViewModel() {
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    private val _adminMode = MutableStateFlow(false)
    val adminMode: StateFlow<Boolean> = _adminMode.asStateFlow()
    private val _pantryEntries = MutableStateFlow<List<PantryEntry>>(emptyList())
    val pantryEntries: StateFlow<List<PantryEntry>> = _pantryEntries.asStateFlow()
    private val _remindersEnabled = MutableStateFlow(false)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()
    private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()
    private val _notificationLogs = MutableStateFlow<List<NotificationLogEntry>>(emptyList())
    val notificationLogs: StateFlow<List<NotificationLogEntry>> = _notificationLogs.asStateFlow()

    private var profileJob: Job? = null
    private var pantryJob: Job? = null
    private var remindersJob: Job? = null
    private var notificationPrefsJob: Job? = null
    private var notificationLogsJob: Job? = null
    private var currentUserId: String = ""
    private var pendingProfile: UserProfile? = null

    fun loadProfileForUser(userId: String) {
        if (currentUserId == userId) {
            flushPendingProfile()
            return
        }
        currentUserId = userId
        _userProfile.value = pendingProfile ?: UserProfile()
        _isProfileLoading.value = true
        profileJob?.cancel()
        pantryJob?.cancel()
        remindersJob?.cancel()
        notificationPrefsJob?.cancel()
        notificationLogsJob?.cancel()
        profileJob = viewModelScope.launch {
            userProfileLocalRepository.getUserProfile(userId).collectLatest { profile ->
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
        pantryJob = viewModelScope.launch {
            userProfileLocalRepository.getPantryEntries(userId).collectLatest { entries ->
                _pantryEntries.value = entries
            }
        }
        remindersJob = viewModelScope.launch {
            notificationLocalRepository.getRemindersEnabled(userId).collectLatest { enabled ->
                _remindersEnabled.value = enabled
            }
        }
        notificationPrefsJob = viewModelScope.launch {
            notificationLocalRepository.getNotificationPreferences(userId).collectLatest { prefs ->
                _notificationPreferences.value = prefs
                _remindersEnabled.value = prefs.masterEnabled
            }
        }
        notificationLogsJob = viewModelScope.launch {
            notificationLocalRepository.getNotificationLogs(userId).collectLatest { logs ->
                _notificationLogs.value = logs
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
        pantryJob?.cancel()
        remindersJob?.cancel()
        notificationPrefsJob?.cancel()
        notificationLogsJob?.cancel()
        _userProfile.value = UserProfile()
        _isProfileLoading.value = false
        _pantryEntries.value = emptyList()
        _remindersEnabled.value = false
        _notificationPreferences.value = NotificationPreferences()
        _notificationLogs.value = emptyList()
        _adminMode.value = false
        pendingProfile = null
    }

    fun setAdminMode(enabled: Boolean) {
        _adminMode.value = enabled
    }

    private fun saveProfile() {
        if (currentUserId.isBlank()) {
            pendingProfile = _userProfile.value
            return
        }
        viewModelScope.launch {
            userProfileLocalRepository.updateProfile(currentUserId, _userProfile.value)
        }
    }

    private fun flushPendingProfile() {
        val pending = pendingProfile ?: return
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            userProfileLocalRepository.updateProfile(currentUserId, pending)
        }
    }

    fun updatePersonalDetails(age: Int, weight: Int, height: Int, activity: String) {
        _userProfile.update { 
            it.copy(age = age, weightKg = weight, heightCm = height, activityLevel = activity)
        }
        saveProfile()
    }

    fun updateUnitPreferences(heightUnit: String, weightUnit: String) {
        _userProfile.update { it.copy(heightUnit = heightUnit, weightUnit = weightUnit) }
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

    fun updateAllergies(allergies: List<String>) {
        _userProfile.update { it.copy(allergies = allergies) }
        saveProfile()
    }

    fun updateCookingPreferences(maxMinutes: Int, variety: String) {
        _userProfile.update { it.copy(maxCookingTimeMinutes = maxMinutes, varietyPreference = variety) }
        saveProfile()
    }

    fun updatePlanningPriority(priority: String) {
        _userProfile.update { it.copy(planningPriority = priority) }
        saveProfile()
    }

    fun updateBudget(budget: Int) {
        _userProfile.update { it.copy(weeklyBudgetPhp = budget) }
        saveProfile()
    }

    fun updateHouseholdSize(size: Int) {
        _userProfile.update { it.copy(householdSize = size.coerceIn(1, 6)) }
        saveProfile()
    }

    fun updatePantryItems(items: List<String>) {
        val existing = _pantryEntries.value.associateBy { normalizePantryNameKey(it.name) }
        val entries = items.mapNotNull { raw ->
            val name = raw.trim()
            val normalizedKey = normalizePantryNameKey(name)
            if (normalizedKey.isBlank()) null
            else (existing[normalizedKey]?.copy(name = name) ?: PantryEntry(name = name))
        }.distinctBy { normalizePantryNameKey(it.name) }
        val names = entries.map { it.name.trim() }
        _pantryEntries.value = entries
        _userProfile.update { it.copy(pantryItems = names) }
        saveProfile()
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            userProfileLocalRepository.savePantryEntries(currentUserId, entries)
        }
    }

    fun updatePantryEntries(entries: List<PantryEntry>) {
        val normalizedEntries = entries.mapNotNull { entry ->
            val trimmedName = entry.name.trim()
            val normalizedKey = normalizePantryNameKey(trimmedName)
            if (normalizedKey.isBlank()) {
                null
            } else {
                entry.copy(
                    name = trimmedName,
                    quantity = entry.quantity?.trim()?.takeIf { it.isNotBlank() },
                    expiryDate = entry.expiryDate?.trim()?.takeIf { it.isNotBlank() }
                )
            }
        }.distinctBy { normalizePantryNameKey(it.name) }
        _pantryEntries.value = normalizedEntries
        val names = normalizedEntries.map { it.name.trim() }
        _userProfile.update { it.copy(pantryItems = names) }
        saveProfile()
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            userProfileLocalRepository.savePantryEntries(currentUserId, normalizedEntries)
        }
    }

    private fun normalizePantryNameKey(raw: String): String =
        raw.trim()
            .lowercase(Locale.ENGLISH)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun setRemindersEnabled(enabled: Boolean) {
        updateNotificationPreferences { prefs ->
            prefs.copy(masterEnabled = enabled)
        }
    }

    fun updateNotificationPreferences(
        transform: (NotificationPreferences) -> NotificationPreferences
    ): NotificationPreferences {
        val updated = transform(_notificationPreferences.value)
        _notificationPreferences.value = updated
        _remindersEnabled.value = updated.masterEnabled
        if (currentUserId.isNotBlank()) {
            viewModelScope.launch {
                notificationLocalRepository.saveNotificationPreferences(currentUserId, updated)
            }
        }
        return updated
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

    val activeUserId: String
        get() = currentUserId

    class Factory(
        private val userProfileLocalRepository: UserProfileLocalRepository,
        private val notificationLocalRepository: NotificationLocalRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return UserViewModel(userProfileLocalRepository, notificationLocalRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
