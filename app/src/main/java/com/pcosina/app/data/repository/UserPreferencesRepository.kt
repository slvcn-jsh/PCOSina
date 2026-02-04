package com.pcosina.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
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
}
