package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryActionFeedbackPolicyTest {

    @Test
    fun groceryRemainingActions_emitCurrentInlineFeedbackMessages() {
        val groceryText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "GroceryRefinedScreen.kt"
        )

        assertTrue(
            "Share action should confirm success feedback in the refined screen.",
            groceryText.contains("Share options opened for your grocery list.")
        )
        assertTrue(
            "Sync action should confirm success feedback in the refined screen.",
            groceryText.contains("Ingredients synced from your current meal plan.")
        )
        assertTrue(
            "Pantry add/remove actions should keep inline confirmation feedback.",
            groceryText.contains("added to pantry.") &&
                groceryText.contains("removed from pantry.")
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
