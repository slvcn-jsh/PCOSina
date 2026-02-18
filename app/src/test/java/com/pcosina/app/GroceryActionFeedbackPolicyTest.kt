package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryActionFeedbackPolicyTest {

    @Test
    fun groceryRemainingActions_emitUnifiedBannerMessages() {
        val groceryText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "GroceryListScreen.kt"
        )

        assertTrue(
            "Share action should post loading feedback.",
            groceryText.contains("Preparing grocery list to share…")
        )
        assertTrue(
            "Share action should confirm success feedback.",
            groceryText.contains("Share options opened for your grocery list.")
        )
        assertTrue(
            "Clear search should post confirmation feedback.",
            groceryText.contains("Search cleared. Showing all categories.")
        )
        assertTrue(
            "Expand/collapse all should post confirmation feedback.",
            groceryText.contains("Collapsed all categories.") &&
                groceryText.contains("Expanded all categories.")
        )
    }

    private fun readMainSource(vararg segments: String): String {
        val mainSourceRoot = resolveMainSourceRoot()
        val path = mainSourceRoot.resolve(
            Paths.get(segments.first(), *segments.drop(1).toTypedArray())
        )
        return String(Files.readAllBytes(path))
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for grocery action feedback policy test.")
    }
}
