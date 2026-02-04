package com.pcosina.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import android.util.Log
import com.pcosina.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        fun name(userId: String) = stringPreferencesKey("name_$userId")
        fun age(userId: String) = intPreferencesKey("age_$userId")
        fun weight(userId: String) = intPreferencesKey("weight_$userId")
        fun height(userId: String) = intPreferencesKey("height_$userId")
        fun activity(userId: String) = stringPreferencesKey("activity_$userId")
        fun goal(userId: String) = stringPreferencesKey("goal_$userId")
        fun insulin(userId: String) = stringPreferencesKey("insulin_$userId")
        fun symptoms(userId: String) = stringPreferencesKey("symptoms_$userId")
        fun comorbidities(userId: String) = stringPreferencesKey("comorbidities_$userId")
        fun restrictions(userId: String) = stringPreferencesKey("restrictions_$userId")
        fun budget(userId: String) = intPreferencesKey("budget_$userId")
        fun completed(userId: String) = booleanPreferencesKey("onboarding_complete_$userId")
        fun lastPlanJson(userId: String) = stringPreferencesKey("last_plan_json_$userId")
        fun lastPlanTimestamp(userId: String) = longPreferencesKey("last_plan_timestamp_$userId")
        // Task #1: Persistent Grocery Storage
        fun groceryJson(userId: String) = stringPreferencesKey("grocery_json_$userId")
        fun migrationLogged(userId: String) = booleanPreferencesKey("migration_logged_$userId")
        fun dailyLogsJson(userId: String) = stringPreferencesKey("daily_logs_json_$userId")
        fun feedbackQueueJson(userId: String) = stringPreferencesKey("feedback_queue_json_$userId")
        fun weeklyJournal(userId: String, weekStart: String) = stringPreferencesKey("weekly_journal_${userId}_$weekStart")
    }

    private object LegacyKeys {
        fun name(email: String) = stringPreferencesKey("name_$email")
        fun age(email: String) = intPreferencesKey("age_$email")
        fun weight(email: String) = intPreferencesKey("weight_$email")
        fun height(email: String) = intPreferencesKey("height_$email")
        fun activity(email: String) = stringPreferencesKey("activity_$email")
        fun goal(email: String) = stringPreferencesKey("goal_$email")
        fun insulin(email: String) = stringPreferencesKey("insulin_$email")
        fun symptoms(email: String) = stringPreferencesKey("symptoms_$email")
        fun comorbidities(email: String) = stringPreferencesKey("comorbidities_$email")
        fun restrictions(email: String) = stringPreferencesKey("restrictions_$email")
        fun budget(email: String) = intPreferencesKey("budget_$email")
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
            preferences[Keys.activity(userId)] = preferences[LegacyKeys.activity(email)] ?: "Lightly Active"
            preferences[Keys.goal(userId)] = preferences[LegacyKeys.goal(email)] ?: "Support PCOS symptom management"
            preferences[Keys.insulin(userId)] = preferences[LegacyKeys.insulin(email)] ?: "Mild"
            preferences[Keys.symptoms(userId)] = preferences[LegacyKeys.symptoms(email)] ?: ""
            preferences[Keys.comorbidities(userId)] = preferences[LegacyKeys.comorbidities(email)] ?: ""
            preferences[Keys.restrictions(userId)] = preferences[LegacyKeys.restrictions(email)] ?: ""
            preferences[Keys.budget(userId)] = preferences[LegacyKeys.budget(email)] ?: 2000
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
                activityLevel = preferences[Keys.activity(userId)] ?: "Lightly Active",
                goal = preferences[Keys.goal(userId)] ?: "Support PCOS symptom management",
                insulinResistanceLevel = preferences[Keys.insulin(userId)] ?: "Mild",
                symptoms = preferences[Keys.symptoms(userId)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                comorbidities = preferences[Keys.comorbidities(userId)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                dietaryRestrictions = preferences[Keys.restrictions(userId)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                weeklyBudgetPhp = preferences[Keys.budget(userId)] ?: 2000,
                isProfileCompleted = preferences[Keys.completed(userId)] ?: false
            )
        }

    suspend fun updateProfile(userId: String, profile: UserProfile) {
        context.dataStore.edit { preferences ->
            preferences[Keys.name(userId)] = profile.displayName
            preferences[Keys.age(userId)] = profile.age
            preferences[Keys.weight(userId)] = profile.weightKg
            preferences[Keys.height(userId)] = profile.heightCm
            preferences[Keys.activity(userId)] = profile.activityLevel
            preferences[Keys.goal(userId)] = profile.goal
            preferences[Keys.insulin(userId)] = profile.insulinResistanceLevel
            preferences[Keys.symptoms(userId)] = profile.symptoms.joinToString(",")
            preferences[Keys.comorbidities(userId)] = profile.comorbidities.joinToString(",")
            preferences[Keys.restrictions(userId)] = profile.dietaryRestrictions.joinToString(",")
            preferences[Keys.budget(userId)] = profile.weeklyBudgetPhp
            preferences[Keys.completed(userId)] = profile.isProfileCompleted
        }
    }

    fun getSavedPlanJson(userId: String): Flow<String?> = context.dataStore.data.map { it[Keys.lastPlanJson(userId)] }
    fun getSavedPlanTimestamp(userId: String): Flow<Long> = context.dataStore.data.map { it[Keys.lastPlanTimestamp(userId)] ?: 0L }

    suspend fun savePlanJson(userId: String, json: String, timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.lastPlanJson(userId)] = json
            preferences[Keys.lastPlanTimestamp(userId)] = timestamp
        }
    }

    // Grocery Persistence Logic
    fun getGroceryJson(userId: String): Flow<String?> = context.dataStore.data.map { it[Keys.groceryJson(userId)] }
    suspend fun saveGroceryJson(userId: String, json: String) {
        context.dataStore.edit { it[Keys.groceryJson(userId)] = json }
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
}
