package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
            "GroceryListScreen.kt"
        )
        assertTrue(
            "Grocery should hint horizontal pantry scrolling.",
            groceryText.contains("Swipe left or right to review pantry chips.")
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
