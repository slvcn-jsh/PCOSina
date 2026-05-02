package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanDayNavigationPolicyTest {

    @Test
    fun mealPlan_dayNavigationUsesFullWeekStripWithTodayMarker() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "MealPlanRefinedScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue(
            "Meal plan should render the full week strip directly, including weekend days.",
            text.contains("dates.forEachIndexed")
        )
        assertTrue(
            "Meal plan should keep a visible Today marker in the week strip.",
            text.contains("dates.forEachIndexed") &&
                text.contains("isToday -> PcosinaSoftPink") &&
                text.contains("selected -> PcosinaPink")
        )
        assertFalse(
            "Meal plan should not keep side arrow controls when the full 7-day strip is already visible.",
            text.contains("WeekArrowButton(") ||
                text.contains("Icons.Filled.ChevronLeft") ||
                text.contains("Icons.Filled.ChevronRight")
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
