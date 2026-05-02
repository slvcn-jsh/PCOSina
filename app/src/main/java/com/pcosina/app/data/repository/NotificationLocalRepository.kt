package com.pcosina.app.data.repository

import com.pcosina.app.data.model.NotificationLogEntry
import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface NotificationLocalRepository {
    fun getRemindersEnabled(userId: String): Flow<Boolean>
    fun getNotificationPreferences(userId: String): Flow<NotificationPreferences>
    suspend fun saveNotificationPreferences(userId: String, prefs: NotificationPreferences)
    fun getNotificationLogs(userId: String): Flow<List<NotificationLogEntry>>
    suspend fun getNotificationLastFired(userId: String, type: String): Long
    suspend fun markNotificationDelivered(
        userId: String,
        type: String,
        title: String,
        body: String,
        deliveredAt: Long = System.currentTimeMillis()
    )
    suspend fun getMostRecentDailyLogDate(userId: String): String?
    fun getUserProfile(userId: String): Flow<UserProfile>
}

class UserPreferencesNotificationLocalRepository(
    private val userPreferencesRepository: UserPreferencesRepository
) : NotificationLocalRepository {
    override fun getRemindersEnabled(userId: String): Flow<Boolean> =
        userPreferencesRepository.getRemindersEnabled(userId)

    override fun getNotificationPreferences(userId: String): Flow<NotificationPreferences> =
        userPreferencesRepository.getNotificationPreferences(userId)

    override suspend fun saveNotificationPreferences(userId: String, prefs: NotificationPreferences) =
        userPreferencesRepository.saveNotificationPreferences(userId, prefs)

    override fun getNotificationLogs(userId: String): Flow<List<NotificationLogEntry>> =
        userPreferencesRepository.getNotificationLogs(userId)

    override suspend fun getNotificationLastFired(userId: String, type: String): Long =
        userPreferencesRepository.getNotificationLastFired(userId, type)

    override suspend fun markNotificationDelivered(
        userId: String,
        type: String,
        title: String,
        body: String,
        deliveredAt: Long
    ) = userPreferencesRepository.markNotificationDelivered(
        userId = userId,
        type = type,
        title = title,
        body = body,
        deliveredAt = deliveredAt
    )

    override suspend fun getMostRecentDailyLogDate(userId: String): String? =
        userPreferencesRepository.getMostRecentDailyLogDate(userId)

    override fun getUserProfile(userId: String): Flow<UserProfile> =
        userPreferencesRepository.getUserProfile(userId)
}
