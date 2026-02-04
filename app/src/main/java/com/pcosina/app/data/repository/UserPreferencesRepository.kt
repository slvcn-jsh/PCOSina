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
        // Task #1: Persistent Grocery Storage
        fun groceryJson(email: String) = stringPreferencesKey("grocery_json_$email")
    }

    fun getUserProfile(email: String): Flow<UserProfile> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }.map { preferences ->
            UserProfile(
                displayName = preferences[Keys.name(email)] ?: "",
                age = preferences[Keys.age(email)] ?: 0,
                weightKg = preferences[Keys.weight(email)] ?: 0,
                heightCm = preferences[Keys.height(email)] ?: 0,
                activityLevel = preferences[Keys.activity(email)] ?: "Lightly Active",
                goal = preferences[Keys.goal(email)] ?: "Support PCOS symptom management",
                insulinResistanceLevel = preferences[Keys.insulin(email)] ?: "Mild",
                symptoms = preferences[Keys.symptoms(email)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                comorbidities = preferences[Keys.comorbidities(email)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                dietaryRestrictions = preferences[Keys.restrictions(email)]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                weeklyBudgetPhp = preferences[Keys.budget(email)] ?: 2000,
                isProfileCompleted = preferences[Keys.completed(email)] ?: false
            )
        }

    suspend fun updateProfile(email: String, profile: UserProfile) {
        context.dataStore.edit { preferences ->
            preferences[Keys.name(email)] = profile.displayName
            preferences[Keys.age(email)] = profile.age
            preferences[Keys.weight(email)] = profile.weightKg
            preferences[Keys.height(email)] = profile.heightCm
            preferences[Keys.activity(email)] = profile.activityLevel
            preferences[Keys.goal(email)] = profile.goal
            preferences[Keys.insulin(email)] = profile.insulinResistanceLevel
            preferences[Keys.symptoms(email)] = profile.symptoms.joinToString(",")
            preferences[Keys.comorbidities(email)] = profile.comorbidities.joinToString(",")
            preferences[Keys.restrictions(email)] = profile.dietaryRestrictions.joinToString(",")
            preferences[Keys.budget(email)] = profile.weeklyBudgetPhp
            preferences[Keys.completed(email)] = profile.isProfileCompleted
        }
    }

    fun getSavedPlanJson(email: String): Flow<String?> = context.dataStore.data.map { it[Keys.lastPlanJson(email)] }
    fun getSavedPlanTimestamp(email: String): Flow<Long> = context.dataStore.data.map { it[Keys.lastPlanTimestamp(email)] ?: 0L }

    suspend fun savePlanJson(email: String, json: String, timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[Keys.lastPlanJson(email)] = json
            preferences[Keys.lastPlanTimestamp(email)] = timestamp
        }
    }

    // Grocery Persistence Logic
    fun getGroceryJson(email: String): Flow<String?> = context.dataStore.data.map { it[Keys.groceryJson(email)] }
    suspend fun saveGroceryJson(email: String, json: String) {
        context.dataStore.edit { it[Keys.groceryJson(email)] = json }
    }
}
