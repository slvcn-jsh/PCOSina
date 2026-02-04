package com.pcosina.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.FeedbackEntry
import com.pcosina.app.data.repository.FeedbackRepository
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

    private val _feedbackQueue = MutableStateFlow<List<FeedbackEntry>>(emptyList())
    val feedbackQueue: StateFlow<List<FeedbackEntry>> = _feedbackQueue.asStateFlow()

    fun loadForUser(userId: String, weekStart: String) {
        if (currentUserId == userId) {
            loadWeeklyJournal(weekStart)
            return
        }
        currentUserId = userId
        viewModelScope.launch {
            val json = userPrefsRepository.getDailyLogsJson(userId).first()
            val list: List<DailyLog> = if (!json.isNullOrBlank()) {
                try { gson.fromJson(json, logType) } catch (_: Exception) { emptyList() }
            } else emptyList()
            _dailyLogs.value = list.associateBy { it.date }

            val fq = userPrefsRepository.getFeedbackQueueJson(userId).first()
            val entries: List<FeedbackEntry> = if (!fq.isNullOrBlank()) {
                try { gson.fromJson(fq, feedbackType) } catch (_: Exception) { emptyList() }
            } else emptyList()
            _feedbackQueue.value = entries

            loadWeeklyJournal(weekStart)
        }
    }

    fun loadWeeklyJournal(weekStart: String) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val text = userPrefsRepository.getWeeklyJournal(currentUserId, weekStart).first()
            _weeklyJournal.value = text ?: ""
        }
    }

    private fun persistLogs(map: Map<String, DailyLog>) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val json = gson.toJson(map.values.toList())
            userPrefsRepository.saveDailyLogsJson(currentUserId, json)
        }
    }

    fun toggleMeal(date: LocalDate, mealId: String) {
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val updatedIds = if (current?.completedMealIds?.contains(mealId) == true) {
            current.completedMealIds.filterNot { it == mealId }
        } else {
            (current?.completedMealIds ?: emptyList()) + mealId
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

    fun setWeight(date: LocalDate, weight: Float?) {
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val updated = (current ?: DailyLog(date = key)).copy(
            weightKg = weight,
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
            userPrefsRepository.saveWeeklyJournal(currentUserId, weekStart, text)
            _weeklyJournal.value = text
        }
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
        viewModelScope.launch {
            val updated = _feedbackQueue.value.map { entry ->
                if (entry.status == "Queued") {
                    val ok = feedbackRepository.sendFeedback(entry.message)
                    if (ok) entry.copy(status = "Sent") else entry.copy(status = "Failed")
                } else entry
            }
            _feedbackQueue.value = updated
            persistFeedback(updated)
        }
    }

    private fun persistFeedback(entries: List<FeedbackEntry>) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            userPrefsRepository.saveFeedbackQueueJson(currentUserId, gson.toJson(entries))
        }
    }

    class Factory(
        private val userPrefsRepository: UserPreferencesRepository,
        private val feedbackRepository: FeedbackRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ProgressViewModel(userPrefsRepository, feedbackRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
