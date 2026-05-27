package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase8FinalQaPolicyTest {

    @Test
    fun commonUserPaths_doNotExposeImplementationOrDeveloperWording() {
        val commonSources = listOf(
            readMain("notifications", "NotificationScheduler.kt"),
            readMain("ui", "screens", "CommunityScreen.kt"),
            readMain("ui", "screens", "DashboardRefinedScreen.kt"),
            readMain("ui", "screens", "GroceryRefinedScreen.kt"),
            readMain("ui", "screens", "MealPlanRefinedScreen.kt"),
            readMain("ui", "screens", "MoreToolsScreen.kt"),
            readMain("ui", "screens", "NotificationScreen.kt"),
            readMain("ui", "screens", "ProgressRefinedScreen.kt"),
            readMain("ui", "screens", "RecipeDetailsScreen.kt"),
            readMain("ui", "screens", "SettingsScreen.kt"),
            readMain("ui", "screens", "UserProfileScreen.kt")
        ).joinToString("\n")

        listOf(
            "backend ML",
            "debug notification",
            "refined shell",
            "UI pass",
            "developer wording",
            "stack trace",
            "lorem ipsum",
            "dummy data"
        ).forEach { phrase ->
            assertFalse(
                "Common user paths should not expose internal/developer wording: $phrase",
                commonSources.contains(phrase, ignoreCase = true)
            )
        }
    }

    @Test
    fun offlineRestoreAndSafetyBoundaries_areStillDocumentedAndSurfaced() {
        val settings = readMain("ui", "screens", "SettingsScreen.kt")
        val repository = readMain("data", "repository", "MealPlanRepository.kt")
        val restoreMatrix = read(resolve("docs", "data-models", "offline_restore_matrix.md"))
        val productionContract = read(resolve("docs", "architecture", "production_contract.md"))
        val mlAdr = read(resolve("docs", "adr", "ADR-004-ml-shadow-canary.md"))

        assertTrue("Settings should surface storage and restore expectations.", settings.contains("Storage and restore"))
        assertTrue(
            "Offline generation failure should stay simple and user-facing.",
            repository.contains("Internet connection is needed to generate a new plan. You can still view saved plans and groceries offline.")
        )
        assertTrue("Restore matrix should document uninstall behavior.", restoreMatrix.contains("| Uninstall/reinstall |"))
        assertTrue("Restore matrix should call out local-only history limits.", restoreMatrix.contains("local-only"))
        assertTrue("Production contract should keep hard-constraint boundary.", productionContract.contains("Hard constraints are never bypassed"))
        assertTrue("ML ADR should keep Stage 1 ranking-only authority.", mlAdr.contains("ML scores are allowed only in Stage 1 candidate ranking."))
    }

    private fun readMain(vararg parts: String): String =
        read(resolve("app", "src", "main", "java", "com", "pcosina", "app", *parts))

    private fun read(path: Path): String = String(Files.readAllBytes(path))

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
