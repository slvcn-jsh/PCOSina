package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryAccessibilityPolicyTest {

    @Test
    fun groceryIncludesAffordanceHintsAndAccessibleLabels() {
        val groceryText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "GroceryRefinedScreen.kt"
        )
        assertTrue(
            "Grocery should keep the primary pantry add affordance labelled for accessibility.",
            groceryText.contains("contentDescription = \"Add pantry item\"")
        )
        assertTrue(
            "Grocery category toggle should provide TalkBack context.",
            groceryText.contains("Collapse \$title category")
        )
        assertTrue(
            "Grocery pantry removal should have explicit content description.",
            groceryText.contains("Remove pantry item")
        )
        assertTrue(
            "Grocery accessibility pass should set 48dp touch targets.",
            groceryText.contains("heightIn(min = 48.dp)")
        )
        assertTrue(
            "Grocery should expose the new filter dialog on the refined screen.",
            groceryText.contains("Select Filters") &&
                groceryText.contains("Need to buy") &&
                groceryText.contains("Bought/Pantry")
        )
        assertFalse(
            "Grocery should no longer keep the misleading budget CTA that sent users to Progress.",
            groceryText.contains("Update budget in Progress")
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
            ?: error("Could not locate main source root for grocery accessibility policy test.")
    }
}
