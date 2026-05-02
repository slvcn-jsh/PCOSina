package com.pcosina.app.data.repository

import kotlinx.coroutines.flow.Flow

interface GroceryLocalRepository {
    fun getGroceryJson(userId: String): Flow<String?>
    suspend fun saveGroceryJson(userId: String, json: String)
    fun getGrocerySourcesJson(userId: String): Flow<String?>
    suspend fun saveGrocerySourcesJson(userId: String, json: String)
    fun getGrocerySnapshotsJson(userId: String): Flow<String?>
    suspend fun saveGrocerySnapshotsJson(userId: String, json: String)
    fun getSavedPlanTimestamp(userId: String): Flow<Long>
    fun getActivePlanId(userId: String): Flow<String?>
    suspend fun saveActivePlanId(userId: String, id: String?)
    suspend fun clearGrocerySnapshots(userId: String)
}

class UserPreferencesGroceryLocalRepository(
    private val userPreferencesRepository: UserPreferencesRepository
) : GroceryLocalRepository {
    override fun getGroceryJson(userId: String): Flow<String?> =
        userPreferencesRepository.getGroceryJson(userId)

    override suspend fun saveGroceryJson(userId: String, json: String) =
        userPreferencesRepository.saveGroceryJson(userId, json)

    override fun getGrocerySourcesJson(userId: String): Flow<String?> =
        userPreferencesRepository.getGrocerySourcesJson(userId)

    override suspend fun saveGrocerySourcesJson(userId: String, json: String) =
        userPreferencesRepository.saveGrocerySourcesJson(userId, json)

    override fun getGrocerySnapshotsJson(userId: String): Flow<String?> =
        userPreferencesRepository.getGrocerySnapshotsJson(userId)

    override suspend fun saveGrocerySnapshotsJson(userId: String, json: String) =
        userPreferencesRepository.saveGrocerySnapshotsJson(userId, json)

    override fun getSavedPlanTimestamp(userId: String): Flow<Long> =
        userPreferencesRepository.getSavedPlanTimestamp(userId)

    override fun getActivePlanId(userId: String): Flow<String?> =
        userPreferencesRepository.getActivePlanId(userId)

    override suspend fun saveActivePlanId(userId: String, id: String?) =
        userPreferencesRepository.saveActivePlanId(userId, id)

    override suspend fun clearGrocerySnapshots(userId: String) =
        userPreferencesRepository.clearGrocerySnapshots(userId)
}
