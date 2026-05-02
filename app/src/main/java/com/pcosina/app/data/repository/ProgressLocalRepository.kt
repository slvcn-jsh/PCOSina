package com.pcosina.app.data.repository

import kotlinx.coroutines.flow.Flow

interface ProgressLocalRepository {
    fun getDailyLogsJson(userId: String): Flow<String?>
    fun getWeeklyJournal(userId: String, weekStart: String): Flow<String?>
    fun getFeedbackQueueJson(userId: String): Flow<String?>
    fun getPlanFeedbackTags(userId: String): Flow<List<String>>
    suspend fun savePlanFeedbackTags(userId: String, tags: List<String>)
    fun getProgressMode(userId: String): Flow<String>
    suspend fun saveProgressMode(userId: String, mode: String)
    fun getProgressAdvancedAnalyticsExpanded(userId: String): Flow<Boolean>
    suspend fun saveProgressAdvancedAnalyticsExpanded(userId: String, expanded: Boolean)
    suspend fun clearLegacyReflections(userId: String)
    suspend fun saveFeedbackQueueJson(userId: String, json: String)
}

class UserPreferencesProgressLocalRepository(
    private val userPreferencesRepository: UserPreferencesRepository
) : ProgressLocalRepository {
    override fun getDailyLogsJson(userId: String): Flow<String?> =
        userPreferencesRepository.getDailyLogsJson(userId)

    override fun getWeeklyJournal(userId: String, weekStart: String): Flow<String?> =
        userPreferencesRepository.getWeeklyJournal(userId, weekStart)

    override fun getFeedbackQueueJson(userId: String): Flow<String?> =
        userPreferencesRepository.getFeedbackQueueJson(userId)

    override fun getPlanFeedbackTags(userId: String): Flow<List<String>> =
        userPreferencesRepository.getPlanFeedbackTags(userId)

    override suspend fun savePlanFeedbackTags(userId: String, tags: List<String>) =
        userPreferencesRepository.savePlanFeedbackTags(userId, tags)

    override fun getProgressMode(userId: String): Flow<String> =
        userPreferencesRepository.getProgressMode(userId)

    override suspend fun saveProgressMode(userId: String, mode: String) =
        userPreferencesRepository.saveProgressMode(userId, mode)

    override fun getProgressAdvancedAnalyticsExpanded(userId: String): Flow<Boolean> =
        userPreferencesRepository.getProgressAdvancedAnalyticsExpanded(userId)

    override suspend fun saveProgressAdvancedAnalyticsExpanded(userId: String, expanded: Boolean) =
        userPreferencesRepository.saveProgressAdvancedAnalyticsExpanded(userId, expanded)

    override suspend fun clearLegacyReflections(userId: String) =
        userPreferencesRepository.clearLegacyReflections(userId)

    override suspend fun saveFeedbackQueueJson(userId: String, json: String) =
        userPreferencesRepository.saveFeedbackQueueJson(userId, json)
}
