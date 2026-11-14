package com.pcosina.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.FeedbackEntry
import com.pcosina.app.data.model.MealCheckIn
import com.pcosina.app.data.model.PlannerPlanResponse
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.data.repository.ProgressLocalRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.data.repository.UserPreferencesProgressLocalRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.domain.MealLoggingPolicyUseCase
import com.pcosina.app.util.safeUserLogScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal fun parseDailyLogsSafely(raw: String?, gson: Gson = Gson()): List<DailyLog> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = runCatching { JsonParser.parseString(raw).asJsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { element ->
        val parsed = runCatching { gson.fromJson(element, DailyLog::class.java) }.getOrNull() ?: return@mapNotNull null
        sanitizeDailyLog(parsed)
    }
}

internal fun parseFeedbackEntriesSafely(raw: String?, gson: Gson = Gson()): List<FeedbackEntry> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = runCatching { JsonParser.parseString(raw).asJsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { element ->
        val parsed = runCatching { gson.fromJson(element, FeedbackEntry::class.java) }.getOrNull() ?: return@mapNotNull null
        sanitizeFeedbackEntry(parsed)
    }
}

private fun sanitizeDailyLog(log: DailyLog): DailyLog? {
    val normalizedDate = safeIsoDate(log.date) ?: return null
    val normalizedMealCheckIns = ((log.mealCheckIns as? List<*>) ?: emptyList<Any?>())
        .mapNotNull { entry -> sanitizeMealCheckIn(entry as? MealCheckIn) }
        .sortedByDescending { it.timestamp }
    return log.copy(
        date = normalizedDate,
        completedMealIds = sanitizeStringList(log.completedMealIds).distinct(),
        skippedMealIds = sanitizeStringList(log.skippedMealIds).distinct(),
        mealCheckIns = normalizedMealCheckIns,
        weightNote = safeTrimmedText(log.weightNote),
        energyLevel = clampFeedbackLevel(log.energyLevel),
        cravingsLevel = clampFeedbackLevel(log.cravingsLevel),
        moodLevel = clampFeedbackLevel(log.moodLevel),
        symptomTags = sanitizeStringList(log.symptomTags).distinct(),
        symptomSeverityByTag = sanitizeSymptomSeverityMap(log.symptomSeverityByTag),
        symptomsNote = safeTrimmedText(log.symptomsNote),
        journalText = safeTrimmedText(log.journalText),
        timestamp = log.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}

private fun sanitizeMealCheckIn(entry: MealCheckIn?): MealCheckIn? {
    if (entry == null) return null
    val normalizedRecipeId = safeTrimmedText(entry.recipeId)
        ?: safeTrimmedText(entry.mealKey)?.substringAfter("::", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        ?: return null
    val normalizedMealLabel = safeTrimmedText(entry.mealLabel)
        ?: safeTrimmedText(entry.mealKey)?.substringBefore("::", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        ?: "Meal"
    val normalizedMealKey = safeTrimmedText(entry.mealKey)
        ?: ProgressViewModel.buildMealKey(normalizedMealLabel, normalizedRecipeId)
    return entry.copy(
        mealKey = normalizedMealKey,
        recipeId = normalizedRecipeId,
        mealLabel = normalizedMealLabel,
        energyLevel = clampFeedbackLevel(entry.energyLevel),
        fullnessLevel = clampFeedbackLevel(entry.fullnessLevel),
        cravingsLevel = clampFeedbackLevel(entry.cravingsLevel),
        satisfactionLevel = clampFeedbackLevel(entry.satisfactionLevel),
        timestamp = entry.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}

private fun sanitizeFeedbackEntry(entry: FeedbackEntry): FeedbackEntry? {
    val id = safeTrimmedText(entry.id) ?: return null
    val message = safeTrimmedText(entry.message) ?: return null
    val normalizedStatus = when (safeTrimmedText(entry.status)?.lowercase()) {
        "sending" -> "Sending"
        "sent" -> "Sent"
        "failed" -> "Failed"
        else -> "Queued"
    }
    return entry.copy(
        id = id,
        message = message,
        status = normalizedStatus,
        attempts = entry.attempts.coerceAtLeast(0),
        lastError = safeTrimmedText(entry.lastError),
        lastTriedAt = entry.lastTriedAt?.takeIf { it > 0 },
        createdAt = entry.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
    )
}

private fun sanitizeStringList(values: Any?): List<String> =
    ((values as? List<*>) ?: emptyList<Any?>())
        .mapNotNull { safeTrimmedText(it) }

private fun sanitizeSymptomSeverityMap(values: Any?): Map<String, Int> =
    ((values as? Map<*, *>) ?: emptyMap<Any?, Any?>())
        .mapNotNull { (key, value) ->
            val label = safeTrimmedText(key) ?: return@mapNotNull null
            val level = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }?.coerceIn(1, 5) ?: return@mapNotNull null
            label to level
        }
        .distinctBy { it.first.lowercase(Locale.ENGLISH) }
        .toMap()

private fun safeIsoDate(value: Any?): String? {
    val raw = safeTrimmedText(value) ?: return null
    return runCatching { LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE) }
        .getOrNull()
        ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

private fun safeTrimmedText(value: Any?): String? =
    (value as? String)?.trim()?.takeIf { it.isNotBlank() }

private fun clampFeedbackLevel(value: Int?): Int? = value?.coerceIn(1, 5)

class ProgressViewModel(
    private val progressLocalRepository: ProgressLocalRepository,
    private val reflectionStore: ReflectionStore,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {
    constructor(
        userPrefsRepository: UserPreferencesRepository,
        reflectionStore: ReflectionStore,
        feedbackRepository: FeedbackRepository
    ) : this(
        UserPreferencesProgressLocalRepository(userPrefsRepository),
        reflectionStore,
        feedbackRepository
    )


    private val gson = Gson()
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val mealLoggingPolicyUseCase = MealLoggingPolicyUseCase()

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
    private val _savedProgressMode = MutableStateFlow("Today")
    val savedProgressMode: StateFlow<String> = _savedProgressMode.asStateFlow()
    private val _savedAdvancedWeekAnalyticsExpanded = MutableStateFlow(false)
    val savedAdvancedWeekAnalyticsExpanded: StateFlow<Boolean> =
        _savedAdvancedWeekAnalyticsExpanded.asStateFlow()
    private var isSendingFeedback = false
    private val retryBaseDelayMs = 2000L
    private val retryMaxDelayMs = 60000L
    private val retryMaxAttempts = 5
    private val feedbackHistoryLimit = 20

    companion object {
        private const val MEAL_KEY_SEPARATOR = "::"
        const val LoggingPolicySummary =
            "Logging follows breakfast, lunch, and dinner order. Skip a planned meal if you did not eat it. Past and future days are read-only."

        fun buildMealKey(mealLabel: String, recipeId: String): String {
            return "$mealLabel$MEAL_KEY_SEPARATOR$recipeId"
        }

        fun extractMealLabel(mealKey: String): String? {
            return if (mealKey.contains(MEAL_KEY_SEPARATOR)) {
                mealKey.substringBefore(MEAL_KEY_SEPARATOR).takeIf { it.isNotBlank() }
            } else {
                null
            }
        }

        fun extractRecipeId(mealKey: String): String {
            return if (mealKey.contains(MEAL_KEY_SEPARATOR)) {
                mealKey.substringAfter(MEAL_KEY_SEPARATOR)
            } else {
                mealKey
            }
        }

        fun mealSlotMatchesKey(storedKey: String, recipeId: String, mealLabel: String? = null): Boolean {
            val normalizedRecipeId = extractRecipeId(recipeId)
            val normalizedMealLabel = mealLabel?.trim().orEmpty()
            val storedRecipeId = extractRecipeId(storedKey)
            if (storedKey != normalizedRecipeId && storedRecipeId != normalizedRecipeId) return false
            val storedMealLabel = extractMealLabel(storedKey)
            return normalizedMealLabel.isBlank() ||
                storedMealLabel == null ||
                storedMealLabel.equals(normalizedMealLabel, ignoreCase = true)
        }
    }

    fun isDateLoggable(date: LocalDate, now: LocalDate = LocalDate.now()): Boolean =
        mealLoggingPolicyUseCase.isDateLoggable(date, now)

    fun loggingLockReason(date: LocalDate, now: LocalDate = LocalDate.now()): String =
        mealLoggingPolicyUseCase.loggingLockReason(date, now)

    fun mealLoggingLockReason(
        date: LocalDate,
        mealLabel: String?,
        plannedMealLabels: List<String> = emptyList(),
        now: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): String {
        return mealLoggingDecision(
            date = date,
            mealLabel = mealLabel,
            plannedMealLabels = plannedMealLabels,
            now = now,
            currentTime = currentTime
        ).reason
    }

    fun canLogMealNow(
        date: LocalDate,
        mealLabel: String?,
        plannedMealLabels: List<String> = emptyList(),
        now: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): Boolean = mealLoggingDecision(
        date = date,
        mealLabel = mealLabel,
        plannedMealLabels = plannedMealLabels,
        now = now,
        currentTime = currentTime
    ).allowed

    fun loadForUser(userId: String, weekStart: String, fallbackWeekStart: String? = null) {
        if (currentUserId == userId) {
            loadWeeklyJournal(weekStart, fallbackWeekStart)
            loadWeeklySpend(weekStart, fallbackWeekStart)
            loadProgressUiPreferences()
            return
        }
        currentUserId = userId
        viewModelScope.launch {
            runCatching {
                var json = reflectionStore.getDailyLogsJson(userId)
                if (json.isNullOrBlank()) {
                    val legacy = progressLocalRepository.getDailyLogsJson(userId).first()
                    if (!legacy.isNullOrBlank()) {
                        reflectionStore.saveDailyLogsJson(userId, legacy)
                        json = legacy
                    }
                }
                val list = parseDailyLogsSafely(json, gson)
                _dailyLogs.value = list
                    .sortedBy { it.timestamp }
                    .associateBy { it.date }

                val fq = progressLocalRepository.getFeedbackQueueJson(userId).first()
                val entries = parseFeedbackEntriesSafely(fq, gson)
                val normalized = entries.map { entry ->
                    if (entry.status == "Sending") {
                        entry.copy(status = "Queued", lastError = null)
                    } else {
                        entry
                    }
                }.takeLast(feedbackHistoryLimit)
                _feedbackQueue.value = normalized

                loadWeeklyJournal(weekStart, fallbackWeekStart)
                loadWeeklySpend(weekStart, fallbackWeekStart)
                val tags = progressLocalRepository.getPlanFeedbackTags(userId).first()
                _planFeedbackTags.value = tags
                fetchProgressUiPreferences()
            }.onFailure { error ->
                Log.e("ProgressViewModel", "Failed to load progress state safely.", error)
                _dailyLogs.value = emptyMap()
                _weeklyJournal.value = ""
                _weeklySpend.value = null
                _feedbackQueue.value = emptyList()
                _planFeedbackTags.value = emptyList()
                _savedProgressMode.value = "Today"
                _savedAdvancedWeekAnalyticsExpanded.value = false
            }
        }
    }

    private suspend fun fetchProgressUiPreferences() {
        if (currentUserId.isBlank()) {
            _savedProgressMode.value = "Today"
            _savedAdvancedWeekAnalyticsExpanded.value = false
            return
        }
        runCatching {
            _savedProgressMode.value = progressLocalRepository.getProgressMode(currentUserId).first()
            _savedAdvancedWeekAnalyticsExpanded.value =
                progressLocalRepository.getProgressAdvancedAnalyticsExpanded(currentUserId).first()
        }.onFailure { error ->
            Log.e("ProgressViewModel", "Failed to load saved progress UI preferences.", error)
            _savedProgressMode.value = "Today"
            _savedAdvancedWeekAnalyticsExpanded.value = false
        }
    }

    fun loadProgressUiPreferences() {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            fetchProgressUiPreferences()
        }
    }

    fun setProgressModePreference(mode: String) {
        if (currentUserId.isBlank()) return
        val normalized = if (mode.equals("Week", ignoreCase = true)) "Week" else "Today"
        _savedProgressMode.value = normalized
        viewModelScope.launch {
            progressLocalRepository.saveProgressMode(currentUserId, normalized)
            Log.i("ProgressUX", "Saved focus mode: $normalized ${safeUserLogScope(currentUserId)}")
        }
    }

    fun setAdvancedWeekAnalyticsExpandedPreference(expanded: Boolean) {
        if (currentUserId.isBlank()) return
        _savedAdvancedWeekAnalyticsExpanded.value = expanded
        viewModelScope.launch {
            progressLocalRepository.saveProgressAdvancedAnalyticsExpanded(currentUserId, expanded)
            Log.i("ProgressUX", "Saved advanced analytics expanded=$expanded ${safeUserLogScope(currentUserId)}")
        }
    }

    fun loadWeeklyJournal(weekStart: String, fallbackWeekStart: String? = null) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val primary = reflectionStore.getWeeklyJournal(currentUserId, weekStart)
                if (!primary.isNullOrBlank()) {
                    _weeklyJournal.value = primary
                    return@launch
                }
                val legacyPrimary = progressLocalRepository.getWeeklyJournal(currentUserId, weekStart).first()
                if (!legacyPrimary.isNullOrBlank()) {
                    reflectionStore.saveWeeklyJournal(currentUserId, weekStart, legacyPrimary)
                    _weeklyJournal.value = legacyPrimary
                    return@launch
                }
                if (!fallbackWeekStart.isNullOrBlank() && fallbackWeekStart != weekStart) {
                    val fallback = reflectionStore.getWeeklyJournal(currentUserId, fallbackWeekStart)
                    if (!fallback.isNullOrBlank()) {
                        _weeklyJournal.value = fallback
                        reflectionStore.saveWeeklyJournal(currentUserId, weekStart, fallback)
                        return@launch
                    }
                    val legacyFallback = progressLocalRepository.getWeeklyJournal(currentUserId, fallbackWeekStart).first()
                    if (!legacyFallback.isNullOrBlank()) {
                        reflectionStore.saveWeeklyJournal(currentUserId, fallbackWeekStart, legacyFallback)
                        reflectionStore.saveWeeklyJournal(currentUserId, weekStart, legacyFallback)
                        _weeklyJournal.value = legacyFallback
                        return@launch
                    }
                }
                _weeklyJournal.value = ""
            }.onFailure { error ->
                Log.e("ProgressViewModel", "Failed to load weekly journal safely.", error)
                _weeklyJournal.value = ""
            }
        }
    }

    fun loadWeeklySpend(weekStart: String, fallbackWeekStart: String? = null) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            runCatching {
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
            }.onFailure { error ->
                Log.e("ProgressViewModel", "Failed to load weekly spend safely.", error)
                _weeklySpend.value = null
            }
        }
    }

    private fun persistLogs(map: Map<String, DailyLog>) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            val json = gson.toJson(map.values.toList())
            reflectionStore.saveDailyLogsJson(currentUserId, json)
        }
    }

    private fun matchesMealCheckIn(
        entry: MealCheckIn,
        recipeId: String,
        mealLabel: String? = null
    ): Boolean {
        val normalizedRecipeId = extractRecipeId(recipeId)
        val normalizedMealLabel = mealLabel?.trim().orEmpty()
        if (extractRecipeId(entry.mealKey) != normalizedRecipeId && entry.recipeId != normalizedRecipeId) {
            return false
        }
        return normalizedMealLabel.isBlank() || entry.mealLabel.equals(normalizedMealLabel, ignoreCase = true)
    }

    private fun completedMealLabelsFor(date: LocalDate): List<String> {
        val key = date.format(dateFmt)
        return _dailyLogs.value[key]
            ?.completedMealIds
            .orEmpty()
            .mapNotNull(::extractMealLabel)
    }

    private fun skippedMealLabelsFor(date: LocalDate): List<String> {
        val key = date.format(dateFmt)
        return _dailyLogs.value[key]
            ?.skippedMealIds
            .orEmpty()
            .mapNotNull(::extractMealLabel)
    }

    private fun mealLoggingDecision(
        date: LocalDate,
        mealLabel: String?,
        plannedMealLabels: List<String> = emptyList(),
        now: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ) = mealLoggingPolicyUseCase.evaluate(
        date = date,
        mealLabel = mealLabel,
        completedMealLabels = completedMealLabelsFor(date),
        skippedMealLabels = skippedMealLabelsFor(date),
        plannedMealLabels = plannedMealLabels,
        now = now,
        currentTime = currentTime
    )

    fun toggleMeal(date: LocalDate, recipeId: String, mealLabel: String): Boolean {
        if (!isDateLoggable(date)) return false
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val mealKey = buildMealKey(mealLabel, recipeId)
        val currentIds = current?.completedMealIds ?: emptyList()
        val skippedIds = current?.skippedMealIds.orEmpty()
        val hasKey = currentIds.contains(mealKey)
        val hasLegacy = currentIds.contains(recipeId)
        if (hasKey || hasLegacy) return true
        if (skippedIds.any { skippedKey -> mealSlotMatchesKey(skippedKey, recipeId, mealLabel) }) return false
        if (!hasKey && !hasLegacy) {
            val decision = mealLoggingDecision(date = date, mealLabel = mealLabel)
            if (!decision.allowed) return false
        }
        val updated = (current ?: DailyLog(date = key)).copy(
            completedMealIds = currentIds + mealKey,
            skippedMealIds = skippedIds,
            mealCheckIns = current?.mealCheckIns.orEmpty(),
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
        return true
    }

    fun isMealSkipped(date: LocalDate, recipeId: String, mealLabel: String? = null): Boolean {
        val key = date.format(dateFmt)
        val skippedIds = _dailyLogs.value[key]?.skippedMealIds.orEmpty()
        return skippedIds.any { skippedKey -> mealSlotMatchesKey(skippedKey, recipeId, mealLabel) }
    }

    fun markMealAsEaten(
        date: LocalDate,
        recipeId: String,
        mealLabel: String? = null,
        plannedMealLabels: List<String> = emptyList()
    ): Boolean {
        if (!isDateLoggable(date)) return false
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val currentIds = current?.completedMealIds ?: emptyList()
        val skippedIds = current?.skippedMealIds ?: emptyList()
        val normalizedRecipeId = extractRecipeId(recipeId)
        val normalizedMealLabel = mealLabel?.trim().orEmpty()
        val mealKey = if (normalizedMealLabel.isNotBlank()) {
            buildMealKey(normalizedMealLabel, normalizedRecipeId)
        } else {
            normalizedRecipeId
        }
        val alreadyLogged = if (normalizedMealLabel.isNotBlank()) {
            currentIds.contains(mealKey) || currentIds.contains(normalizedRecipeId)
        } else {
            currentIds.any { extractRecipeId(it) == normalizedRecipeId }
        }
        if (alreadyLogged) return true
        if (skippedIds.any { skippedKey -> mealSlotMatchesKey(skippedKey, normalizedRecipeId, normalizedMealLabel) }) {
            return false
        }
        val decision = mealLoggingDecision(
            date = date,
            mealLabel = mealLabel,
            plannedMealLabels = plannedMealLabels
        )
        if (!decision.allowed) return false
        val updated = (current ?: DailyLog(date = key)).copy(
            completedMealIds = currentIds + mealKey,
            skippedMealIds = skippedIds,
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
        return true
    }

    fun skipMeal(
        date: LocalDate,
        recipeId: String,
        mealLabel: String,
        plannedMealLabels: List<String> = emptyList()
    ): Boolean {
        if (!isDateLoggable(date)) return false
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val normalizedRecipeId = extractRecipeId(recipeId)
        val normalizedMealLabel = mealLabel.trim().ifBlank { "Meal" }
        val mealKey = buildMealKey(normalizedMealLabel, normalizedRecipeId)
        val currentIds = current?.completedMealIds ?: emptyList()
        val skippedIds = current?.skippedMealIds ?: emptyList()
        val alreadyLogged = currentIds.contains(mealKey) ||
            currentIds.contains(normalizedRecipeId) ||
            currentIds.any { extractRecipeId(it) == normalizedRecipeId && extractMealLabel(it).equals(normalizedMealLabel, ignoreCase = true) }
        if (alreadyLogged) return false
        if (skippedIds.contains(mealKey)) return true
        val decision = mealLoggingDecision(
            date = date,
            mealLabel = normalizedMealLabel,
            plannedMealLabels = plannedMealLabels
        )
        if (!decision.allowed) return false
        val updated = (current ?: DailyLog(date = key)).copy(
            skippedMealIds = (skippedIds + mealKey).distinct(),
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
        return true
    }

    fun unskipMeal(date: LocalDate, recipeId: String, mealLabel: String): Boolean {
        if (!isDateLoggable(date)) return false
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key] ?: return true
        val normalizedRecipeId = extractRecipeId(recipeId)
        val normalizedMealLabel = mealLabel.trim().ifBlank { "Meal" }
        val mealKey = buildMealKey(normalizedMealLabel, normalizedRecipeId)
        val updatedSkippedIds = current.skippedMealIds.filterNot { skippedKey ->
            skippedKey == mealKey ||
                (extractRecipeId(skippedKey) == normalizedRecipeId &&
                    extractMealLabel(skippedKey).equals(normalizedMealLabel, ignoreCase = true))
        }
        if (updatedSkippedIds == current.skippedMealIds) return true
        val updated = current.copy(
            skippedMealIds = updatedSkippedIds,
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
        return true
    }

    fun setWeight(date: LocalDate, weight: Float?, note: String? = null): Boolean {
        if (!isDateLoggable(date)) return false
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
        return true
    }

    fun saveReflection(
        date: LocalDate,
        energyLevel: Int?,
        cravingsLevel: Int?,
        moodLevel: Int?,
        symptomTags: List<String>,
        symptomSeverityByTag: Map<String, Int> = emptyMap(),
        symptomsNote: String?
    ): Boolean {
        if (!isDateLoggable(date)) return false
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val updated = (current ?: DailyLog(date = key)).copy(
            energyLevel = energyLevel,
            cravingsLevel = cravingsLevel,
            moodLevel = moodLevel,
            symptomTags = (symptomTags + symptomSeverityByTag.keys).map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            symptomSeverityByTag = sanitizeSymptomSeverityMap(symptomSeverityByTag),
            symptomsNote = symptomsNote?.takeIf { it.isNotBlank() },
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
        return true
    }

    fun saveMealCheckIn(
        date: LocalDate,
        recipeId: String,
        mealLabel: String,
        energyLevel: Int?,
        fullnessLevel: Int?,
        cravingsLevel: Int?,
        satisfactionLevel: Int?
    ): Boolean {
        if (!isDateLoggable(date)) return false
        val key = date.format(dateFmt)
        val current = _dailyLogs.value[key]
        val normalizedRecipeId = extractRecipeId(recipeId)
        val normalizedMealLabel = mealLabel.trim().ifBlank { "Meal" }
        val mealKey = buildMealKey(normalizedMealLabel, normalizedRecipeId)
        val updatedCheckIn = MealCheckIn(
            mealKey = mealKey,
            recipeId = normalizedRecipeId,
            mealLabel = normalizedMealLabel,
            energyLevel = energyLevel,
            fullnessLevel = fullnessLevel,
            cravingsLevel = cravingsLevel,
            satisfactionLevel = satisfactionLevel,
            timestamp = System.currentTimeMillis()
        )
        val updated = (current ?: DailyLog(date = key)).copy(
            mealCheckIns = current?.mealCheckIns.orEmpty()
                .filterNot { entry ->
                    matchesMealCheckIn(entry, recipeId = normalizedRecipeId, mealLabel = normalizedMealLabel)
                } + updatedCheckIn,
            timestamp = System.currentTimeMillis()
        )
        val newMap = _dailyLogs.value.toMutableMap()
        newMap[key] = updated
        _dailyLogs.value = newMap
        persistLogs(newMap)
        return true
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
        _savedProgressMode.value = "Today"
        _savedAdvancedWeekAnalyticsExpanded.value = false
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
            progressLocalRepository.savePlanFeedbackTags(currentUserId, current)
        }
    }

    fun savePlanFeedbackTags(tags: List<String>) {
        if (currentUserId.isBlank()) return
        val normalized = tags
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        _planFeedbackTags.value = normalized
        viewModelScope.launch {
            progressLocalRepository.savePlanFeedbackTags(currentUserId, normalized)
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
            progressLocalRepository.clearLegacyReflections(currentUserId)
        }
        _dailyLogs.value = emptyMap()
        _weeklyJournal.value = ""
        _weeklySpend.value = null
    }

    suspend fun queueFeedback(message: String): Boolean {
        val normalizedMessage = message.trim()
        if (currentUserId.isBlank() || normalizedMessage.isBlank()) return false
        val previous = _feedbackQueue.value
        val entry = FeedbackEntry(id = UUID.randomUUID().toString(), message = normalizedMessage)
        val updated = (previous + entry).takeLast(feedbackHistoryLimit)
        _feedbackQueue.value = updated
        return runCatching {
            progressLocalRepository.saveFeedbackQueueJson(currentUserId, gson.toJson(updated))
        }.onFailure { error ->
            _feedbackQueue.value = previous
            Log.e("ProgressViewModel", "Failed to persist feedback queue safely.", error)
        }.isSuccess
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
                            entries[i] = sendingEntry.copy(
                                status = "Sent",
                                attempts = sendingEntry.attempts + 1,
                                lastError = null,
                            )
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
            progressLocalRepository.saveFeedbackQueueJson(currentUserId, gson.toJson(entries))
        }
    }

    fun seedDemoWeeks(plan: PlannerPlanResponse) {
        val today = LocalDate.now()
        val start = today
        val seededLogs = plan.days.mapIndexed { index, day ->
            val date = start.plusDays(index.toLong()).format(dateFmt)
            val completedMeals = day.meals.take(if (index < 3) day.meals.size else 1).map { meal ->
                buildMealKey(meal.mealLabel, meal.recipeId)
            }
            date to DailyLog(
                date = date,
                completedMealIds = completedMeals,
                timestamp = System.currentTimeMillis()
            )
        }.toMap()
        _dailyLogs.value = seededLogs
    }

    class Factory(
        private val progressLocalRepository: ProgressLocalRepository,
        private val reflectionStore: ReflectionStore,
        private val feedbackRepository: FeedbackRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ProgressViewModel(progressLocalRepository, reflectionStore, feedbackRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
