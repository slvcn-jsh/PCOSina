package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapAndGroceryRegressionPolicyTest {

    @Test
    fun recipeDetails_doesNotExposeManualAddToGroceryWhenPlanSyncOwnsGrocery() {
        val recipePath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "RecipeDetailsScreen.kt"
        )
        val source = read(recipePath)

        assertFalse(
            "RecipeDetails should not show a manual add-to-grocery button because generated plans already sync grocery sources.",
            source.contains("Add ingredients to Grocery") ||
                source.contains("addToGroceryAction") ||
                source.contains("groceryViewModel.addItems(items)")
        )
    }

    @Test
    fun mealPlanSwap_updatesMealSourcesOnlyAfterSuccessfulIngredientLookup() {
        val mealPlanPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "MealPlanRefinedScreen.kt"
        )
        val source = read(mealPlanPath)

        assertTrue(
            "Swap flow should load grocery sources and only replace meal items on successful ingredient lookup.",
            source.contains("val sources = swapGrocerySourceLoader?.invoke(option.id)") &&
                source.contains("sources.onSuccess { newSources ->") &&
                source.contains("groceryViewModel.replaceMealItems(mealId, newSources)")
        )
    }

    @Test
    fun weeklyCost_isNotMultipliedAgainOnClient() {
        val dashboardPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "DashboardRefinedScreen.kt"
        )
        val mealPlanPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "MealPlanRefinedScreen.kt"
        )
        val progressPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "ProgressRefinedScreen.kt"
        )

        val dashboard = read(dashboardPath)
        val mealPlan = read(mealPlanPath)
        val progress = read(progressPath)

        assertFalse(
            "Dashboard should not multiply estimatedWeeklyCost after backend calculation.",
            dashboard.contains("estimatedWeeklyCost?.times(")
        )
        assertFalse(
            "MealPlan should not multiply estimatedWeeklyCost after backend calculation.",
            mealPlan.contains("estimatedWeeklyCost\n                                ?.times(") ||
                mealPlan.contains("estimatedWeeklyCost?.times(")
        )
        assertFalse(
            "Progress should not multiply estimatedWeeklyCost after backend calculation.",
            progress.contains("estimatedWeeklyCost\n                ?.times(") ||
                progress.contains("estimatedWeeklyCost\n        ?.times(") ||
                progress.contains("estimatedWeeklyCost?.times(")
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
