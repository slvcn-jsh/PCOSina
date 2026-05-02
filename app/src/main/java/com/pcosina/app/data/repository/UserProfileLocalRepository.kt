package com.pcosina.app.data.repository

import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileLocalRepository {
    fun getUserProfile(userId: String): Flow<UserProfile>
    suspend fun updateProfile(userId: String, profile: UserProfile)
    fun getPantryEntries(userId: String): Flow<List<PantryEntry>>
    suspend fun savePantryEntries(userId: String, entries: List<PantryEntry>)
}

class UserPreferencesUserProfileLocalRepository(
    private val userPreferencesRepository: UserPreferencesRepository
) : UserProfileLocalRepository {
    override fun getUserProfile(userId: String): Flow<UserProfile> =
        userPreferencesRepository.getUserProfile(userId)

    override suspend fun updateProfile(userId: String, profile: UserProfile) =
        userPreferencesRepository.updateProfile(userId, profile)

    override fun getPantryEntries(userId: String): Flow<List<PantryEntry>> =
        userPreferencesRepository.getPantryEntries(userId)

    override suspend fun savePantryEntries(userId: String, entries: List<PantryEntry>) =
        userPreferencesRepository.savePantryEntries(userId, entries)
}
