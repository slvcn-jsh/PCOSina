package com.pcosina.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.NotificationLogEntry
import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.util.safeUserLogScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.time.LocalDate

// Local preferences cache; per-user isolation is handled by key prefixes (userId/email).
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * Local-first persistence for profile, plans, and cached groceries.
 * User profile data is also synced to Firestore to survive reinstall/new-device scenarios.
 */
class UserPreferencesRepository(private val context: Context) {
    private val gson = Gson()
    private val reflectionStore by lazy { ReflectionStore(context) }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val pantryType = object : TypeToken<List<PantryEntry>>() {}.type
    private val notificationLogsType = object : TypeToken<List<NotificationLogEntry>>() {}.type
    private val notificationLastFiredType = object : TypeToken<Map<String, Long>>() {}.type
    private val dailyLogsType = object : TypeToken<List<DailyLog>>() {}.type
    private object Cloud {
        const val profileCollection = "profiles"
        const val updatedAtEpochMs = "updatedAtEpochMs"
        const val artifactsUpdatedAtEpochMs = "artifactsUpdatedAtEpochMs"
        const val syncTimeoutMs = 3000L
        const val timestampSkewMs = 1000L

        // Cloud payload keys for local-first artifacts.
        const val pantryEntriesJson = "pantryEntriesJson"
        const val lastPlanJson = "lastPlanJson"
        const val lastPlanTimestamp = "lastPlanTimestamp"
        const val planHistoryJson = "planHistoryJson"
        const val activePlanId = "activePlanId"
        const val groceryJson = "groceryJson"
        const val grocerySourcesJson = "grocerySourcesJson"
        const val grocerySnapshotsJson = "grocerySnapshotsJson"
        const val dailyLogsJson = "dailyLogsJson"
        const val feedbackQueueJson = "feedbackQueueJson"
        const val planFeedbackTagsCsv = "planFeedbackTagsCsv"
        const val lastReviewedWeek = "lastReviewedWeek"
        const val progressMode = "progressMode"
        const val progressAdvancedAnalyticsExpanded = "progressAdvancedAnalyticsExpanded"
        const val remindersEnabled = "remindersEnabled"
        const val notificationMaster = "notificationMaster"
        const val notificationMeals = "notificationMeals"
        const val notificationPlanReady = "notificationPlanReady"
        const val notificationGrocerySync = "notificationGrocerySync"
        const val notificationWeeklyReset = "notificationWeeklyReset"
        const val notificationWeeklyResetDay = "notificationWeeklyResetDay"
        const val notificationWeeklyResetHour = "notificationWeeklyResetHour"
        const val notificationWeeklyResetMinute = "notificationWeeklyResetMinute"
        const val notificationStreak = "notificationStreak"
        const val notificationInactivity = "notificationInactivity"
        const val notificationBreakfastHour = "notificationBreakfastHour"
        const val notificationBreakfastMinute = "notificationBreakfastMinute"
        const val notificationLunchHour = "notificationLunchHour"
        const val notificationLunchMinute = "notificationLunchMinute"
        const val notificationDinnerHour = "notificationDinnerHour"
        const val notificationDinnerMinute = "notificationDinnerMinute"
        const val notificationQuietEnabled = "notificationQuietEnabled"
        const val notificationQuietStartHour = "notificationQuietStartHour"
        const val notificationQuietStartMinute = "notificationQuietStartMinute"
        const val notificationQuietEndHour = "notificationQuietEndHour"
        const val notificationQuietEndMinute = "notificationQuietEndMinute"
        const val notificationLogsJson = "notificationLogsJson"
        const val notificationLastFiredJson = "notificationLastFiredJson"
        const val weeklyJournalMap = "weeklyJournalMap"
    }

    private object Keys {
        val adminMode = booleanPreferencesKey("admin_mode")
        val profileCompletionMigrationDone = booleanPreferencesKey("profile_completion_key_migration_done_v1")
        fun profileUpdatedAt(userId: String) = longPreferencesKey("profile_updated_at_$userId")
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
        fun profileCompleted(userId: String) = booleanPreferencesKey("profile_complete_$userId")
        // Keep this persisted key for backward compatibility only.
        fun profileCompletedLegacyAlias(userId: String) = booleanPreferencesKey("onboarding_complete_$userId")
        fun lastPlanJson(userId: String) = stringPreferencesKey("last_plan_json_$userId")
        fun lastPlanTimestamp(userId: String) = longPreferencesKey("last_plan_timestamp_$userId")
        fun planHistoryJson(userId: String) = stringPreferencesKey("plan_history_json_$userId")
        fun activePlanId(userId: String) = stringPreferencesKey("active_plan_id_$userId")
        // Task #1: Persistent Grocery Storage
        fun groceryJson(userId: String) = stringPreferencesKey("grocery_json_$userId")
        fun grocerySourcesJson(userId: String) = stringPreferencesKey("grocery_sources_json_$userId")
        fun grocerySnapshotsJson(userId: String) = stringPreferencesKey("grocery_snapshots_json_$userId")
        fun migrationLogged(userId: String) = booleanPreferencesKey("migration_logged_$userId")
        fun cloudArtifactsUpdatedAt(userId: String) = longPreferencesKey("cloud_artifacts_updated_at_$userId")
        fun dailyLogsJson(userId: String) = stringPreferencesKey("daily_logs_json_$userId")
        fun feedbackQueueJson(userId: String) = stringPreferencesKey("feedback_queue_json_$userId")
        fun weeklyJournal(userId: String, weekStart: String) = stringPreferencesKey("weekly_journal_${userId}_$weekStart")
        fun planFeedbackTags(userId: String) = stringPreferencesKey("plan_feedback_tags_$userId")
        fun remindersEnabled(userId: String) = booleanPreferencesKey("reminders_enabled_$userId")
        fun lastReviewedWeek(userId: String) = stringPreferencesKey("last_reviewed_week_$userId")
        fun progressMode(userId: String) = stringPreferencesKey("progress_mode_$userId")
        fun progressAdvancedAnalyticsExpanded(userId: String) =
            booleanPreferencesKey("progress_advanced_analytics_expanded_$userId")
        fun notificationMaster(userId: String) = booleanPreferencesKey("notif_master_$userId")
        fun notificationMeals(userId: String) = booleanPreferencesKey("notif_meals_$userId")
        fun notificationPlanReady(userId: String) = booleanPreferencesKey("notif_plan_ready_$userId")
        fun notificationGrocerySync(userId: String) = booleanPreferencesKey("notif_grocery_sync_$userId")
        fun notificationWeeklyReset(userId: String) = booleanPreferencesKey("notif_weekly_reset_$userId")
        fun notificationWeeklyResetDay(userId: String) = intPreferencesKey("notif_weekly_reset_day_$userId")
        fun notificationWeeklyResetHour(userId: String) = intPreferencesKey("notif_weekly_reset_hour_$userId")
        fun notificationWeeklyResetMinute(userId: String) = intPreferencesKey("notif_weekly_reset_minute_$userId")
        fun notificationStreak(userId: String) = booleanPreferencesKey("notif_streak_$userId")
        fun notificationInactivity(userId: String) = booleanPreferencesKey("notif_inactivity_$userId")
        fun notificationBreakfastHour(userId: String) = intPreferencesKey("notif_breakfast_hour_$userId")
        fun notificationBreakfastMinute(userId: String) = intPreferencesKey("notif_breakfast_minute_$userId")
        fun notificationLunchHour(userId: String) = intPreferencesKey("notif_lunch_hour_$userId")
        fun notificationLunchMinute(userId: String) = intPreferencesKey("notif_lunch_minute_$userId")
        fun notificationDinnerHour(userId: String) = intPreferencesKey("notif_dinner_hour_$userId")
        fun notificationDinnerMinute(userId: String) = intPreferencesKey("notif_dinner_minute_$userId")
        fun notificationQuietEnabled(userId: String) = booleanPreferencesKey("notif_quiet_enabled_$userId")
        fun notificationQuietStartHour(userId: String) = intPreferencesKey("notif_quiet_start_hour_$userId")
        fun notificationQuietStartMinute(userId: String) = intPreferencesKey("notif_quiet_start_minute_$userId")
        fun notificationQuietEndHour(userId: String) = intPreferencesKey("notif_quiet_end_hour_$userId")
        fun notificationQuietEndMinute(userId: String) = intPreferencesKey("notif_quiet_end_minute_$userId")
        fun notificationLogs(userId: String) = stringPreferencesKey("notif_logs_$userId")
        fun notificationLastFiredJson(userId: String) = stringPreferencesKey("notif_last_fired_map_$userId")
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
        fun profileCompleted(email: String) = booleanPreferencesKey("profile_complete_$email")
        // Keep this persisted key for backward compatibility only.
        fun profileCompletedLegacyAlias(email: String) = booleanPreferencesKey("onboarding_complete_$email")
        fun lastPlanJson(email: String) = stringPreferencesKey("last_plan_json_$email")
        fun lastPlanTimestamp(email: String) = longPreferencesKey("last_plan_timestamp_$email")
        fun groceryJson(email: String) = stringPreferencesKey("grocery_json_$email")
    }

    private object SecureArtifacts {
        const val pantryEntries = "pantry_entries"
        const val lastPlanJson = "last_plan_json"
        const val planHistoryJson = "plan_history_json"
        const val groceryJson = "grocery_json"
        const val grocerySourcesJson = "grocery_sources_json"
        const val grocerySnapshotsJson = "grocery_snapshots_json"
        const val feedbackQueueJson = "feedback_queue_json"
    }

    private fun secureArtifactOrLegacy(userId: String, secureName: String, legacyValue: String?): String? {
        return reflectionStore.getArtifactJson(userId, secureName) ?: legacyValue
    }

    private fun writeSecureArtifact(userId: String, secureName: String, value: String?) {
        reflectionStore.saveArtifactJson(userId, secureName, value)
    }

    suspend fun migrateAllProfileCompletionAliases() {
        context.dataStore.edit { preferences ->
            if (preferences[Keys.profileCompletionMigrationDone] == true) return@edit
            val legacyPrefix = "onboarding_complete_"
            val newPrefix = "profile_complete_"
            val legacyKeyNames = preferences.asMap().keys
                .map { key -> key.name }
                .filter { keyName -> keyName.startsWith(legacyPrefix) }
            var migratedCount = 0

            legacyKeyNames.forEach { legacyName ->
                val suffix = legacyName.removePrefix(legacyPrefix)
                if (suffix.isBlank()) return@forEach
                val legacyKey = booleanPreferencesKey(legacyName)
                val legacyValue = preferences[legacyKey] ?: return@forEach
                val newKey = booleanPreferencesKey("$newPrefix$suffix")
                if (!preferences.contains(newKey)) {
                    preferences[newKey] = legacyValue
                }
                preferences.remove(legacyKey)
                migratedCount += 1
            }
            preferences[Keys.profileCompletionMigrationDone] = true
            if (migratedCount > 0) {
                Log.i("PCOSINA", "Migrated $migratedCount legacy completion keys to profile_complete_*")
            }
        }
    }

    suspend fun migrateFromEmailIfNeeded(userId: String, email: String) {
        if (userId.isBlank() || email.isBlank()) return
        context.dataStore.edit { preferences ->
            val hasUidData = preferences.contains(Keys.profileCompleted(userId)) ||
                preferences.contains(Keys.profileCompletedLegacyAlias(userId)) ||
                preferences.contains(Keys.name(userId)) ||
                preferences.contains(Keys.lastPlanJson(userId)) ||
                preferences.contains(Keys.groceryJson(userId))

            if (hasUidData) return@edit

            val hasLegacyData = preferences.contains(LegacyKeys.profileCompleted(email)) ||
                preferences.contains(LegacyKeys.profileCompletedLegacyAlias(email)) ||
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
            preferences[Keys.goal(userId)] = preferences[LegacyKeys.goal(email)] ?: ""
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
            val legacyProfileCompleted = preferences[LegacyKeys.profileCompleted(email)]
            val legacyProfileCompletedAlias = preferences[LegacyKeys.profileCompletedLegacyAlias(email)]
            preferences[Keys.profileCompleted(userId)] = legacyProfileCompleted ?: legacyProfileCompletedAlias ?: false
            preferences.remove(Keys.profileCompletedLegacyAlias(userId))

            preferences[Keys.lastPlanJson(userId)] = preferences[LegacyKeys.lastPlanJson(email)] ?: ""
            preferences[Keys.lastPlanTimestamp(userId)] = preferences[LegacyKeys.lastPlanTimestamp(email)] ?: 0L

            preferences[Keys.groceryJson(userId)] = preferences[LegacyKeys.groceryJson(email)] ?: ""
            preferences[Keys.profileUpdatedAt(userId)] = System.currentTimeMillis()

            if (preferences[Keys.migrationLogged(userId)] != true) {
                Log.i("PCOSINA", "Migrated legacy email data to UID for ${safeUserLogScope(userId)}")
                preferences[Keys.migrationLogged(userId)] = true
            }
        }
    }

    suspend fun migrateProfileCompletionKeyIfNeeded(userId: String) {
        if (userId.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[Keys.profileCompleted(userId)]
            val legacyAlias = preferences[Keys.profileCompletedLegacyAlias(userId)]
            when {
                current == null && legacyAlias != null -> {
                    preferences[Keys.profileCompleted(userId)] = legacyAlias
                    preferences.remove(Keys.profileCompletedLegacyAlias(userId))
                }
                current != null && legacyAlias != null -> {
                    preferences.remove(Keys.profileCompletedLegacyAlias(userId))
                }
            }
        }
    }

    suspend fun countLegacyProfileCompletionAliases(): Int {
        val prefs = context.dataStore.data.first()
        return prefs.asMap().keys.count { it.name.startsWith("onboarding_complete_") }
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
                goal = preferences[Keys.goal(userId)] ?: "",
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
                isProfileCompleted = preferences[Keys.profileCompleted(userId)]
                    ?: preferences[Keys.profileCompletedLegacyAlias(userId)]
                    ?: false
            )
        }

    suspend fun syncProfileWithCloud(userId: String) {
        if (userId.isBlank()) return
        try {
            val localPreferences = context.dataStore.data.first()
            val localProfile = userProfileFromPreferences(localPreferences, userId)
            val localUpdatedAt = localPreferences[Keys.profileUpdatedAt(userId)] ?: 0L

            val snapshot = withTimeoutOrNull(Cloud.syncTimeoutMs) {
                firestore.collection(Cloud.profileCollection).document(userId).get().await()
            } ?: return

            if (!snapshot.exists()) {
                if (hasMeaningfulProfileData(localProfile)) {
                    val now = if (localUpdatedAt > 0L) localUpdatedAt else System.currentTimeMillis()
                    syncProfileToCloud(userId, localProfile, now)
                }
                if (hasMeaningfulArtifactData(localPreferences, userId)) {
                    val artifactsNow = localPreferences[Keys.cloudArtifactsUpdatedAt(userId)] ?: System.currentTimeMillis()
                    syncArtifactsToCloud(userId, artifactsNow)
                }
                return
            }

            val data = snapshot.data ?: emptyMap()
            val remoteProfile = userProfileFromCloudData(data)
            val remoteUpdatedAt = (data[Cloud.updatedAtEpochMs] as? Number)?.toLong() ?: 0L
            val remoteHasData = hasMeaningfulProfileData(remoteProfile)
            val localHasData = hasMeaningfulProfileData(localProfile)
            val now = System.currentTimeMillis()

            when {
                remoteHasData && (!localHasData || remoteUpdatedAt > localUpdatedAt + Cloud.timestampSkewMs) -> {
                    updateProfileLocalOnly(
                        userId = userId,
                        profile = remoteProfile,
                        updatedAtMs = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now
                    )
                }
                localHasData && (!remoteHasData || localUpdatedAt > remoteUpdatedAt + Cloud.timestampSkewMs) -> {
                    val effectiveUpdatedAt = if (localUpdatedAt > 0L) localUpdatedAt else now
                    syncProfileToCloud(userId, localProfile, effectiveUpdatedAt)
                }
                remoteHasData && localHasData && localUpdatedAt == 0L -> {
                    updateProfileLocalOnly(
                        userId = userId,
                        profile = remoteProfile,
                        updatedAtMs = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now
                    )
                }
            }

            // Keep non-profile artifacts (plans, pantry entries, groceries, logs) in sync too.
            syncArtifactsWithCloud(userId)
        } catch (e: Exception) {
            Log.w("PCOSINA", "Cloud profile sync skipped for ${safeUserLogScope(userId)}: ${e.message}")
        }
    }

    suspend fun updateProfile(userId: String, profile: UserProfile) {
        val now = System.currentTimeMillis()
        updateProfileLocalOnly(userId, profile, now)
        syncProfileToCloud(userId, profile, now)
    }

    private suspend fun updateProfileLocalOnly(userId: String, profile: UserProfile, updatedAtMs: Long) {
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
            preferences[Keys.profileCompleted(userId)] = profile.isProfileCompleted
            preferences[Keys.profileUpdatedAt(userId)] = updatedAtMs
            preferences.remove(Keys.profileCompletedLegacyAlias(userId))
        }
    }

    private suspend fun syncProfileToCloud(userId: String, profile: UserProfile, updatedAtMs: Long) {
        if (userId.isBlank()) return
        try {
            val payload = mutableMapOf<String, Any>(
                "displayName" to profile.displayName,
                "age" to profile.age,
                "heightCm" to profile.heightCm,
                "weightKg" to profile.weightKg,
                "heightUnit" to profile.heightUnit,
                "weightUnit" to profile.weightUnit,
                "activityLevel" to profile.activityLevel,
                "goal" to profile.goal,
                "insulinResistanceLevel" to profile.insulinResistanceLevel,
                "symptoms" to profile.symptoms,
                "comorbidities" to profile.comorbidities,
                "dietaryRestrictions" to profile.dietaryRestrictions,
                "allergies" to profile.allergies,
                "weeklyBudgetPhp" to profile.weeklyBudgetPhp,
                "maxCookingTimeMinutes" to profile.maxCookingTimeMinutes,
                "varietyPreference" to profile.varietyPreference,
                "planningPriority" to profile.planningPriority,
                "pantryItems" to profile.pantryItems,
                "isProfileCompleted" to profile.isProfileCompleted,
                Cloud.updatedAtEpochMs to updatedAtMs
            )
            withTimeoutOrNull(Cloud.syncTimeoutMs) {
                firestore.collection(Cloud.profileCollection)
                    .document(userId)
                    .set(payload, SetOptions.merge())
                    .await()
            }
        } catch (e: Exception) {
            Log.w("PCOSINA", "Cloud profile upload skipped for ${safeUserLogScope(userId)}: ${e.message}")
        }
    }

    private fun userProfileFromPreferences(preferences: Preferences, userId: String): UserProfile =
        UserProfile(
            displayName = preferences[Keys.name(userId)] ?: "",
            age = preferences[Keys.age(userId)] ?: 0,
            weightKg = preferences[Keys.weight(userId)] ?: 0,
            heightCm = preferences[Keys.height(userId)] ?: 0,
            weightUnit = preferences[Keys.weightUnit(userId)] ?: "kg",
            heightUnit = preferences[Keys.heightUnit(userId)] ?: "cm",
            activityLevel = preferences[Keys.activity(userId)] ?: "Lightly Active",
            goal = preferences[Keys.goal(userId)] ?: "",
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
            isProfileCompleted = preferences[Keys.profileCompleted(userId)]
                ?: preferences[Keys.profileCompletedLegacyAlias(userId)]
                ?: false
        )

    private fun userProfileFromCloudData(data: Map<String, Any>): UserProfile {
        fun readString(key: String, fallback: String): String =
            (data[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
        fun readInt(key: String, fallback: Int): Int =
            (data[key] as? Number)?.toInt() ?: fallback
        fun readBool(key: String, fallback: Boolean): Boolean =
            (data[key] as? Boolean) ?: fallback
        fun readStringList(key: String): List<String> =
            (data[key] as? List<*>)?.mapNotNull { it?.toString()?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        return UserProfile(
            displayName = readString("displayName", ""),
            age = readInt("age", 0),
            heightCm = readInt("heightCm", 0),
            weightKg = readInt("weightKg", 0),
            heightUnit = readString("heightUnit", "cm"),
            weightUnit = readString("weightUnit", "kg"),
            activityLevel = readString("activityLevel", "Lightly Active"),
            goal = readString("goal", ""),
            insulinResistanceLevel = readString("insulinResistanceLevel", "Mild"),
            symptoms = readStringList("symptoms"),
            comorbidities = readStringList("comorbidities"),
            dietaryRestrictions = readStringList("dietaryRestrictions"),
            allergies = readStringList("allergies"),
            weeklyBudgetPhp = readInt("weeklyBudgetPhp", 0),
            maxCookingTimeMinutes = readInt("maxCookingTimeMinutes", 45),
            varietyPreference = readString("varietyPreference", "Balanced"),
            planningPriority = readString("planningPriority", "Balanced"),
            pantryItems = readStringList("pantryItems"),
            isProfileCompleted = readBool("isProfileCompleted", false)
        )
    }

    private fun hasMeaningfulProfileData(profile: UserProfile): Boolean {
        return profile.displayName.isNotBlank() ||
            profile.age > 0 ||
            profile.heightCm > 0 ||
            profile.weightKg > 0 ||
            profile.goal.isNotBlank() ||
            profile.symptoms.isNotEmpty() ||
            profile.comorbidities.isNotEmpty() ||
            profile.dietaryRestrictions.isNotEmpty() ||
            profile.isProfileCompleted
    }

    private fun weeklyJournalPrefix(userId: String) = "weekly_journal_${userId}_"

    private fun readCloudString(data: Map<String, Any>, key: String): String =
        (data[key] as? String).orEmpty()

    private fun readCloudLong(data: Map<String, Any>, key: String, fallback: Long = 0L): Long =
        (data[key] as? Number)?.toLong() ?: fallback

    private fun readCloudInt(data: Map<String, Any>, key: String, fallback: Int = 0): Int =
        (data[key] as? Number)?.toInt() ?: fallback

    private fun readCloudBool(data: Map<String, Any>, key: String, fallback: Boolean = false): Boolean =
        (data[key] as? Boolean) ?: fallback

    private fun hasMeaningfulArtifactData(preferences: Preferences, userId: String): Boolean {
        val hasWeeklyJournal = reflectionStore.getAllWeeklyJournals(userId).isNotEmpty() ||
            preferences.asMap().keys.any { it.name.startsWith(weeklyJournalPrefix(userId)) }
        val hasSyncableSettings = preferences.contains(Keys.progressMode(userId)) ||
            preferences.contains(Keys.progressAdvancedAnalyticsExpanded(userId)) ||
            preferences.contains(Keys.remindersEnabled(userId)) ||
            preferences.contains(Keys.notificationMaster(userId)) ||
            preferences.contains(Keys.notificationMeals(userId)) ||
            preferences.contains(Keys.notificationPlanReady(userId)) ||
            preferences.contains(Keys.notificationGrocerySync(userId)) ||
            preferences.contains(Keys.notificationWeeklyReset(userId)) ||
            preferences.contains(Keys.notificationWeeklyResetDay(userId)) ||
            preferences.contains(Keys.notificationWeeklyResetHour(userId)) ||
            preferences.contains(Keys.notificationWeeklyResetMinute(userId)) ||
            preferences.contains(Keys.notificationStreak(userId)) ||
            preferences.contains(Keys.notificationInactivity(userId)) ||
            preferences.contains(Keys.notificationBreakfastHour(userId)) ||
            preferences.contains(Keys.notificationBreakfastMinute(userId)) ||
            preferences.contains(Keys.notificationLunchHour(userId)) ||
            preferences.contains(Keys.notificationLunchMinute(userId)) ||
            preferences.contains(Keys.notificationDinnerHour(userId)) ||
            preferences.contains(Keys.notificationDinnerMinute(userId)) ||
            preferences.contains(Keys.notificationQuietEnabled(userId)) ||
            preferences.contains(Keys.notificationQuietStartHour(userId)) ||
            preferences.contains(Keys.notificationQuietStartMinute(userId)) ||
            preferences.contains(Keys.notificationQuietEndHour(userId)) ||
            preferences.contains(Keys.notificationQuietEndMinute(userId)) ||
            preferences.contains(Keys.notificationLogs(userId)) ||
            preferences.contains(Keys.notificationLastFiredJson(userId))
        return !secureArtifactOrLegacy(userId, SecureArtifacts.pantryEntries, preferences[Keys.pantryEntries(userId)]).isNullOrBlank() ||
            !secureArtifactOrLegacy(userId, SecureArtifacts.lastPlanJson, preferences[Keys.lastPlanJson(userId)]).isNullOrBlank() ||
            !secureArtifactOrLegacy(userId, SecureArtifacts.planHistoryJson, preferences[Keys.planHistoryJson(userId)]).isNullOrBlank() ||
            !preferences[Keys.activePlanId(userId)].isNullOrBlank() ||
            (preferences[Keys.lastPlanTimestamp(userId)] ?: 0L) > 0L ||
            !secureArtifactOrLegacy(userId, SecureArtifacts.groceryJson, preferences[Keys.groceryJson(userId)]).isNullOrBlank() ||
            !secureArtifactOrLegacy(userId, SecureArtifacts.grocerySourcesJson, preferences[Keys.grocerySourcesJson(userId)]).isNullOrBlank() ||
            !secureArtifactOrLegacy(userId, SecureArtifacts.grocerySnapshotsJson, preferences[Keys.grocerySnapshotsJson(userId)]).isNullOrBlank() ||
            !(reflectionStore.getDailyLogsJson(userId) ?: preferences[Keys.dailyLogsJson(userId)]).isNullOrBlank() ||
            !secureArtifactOrLegacy(userId, SecureArtifacts.feedbackQueueJson, preferences[Keys.feedbackQueueJson(userId)]).isNullOrBlank() ||
            !preferences[Keys.planFeedbackTags(userId)].isNullOrBlank() ||
            !preferences[Keys.lastReviewedWeek(userId)].isNullOrBlank() ||
            hasSyncableSettings ||
            hasWeeklyJournal
    }

    private fun hasMeaningfulArtifactData(data: Map<String, Any>): Boolean {
        val weekly = data[Cloud.weeklyJournalMap] as? Map<*, *>
        val hasSyncableSettings = data.containsKey(Cloud.progressMode) ||
            data.containsKey(Cloud.progressAdvancedAnalyticsExpanded) ||
            data.containsKey(Cloud.remindersEnabled) ||
            data.containsKey(Cloud.notificationMaster) ||
            data.containsKey(Cloud.notificationMeals) ||
            data.containsKey(Cloud.notificationPlanReady) ||
            data.containsKey(Cloud.notificationGrocerySync) ||
            data.containsKey(Cloud.notificationWeeklyReset) ||
            data.containsKey(Cloud.notificationWeeklyResetDay) ||
            data.containsKey(Cloud.notificationWeeklyResetHour) ||
            data.containsKey(Cloud.notificationWeeklyResetMinute) ||
            data.containsKey(Cloud.notificationStreak) ||
            data.containsKey(Cloud.notificationInactivity) ||
            data.containsKey(Cloud.notificationBreakfastHour) ||
            data.containsKey(Cloud.notificationBreakfastMinute) ||
            data.containsKey(Cloud.notificationLunchHour) ||
            data.containsKey(Cloud.notificationLunchMinute) ||
            data.containsKey(Cloud.notificationDinnerHour) ||
            data.containsKey(Cloud.notificationDinnerMinute) ||
            data.containsKey(Cloud.notificationQuietEnabled) ||
            data.containsKey(Cloud.notificationQuietStartHour) ||
            data.containsKey(Cloud.notificationQuietStartMinute) ||
            data.containsKey(Cloud.notificationQuietEndHour) ||
            data.containsKey(Cloud.notificationQuietEndMinute) ||
            data.containsKey(Cloud.notificationLogsJson) ||
            data.containsKey(Cloud.notificationLastFiredJson)
        return readCloudString(data, Cloud.pantryEntriesJson).isNotBlank() ||
            readCloudString(data, Cloud.lastPlanJson).isNotBlank() ||
            readCloudString(data, Cloud.planHistoryJson).isNotBlank() ||
            readCloudString(data, Cloud.activePlanId).isNotBlank() ||
            readCloudLong(data, Cloud.lastPlanTimestamp) > 0L ||
            readCloudString(data, Cloud.groceryJson).isNotBlank() ||
            readCloudString(data, Cloud.grocerySourcesJson).isNotBlank() ||
            readCloudString(data, Cloud.grocerySnapshotsJson).isNotBlank() ||
            readCloudString(data, Cloud.dailyLogsJson).isNotBlank() ||
            readCloudString(data, Cloud.feedbackQueueJson).isNotBlank() ||
            readCloudString(data, Cloud.planFeedbackTagsCsv).isNotBlank() ||
            readCloudString(data, Cloud.lastReviewedWeek).isNotBlank() ||
            hasSyncableSettings ||
            !weekly.isNullOrEmpty()
    }

    private fun buildArtifactPayload(preferences: Preferences, userId: String, updatedAtMs: Long): Map<String, Any> {
        val weeklyMap = reflectionStore.getAllWeeklyJournals(userId).ifEmpty {
            val weeklyPrefix = weeklyJournalPrefix(userId)
            preferences.asMap().entries
                .mapNotNull { (key, value) ->
                    val name = key.name
                    if (!name.startsWith(weeklyPrefix)) return@mapNotNull null
                    val weekStart = name.removePrefix(weeklyPrefix)
                    val text = value as? String ?: return@mapNotNull null
                    weekStart to text
                }
                .toMap()
        }

        return mapOf(
            Cloud.artifactsUpdatedAtEpochMs to updatedAtMs,
            Cloud.pantryEntriesJson to (secureArtifactOrLegacy(userId, SecureArtifacts.pantryEntries, preferences[Keys.pantryEntries(userId)]) ?: ""),
            Cloud.lastPlanJson to (secureArtifactOrLegacy(userId, SecureArtifacts.lastPlanJson, preferences[Keys.lastPlanJson(userId)]) ?: ""),
            Cloud.lastPlanTimestamp to (preferences[Keys.lastPlanTimestamp(userId)] ?: 0L),
            Cloud.planHistoryJson to (secureArtifactOrLegacy(userId, SecureArtifacts.planHistoryJson, preferences[Keys.planHistoryJson(userId)]) ?: ""),
            Cloud.activePlanId to (preferences[Keys.activePlanId(userId)] ?: ""),
            Cloud.groceryJson to (secureArtifactOrLegacy(userId, SecureArtifacts.groceryJson, preferences[Keys.groceryJson(userId)]) ?: ""),
            Cloud.grocerySourcesJson to (secureArtifactOrLegacy(userId, SecureArtifacts.grocerySourcesJson, preferences[Keys.grocerySourcesJson(userId)]) ?: ""),
            Cloud.grocerySnapshotsJson to (secureArtifactOrLegacy(userId, SecureArtifacts.grocerySnapshotsJson, preferences[Keys.grocerySnapshotsJson(userId)]) ?: ""),
            Cloud.dailyLogsJson to ((reflectionStore.getDailyLogsJson(userId) ?: preferences[Keys.dailyLogsJson(userId)]) ?: ""),
            Cloud.feedbackQueueJson to (secureArtifactOrLegacy(userId, SecureArtifacts.feedbackQueueJson, preferences[Keys.feedbackQueueJson(userId)]) ?: ""),
            Cloud.planFeedbackTagsCsv to (preferences[Keys.planFeedbackTags(userId)] ?: ""),
            Cloud.lastReviewedWeek to (preferences[Keys.lastReviewedWeek(userId)] ?: ""),
            Cloud.progressMode to (preferences[Keys.progressMode(userId)] ?: "Today"),
            Cloud.progressAdvancedAnalyticsExpanded to (preferences[Keys.progressAdvancedAnalyticsExpanded(userId)] ?: false),
            Cloud.remindersEnabled to (preferences[Keys.remindersEnabled(userId)] ?: false),
            Cloud.notificationMaster to (preferences[Keys.notificationMaster(userId)] ?: false),
            Cloud.notificationMeals to (preferences[Keys.notificationMeals(userId)] ?: true),
            Cloud.notificationPlanReady to (preferences[Keys.notificationPlanReady(userId)] ?: true),
            Cloud.notificationGrocerySync to (preferences[Keys.notificationGrocerySync(userId)] ?: true),
            Cloud.notificationWeeklyReset to (preferences[Keys.notificationWeeklyReset(userId)] ?: true),
            Cloud.notificationWeeklyResetDay to (preferences[Keys.notificationWeeklyResetDay(userId)] ?: 1),
            Cloud.notificationWeeklyResetHour to (preferences[Keys.notificationWeeklyResetHour(userId)] ?: 9),
            Cloud.notificationWeeklyResetMinute to (preferences[Keys.notificationWeeklyResetMinute(userId)] ?: 0),
            Cloud.notificationStreak to (preferences[Keys.notificationStreak(userId)] ?: false),
            Cloud.notificationInactivity to (preferences[Keys.notificationInactivity(userId)] ?: true),
            Cloud.notificationBreakfastHour to (preferences[Keys.notificationBreakfastHour(userId)] ?: 8),
            Cloud.notificationBreakfastMinute to (preferences[Keys.notificationBreakfastMinute(userId)] ?: 0),
            Cloud.notificationLunchHour to (preferences[Keys.notificationLunchHour(userId)] ?: 12),
            Cloud.notificationLunchMinute to (preferences[Keys.notificationLunchMinute(userId)] ?: 30),
            Cloud.notificationDinnerHour to (preferences[Keys.notificationDinnerHour(userId)] ?: 19),
            Cloud.notificationDinnerMinute to (preferences[Keys.notificationDinnerMinute(userId)] ?: 0),
            Cloud.notificationQuietEnabled to (preferences[Keys.notificationQuietEnabled(userId)] ?: false),
            Cloud.notificationQuietStartHour to (preferences[Keys.notificationQuietStartHour(userId)] ?: 22),
            Cloud.notificationQuietStartMinute to (preferences[Keys.notificationQuietStartMinute(userId)] ?: 0),
            Cloud.notificationQuietEndHour to (preferences[Keys.notificationQuietEndHour(userId)] ?: 6),
            Cloud.notificationQuietEndMinute to (preferences[Keys.notificationQuietEndMinute(userId)] ?: 30),
            Cloud.notificationLogsJson to (preferences[Keys.notificationLogs(userId)] ?: ""),
            Cloud.notificationLastFiredJson to (preferences[Keys.notificationLastFiredJson(userId)] ?: ""),
            Cloud.weeklyJournalMap to weeklyMap
        )
    }

    private suspend fun applyArtifactsLocalOnly(
        userId: String,
        data: Map<String, Any>,
        updatedAtMs: Long
    ) {
        if (data.containsKey(Cloud.pantryEntriesJson)) {
            writeSecureArtifact(userId, SecureArtifacts.pantryEntries, readCloudString(data, Cloud.pantryEntriesJson))
        }
        if (data.containsKey(Cloud.lastPlanJson)) {
            writeSecureArtifact(userId, SecureArtifacts.lastPlanJson, readCloudString(data, Cloud.lastPlanJson))
        }
        if (data.containsKey(Cloud.planHistoryJson)) {
            writeSecureArtifact(userId, SecureArtifacts.planHistoryJson, readCloudString(data, Cloud.planHistoryJson))
        }
        if (data.containsKey(Cloud.groceryJson)) {
            writeSecureArtifact(userId, SecureArtifacts.groceryJson, readCloudString(data, Cloud.groceryJson))
        }
        if (data.containsKey(Cloud.grocerySourcesJson)) {
            writeSecureArtifact(userId, SecureArtifacts.grocerySourcesJson, readCloudString(data, Cloud.grocerySourcesJson))
        }
        if (data.containsKey(Cloud.grocerySnapshotsJson)) {
            writeSecureArtifact(userId, SecureArtifacts.grocerySnapshotsJson, readCloudString(data, Cloud.grocerySnapshotsJson))
        }
        if (data.containsKey(Cloud.dailyLogsJson)) {
            reflectionStore.saveDailyLogsJson(userId, readCloudString(data, Cloud.dailyLogsJson))
        }
        if (data.containsKey(Cloud.feedbackQueueJson)) {
            writeSecureArtifact(userId, SecureArtifacts.feedbackQueueJson, readCloudString(data, Cloud.feedbackQueueJson))
        }
        if (data.containsKey(Cloud.weeklyJournalMap)) {
            val weeklyMap = (data[Cloud.weeklyJournalMap] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val weekStart = k?.toString()?.trim().orEmpty()
                if (weekStart.isBlank()) null else weekStart to v?.toString().orEmpty()
            }?.toMap().orEmpty()
            reflectionStore.replaceWeeklyJournals(userId, weeklyMap)
        }
        context.dataStore.edit { preferences ->
            fun setString(key: Preferences.Key<String>, cloudKey: String) {
                if (data.containsKey(cloudKey)) {
                    preferences[key] = readCloudString(data, cloudKey)
                }
            }
            fun setLong(key: Preferences.Key<Long>, cloudKey: String, fallback: Long = 0L) {
                if (data.containsKey(cloudKey)) {
                    preferences[key] = readCloudLong(data, cloudKey, fallback)
                }
            }
            fun setInt(key: Preferences.Key<Int>, cloudKey: String, fallback: Int = 0) {
                if (data.containsKey(cloudKey)) {
                    preferences[key] = readCloudInt(data, cloudKey, fallback)
                }
            }
            fun setBool(key: Preferences.Key<Boolean>, cloudKey: String, fallback: Boolean = false) {
                if (data.containsKey(cloudKey)) {
                    preferences[key] = readCloudBool(data, cloudKey, fallback)
                }
            }

            if (data.containsKey(Cloud.pantryEntriesJson)) preferences.remove(Keys.pantryEntries(userId))
            if (data.containsKey(Cloud.lastPlanJson)) preferences.remove(Keys.lastPlanJson(userId))
            setLong(Keys.lastPlanTimestamp(userId), Cloud.lastPlanTimestamp)
            if (data.containsKey(Cloud.planHistoryJson)) preferences.remove(Keys.planHistoryJson(userId))

            if (data.containsKey(Cloud.activePlanId)) {
                val remoteActive = readCloudString(data, Cloud.activePlanId)
                if (remoteActive.isBlank()) preferences.remove(Keys.activePlanId(userId))
                else preferences[Keys.activePlanId(userId)] = remoteActive
            }

            if (data.containsKey(Cloud.groceryJson)) preferences.remove(Keys.groceryJson(userId))
            if (data.containsKey(Cloud.grocerySourcesJson)) preferences.remove(Keys.grocerySourcesJson(userId))
            if (data.containsKey(Cloud.grocerySnapshotsJson)) preferences.remove(Keys.grocerySnapshotsJson(userId))
            if (data.containsKey(Cloud.dailyLogsJson)) preferences.remove(Keys.dailyLogsJson(userId))
            if (data.containsKey(Cloud.feedbackQueueJson)) preferences.remove(Keys.feedbackQueueJson(userId))
            setString(Keys.planFeedbackTags(userId), Cloud.planFeedbackTagsCsv)
            setString(Keys.lastReviewedWeek(userId), Cloud.lastReviewedWeek)
            setString(Keys.progressMode(userId), Cloud.progressMode)
            setBool(Keys.progressAdvancedAnalyticsExpanded(userId), Cloud.progressAdvancedAnalyticsExpanded)
            setBool(Keys.remindersEnabled(userId), Cloud.remindersEnabled)
            setBool(Keys.notificationMaster(userId), Cloud.notificationMaster)
            setBool(Keys.notificationMeals(userId), Cloud.notificationMeals, true)
            setBool(Keys.notificationPlanReady(userId), Cloud.notificationPlanReady, true)
            setBool(Keys.notificationGrocerySync(userId), Cloud.notificationGrocerySync, true)
            setBool(Keys.notificationWeeklyReset(userId), Cloud.notificationWeeklyReset, true)
            setInt(Keys.notificationWeeklyResetDay(userId), Cloud.notificationWeeklyResetDay, 1)
            setInt(Keys.notificationWeeklyResetHour(userId), Cloud.notificationWeeklyResetHour, 9)
            setInt(Keys.notificationWeeklyResetMinute(userId), Cloud.notificationWeeklyResetMinute, 0)
            setBool(Keys.notificationStreak(userId), Cloud.notificationStreak)
            setBool(Keys.notificationInactivity(userId), Cloud.notificationInactivity, true)
            setInt(Keys.notificationBreakfastHour(userId), Cloud.notificationBreakfastHour, 8)
            setInt(Keys.notificationBreakfastMinute(userId), Cloud.notificationBreakfastMinute, 0)
            setInt(Keys.notificationLunchHour(userId), Cloud.notificationLunchHour, 12)
            setInt(Keys.notificationLunchMinute(userId), Cloud.notificationLunchMinute, 30)
            setInt(Keys.notificationDinnerHour(userId), Cloud.notificationDinnerHour, 19)
            setInt(Keys.notificationDinnerMinute(userId), Cloud.notificationDinnerMinute, 0)
            setBool(Keys.notificationQuietEnabled(userId), Cloud.notificationQuietEnabled)
            setInt(Keys.notificationQuietStartHour(userId), Cloud.notificationQuietStartHour, 22)
            setInt(Keys.notificationQuietStartMinute(userId), Cloud.notificationQuietStartMinute, 0)
            setInt(Keys.notificationQuietEndHour(userId), Cloud.notificationQuietEndHour, 6)
            setInt(Keys.notificationQuietEndMinute(userId), Cloud.notificationQuietEndMinute, 30)
            setString(Keys.notificationLogs(userId), Cloud.notificationLogsJson)
            setString(Keys.notificationLastFiredJson(userId), Cloud.notificationLastFiredJson)

            if (data.containsKey(Cloud.weeklyJournalMap)) {
                val prefix = weeklyJournalPrefix(userId)
                preferences.asMap().keys
                    .filter { it.name.startsWith(prefix) }
                    .toList()
                    .forEach { preferences.remove(it) }
            }

            preferences[Keys.cloudArtifactsUpdatedAt(userId)] = updatedAtMs
        }
    }

    private suspend fun syncArtifactsToCloud(userId: String, updatedAtMs: Long = System.currentTimeMillis()) {
        if (userId.isBlank()) return
        try {
            val localPreferences = context.dataStore.data.first()
            val payload = buildArtifactPayload(localPreferences, userId, updatedAtMs)
            withTimeoutOrNull(Cloud.syncTimeoutMs) {
                firestore.collection(Cloud.profileCollection)
                    .document(userId)
                    .set(payload, SetOptions.merge())
                    .await()
            }
        } catch (e: Exception) {
            Log.w("PCOSINA", "Cloud artifact upload skipped for ${safeUserLogScope(userId)}: ${e.message}")
        }
    }

    private suspend fun syncArtifactsWithCloud(userId: String) {
        if (userId.isBlank()) return
        try {
            val localPreferences = context.dataStore.data.first()
            val localUpdatedAt = localPreferences[Keys.cloudArtifactsUpdatedAt(userId)] ?: 0L
            val localHasData = hasMeaningfulArtifactData(localPreferences, userId)

            val snapshot = withTimeoutOrNull(Cloud.syncTimeoutMs) {
                firestore.collection(Cloud.profileCollection).document(userId).get().await()
            } ?: return

            if (!snapshot.exists()) {
                if (localHasData) {
                    val now = if (localUpdatedAt > 0L) localUpdatedAt else System.currentTimeMillis()
                    syncArtifactsToCloud(userId, now)
                }
                return
            }

            val data = snapshot.data ?: emptyMap()
            val remoteUpdatedAt = (data[Cloud.artifactsUpdatedAtEpochMs] as? Number)?.toLong() ?: 0L
            val remoteHasData = hasMeaningfulArtifactData(data)
            val now = System.currentTimeMillis()

            when {
                remoteHasData && (!localHasData || remoteUpdatedAt > localUpdatedAt + Cloud.timestampSkewMs) -> {
                    applyArtifactsLocalOnly(
                        userId = userId,
                        data = data,
                        updatedAtMs = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now
                    )
                }
                localHasData && localUpdatedAt == 0L && remoteHasData -> {
                    val bootstrapTimestamp = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now
                    context.dataStore.edit { preferences ->
                        preferences[Keys.cloudArtifactsUpdatedAt(userId)] = bootstrapTimestamp
                    }
                }
                localHasData && localUpdatedAt == 0L -> {
                    syncArtifactsToCloud(userId, now)
                }
                localHasData && (!remoteHasData || localUpdatedAt > remoteUpdatedAt + Cloud.timestampSkewMs) -> {
                    val effectiveUpdatedAt = if (localUpdatedAt > 0L) localUpdatedAt else now
                    syncArtifactsToCloud(userId, effectiveUpdatedAt)
                }
            }
        } catch (e: Exception) {
            Log.w("PCOSINA", "Cloud artifact sync skipped for ${safeUserLogScope(userId)}: ${e.message}")
        }
    }

    private suspend fun editArtifactsAndSync(userId: String, mutate: (MutablePreferences) -> Unit) {
        val now = System.currentTimeMillis()
        context.dataStore.edit { preferences ->
            mutate(preferences)
            preferences[Keys.cloudArtifactsUpdatedAt(userId)] = now
        }
        syncArtifactsToCloud(userId, now)
    }

    fun getPantryEntries(userId: String): Flow<List<PantryEntry>> =
        context.dataStore.data.map { prefs ->
            val json = secureArtifactOrLegacy(userId, SecureArtifacts.pantryEntries, prefs[Keys.pantryEntries(userId)])
            if (!json.isNullOrBlank()) {
                try { gson.fromJson<List<PantryEntry>>(json, pantryType) } catch (_: Exception) { emptyList() }
            } else {
                // Fallback to legacy pantry list (names only)
                val names = prefs[Keys.pantry(userId)]?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                names.map { PantryEntry(name = it) }
            }
        }

    suspend fun savePantryEntries(userId: String, entries: List<PantryEntry>) {
        writeSecureArtifact(userId, SecureArtifacts.pantryEntries, gson.toJson(entries))
        editArtifactsAndSync(userId) { prefs ->
            prefs.remove(Keys.pantryEntries(userId))
        }
    }

    fun getAdminMode(): Flow<Boolean> = context.dataStore.data.map { it[Keys.adminMode] ?: false }

    suspend fun setAdminMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.adminMode] = enabled
        }
    }

    fun getSavedPlanJson(userId: String): Flow<String?> =
        context.dataStore.data.map { secureArtifactOrLegacy(userId, SecureArtifacts.lastPlanJson, it[Keys.lastPlanJson(userId)]) }
    fun getSavedPlanTimestamp(userId: String): Flow<Long> = context.dataStore.data.map { it[Keys.lastPlanTimestamp(userId)] ?: 0L }
    fun getPlanHistoryJson(userId: String): Flow<String?> =
        context.dataStore.data.map { secureArtifactOrLegacy(userId, SecureArtifacts.planHistoryJson, it[Keys.planHistoryJson(userId)]) }
    fun getActivePlanId(userId: String): Flow<String?> = context.dataStore.data.map { it[Keys.activePlanId(userId)] }

    suspend fun savePlanJson(userId: String, json: String, timestamp: Long) {
        writeSecureArtifact(userId, SecureArtifacts.lastPlanJson, json)
        editArtifactsAndSync(userId) { preferences ->
            preferences.remove(Keys.lastPlanJson(userId))
            preferences[Keys.lastPlanTimestamp(userId)] = timestamp
        }
    }

    suspend fun savePlanHistoryJson(userId: String, json: String) {
        writeSecureArtifact(userId, SecureArtifacts.planHistoryJson, json)
        editArtifactsAndSync(userId) { preferences ->
            preferences.remove(Keys.planHistoryJson(userId))
        }
    }

    suspend fun saveActivePlanId(userId: String, id: String?) {
        editArtifactsAndSync(userId) { preferences ->
            if (id.isNullOrBlank()) preferences.remove(Keys.activePlanId(userId))
            else preferences[Keys.activePlanId(userId)] = id
        }
    }

    // Grocery Persistence Logic
    fun getGroceryJson(userId: String): Flow<String?> =
        context.dataStore.data.map { secureArtifactOrLegacy(userId, SecureArtifacts.groceryJson, it[Keys.groceryJson(userId)]) }
    suspend fun saveGroceryJson(userId: String, json: String) {
        writeSecureArtifact(userId, SecureArtifacts.groceryJson, json)
        editArtifactsAndSync(userId) { it.remove(Keys.groceryJson(userId)) }
    }

    fun getGrocerySourcesJson(userId: String): Flow<String?> =
        context.dataStore.data.map { secureArtifactOrLegacy(userId, SecureArtifacts.grocerySourcesJson, it[Keys.grocerySourcesJson(userId)]) }

    suspend fun saveGrocerySourcesJson(userId: String, json: String) {
        writeSecureArtifact(userId, SecureArtifacts.grocerySourcesJson, json)
        editArtifactsAndSync(userId) { it.remove(Keys.grocerySourcesJson(userId)) }
    }

    fun getGrocerySnapshotsJson(userId: String): Flow<String?> =
        context.dataStore.data.map { secureArtifactOrLegacy(userId, SecureArtifacts.grocerySnapshotsJson, it[Keys.grocerySnapshotsJson(userId)]) }

    suspend fun saveGrocerySnapshotsJson(userId: String, json: String) {
        writeSecureArtifact(userId, SecureArtifacts.grocerySnapshotsJson, json)
        editArtifactsAndSync(userId) { it.remove(Keys.grocerySnapshotsJson(userId)) }
    }

    fun getDailyLogsJson(userId: String): Flow<String?> =
        context.dataStore.data.map { reflectionStore.getDailyLogsJson(userId) ?: it[Keys.dailyLogsJson(userId)] }
    suspend fun saveDailyLogsJson(userId: String, json: String) {
        reflectionStore.saveDailyLogsJson(userId, json)
        editArtifactsAndSync(userId) { it.remove(Keys.dailyLogsJson(userId)) }
    }

    fun getWeeklyJournal(userId: String, weekStart: String): Flow<String?> =
        context.dataStore.data.map { reflectionStore.getWeeklyJournal(userId, weekStart) ?: it[Keys.weeklyJournal(userId, weekStart)] }

    suspend fun saveWeeklyJournal(userId: String, weekStart: String, text: String) {
        reflectionStore.saveWeeklyJournal(userId, weekStart, text)
        editArtifactsAndSync(userId) { it.remove(Keys.weeklyJournal(userId, weekStart)) }
    }

    fun getFeedbackQueueJson(userId: String): Flow<String?> =
        context.dataStore.data.map { secureArtifactOrLegacy(userId, SecureArtifacts.feedbackQueueJson, it[Keys.feedbackQueueJson(userId)]) }

    suspend fun saveFeedbackQueueJson(userId: String, json: String) {
        writeSecureArtifact(userId, SecureArtifacts.feedbackQueueJson, json)
        editArtifactsAndSync(userId) { it.remove(Keys.feedbackQueueJson(userId)) }
    }

    fun getPlanFeedbackTags(userId: String): Flow<List<String>> =
        context.dataStore.data.map {
            it[Keys.planFeedbackTags(userId)]?.split(",")?.filter { tag -> tag.isNotBlank() } ?: emptyList()
        }

    suspend fun savePlanFeedbackTags(userId: String, tags: List<String>) {
        editArtifactsAndSync(userId) { it[Keys.planFeedbackTags(userId)] = tags.joinToString(",") }
    }

    fun getLastReviewedWeek(userId: String): Flow<String?> =
        context.dataStore.data.map { it[Keys.lastReviewedWeek(userId)] }

    suspend fun saveLastReviewedWeek(userId: String, weekStart: String) {
        editArtifactsAndSync(userId) { it[Keys.lastReviewedWeek(userId)] = weekStart }
    }

    fun getProgressMode(userId: String): Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.progressMode(userId)] ?: "Today"
        }

    suspend fun saveProgressMode(userId: String, mode: String) {
        editArtifactsAndSync(userId) { prefs ->
            prefs[Keys.progressMode(userId)] = mode
        }
    }

    fun getProgressAdvancedAnalyticsExpanded(userId: String): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.progressAdvancedAnalyticsExpanded(userId)] ?: false
        }

    suspend fun saveProgressAdvancedAnalyticsExpanded(userId: String, expanded: Boolean) {
        editArtifactsAndSync(userId) { prefs ->
            prefs[Keys.progressAdvancedAnalyticsExpanded(userId)] = expanded
        }
    }

    fun getRemindersEnabled(userId: String): Flow<Boolean> =
        context.dataStore.data.map {
            it[Keys.notificationMaster(userId)] ?: it[Keys.remindersEnabled(userId)] ?: false
        }

    suspend fun setRemindersEnabled(userId: String, enabled: Boolean) {
        editArtifactsAndSync(userId) {
            it[Keys.remindersEnabled(userId)] = enabled
            it[Keys.notificationMaster(userId)] = enabled
        }
    }

    fun getNotificationPreferences(userId: String): Flow<NotificationPreferences> =
        context.dataStore.data.map { prefs ->
            NotificationPreferences(
                masterEnabled = prefs[Keys.notificationMaster(userId)]
                    ?: prefs[Keys.remindersEnabled(userId)]
                    ?: false,
                mealRemindersEnabled = prefs[Keys.notificationMeals(userId)] ?: true,
                planReadyEnabled = prefs[Keys.notificationPlanReady(userId)] ?: true,
                grocerySyncEnabled = prefs[Keys.notificationGrocerySync(userId)] ?: true,
                weeklyResetEnabled = prefs[Keys.notificationWeeklyReset(userId)] ?: true,
                weeklyResetDayOfWeek = prefs[Keys.notificationWeeklyResetDay(userId)] ?: 1,
                weeklyResetHour = prefs[Keys.notificationWeeklyResetHour(userId)] ?: 9,
                weeklyResetMinute = prefs[Keys.notificationWeeklyResetMinute(userId)] ?: 0,
                streakNudgesEnabled = prefs[Keys.notificationStreak(userId)] ?: false,
                inactivityNudgesEnabled = prefs[Keys.notificationInactivity(userId)] ?: true,
                breakfastHour = prefs[Keys.notificationBreakfastHour(userId)] ?: 8,
                breakfastMinute = prefs[Keys.notificationBreakfastMinute(userId)] ?: 0,
                lunchHour = prefs[Keys.notificationLunchHour(userId)] ?: 12,
                lunchMinute = prefs[Keys.notificationLunchMinute(userId)] ?: 30,
                dinnerHour = prefs[Keys.notificationDinnerHour(userId)] ?: 19,
                dinnerMinute = prefs[Keys.notificationDinnerMinute(userId)] ?: 0,
                quietHoursEnabled = prefs[Keys.notificationQuietEnabled(userId)] ?: false,
                quietStartHour = prefs[Keys.notificationQuietStartHour(userId)] ?: 22,
                quietStartMinute = prefs[Keys.notificationQuietStartMinute(userId)] ?: 0,
                quietEndHour = prefs[Keys.notificationQuietEndHour(userId)] ?: 6,
                quietEndMinute = prefs[Keys.notificationQuietEndMinute(userId)] ?: 30
            )
        }

    suspend fun saveNotificationPreferences(userId: String, prefs: NotificationPreferences) {
        editArtifactsAndSync(userId) {
            it[Keys.notificationMaster(userId)] = prefs.masterEnabled
            it[Keys.remindersEnabled(userId)] = prefs.masterEnabled
            it[Keys.notificationMeals(userId)] = prefs.mealRemindersEnabled
            it[Keys.notificationPlanReady(userId)] = prefs.planReadyEnabled
            it[Keys.notificationGrocerySync(userId)] = prefs.grocerySyncEnabled
            it[Keys.notificationWeeklyReset(userId)] = prefs.weeklyResetEnabled
            it[Keys.notificationWeeklyResetDay(userId)] = prefs.weeklyResetDayOfWeek.coerceIn(1, 7)
            it[Keys.notificationWeeklyResetHour(userId)] = prefs.weeklyResetHour.coerceIn(0, 23)
            it[Keys.notificationWeeklyResetMinute(userId)] = prefs.weeklyResetMinute.coerceIn(0, 59)
            it[Keys.notificationStreak(userId)] = prefs.streakNudgesEnabled
            it[Keys.notificationInactivity(userId)] = prefs.inactivityNudgesEnabled
            it[Keys.notificationBreakfastHour(userId)] = prefs.breakfastHour
            it[Keys.notificationBreakfastMinute(userId)] = prefs.breakfastMinute
            it[Keys.notificationLunchHour(userId)] = prefs.lunchHour
            it[Keys.notificationLunchMinute(userId)] = prefs.lunchMinute
            it[Keys.notificationDinnerHour(userId)] = prefs.dinnerHour
            it[Keys.notificationDinnerMinute(userId)] = prefs.dinnerMinute
            it[Keys.notificationQuietEnabled(userId)] = prefs.quietHoursEnabled
            it[Keys.notificationQuietStartHour(userId)] = prefs.quietStartHour
            it[Keys.notificationQuietStartMinute(userId)] = prefs.quietStartMinute
            it[Keys.notificationQuietEndHour(userId)] = prefs.quietEndHour
            it[Keys.notificationQuietEndMinute(userId)] = prefs.quietEndMinute
        }
    }

    fun getNotificationLogs(userId: String): Flow<List<NotificationLogEntry>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[Keys.notificationLogs(userId)]
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    gson.fromJson<List<NotificationLogEntry>>(raw, notificationLogsType)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

    suspend fun appendNotificationLog(userId: String, entry: NotificationLogEntry) {
        editArtifactsAndSync(userId) { prefs ->
            val current = prefs[Keys.notificationLogs(userId)]
                ?.let {
                    try {
                        gson.fromJson<List<NotificationLogEntry>>(it, notificationLogsType)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                .orEmpty()
            val updated = (listOf(entry) + current).take(80)
            prefs[Keys.notificationLogs(userId)] = gson.toJson(updated)
        }
    }

    suspend fun getNotificationLastFired(userId: String, type: String): Long {
        val prefs = context.dataStore.data.first()
        val raw = prefs[Keys.notificationLastFiredJson(userId)] ?: return 0L
        val map = try {
            gson.fromJson<Map<String, Long>>(raw, notificationLastFiredType)
        } catch (_: Exception) {
            emptyMap()
        }
        return map[type] ?: 0L
    }

    suspend fun setNotificationLastFired(userId: String, type: String, timestamp: Long) {
        editArtifactsAndSync(userId) { prefs ->
            val raw = prefs[Keys.notificationLastFiredJson(userId)]
            val map = raw?.let {
                try {
                    gson.fromJson<Map<String, Long>>(it, notificationLastFiredType).toMutableMap()
                } catch (_: Exception) {
                    mutableMapOf()
                }
            } ?: mutableMapOf()
            map[type] = timestamp
            prefs[Keys.notificationLastFiredJson(userId)] = gson.toJson(map)
        }
    }

    suspend fun markNotificationDelivered(
        userId: String,
        type: String,
        title: String,
        body: String,
        deliveredAt: Long = System.currentTimeMillis()
    ) {
        setNotificationLastFired(userId, type, deliveredAt)
        appendNotificationLog(
            userId = userId,
            entry = NotificationLogEntry(
                type = type,
                title = title,
                body = body,
                deliveredAt = deliveredAt
            )
        )
    }

    suspend fun getMostRecentDailyLogDate(userId: String): String? {
        val reflectionDate = mostRecentDailyLogDateFromJson(
            reflectionStore.getDailyLogsJson(userId)
        )
        val legacyDate = mostRecentDailyLogDateFromJson(
            getDailyLogsJson(userId).first()
        )
        return listOfNotNull(reflectionDate, legacyDate)
            .maxOrNull()
            ?.toString()
    }

    private fun mostRecentDailyLogDateFromJson(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val logs = try {
            gson.fromJson<List<DailyLog>>(raw, dailyLogsType)
        } catch (_: Exception) {
            emptyList()
        }
        return logs.mapNotNull { log ->
            runCatching { LocalDate.parse(log.date) }.getOrNull()
        }.maxOrNull()
    }

    suspend fun clearPlanHistory(userId: String) {
        writeSecureArtifact(userId, SecureArtifacts.planHistoryJson, null)
        writeSecureArtifact(userId, SecureArtifacts.lastPlanJson, null)
        editArtifactsAndSync(userId) { preferences ->
            preferences.remove(Keys.planHistoryJson(userId))
            preferences.remove(Keys.activePlanId(userId))
            preferences.remove(Keys.lastPlanJson(userId))
            preferences.remove(Keys.lastPlanTimestamp(userId))
            preferences.remove(Keys.lastReviewedWeek(userId))
        }
    }

    suspend fun clearGrocerySnapshots(userId: String) {
        writeSecureArtifact(userId, SecureArtifacts.groceryJson, null)
        writeSecureArtifact(userId, SecureArtifacts.grocerySourcesJson, null)
        writeSecureArtifact(userId, SecureArtifacts.grocerySnapshotsJson, null)
        editArtifactsAndSync(userId) { preferences ->
            preferences.remove(Keys.activePlanId(userId))
            preferences.remove(Keys.grocerySnapshotsJson(userId))
            preferences.remove(Keys.groceryJson(userId))
            preferences.remove(Keys.grocerySourcesJson(userId))
        }
    }

    suspend fun clearLegacyReflections(userId: String) {
        editArtifactsAndSync(userId) { preferences ->
            preferences.remove(Keys.dailyLogsJson(userId))
            val weeklyPrefix = "weekly_journal_${userId}_"
            preferences.asMap().keys
                .filter { it.name.startsWith(weeklyPrefix) }
                .toList()
                .forEach { preferences.remove(it) }
        }
    }
}
