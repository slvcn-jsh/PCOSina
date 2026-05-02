package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileLocalRepositoryPolicyTest {

    @Test
    fun userViewModel_usesUserProfileLocalRepositoryBoundary() {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "UserViewModel.kt"
            )
        )

        assertTrue(viewModel.contains("private val userProfileLocalRepository: UserProfileLocalRepository"))
        assertTrue(viewModel.contains("userProfileLocalRepository.getUserProfile(userId).collectLatest"))
        assertTrue(viewModel.contains("userProfileLocalRepository.savePantryEntries(currentUserId, normalizedEntries)"))
        assertTrue(!viewModel.contains("private val userPreferencesRepository: UserPreferencesRepository"))
    }

    @Test
    fun appNavHost_wiresUserProfileLocalRepositoryIntoUserViewModel() {
        val navHost = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "navigation", "AppNavHost.kt"
            )
        )
        val profileRepo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserProfileLocalRepository.kt"
            )
        )

        assertTrue(navHost.contains("val userProfileLocalRepository = remember { UserPreferencesUserProfileLocalRepository(userPrefsRepository) }"))
        assertTrue(navHost.contains("UserViewModel.Factory(userProfileLocalRepository, notificationLocalRepository)"))
        assertTrue(profileRepo.contains("interface UserProfileLocalRepository"))
        assertTrue(profileRepo.contains("class UserPreferencesUserProfileLocalRepository"))
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
