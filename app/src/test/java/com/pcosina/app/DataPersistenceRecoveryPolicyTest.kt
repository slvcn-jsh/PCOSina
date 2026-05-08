package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class DataPersistenceRecoveryPolicyTest {

    @Test
    fun profileCloudSyncReturnsExplicitResultForStartupGate() {
        val source = readMain("data", "repository", "UserPreferencesRepository.kt")
        assertTrue(source.contains("enum class CloudProfileSyncResult"))
        assertTrue(source.contains("suspend fun hasLocalProfileData(userId: String): Boolean"))
        assertTrue(source.contains("suspend fun syncProfileWithCloud(userId: String): CloudProfileSyncResult"))
        assertTrue(source.contains("return CloudProfileSyncResult.Failed"))
        assertTrue(source.contains("return CloudProfileSyncResult.RestoredFromCloud"))
    }

    @Test
    fun freshInstallContinuesLocallyWhenCloudRestoreFails() {
        val source = readMain("ui", "navigation", "AppNavHost.kt")
        assertTrue(source.contains("val profileCloudRestoreBlocked = remember { mutableStateOf(false) }"))
        assertTrue(source.contains("val localProfileHadData = userPrefsRepository.hasLocalProfileData(userId)"))
        assertTrue(source.contains("!localProfileHadData && syncResult == CloudProfileSyncResult.Failed"))
        assertTrue(source.contains("(profileBoundToSession && !isProfileLoading && !profileCloudSyncInProgress.value)"))
        assertTrue(source.contains("mealPlanViewModel.loadSavedPlan(userId)"))
        assertTrue(source.contains("groceryViewModel.loadGroceryForUser(userId)"))
    }

    @Test
    fun splashExplainsCloudRestoreFallbackInsteadOfRetryLoop() {
        val splash = readMain("ui", "screens", "SplashScreen.kt")
        val nav = readMain("ui", "navigation", "AppNavHost.kt")
        assertTrue(splash.contains("statusText: String = \"Loading your local plan context\""))
        assertTrue(nav.contains("Cloud restore is unavailable. Continuing locally."))
        assertTrue(nav.contains("splashReady.value = true"))
    }

    private fun readMain(vararg parts: String): String =
        read(resolve("app", "src", "main", "java", "com", "pcosina", "app", *parts))

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallback = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(fallback)) return fallback
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path)).replace("\r\n", "\n")
}
