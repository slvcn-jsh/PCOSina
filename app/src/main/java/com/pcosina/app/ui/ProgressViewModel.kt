package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.DemoWeekSeed
import com.pcosina.app.data.model.FeedbackEntry
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class ProgressViewModel(
    private val userPrefsRepository: UserPreferencesRepository,
    private val reflectionStore: ReflectionStore,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val gson = Gson()
    private val logType = object : TypeToken<List<DailyLog>>() {}.type
    private val feedbackType = object : TypeToken<List<FeedbackEntry>>() {}.type
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    private var currentUserId: String = ""

    private val _dailyLogs = MutableStateFlow<Map<String, DailyLog>>(emptyMap())
    val dailyLogs: StateFlow<Map<String, DailyLog>> = _dailyLogs.asStateFlow()

    private val _weeklyJournal = MutableStateFlow("")
    val weeklyJournal: StateFlow<String> = _weeklyJournal.asStateFlow()
    private val _weeklySpend = MutableStateFlow<Int?>(null)
    val weeklySpend: StateFlow<Int?> = _weeklySpend.asStateFlow()

    private val _feedbackQueue = MutableStateFlow<List<FeedbackEntry>>(emptyList())
    val feedbackQueue: StateFlow<List<FeedbackEntry>> = _feedbackQueue.asStateFlow()
    private val _planFeedbackTags = MutableStateFlow<List<String>>(emptyList())
    val planFeedbackTags: StateFlow<List<String>> = _planFeedbackTags.asStateFlow()
    private var isSendingFeedback = false
    private val retryBaseDelayMs = 2000L
    private val retryMaxDelayMs = 60000L
    private val retryMaxAttempts = 5

    companion object {
        private const val MEAL_KEY_SEPARATOR = "::"

        fun buildMealKey(mealLabel: String, recipeId: String): String {
            return "$mealLabel$MEAL_KEY_SEPARATOR$recipeId"
        }

        fun extractRecipeId(mealKey: String): String {
            return if (mealKey.contains(MEAL_KEY_SEPARATOR)) {
                mealKey.substringAfter(MEAL_KEY_SEPARATOR)
            } else {
                mealKey
            }
        }
    }

    fun loadForUser(userId: String, weekStart: String, fallbackWeekStart: String? = null) {
        if (currentUserId == userId) {
            loadWeeklyJournal(weekStart, fallbackWeekStart)
            loadWeeklySpend(weekStart, fallbackWeekStart)
            return
        }
        currentUserId = userId
        viewModelScope.launch {
            var json = reflectionStore.getDailyLogsJson(userId)
            if (json.isNullOrBlank()) {
                val legacy = userPrefsRepository.getDailyLogsJson(userId).first()
                if (!legacy.isNullOrBlank()) {
                    reflectionStore.saveDailyLogsJson(userId, legacy)
                    json = legacy
                }
            }
            val list: List<DailyLog> = if (!json.isNullOrBlank()) {
                try { gson.fromJson(json, logType) } catch (_: Exception) { emptyList() }
            } else emptyList()
            _dailyLogs.value = list.associateBy { it.date }

            val fq = userPrefsRepository.getFeedbackQueueJson(userId).first()
            val entries: List<FeedbackEntry> = if (!fq.isNullOrBlank()) {
                try { gson.fromJson(fq, feedbackType) } catch (_: Exception) { emptyList() }
            } else emptyList()
            // Drop already-sent entries to avoid stale queue items piling up
            val normalized = entries.map { entry ->
                if (entry.status == "Sending") {
                    entry.copy(status = "Queued", lastError = null)
                } else {
                    entry
                }
            }.filter { it.status != "Sent" }
            _feedbackQueue.value = normalized

            loadWeeklyJournal(weekStart, fallbackWeekStart)
            loadWeeklySpend(weekStart, fallbackWeekStart)
            val tags = userPrefsRepository.getPlanFeedbackTags(userId).first()
            _planFeedbackTags.value = tags
        }
    }

    fun loadWeeklyJournal(weekStart: String, fallbackWeekStart: String? = null) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val primary = reflectionStore.getWeeklyJournal(currentUserId, weekStart)
            if (!primary.isNullOrBlank()) {
                _weeklyJournal.value = primary
                return@launch
            }
            val legacyPrimary = userPrefsRepository.getWeeklyJournal(currentUserId, weekStart).first()
            if (!legacyPrimary.isNullOrBlank()) {
                reflectionStore.saveWeeklyJournal(currentUserId, weekStart, legacyPrimary)
                _weeklyJournal.value = legacyPrimary
                return@launch
            }
            if (!fallbackWeekStart.isNullOrBlank() && fallbackWeekStart != weekStart) {
                val fallback = reflectionStore.getWeeklyJournal(currentUserId, fallbackWeekStart)
                if (!fallback.isNullOrBlank()) {
                    _weeklyJournal.value = fallback
                    // Migrate legacy week key forward for future loads
                    reflectionStore.saveWeeklyJournal(currentUserId, weekStart, fallback)
                    return@launch
                }
                val legacyFallback = userPrefsRepository.getWeeklyJournal(currentUserId, fallbackWeekStart).first()
                if (!legacyFallback.isNullOrBlank()) {
                    reflectionStore.saveWeeklyJournal(currentUserId, fallbackWeekStart, legacyFallback)
                    reflectionStore.saveWeeklyJournal(currentUserId, weekStart, legacyFallback)
                    _weeklyJournal.value = legacyFallback
                    return@launch
                }
            }
            _weeklyJournal.value = ""
        }
    }

    fun loadWeeklySpend(weekStart: String, fallbackWeekStart: String? = null) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val primary = reflectionStore.getWeeklySpend(currentUserId, weekStart)
            if (primary != null) {
                _weeklySpend.value = primary
                return@launch
            }
            if (!fallbackWeekStart.isNullOrBlank() && fallbackWeekStart != weekStart) {
                val fallback = reflectionStore.getWeeklySpend(currentUserId, fallbackWeekStart)
                if (fallback != null) {
                    reflectionStore.saveWeeklySpend(currentUserId, weekStart, fallback)
                    _weeklySpend.value = fallback
                    return@launch
                }
            }
            _weeklySpend.value = null
        }
    }

    private fun persistLogs(map: Map<String, DailyLog>) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val json = gson.toJson(map.values.toList())
            reflectionStore.saveDailyLogsJson(currentUserId, json)
        }
    }

    fun toggleMeal(date: LocalDate, recipeId: String, mealLabel: String) {
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val mealKey = buildMealKey(mealLabel, recipeId)
        val currentIds = current?.completedMealIds ?: emptyList()
        val hasKey = currentIds.contains(mealKey)
        val hasLegacy = currentIds.contains(recipeId)
        val updatedIds = if (hasKey || hasLegacy) {
            currentIds.filterNot { it == mealKey || it == recipeId }
        } else {
            currentIds + mealKey
        }
        val updated = (current ?: DailyLog(date = key)).copy(
            completedMealIds = updatedIds,
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
    }

    fun setWeight(date: LocalDate, weight: Float?, note: String? = null) {
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val updated = (current ?: DailyLog(date = key)).copy(
            weightKg = weight,
            weightNote = note?.takeIf { it.isNotBlank() },
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
    }

    fun saveReflection(
        date: LocalDate,
        energyLevel: Int?,
        cravingsLevel: Int?,
        moodLevel: Int?,
        symptomTags: List<String>,
        symptomsNote: String?
    ) {
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val updated = (current ?: DailyLog(date = key)).copy(
            energyLevel = energyLevel,
            cravingsLevel = cravingsLevel,
            moodLevel = moodLevel,
            symptomTags = symptomTags,
            symptomsNote = symptomsNote?.takeIf { it.isNotBlank() },
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
    }

    fun saveWeeklyJournal(weekStart: String, text: String) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            reflectionStore.saveWeeklyJournal(currentUserId, weekStart, text)
            _weeklyJournal.value = text
        }
    }

    fun saveWeeklySpend(weekStart: String, value: Int?) {
        if (currentUserId.isBlank()) return
        reflectionStore.saveWeeklySpend(currentUserId, weekStart, value)
        _weeklySpend.value = value
    }

    fun reset() {
        currentUserId = ""
        _dailyLogs.value = emptyMap()
        _weeklyJournal.value = ""
        _weeklySpend.value = null
        _feedbackQueue.value = emptyList()
        _planFeedbackTags.value = emptyList()
    }

    fun togglePlanFeedbackTag(tag: String) {
        if (currentUserId.isBlank()) return
        val current = _planFeedbackTags.value.toMutableList()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _planFeedbackTags.value = current
        viewModelScope.launch {
            userPrefsRepository.savePlanFeedbackTags(currentUserId, current)
        }
    }

    fun exportReflections(): java.io.File? {
        if (currentUserId.isBlank()) return null
        return reflectionStore.exportReflections(currentUserId)
    }

    fun clearReflectionsForUser() {
        if (currentUserId.isBlank()) return
        reflectionStore.clearForUser(currentUserId)
        viewModelScope.launch {
            userPrefsRepository.clearLegacyReflections(currentUserId)
        }
        _dailyLogs.value = emptyMap()
        _weeklyJournal.value = ""
        _weeklySpend.value = null
    }

    fun seedDemoWeeks(seeds: List<DemoWeekSeed>) {
        if (currentUserId.isBlank()) return
        val updated = _dailyLogs.value.toMutableMap()
        seeds.forEach { seed ->
            seed.dailyLogs.forEach { log -> updated[log.date] = log }
            reflectionStore.saveWeeklyJournal(currentUserId, seed.planInstance.weekStart, seed.weeklyJournal)
            reflectionStore.saveWeeklySpend(currentUserId, seed.planInstance.weekStart, seed.weeklySpend)
        }
        _dailyLogs.value = updated
        persistLogs(updated)
    }

    fun queueFeedback(message: String) {
        if (currentUserId.isBlank()) return
        val entry = FeedbackEntry(id = UUID.randomUUID().toString(), message = message)
        val updated = _feedbackQueue.value + entry
        _feedbackQueue.value = updated
        persistFeedback(updated)
    }

    fun trySendQueuedFeedback(isOnline: Boolean) {
        if (!isOnline || currentUserId.isBlank()) return
        if (isSendingFeedback) return
        isSendingFeedback = true
        viewModelScope.launch {
            try {
                val entries = _feedbackQueue.value.toMutableList()
                var i = 0
                while (i < entries.size) {
                    val entry = entries[i]
                    if ((entry.status == "Queued" || entry.status == "Failed") && shouldAttemptRetry(entry)) {
                        val triedAt = System.currentTimeMillis()
                        val sendingEntry = entry.copy(
                            status = "Sending",
                            lastTriedAt = triedAt,
                            lastError = null
                        )
                        entries[i] = sendingEntry
                        _feedbackQueue.value = entries.toList()
                        persistFeedback(entries)
                        val result = feedbackRepository.sendFeedback(entry.message)
                        if (result.ok) {
                            // Remove successful entries so they don't pile up in the UI
                            entries.removeAt(i)
                            i -= 1
                        } else {
                            val nextAttempts = sendingEntry.attempts + 1
                            val errorText = result.error ?: "Unknown error"
                            val isHttpError = errorText.startsWith("HTTP")
                            val newStatus = if (nextAttempts >= retryMaxAttempts || (isHttpError && nextAttempts >= 1)) {
                                "Failed"
                            } else {
                                "Queued"
                            }
                            entries[i] = sendingEntry.copy(
                                status = newStatus,
                                attempts = nextAttempts,
                                lastError = errorText
                            )
                        }
                    }
                    i += 1
                }
                _feedbackQueue.value = entries.toList()
                persistFeedback(entries)
            } finally {
                isSendingFeedback = false
            }
        }
    }

    fun retryFeedback(entryId: String, isOnline: Boolean) {
        if (currentUserId.isBlank()) return
        val updated = _feedbackQueue.value.map { entry ->
            if (entry.id == entryId) entry.copy(status = "Queued", lastError = null, lastTriedAt = null) else entry
        }
        _feedbackQueue.value = updated
        persistFeedback(updated)
        trySendQueuedFeedback(isOnline)
    }

    fun retryAllFeedback(isOnline: Boolean) {
        if (currentUserId.isBlank()) return
        val updated = _feedbackQueue.value.map { entry ->
            if (entry.status == "Failed") {
                entry.copy(status = "Queued", lastError = null, lastTriedAt = null)
            } else {
                entry
            }
        }
        _feedbackQueue.value = updated
        persistFeedback(updated)
        trySendQueuedFeedback(isOnline)
    }

    private fun retryDelayMs(attempts: Int): Long {
        val exp = kotlin.math.min(attempts, 5)
        val delay = retryBaseDelayMs * (1L shl exp)
        return kotlin.math.min(delay, retryMaxDelayMs)
    }

    private fun shouldAttemptRetry(entry: FeedbackEntry): Boolean {
        if (entry.attempts >= retryMaxAttempts) return false
        val last = entry.lastTriedAt ?: return true
        val waitMs = retryDelayMs(entry.attempts)
        return (System.currentTimeMillis() - last) >= waitMs
    }

    private fun persistFeedback(entries: List<FeedbackEntry>) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            userPrefsRepository.saveFeedbackQueueJson(currentUserId, gson.toJson(entries))
        }
    }

    class Factory(
        private val userPrefsRepository: UserPreferencesRepository,
        private val reflectionStore: ReflectionStore,
        private val feedbackRepository: FeedbackRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ProgressViewModel(userPrefsRepository, reflectionStore, feedbackRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
