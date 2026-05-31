package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDetailsSlotLoggingPolicyTest {

    @Test
    fun recipeDetails_disablesMarkAsEatenOnlyWhenAllSlotsForRecipeAreLogged() {
        val recipePath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "RecipeDetailsScreen.kt"
        )
        val source = read(recipePath)

        assertTrue(
            "Recipe details should compute remaining slots for the current recipe.",
            source.contains("val remainingRecipeSlots = remember(remainingTodaySlots, recipeId)")
        )
        assertTrue(
            "Recipe details should support meal-label hints for slot-accurate logging.",
            source.contains("val hintedRemainingSlot = remember(remainingTodaySlots, recipeId, mealLabelHint)")
        )
        assertTrue(
            "Recipe details should normalize meal labels before matching slots.",
            source.contains("normalizeMealLabel(")
        )
        assertTrue(
            "Recipe details should still fall back to recipe-level remaining slot matching.",
            source.contains("val slotToLog = hintedRemainingSlot ?: remainingRecipeSlots.firstOrNull")
        )
        assertTrue(
            "Recipe details should disable logging from completed slot data.",
            source.contains("completedRemainingTodaySlots.none { slot ->")
        )
        assertTrue(
            "Recipe details should detect when the selected planned slot was skipped.",
            source.contains("val selectedSlotSkipped = remember(") &&
                source.contains("progressViewModel.isMealSkipped(")
        )
        assertTrue(
            "Recipe details should keep skipped planned meals locked until the user undoes Skip from Plan.",
            source.contains("Undo Skip from Plan before logging it") &&
                source.contains("enabled = !alreadyLoggedToday && !selectedSlotSkipped")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
