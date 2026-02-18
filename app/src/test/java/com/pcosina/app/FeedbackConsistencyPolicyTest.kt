package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackConsistencyPolicyTest {

    @Test
    fun dashboardGroceryMealPlanRecipeSettings_useSharedFeedbackBanners() {
        val dashboardText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "DashboardScreen.kt"
        )
        val groceryText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "GroceryListScreen.kt"
        )
        val mealPlanText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "MealPlanScreen.kt"
        )
        val recipeText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "RecipeDetailsScreen.kt"
        )
        val settingsText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "SettingsScreen.kt"
        )
        assertTrue("Dashboard should render AppFeedbackBanner.", dashboardText.contains("AppFeedbackBanner"))
        assertTrue("Dashboard should emit dashboard feedback messages.", dashboardText.contains("postDashboardFeedback"))
        assertTrue(
            "Dashboard should avoid toast-only feedback for admin actions.",
            !dashboardText.contains("Toast.makeText")
        )
        assertTrue("Grocery should render AppFeedbackBanner.", groceryText.contains("AppFeedbackBanner"))
        assertTrue("Grocery should emit grocery feedback messages.", groceryText.contains("postGroceryFeedback"))
        assertTrue("Meal Plan should render AppFeedbackBanner.", mealPlanText.contains("AppFeedbackBanner"))
        assertTrue("Meal Plan should emit inline meal-plan feedback messages.", mealPlanText.contains("postMealPlanFeedback"))
        assertTrue(
            "Meal Plan should avoid snackbar-only confirmation flows.",
            !mealPlanText.contains("showSnackbar(")
        )
        assertTrue("Recipe details should render AppFeedbackBanner.", recipeText.contains("AppFeedbackBanner"))
        assertTrue("Recipe details should emit recipe feedback messages.", recipeText.contains("postRecipeFeedback"))
        assertTrue(
            "Recipe details should avoid snackbar-only confirmation flows.",
            !recipeText.contains("showSnackbar(")
        )
        assertTrue("Settings should render AppFeedbackBanner.", settingsText.contains("AppFeedbackBanner"))
        assertTrue("Settings should emit settings feedback messages.", settingsText.contains("postSettingsFeedback"))
        assertTrue(
            "Settings should avoid toast-only user feedback flows.",
            !settingsText.contains("Toast.makeText")
        )
    }

    @Test
    fun progressUsesNormalizedOfflineCopy() {
        val progressText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "ProgressScreen.kt"
        )
        assertTrue("Progress should use normalized online feedback copy.", progressText.contains("ActionFeedbackCopy.OnlineSync"))
        assertTrue("Progress should use normalized offline feedback copy.", progressText.contains("ActionFeedbackCopy.OfflineSync"))
    }

    @Test
    fun mealPlanDialogs_useHeadingAndActionRowSemantics() {
        val mealPlanText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "MealPlanScreen.kt"
        )

        assertTrue(
            "Meal Plan dialogs should use semantic heading markers for accessibility.",
            mealPlanText.contains("modifier = Modifier.semantics { heading() }")
        )
        assertTrue(
            "Meal Plan dialogs should use shared action-row button semantics.",
            mealPlanText.contains("MealPlanDialogGotItButton")
        )
        assertTrue(
            "Dialog action row should include traversal order semantics.",
            mealPlanText.contains("traversalIndex = 1f")
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
            ?: error("Could not locate main source root for feedback consistency policy test.")
    }
}
