package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryListDataIsolationPolicyTest {

    @Test
    fun groceryList_doesNotMergeStaticDummyItemsIntoUserList() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "GroceryRefinedScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertFalse(
            "Grocery list must not combine static DummyData entries with account-scoped grocery data.",
            text.contains("DummyData.groceryList + addedItems")
        )
        assertTrue(
            "Grocery screen should show an explicit empty state when no account grocery data exists.",
            text.contains("No ingredients are synced yet.")
        )
    }

    @Test
    fun groceryList_searchEmptyStateAndInlineFeedback_arePresent() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "GroceryRefinedScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue(
            "Grocery screen should show clear no-results messages for current filter and query misses.",
            text.contains("No ingredients match your current filters.") &&
                text.contains("No ingredients match")
        )
        assertTrue(
            "Refined grocery should keep inline feedback state for user-visible confirmations.",
            text.contains("var feedbackMessage by remember")
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for grocery policy test.")
    }
}
