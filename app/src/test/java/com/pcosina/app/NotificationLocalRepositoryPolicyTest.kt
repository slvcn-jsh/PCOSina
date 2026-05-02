package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationLocalRepositoryPolicyTest {

    @Test
    fun userViewModel_usesNotificationLocalRepositoryBoundary() {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "UserViewModel.kt"
            )
        )

        assertTrue(viewModel.contains("private val notificationLocalRepository: NotificationLocalRepository"))
        assertTrue(viewModel.contains("notificationLocalRepository.getNotificationPreferences(userId).collectLatest"))
        assertTrue(viewModel.contains("notificationLocalRepository.saveNotificationPreferences(currentUserId, updated)"))
    }

    @Test
    fun appNavHost_wiresNotificationLocalRepositoryIntoUserViewModel() {
        val navHost = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "navigation", "AppNavHost.kt"
            )
        )
        val notificationRepo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "NotificationLocalRepository.kt"
            )
        )

        assertTrue(navHost.contains("val notificationLocalRepository = remember { UserPreferencesNotificationLocalRepository(userPrefsRepository) }"))
        assertTrue(navHost.contains("UserViewModel.Factory(userProfileLocalRepository, notificationLocalRepository)"))
        assertTrue(notificationRepo.contains("interface NotificationLocalRepository"))
        assertTrue(notificationRepo.contains("class UserPreferencesNotificationLocalRepository"))
    }

    @Test
    fun notificationScheduler_and_receiver_useNotificationLocalRepositoryBoundary() {
        val scheduler = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "notifications", "NotificationScheduler.kt"
            )
        )
        val receiver = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "notifications", "NotificationRescheduleReceiver.kt"
            )
        )

        assertTrue(scheduler.contains("private fun notificationLocalRepository(context: Context): NotificationLocalRepository"))
        assertTrue(scheduler.contains("val repository = notificationLocalRepository(context)"))
        assertTrue(scheduler.contains("repository: NotificationLocalRepository"))
        assertTrue(receiver.contains("UserPreferencesNotificationLocalRepository("))
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
