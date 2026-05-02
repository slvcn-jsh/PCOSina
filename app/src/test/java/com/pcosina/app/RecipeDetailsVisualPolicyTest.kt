package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDetailsVisualPolicyTest {

    @Test
    fun recipeDetails_usesUserFacingMealSlotCopyAndDropsLegacyFitCard() {
        val recipePath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "RecipeDetailsScreen.kt"
        )
        val source = read(recipePath)

        assertTrue(
            "Recipe details should normalize slot labels through a user-facing helper.",
            source.contains("private fun userFacingMealSlotLabel(")
        )
        assertTrue(
            "Recipe details should keep the simplified not-in-plan lock state copy.",
            source.contains("text = \"Not in today’s plan\"")
        )
        assertFalse(
            "The legacy fit-summary card should no longer be rendered on the active recipe screen.",
            source.contains("text = if (isRecipeInTodayPlan) \"How this recipe fits today\" else \"How this recipe fits\"")
        )
        assertFalse(
            "The old success-path status center test tag should be removed from the active recipe screen.",
            source.contains("recipe_status_center_card")
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
