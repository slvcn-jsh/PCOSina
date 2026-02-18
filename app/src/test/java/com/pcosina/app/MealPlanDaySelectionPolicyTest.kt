package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanDaySelectionPolicyTest {

    @Test
    fun mealPlanScreen_keepsSelectedDayStableDuringInPlaceUiStateUpdates() {
        val mealPlanPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "MealPlanScreen.kt"
        )
        val source = read(mealPlanPath)

        assertFalse(
            "Selected day reset must not be keyed to uiState mutations.",
            source.contains("LaunchedEffect(activePlanId, uiState)")
        )
        assertTrue(
            "Selected day initialization should be anchored to plan identity.",
            source.contains("LaunchedEffect(activePlanId, currentPlan?.weekLabel)")
        )
        assertTrue(
            "Selected day should only auto-reset when plan anchor changes.",
            source.contains("if (anchor != null && anchor != selectedDayAnchor)")
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
