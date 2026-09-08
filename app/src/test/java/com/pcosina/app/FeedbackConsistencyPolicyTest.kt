package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackConsistencyPolicyTest {

    @Test
    fun dashboardRecipeSettings_keepReusableFeedbackBanners() {
        val dashboardText = readMainSource("com", "pcosina", "app", "ui", "screens", "DashboardRefinedScreen.kt")
        val recipeText = readMainSource("com", "pcosina", "app", "ui", "screens", "RecipeDetailsScreen.kt")
        val settingsText = readMainSource("com", "pcosina", "app", "ui", "screens", "SettingsScreen.kt")

        assertTrue("Dashboard should render AppFeedbackBanner.", dashboardText.contains("AppFeedbackBanner"))
        assertTrue("Dashboard should avoid toast-only feedback for home actions.", !dashboardText.contains("Toast.makeText"))
        assertTrue("Recipe details should render AppFeedbackBanner.", recipeText.contains("AppFeedbackBanner"))
        assertTrue("Recipe details should emit recipe feedback messages.", recipeText.contains("postRecipeFeedback"))
        assertTrue("Recipe details should avoid snackbar-only confirmation flows.", !recipeText.contains("showSnackbar("))
        assertTrue("Settings should render AppFeedbackBanner.", settingsText.contains("AppFeedbackBanner"))
        assertTrue("Settings should emit settings feedback messages.", settingsText.contains("postSettingsFeedback"))
        assertTrue("Settings should avoid toast-only user feedback flows.", !settingsText.contains("Toast.makeText"))
    }

    @Test
    fun groceryMealPlanProgress_useInlineFeedbackState_notLegacyToastFlows() {
        val groceryText = readMainSource("com", "pcosina", "app", "ui", "screens", "GroceryRefinedScreen.kt")
        val mealPlanText = readMainSource("com", "pcosina", "app", "ui", "screens", "MealPlanRefinedScreen.kt")
        val progressText = readMainSource("com", "pcosina", "app", "ui", "screens", "ProgressRefinedScreen.kt")

        assertTrue("Grocery should use inline feedback message state.", groceryText.contains("var feedbackMessage by remember"))
        assertTrue("Grocery should keep current share confirmation copy.", groceryText.contains("Share options opened for your grocery list."))
        assertTrue("Meal Plan should use inline feedback message state.", mealPlanText.contains("var feedbackMessage by remember"))
        assertTrue("Meal Plan should avoid snackbar-only confirmation flows.", !mealPlanText.contains("showSnackbar("))
        assertTrue("Progress should use inline feedback message state.", progressText.contains("var feedbackMessage by remember"))
        assertTrue("Progress should keep plan-tuning confirmation copy.", progressText.contains("Next-plan preferences updated."))
        assertTrue("Progress should avoid toast-only save flows.", !progressText.contains("Toast.makeText"))
    }

    @Test
    fun mealPlanDialogs_useCurrentConfirmAndRetryCopy() {
        val mealPlanText = readMainSource("com", "pcosina", "app", "ui", "screens", "MealPlanRefinedScreen.kt")

        assertTrue(
            "Meal Plan should keep an explicit replace-week confirmation dialog.",
            mealPlanText.contains("Replace this week's plan?") &&
                mealPlanText.contains("Replace week") &&
                mealPlanText.contains("Keep this week")
        )
        assertTrue(
            "Meal Plan should tell the user to keep waiting on long-running planner jobs.",
            mealPlanText.contains("Keep waiting")
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
