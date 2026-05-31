package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRestorePolicyTest {

    @Test
    fun restoreMatrixDocumentsLifecycleBoundaries() {
        val doc = read(resolve("docs", "data-models", "offline_restore_matrix.md"))
        val conflictPolicy = read(resolve("docs", "data-models", "sync_conflict_policy.md"))

        assertTrue(doc.contains("PCOSina is local-first."))
        assertTrue(doc.contains("App close/reopen"))
        assertTrue(doc.contains("Phone offline"))
        assertTrue(doc.contains("Sign out and sign back in on the same install"))
        assertTrue(doc.contains("Clear app data"))
        assertTrue(doc.contains("Uninstall/reinstall"))
        assertTrue(doc.contains("New device login"))
        assertTrue(doc.contains("Best-effort cloud restore only."))
        assertTrue(doc.contains("Daily meal logs, check-ins, and weekly journals | Yes | No"))
        assertTrue(doc.contains("Notification delivery history | Yes | No"))
        assertTrue(conflictPolicy.contains("offline_restore_matrix.md"))
        assertTrue(conflictPolicy.contains("Daily logs and weekly journals are local-only"))
    }

    @Test
    fun settingsAccountScreenKeepsRestoreDetailsOutOfTheUi() {
        val settings = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "SettingsScreen.kt"
            )
        )

        assertTrue(settings.contains("title = \"Account actions\""))
        assertTrue(settings.contains("Clear saved week data or sign out only when needed."))
        assertTrue(settings.contains("Removes saved plans, grocery snapshots, and progress logs for this account."))
        assertTrue(settings.contains("This does not delete this phone's saved account data."))
        assertFalse(settings.contains("title = \"Storage and restore\""))
        assertFalse(settings.contains("Only cloud-synced profile and artifacts can return after reinstall or new-phone login."))
        assertFalse(settings.contains("Meal logs, weekly journals, and notification delivery logs stay on this install."))
        assertFalse(settings.contains("Synced week backup clears when sync succeeds."))
        assertFalse(
            "Settings should not claim clear-history is phone-only when synced week artifacts are also cleared.",
            settings.contains("from this phone only")
        )
    }

    @Test
    fun userPreferencesRepositoryScopesCloudRestoreToSupportedArtifacts() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )

        assertTrue(source.contains("private enum class ArtifactDomain"))
        listOf(
            "Pantry(Cloud.pantryUpdatedAtEpochMs)",
            "Plan(Cloud.planUpdatedAtEpochMs)",
            "Grocery(Cloud.groceryUpdatedAtEpochMs)",
            "Feedback(Cloud.feedbackUpdatedAtEpochMs)",
            "ProgressUi(Cloud.progressUiUpdatedAtEpochMs)",
            "NotificationPreferences(Cloud.notificationPreferencesUpdatedAtEpochMs)"
        ).forEach { expected ->
            assertTrue("Missing artifact domain: $expected", source.contains(expected))
        }

        assertTrue(source.contains("private fun buildArtifactDomainPayload("))
        assertTrue(source.contains("private suspend fun applyArtifactDomainLocalOnly("))
        assertTrue(source.contains("payload[Cloud.lastPlanJson]"))
        assertTrue(source.contains("payload[Cloud.groceryJson]"))
        assertTrue(source.contains("payload[Cloud.feedbackQueueJson]"))
        assertTrue(source.contains("payload[Cloud.progressMode]"))
        assertTrue(source.contains("payload[Cloud.notificationMaster]"))
        assertTrue(source.contains("writeSecureArtifact(userId, SecureArtifacts.pantryEntries"))
        assertTrue(source.contains("writeSecureArtifact(userId, SecureArtifacts.lastPlanJson"))
        assertTrue(source.contains("writeSecureArtifact(userId, SecureArtifacts.groceryJson"))
    }

    @Test
    fun localOnlyHistoriesAreNotPromisedAsCloudRestoreArtifacts() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )
        val doc = read(resolve("docs", "data-models", "offline_restore_matrix.md"))

        assertTrue(source.contains("Cloud.dailyLogsJson to FieldValue.delete()"))
        assertTrue(source.contains("Cloud.notificationLogsJson to FieldValue.delete()"))
        assertTrue(source.contains("Cloud.weeklyJournalMap to FieldValue.delete()"))
        assertFalse(source.contains("payload[Cloud.dailyLogsJson]"))
        assertFalse(source.contains("payload[Cloud.notificationLogsJson]"))
        assertFalse(source.contains("payload[Cloud.weeklyJournalMap]"))
        assertTrue(doc.contains("Plan history list is local-only."))
        assertTrue(doc.contains("Local-only until dedicated remote storage exists."))
        assertTrue(doc.contains("Delivery logs are device/install history."))
    }

    @Test
    fun generationFailureCopyStaysUserFacingForOfflinePaths() {
        val repo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "MealPlanRepository.kt"
            )
        )
        val screen = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "MealPlanRefinedScreen.kt"
            )
        )

        assertTrue(
            repo.contains(
                "Internet connection is needed to generate a new plan. You can still view saved plans and groceries offline."
            )
        )
        assertFalse(repo.contains("PCOSina could not reach the planner right now"))
        assertFalse(repo.contains("\"HTTP ${'$'}{error.code()}:"))
        assertTrue(screen.contains("Internet connection is needed to generate a new plan. Your saved plan stays available offline."))
        assertTrue(screen.contains("This can take a while when many plan requests are running."))
        assertFalse(screen.contains("backend queue"))
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallback = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(fallback)) return fallback
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path)).replace("\r\n", "\n")
}
