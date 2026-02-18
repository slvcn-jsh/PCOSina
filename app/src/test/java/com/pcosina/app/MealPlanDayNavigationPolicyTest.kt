package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanDayNavigationPolicyTest {

    @Test
    fun mealPlan_dayNavigationShowsFullWeekHintAndJumpToToday() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "MealPlanScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue(
            "Meal plan should explicitly state full-week discoverability, including weekends.",
            text.contains("Sat-Sun included")
        )
        assertTrue(
            "Meal plan should expose a Jump to Today affordance.",
            text.contains("Jump to Today")
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for meal plan policy test.")
    }
}
