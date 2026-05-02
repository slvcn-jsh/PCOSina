package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardHomeCardPolicyTest {

    @Test
    fun dashboard_usesDailyTipsAndKcalFirstMealCards() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "DashboardRefinedScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))

        assertTrue(
            "Dashboard should keep the Home tip card aligned to the Daily Tips wording from the mobile design.",
            text.contains("title = \"Daily Tips\"")
        )
        assertTrue(
            "Home meal cards should emphasize kcal values when recipe details are available.",
            text.contains("text = meal.calories.toString()")
        )
        assertFalse(
            "Legacy Logged today/Open recipe footer copy should no longer be the main meal card emphasis.",
            text.contains("text = if (meal.isLogged) \"Logged today\" else \"Open recipe\"")
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for dashboard policy test.")
    }
}
