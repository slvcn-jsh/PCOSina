package com.pcosina.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Local-only preferences; per-user isolation is handled by key prefixes (userId/email).
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * Local-only persistence for profile, plans, and cached groceries.
 * This does not sync to any cloud backend; use explicit API calls for remote data.
 */
class UserPreferencesRepository(private val context: Context) {
    private val gson = Gson()
    private val pantryType = object : TypeToken<List<PantryEntry>>() {}.type

    private object Keys {
        val adminMode = booleanPreferencesKey("admin_mode")
        fun name(userId: String) = stringPreferencesKey("name_$userId")
        fun age(userId: String) = intPreferencesKey("age_$userId")
        fun weight(userId: String) = intPreferencesKey("weight_$userId")
        fun height(userId: String) = intPreferencesKey("height_$userId")
        fun weightUnit(userId: String) = stringPreferencesKey("weight_unit_$userId")
        fun heightUnit(userId: String) = stringPreferencesKey("height_unit_$userId")
        fun activity(userId: String) = stringPreferencesKey("activity_$userId")
        fun goal(userId: String) = stringPreferencesKey("goal_$userId")
        fun insulin(userId: String) = stringPreferencesKey("insulin_$userId")
        fun symptoms(userId: String) = stringPreferencesKey("symptoms_$userId")
        fun comorbidities(userId: String) = stringPreferencesKey("comorbidities_$userId")
        fun restrictions(userId: String) = stringPreferencesKey("restrictions_$userId")
        fun allergies(userId: String) = stringPreferencesKey("allergies_$userId")
        fun pantry(userId: String) = stringPreferencesKey("pantry_$userId")
        fun pantryEntries(userId: String) = stringPreferencesKey("pantry_entries_$userId")
        fun budget(userId: String) = intPreferencesKey("budget_$userId")
        fun maxCookingTime(userId: String) = intPreferencesKey("max_cooking_time_$userId")
        fun variety(userId: String) = stringPreferencesKey("variety_pref_$userId")
        fun planningPriority(userId: String) = stringPreferencesKey("planning_priority_$userId")
        fun completed(userId: String) = booleanPreferencesKey("onboarding_complete_$userId")
        fun lastPlanJson(userId: String) = stringPreferencesKey("last_plan_json_$userId")
        fun lastPlanTimestamp(userId: String) = longPreferencesKey("last_plan_timestamp_$userId")
        fun planHistoryJson(userId: String) = stringPreferencesKey("plan_history_json_$userId")
        fun activePlanId(userId: String) = stringPreferencesKey("active_plan_id_$userId")
        // Task #1: Persistent Grocery Storage
        fun groceryJson(userId: String) = stringPreferencesKey("grocery_json_$userId")
        fun grocerySourcesJson(userId: String) = stringPreferencesKey("grocery_sources_json_$userId")
        fun grocerySnapshotsJson(userId: String) = stringPreferencesKey("grocery_snapshots_json_$userId")
        fun migrationLogged(userId: String) = booleanPreferencesKey("migration_logged_$userId")
        fun dailyLogsJson(userId: String) = stringPreferencesKey("daily_logs_json_$userId")
        fun feedbackQueueJson(userId: String) = stringPreferencesKey("feedback_queue_json_$userId")
        fun weeklyJournal(userId: String, weekStart: String) = stringPreferencesKey("weekly_journal_${userId}_$weekStart")
        fun planFeedbackTags(userId: String) = stringPreferencesKey("plan_feedback_tags_$userId")
        fun remindersEnabled(userId: String) = booleanPreferencesKey("reminders_enabled_$userId")
    }

    private object LegacyKeys {
        fun name(email: String) = stringPreferencesKey("name_$email")
        fun age(email: String) = intPreferencesKey("age_$email")
        fun weight(email: String) = intPreferencesKey("weight_$email")
        fun height(email: String) = intPreferencesKey("height_$email")
        fun weightUnit(email: String) = stringPreferencesKey("weight_unit_$email")
        fun heightUnit(email: String) = stringPreferencesKey("height_unit_$email")
        fun activity(email: String) = stringPreferencesKey("activity_$email")
        fun goal(email: String) = stringPreferencesKey("goal_$email")
        fun insulin(email: String) = stringPreferencesKey("insulin_$email")
        fun symptoms(email: String) = stringPreferencesKey("symptoms_$email")
        fun comorbidities(email: String) = stringPreferencesKey("comorbidities_$email")
        fun restrictions(email: String) = stringPreferencesKey("restrictions_$email")
        fun allergies(email: String) = stringPreferencesKey("allergies_$email")
        fun pantry(email: String) = stringPreferencesKey("pantry_$email")
        fun pantryEntries(email: String) = stringPreferencesKey("pantry_entries_$email")
        fun budget(email: String) = intPreferencesKey("budget_$email")
        fun maxCookingTime(email: String) = intPreferencesKey("max_cooking_time_$email")
        fun variety(email: String) = stringPreferencesKey("variety_pref_$email")
        fun planningPriority(email: String) = stringPreferencesKey("planning_priority_$email")
        fun completed(email: String) = booleanPreferencesKey("onboarding_complete_$email")
        fun lastPlanJson(email: String) = stringPreferencesKey("last_plan_json_$email")
        fun lastPlanTimestamp(email: String) = longPreferencesKey("last_plan_timestamp_$email")
        fun groceryJson(email: String) = stringPreferencesKey("grocery_json_$email")
    }

    suspend fun migrateFromEmailIfNeeded(userId: String, email: String) {
        if (userId.isBlank() || email.isBlank()) return
        context.dataStore.edit { preferences ->
            val hasUidData = preferences.contains(Keys.completed(userId)) ||
                preferences.contains(Keys.name(userId)) ||
                preferences.contains(Keys.lastPlanJson(userId)) ||
                preferences.contains(Keys.groceryJson(userId))

            if (hasUidData) return@edit

            val hasLegacyData = preferences.contains(LegacyKeys.completed(email)) ||
                preferences.contains(LegacyKeys.name(email)) ||
                preferences.contains(LegacyKeys.lastPlanJson(email)) ||
                preferences.contains(LegacyKeys.groceryJson(email))

            if (!hasLegacyData) return@edit

            preferences[Keys.name(userId)] = preferences[LegacyKeys.name(email)] ?: ""
            preferences[Keys.age(userId)] = preferences[LegacyKeys.age(email)] ?: 0
            preferences[Keys.weight(userId)] = preferences[LegacyKeys.weight(email)] ?: 0
            preferences[Keys.height(userId)] = preferences[LegacyKeys.height(email)] ?: 0
            preferences[Keys.weightUnit(userId)] = preferences[LegacyKeys.weightUnit(email)] ?: "kg"
            preferences[Keys.heightUnit(userId)] = preferences[LegacyKeys.heightUnit(email)] ?: "cm"
            preferences[Keys.activity(userId)] = preferences[LegacyKeys.activity(email)] ?: "Lightly Active"
            preferences[Keys.goal(userId)] = preferences[LegacyKeys.goal(email)] ?: "Support PCOS symptom management"
            preferences[Keys.insulin(userId)] = preferences[LegacyKeys.insulin(email)] ?: "Mild"
            preferences[Keys.symptoms(userId)] = preferences[LegacyKeys.symptoms(email)] ?: ""
            preferences[Keys.comorbidities(userId)] = preferences[LegacyKeys.comorbidities(email)] ?: ""
            preferences[Keys.restrictions(userId)] = preferences[LegacyKeys.restrictions(email)] ?: ""
            preferences[Keys.allergies(userId)] = preferences[LegacyKeys.allergies(email)] ?: ""
            preferences[Keys.pantry(userId)] = preferences[LegacyKeys.pantry(email)] ?: ""
            preferences[Keys.budget(userId)] = preferences[LegacyKeys.budget(email)] ?: 0
            preferences[Keys.maxCookingTime(userId)] = preferences[LegacyKeys.maxCookingTime(email)] ?: 45
            preferences[Keys.variety(userId)] = preferences[LegacyKeys.variety(email)] ?: "Balanced"
            preferences[Keys.planningPriority(userId)] = preferences[LegacyKeys.planningPriority(email)] ?: "Balanced"
            preferences[Keys.completed(userId)] = preferences[LegacyKeys.completed(email)] ?: false

            preferences[Keys.lastPlanJson(userId)] = preferences[LegacyKeys.lastPlanJson(email)] ?: ""
            preferences[Keys.lastPlanTimestamp(userId)] = preferences[LegacyKeys.lastPlanTimestamp(email)] ?: 0L

            preferences[Keys.groceryJson(userId)] = preferences[LegacyKeys.groceryJson(email)] ?: ""

            if (preferences[Keys.migrationLogged(userId)] != true) {
                Log.i("PCOSINA", "Migrated legacy email data to UID for userId=$userId")
                preferences[Keys.migrationLogged(userId)] = true
            }
        }
    }

    fun getUserProfile(userId: String): Flow<UserProfile> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            UserProfile(
                displayName = preferences[Keys.name(userId)] ?: "",
                age = preferences[Keys.age(userId)] ?: 0,
                weightKg = preferences[Keys.weight(userId)] ?: 0,
                heightCm = preferences[Keys.height(userId)] ?: 0,
                weightUnit = preferences[Keys.weightUnit(userId)] ?: "kg",
                heightUnit = preferences[Keys.heightUnit(userId)] ?: "cm",
                activityLevel = preferences[Keys.activity(userId)] ?: "Lightly Active",
                goal = preferences[Keys.goal(userId)] ?: "Support PCOS symptom management",
                insulinResistanceLevel = preferences[Keys.insulin(userId)] ?: "Mild",
                symptoms = preferences[Keys.symptoms(userId)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                comorbidities = preferences[Keys.comorbidities(userId)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                dietaryRestrictions = preferences[Keys.restrictions(userId)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                allergies = preferences[Keys.allergies(userId)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                pantryItems = preferences[Keys.pantry(userId)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                weeklyBudgetPhp = preferences[Keys.budget(userId)] ?: 0,
                maxCookingTimeMinutes = preferences[Keys.maxCookingTime(userId)] ?: 45,
                varietyPreference = preferences[Keys.variety(userId)] ?: "Balanced",
                planningPriority = preferences[Keys.planningPriority(userId)] ?: "Balanced",
                isProfileCompleted = preferences[Keys.completed(userId)] ?: false
            )
        }

    suspend fun updateProfile(userId: String, profile: UserProfile) {
        context.dataStore.edit { preferences ->
            preferences[Keys.name(userId)] = profile.displayName
            preferences[Keys.age(userId)] = profile.age
            preferences[Keys.weight(userId)] = profile.weightKg
            preferences[Keys.height(userId)] = profile.heightCm
            preferences[Keys.weightUnit(userId)] = profile.weightUnit
            preferences[Keys.heightUnit(userId)] = profile.heightUnit
            preferences[Keys.activity(userId)] = profile.activityLevel
            preferences[Keys.goal(userId)] = profile.goal
            preferences[Keys.insulin(userId)] = profile.insulinResistanceLevel
            preferences[Keys.symptoms(userId)] = profile.symptoms.joinToString(",")
            preferences[Keys.comorbidities(userId)] = profile.comorbidities.joinToString(",")
            preferences[Keys.restrictions(userId)] = profile.dietaryRestrictions.joinToString(",")
            preferences[Keys.allergies(userId)] = profile.allergies.joinToString(",")
            preferences[Keys.pantry(userId)] = profile.pantryItems.joinToString(",")
            preferences[Keys.budget(userId)] = profile.weeklyBudgetPhp
            preferences[Keys.maxCookingTime(userId)] = profile.maxCookingTimeMinutes
            preferences[Keys.variety(userId)] = profile.varietyPreference
            preferences[Keys.planningPriority(userId)] = profile.planningPriority
            preferences[Keys.completed(userId)] = profile.isProfileCompleted
        }
    }

    fun getPantryEntries(userId: String): Flow<List<PantryEntry>> =
        context.dataStore.data.map { prefs ->
            val json = prefs[Keys.pantryEntries(userId)]
            if (!json.isNullOrBlank()) {
                try { gson.fromJson<List<PantryEntry>>(json, pantryType) } catch (_: Exception) { emptyList() }
            } else {
                // Fallback to legacy pantry list (names only)
                val names = prefs[Keys.pantry(userId)]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                names.map { PantryEntry(name = it) }
            }
        }

    suspend fun savePantryEntries(userId: String, entries: List<PantryEntry>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.pantryEntries(userId)] = gson.toJson(entries)
        }
    }

    fun getAdminMode(): Flow<Boolean> = context.dataStore.data.map { it[Keys.adminMode] ?: false }

    suspend fun setAdminMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.adminMode] = enabled
        }
    }

    fun getSavedPlanJson(userId: String): Flow<String?> = context.dataStore.data.map { it[Keys.lastPlanJson(userId)] }
    fun getSavedPlanTimestamp(userId: String): Flow<Long> = context.dataStore.data.map { it[Keys.lastPlanTimestamp(userId)] ?: 0L }
    fun getPlanHistoryJson(userId: String): Flow<String?> = context.dataStore.data.map { it[Keys.planHistoryJson(userId)] }
    fun getActivePlanId(userId: String): Flow<String?> = context.dataStore.data.map { it[Keys.activePlanId(userId)] }

    suspend fun savePlanJson(userId: String, json: String, timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.lastPlanJson(userId)] = json
            preferences[Keys.lastPlanTimestamp(userId)] = timestamp
        }
    }

    suspend fun savePlanHistoryJson(userId: String, json: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.planHistoryJson(userId)] = json
        }
    }

    suspend fun saveActivePlanId(userId: String, id: String?) {
        context.dataStore.edit { preferences ->
            if (id.isNullOrBlank()) preferences.remove(Keys.activePlanId(userId))
            else preferences[Keys.activePlanId(userId)] = id
        }
    }

    // Grocery Persistence Logic
    fun getGroceryJson(userId: String): Flow<String?> = context.dataStore.data.map { it[Keys.groceryJson(userId)] }
    suspend fun saveGroceryJson(userId: String, json: String) {
        context.dataStore.edit { it[Keys.groceryJson(userId)] = json }
    }

    fun getGrocerySourcesJson(userId: String): Flow<String?> =
        context.dataStore.data.map { it[Keys.grocerySourcesJson(userId)] }

    suspend fun saveGrocerySourcesJson(userId: String, json: String) {
        context.dataStore.edit { it[Keys.grocerySourcesJson(userId)] = json }
    }

    fun getGrocerySnapshotsJson(userId: String): Flow<String?> =
        context.dataStore.data.map { it[Keys.grocerySnapshotsJson(userId)] }

    suspend fun saveGrocerySnapshotsJson(userId: String, json: String) {
        context.dataStore.edit { it[Keys.grocerySnapshotsJson(userId)] = json }
    }

    fun getDailyLogsJson(userId: String): Flow<String?> = context.dataStore.data.map { it[Keys.dailyLogsJson(userId)] }
    suspend fun saveDailyLogsJson(userId: String, json: String) {
        context.dataStore.edit { it[Keys.dailyLogsJson(userId)] = json }
    }

    fun getWeeklyJournal(userId: String, weekStart: String): Flow<String?> =
        context.dataStore.data.map { it[Keys.weeklyJournal(userId, weekStart)] }

    suspend fun saveWeeklyJournal(userId: String, weekStart: String, text: String) {
        context.dataStore.edit { it[Keys.weeklyJournal(userId, weekStart)] = text }
    }

    fun getFeedbackQueueJson(userId: String): Flow<String?> =
        context.dataStore.data.map { it[Keys.feedbackQueueJson(userId)] }

    suspend fun saveFeedbackQueueJson(userId: String, json: String) {
        context.dataStore.edit { it[Keys.feedbackQueueJson(userId)] = json }
    }

    fun getPlanFeedbackTags(userId: String): Flow<List<String>> =
        context.dataStore.data.map {
            it[Keys.planFeedbackTags(userId)]?.split(",")?.filter { tag -> tag.isNotBlank() } ?: emptyList()
        }

    suspend fun savePlanFeedbackTags(userId: String, tags: List<String>) {
        context.dataStore.edit { it[Keys.planFeedbackTags(userId)] = tags.joinToString(",") }
    }

    fun getRemindersEnabled(userId: String): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.remindersEnabled(userId)] ?: false }

    suspend fun setRemindersEnabled(userId: String, enabled: Boolean) {
        context.dataStore.edit { it[Keys.remindersEnabled(userId)] = enabled }
    }

    suspend fun clearPlanHistory(userId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.planHistoryJson(userId))
            preferences.remove(Keys.activePlanId(userId))
            preferences.remove(Keys.lastPlanJson(userId))
            preferences.remove(Keys.lastPlanTimestamp(userId))
        }
    }

    suspend fun clearGrocerySnapshots(userId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.grocerySnapshotsJson(userId))
            preferences.remove(Keys.groceryJson(userId))
            preferences.remove(Keys.grocerySourcesJson(userId))
        }
    }

    suspend fun clearLegacyReflections(userId: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.dailyLogsJson(userId))
            val weeklyPrefix = "weekly_journal_${userId}_"
            preferences.asMap().keys
                .filter { it.name.startsWith(weeklyPrefix) }
                .toList()
                .forEach { preferences.remove(it) }
        }
    }
}
