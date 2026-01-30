package com.pcosina.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pcosina.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val NAME = stringPreferencesKey("user_name")
        val AGE = intPreferencesKey("user_age")
        val WEIGHT = intPreferencesKey("user_weight")
        val HEIGHT = intPreferencesKey("user_height")
        val ACTIVITY = stringPreferencesKey("user_activity")
        val GOAL = stringPreferencesKey("user_goal")
        val INSULIN = stringPreferencesKey("user_insulin")
        val SYMPTOMS = stringPreferencesKey("user_symptoms")
        val COMORBIDITIES = stringPreferencesKey("user_comorbidities")
        val RESTRICTIONS = stringPreferencesKey("user_restrictions")
        val BUDGET = intPreferencesKey("user_budget")
    }

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }.map { preferences ->
            UserProfile(
                displayName = preferences[PreferencesKeys.NAME] ?: "Maria",
                age = preferences[PreferencesKeys.AGE] ?: 25,
                weightKg = preferences[PreferencesKeys.WEIGHT] ?: 65,
                heightCm = preferences[PreferencesKeys.HEIGHT] ?: 160,
                activityLevel = preferences[PreferencesKeys.ACTIVITY] ?: "Lightly Active",
                goal = preferences[PreferencesKeys.GOAL] ?: "Support PCOS symptom management",
                insulinResistanceLevel = preferences[PreferencesKeys.INSULIN] ?: "Mild",
                symptoms = preferences[PreferencesKeys.SYMPTOMS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                comorbidities = preferences[PreferencesKeys.COMORBIDITIES]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                dietaryRestrictions = preferences[PreferencesKeys.RESTRICTIONS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList(),
                weeklyBudgetPhp = preferences[PreferencesKeys.BUDGET] ?: 2000
            )
        }

    suspend fun updateProfile(profile: UserProfile) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAME] = profile.displayName
            preferences[PreferencesKeys.AGE] = profile.age
            preferences[PreferencesKeys.WEIGHT] = profile.weightKg
            preferences[PreferencesKeys.HEIGHT] = profile.heightCm
            preferences[PreferencesKeys.ACTIVITY] = profile.activityLevel
            preferences[PreferencesKeys.GOAL] = profile.goal
            preferences[PreferencesKeys.INSULIN] = profile.insulinResistanceLevel
            preferences[PreferencesKeys.SYMPTOMS] = profile.symptoms.joinToString(",")
            preferences[PreferencesKeys.COMORBIDITIES] = profile.comorbidities.joinToString(",")
            preferences[PreferencesKeys.RESTRICTIONS] = profile.dietaryRestrictions.joinToString(",")
            preferences[PreferencesKeys.BUDGET] = profile.weeklyBudgetPhp
        }
    }
}
