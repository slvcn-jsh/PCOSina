package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapAndGroceryRegressionPolicyTest {

    @Test
    fun recipeDetails_addToGroceryStoresBaseQuantitiesOnly() {
        val recipePath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "RecipeDetailsScreen.kt"
        )
        val source = read(recipePath)
        val actionStart = source.indexOf("val addToGroceryAction: () -> Unit = {")
        assertTrue("RecipeDetails should keep addToGroceryAction.", actionStart >= 0)
        val actionEnd = source.indexOf("groceryViewModel.addItems(items)", startIndex = actionStart)
        assertTrue("RecipeDetails should add mapped items into GroceryViewModel.", actionEnd > actionStart)
        val actionBlock = source.substring(actionStart, actionEnd)

        assertTrue(
            "RecipeDetails should store base ingredient quantities and let Grocery aggregation scale later.",
            actionBlock.contains("it.quantity")
        )
        assertFalse(
            "RecipeDetails should not pre-scale grocery quantities before storing them.",
            actionBlock.contains("scaleQuantityText(")
        )
    }

    @Test
    fun mealPlanSwap_clearsStaleMealSourcesWhenIngredientLookupFails() {
        val mealPlanPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "MealPlanScreen.kt"
        )
        val source = read(mealPlanPath)

        assertTrue(
            "Swap fallback should clear the old meal grocery sources when ingredient details are unavailable.",
            source.contains("groceryViewModel.replaceMealItems(mealId, emptyList())")
        )
    }

    @Test
    fun householdAwareWeeklyCost_isNotScaledAgainOnClient() {
        val dashboardPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "DashboardScreen.kt"
        )
        val mealPlanPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "MealPlanScreen.kt"
        )
        val progressPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "screens", "ProgressScreen.kt"
        )

        val dashboard = read(dashboardPath)
        val mealPlan = read(mealPlanPath)
        val progress = read(progressPath)

        assertFalse(
            "Dashboard should not multiply estimatedWeeklyCost by household size after backend scaling.",
            dashboard.contains("estimatedWeeklyCost?.times(profile.householdSize.coerceIn(1, 6))")
        )
        assertFalse(
            "MealPlan should not multiply estimatedWeeklyCost by household size after backend scaling.",
            mealPlan.contains("estimatedWeeklyCost\n                                ?.times(userProfile.householdSize.coerceIn(1, 6))")
        )
        assertFalse(
            "Progress should not multiply estimatedWeeklyCost by household size after backend scaling.",
            progress.contains("estimatedWeeklyCost\n                ?.times(profile.householdSize.coerceIn(1, 6))") ||
                progress.contains("estimatedWeeklyCost\n        ?.times(profile.householdSize.coerceIn(1, 6))")
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
