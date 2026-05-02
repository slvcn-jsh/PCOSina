package com.pcosina.app.data.repository

import com.pcosina.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface PlannerLocalRepository {
    fun getUserProfile(userId: String): Flow<UserProfile>
    fun getSavedPlanJson(userId: String): Flow<String?>
    fun getSavedPlanTimestamp(userId: String): Flow<Long>
    fun getPlanHistoryJson(userId: String): Flow<String?>
    fun getActivePlanId(userId: String): Flow<String?>
    suspend fun savePlanJson(userId: String, json: String, timestamp: Long)
    suspend fun savePlanHistoryJson(userId: String, json: String)
    suspend fun saveActivePlanId(userId: String, id: String?)
    fun getPlanFeedbackTags(userId: String): Flow<List<String>>
    fun getLastReviewedWeek(userId: String): Flow<String?>
    suspend fun saveLastReviewedWeek(userId: String, weekStart: String)
    suspend fun clearPlanHistory(userId: String)
}

class UserPreferencesPlannerLocalRepository(
    private val userPreferencesRepository: UserPreferencesRepository
) : PlannerLocalRepository {
    override fun getUserProfile(userId: String): Flow<UserProfile> =
        userPreferencesRepository.getUserProfile(userId)

    override fun getSavedPlanJson(userId: String): Flow<String?> =
        userPreferencesRepository.getSavedPlanJson(userId)

    override fun getSavedPlanTimestamp(userId: String): Flow<Long> =
        userPreferencesRepository.getSavedPlanTimestamp(userId)

    override fun getPlanHistoryJson(userId: String): Flow<String?> =
        userPreferencesRepository.getPlanHistoryJson(userId)

    override fun getActivePlanId(userId: String): Flow<String?> =
        userPreferencesRepository.getActivePlanId(userId)

    override suspend fun savePlanJson(userId: String, json: String, timestamp: Long) =
        userPreferencesRepository.savePlanJson(userId, json, timestamp)

    override suspend fun savePlanHistoryJson(userId: String, json: String) =
        userPreferencesRepository.savePlanHistoryJson(userId, json)

    override suspend fun saveActivePlanId(userId: String, id: String?) =
        userPreferencesRepository.saveActivePlanId(userId, id)

    override fun getPlanFeedbackTags(userId: String): Flow<List<String>> =
        userPreferencesRepository.getPlanFeedbackTags(userId)

    override fun getLastReviewedWeek(userId: String): Flow<String?> =
        userPreferencesRepository.getLastReviewedWeek(userId)

    override suspend fun saveLastReviewedWeek(userId: String, weekStart: String) =
        userPreferencesRepository.saveLastReviewedWeek(userId, weekStart)

    override suspend fun clearPlanHistory(userId: String) =
        userPreferencesRepository.clearPlanHistory(userId)
}
