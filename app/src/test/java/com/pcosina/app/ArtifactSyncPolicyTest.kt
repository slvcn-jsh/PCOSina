package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactSyncPolicyTest {

    @Test
    fun clearMealHistoryDoesNotPurgeSecureArtifacts() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "ReflectionStore.kt"
            )
        )
        assertFalse(
            "ReflectionStore.clearForUser() must not remove artifact_* entries when the user only clears meal history.",
            source.contains("key.startsWith(\"artifact_\") && key.endsWith(\"_${'$'}userId\")")
        )
    }

    @Test
    fun artifactSyncUsesDomainTimestampsInsteadOfOneGlobalStamp() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )
        assertTrue(
            "Artifact sync should maintain separate timestamps for pantry, plan, grocery, feedback, progress UI, and notification preferences.",
            source.contains("Cloud.pantryUpdatedAtEpochMs") &&
                source.contains("Cloud.planUpdatedAtEpochMs") &&
                source.contains("Cloud.groceryUpdatedAtEpochMs") &&
                source.contains("Cloud.feedbackUpdatedAtEpochMs") &&
                source.contains("Cloud.progressUiUpdatedAtEpochMs") &&
                source.contains("Cloud.notificationPreferencesUpdatedAtEpochMs") &&
                source.contains("preferences[domain.localUpdatedAtKey(userId)] = now")
        )
    }

    @Test
    fun cloudHistoryPayloadDeletesOversizedLegacyFields() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )
        assertTrue(
            "Profile artifact sync should delete large legacy history fields instead of keeping them in profiles/{uid}.",
            source.contains("Cloud.planHistoryJson to FieldValue.delete()") &&
                source.contains("Cloud.dailyLogsJson to FieldValue.delete()") &&
                source.contains("Cloud.notificationLogsJson to FieldValue.delete()") &&
                source.contains("Cloud.weeklyJournalMap to FieldValue.delete()")
        )
    }

    @Test
    fun localOnlyHistoriesAvoidLegacyCloudPayloads() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )
        assertTrue(
            "Plan history should remain a secure local artifact while still refreshing the Plan artifact timestamp for sync ordering.",
            source.contains("writeSecureArtifact(userId, SecureArtifacts.planHistoryJson, json)") &&
                source.contains("editArtifactDomainsAndSync(userId, ArtifactDomain.Plan) { preferences ->\n            preferences.remove(Keys.planHistoryJson(userId))") &&
                !source.contains("payload[Cloud.planHistoryJson]")
        )
        assertTrue(
            "Daily logs, weekly journals, and notification logs should stay local-only until they have dedicated remote storage.",
            source.contains("context.dataStore.edit { it.remove(Keys.dailyLogsJson(userId)) }") &&
                source.contains("context.dataStore.edit { it.remove(Keys.weeklyJournal(userId, weekStart)) }") &&
                source.contains("context.dataStore.edit { prefs ->\n            val current = prefs[Keys.notificationLogs(userId)]")
        )
    }

    @Test
    fun clearedDomainsReuseLegacyArtifactTimestampAsTombstone() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )
        assertTrue(
            "Domain sync should fall back to the legacy cloudArtifactsUpdatedAt timestamp even when local data is empty, so cleared domains are not restored from stale cloud copies during upgrade.",
            source.contains("return preferences[localUpdatedAtKey(userId)]\n            ?: (preferences[Keys.cloudArtifactsUpdatedAt(userId)] ?: 0L)")
        )
    }

    @Test
    fun clearMealHistoryDeletesLegacyCloudReflectionFields() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )
        assertTrue(
            "Clearing meal history should actively delete legacy dailyLogsJson and weeklyJournalMap fields from the cloud profile document.",
            source.contains("Cloud.dailyLogsJson to FieldValue.delete()") &&
                source.contains("Cloud.weeklyJournalMap to FieldValue.delete()") &&
                source.contains("Cloud reflection cleanup skipped")
        )
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
