package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanHistoryClearPolicyTest {

    @Test
    fun clearPlanHistory_removesPersistedReviewedWeekKey() {
        val repoPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "data", "repository", "UserPreferencesRepository.kt"
        )
        val source = read(repoPath)

        val hasMethod = source.contains("suspend fun clearPlanHistory(userId: String)")
        val clearsReviewedWeek = source.contains("preferences.remove(Keys.lastReviewedWeek(userId))")

        assertTrue("UserPreferencesRepository should expose clearPlanHistory.", hasMethod)
        assertTrue(
            "clearPlanHistory must remove persisted lastReviewedWeek key.",
            clearsReviewedWeek
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallbackParts = parts.drop(1).toTypedArray()
        val second = Paths.get(fallbackParts.first(), *fallbackParts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
