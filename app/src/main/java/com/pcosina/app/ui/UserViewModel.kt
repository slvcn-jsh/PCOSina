package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.data.model.NotificationLogEntry
import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.data.repository.NotificationLocalRepository
import com.pcosina.app.data.repository.UserPreferencesNotificationLocalRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.data.repository.UserPreferencesUserProfileLocalRepository
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
    constructor(userPreferencesRepository: UserPreferencesRepository) : this(
        UserPreferencesUserProfileLocalRepository(userPreferencesRepository),
        UserPreferencesNotificationLocalRepository(userPreferencesRepository)
    )

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _isProfileLoading = MutableStateFlow(false)
    val isProfileLoading: StateFlow<Boolean> = _isProfileLoading.asStateFlow()

    private val _pantryEntries = MutableStateFlow<List<PantryEntry>>(emptyList())
    val pantryEntries: StateFlow<List<PantryEntry>> = _pantryEntries.asStateFlow()
    private val _remindersEnabled = MutableStateFlow(false)
    val remindersEnabled: StateFlow<Boolean> = _remindersEnabled.asStateFlow()
    private val _notificationPreferences = MutableStateFlow(NotificationPreferences())
    val notificationPreferences: StateFlow<NotificationPreferences> = _notificationPreferences.asStateFlow()
    private val _notificationLogs = MutableStateFlow<List<NotificationLogEntry>>(emptyList())
    val notificationLogs: StateFlow<List<NotificationLogEntry>> = _notificationLogs.asStateFlow()
    private val _hardConstraintRevision = MutableStateFlow(0L)
    val hardConstraintRevision: StateFlow<Long> = _hardConstraintRevision.asStateFlow()
    private val _inventoryRevision = MutableStateFlow(0L)
    val inventoryRevision: StateFlow<Long> = _inventoryRevision.asStateFlow()

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

    fun updateAvatar(avatarId: String) {
        _userProfile.update { it.copy(avatarId = avatarId) }
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
        _hardConstraintRevision.value = 0L
        _inventoryRevision.value = 0L
        pendingProfile = null
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
        val before = _userProfile.value
        val after = before.copy(age = age, weightKg = weight, heightCm = height, activityLevel = activity)
        _userProfile.value = after
        if (before.age != after.age ||
            before.weightKg != after.weightKg ||
            before.heightCm != after.heightCm ||
            before.activityLevel != after.activityLevel
        ) {
            markHardConstraintsChanged()
        }
        saveProfile()
    }

    fun updateWeightSupport(
        targetWeightKg: Int?,
        targetDate: String?,
        weeklyWeightChangeGoalKg: Float?
    ) {
        val before = _userProfile.value
        val after = before.copy(
            targetWeightKg = targetWeightKg,
            targetDate = targetDate?.trim()?.takeIf { value -> value.isNotBlank() },
            weeklyWeightChangeGoalKg = weeklyWeightChangeGoalKg
        )
        _userProfile.value = after
        if (before.targetWeightKg != after.targetWeightKg ||
            before.targetDate != after.targetDate ||
            before.weeklyWeightChangeGoalKg != after.weeklyWeightChangeGoalKg
        ) {
            markHardConstraintsChanged()
        }
        saveProfile()
    }

    fun updateUnitPreferences(heightUnit: String, weightUnit: String) {
        _userProfile.update { it.copy(heightUnit = heightUnit, weightUnit = weightUnit) }
        saveProfile()
    }

    fun updatePcosDetails(symptoms: List<String>, comorbidities: List<String>) {
        val before = _userProfile.value
        val after = before.copy(symptoms = symptoms, comorbidities = comorbidities)
        _userProfile.value = after
        if (before.symptoms != after.symptoms || before.comorbidities != after.comorbidities) {
            markHardConstraintsChanged()
        }
        saveProfile()
    }

    fun updateDietaryRestrictions(restrictions: List<String>) {
        val before = _userProfile.value
        val after = before.copy(dietaryRestrictions = restrictions)
        _userProfile.value = after
        if (before.dietaryRestrictions != after.dietaryRestrictions) markHardConstraintsChanged()
        saveProfile()
    }

    fun updateAllergies(allergies: List<String>) {
        val before = _userProfile.value
        val after = before.copy(allergies = allergies)
        _userProfile.value = after
        if (before.allergies != after.allergies) markHardConstraintsChanged()
        saveProfile()
    }

    fun updateCookingPreferences(maxMinutes: Int, variety: String) {
        val before = _userProfile.value
        val after = before.copy(maxCookingTimeMinutes = maxMinutes, varietyPreference = variety)
        _userProfile.value = after
        if (before.maxCookingTimeMinutes != after.maxCookingTimeMinutes ||
            before.varietyPreference != after.varietyPreference
        ) {
            markHardConstraintsChanged()
        }
        saveProfile()
    }

    fun updatePlanningPriority(priority: String) {
        val before = _userProfile.value
        val after = before.copy(planningPriority = priority)
        _userProfile.value = after
        if (before.planningPriority != after.planningPriority) markHardConstraintsChanged()
        saveProfile()
    }

    fun updateBudget(budget: Int) {
        val before = _userProfile.value
        val after = before.copy(weeklyBudgetPhp = budget)
        _userProfile.value = after
        if (before.weeklyBudgetPhp != after.weeklyBudgetPhp) markHardConstraintsChanged()
        saveProfile()
    }

    fun updatePantryItems(items: List<String>) {
        val existing = _pantryEntries.value.associateBy { normalizePantryNameKey(it.name) }
        val entries = items.mapNotNull { raw ->
            val name = raw.trim()
            val normalizedKey = normalizePantryNameKey(name)
            if (normalizedKey.isBlank()) null
            else (existing[normalizedKey]?.copy(name = name) ?: PantryEntry(name = name))
        }.asReversed()
            .distinctBy { normalizePantryNameKey(it.name) }
            .asReversed()
        val names = entries.map { it.name.trim() }
        val beforePantryNames = _userProfile.value.pantryItems
        _pantryEntries.value = entries
        _userProfile.update { it.copy(pantryItems = names) }
        if (beforePantryNames != names) markInventoryChanged()
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
                    expiryDate = entry.expiryDate?.trim()?.takeIf { it.isNotBlank() },
                    amount = entry.amount?.takeIf { it > 0.0 && !it.isNaN() && !it.isInfinite() },
                    unit = entry.unit
                        ?.trim()
                        ?.lowercase(Locale.ENGLISH)
                        ?.takeIf { it.isNotBlank() }
                )
            }
        }.asReversed()
            .distinctBy { normalizePantryNameKey(it.name) }
            .asReversed()
        val beforeEntries = _pantryEntries.value
        _pantryEntries.value = normalizedEntries
        val names = normalizedEntries.map { it.name.trim() }
        val beforePantryNames = _userProfile.value.pantryItems
        _userProfile.update { it.copy(pantryItems = names) }
        if (beforeEntries != normalizedEntries || beforePantryNames != names) markInventoryChanged()
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

    private fun markHardConstraintsChanged() {
        _hardConstraintRevision.value = _hardConstraintRevision.value + 1
    }

    private fun markInventoryChanged() {
        _inventoryRevision.value = _inventoryRevision.value + 1
    }

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
        val before = _userProfile.value
        val after = before.copy(goal = goal)
        _userProfile.value = after
        if (before.goal != after.goal) markHardConstraintsChanged()
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
