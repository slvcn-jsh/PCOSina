package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactSyncPolicyTest {

    @Test
    fun syncableSettingsCountAsArtifacts() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )
        assertTrue(
            "Artifact detection should include progress and notification settings that are uploaded in buildArtifactPayload().",
            source.contains("preferences.contains(Keys.progressMode(userId))") &&
                source.contains("preferences.contains(Keys.remindersEnabled(userId))") &&
                source.contains("preferences.contains(Keys.notificationMaster(userId))") &&
                source.contains("data.containsKey(Cloud.progressMode)") &&
                source.contains("data.containsKey(Cloud.remindersEnabled)") &&
                source.contains("data.containsKey(Cloud.notificationMaster)")
        )
    }

    @Test
    fun firstUpgradeConflictSeedsTimestampInsteadOfOverwritingLocalArtifacts() {
        val source = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "UserPreferencesRepository.kt"
            )
        )
        assertTrue(
            "First-upgrade conflicts should seed cloudArtifactsUpdatedAt before any upload/download instead of overwriting local artifacts.",
            source.contains("localHasData && localUpdatedAt == 0L && remoteHasData") &&
                source.contains("val bootstrapTimestamp = if (remoteUpdatedAt > 0L) remoteUpdatedAt else now") &&
                source.contains("preferences[Keys.cloudArtifactsUpdatedAt(userId)] = bootstrapTimestamp")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallback = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(fallback)) return fallback
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
