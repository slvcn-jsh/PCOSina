package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureArtifactStoragePolicyTest {

    @Test
    fun reflectionStore_supportsGenericEncryptedArtifacts() {
        val source = read(resolve("app", "src", "main", "java", "com", "pcosina", "app", "data", "repository", "ReflectionStore.kt"))
        assertTrue(source.contains("artifactKey(userId: String, name: String)"))
        assertTrue(source.contains("fun getArtifactJson(userId: String, name: String)"))
        assertTrue(source.contains("fun saveArtifactJson(userId: String, name: String, json: String?)"))
        assertTrue(source.contains("fun replaceWeeklyJournals(userId: String, journals: Map<String, String>)"))
    }

    @Test
    fun userPreferencesRepository_readsSensitiveArtifactsFromEncryptedStore() {
        val source = read(resolve("app", "src", "main", "java", "com", "pcosina", "app", "data", "repository", "UserPreferencesRepository.kt"))
        assertTrue(source.contains("secureArtifactOrLegacy"))
        assertTrue(source.contains("writeSecureArtifact"))
        assertTrue(source.contains("reflectionStore.getDailyLogsJson(userId)"))
        assertTrue(source.contains("reflectionStore.getWeeklyJournal(userId, weekStart)"))
        assertTrue(source.contains("preferences.remove(Keys.lastPlanJson(userId))"))
        assertTrue(source.contains("preferences.remove(Keys.groceryJson(userId))"))
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
