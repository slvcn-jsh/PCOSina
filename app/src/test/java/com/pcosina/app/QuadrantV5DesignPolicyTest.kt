package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadrantV5DesignPolicyTest {

    @Test
    fun onboarding_usesFlatThreeStepFormAndDeviceSaveFooter() {
        val source = readMain("ui", "screens", "UserProfileScreen.kt")

        assertTrue(source.contains("\"PROFILE ONBOARDING\""))
        assertTrue(source.contains("listOf(\"Personal Details\", \"Symptoms\", \"Preferences\")"))
        assertTrue(source.contains("private val ProfileFieldShape = RoundedCornerShape(4.dp)"))
        assertTrue(source.contains("Your progress is saved automatically on this device."))
        assertTrue(source.contains("label = \"None of the above\""))
        assertTrue(source.contains("RequiredProfileLabel(\"Weight\")"))
        assertTrue(source.contains("ProfileUnitDropdown("))
        assertFalse(source.contains("Target weight"))
        assertFalse(source.contains("Target date"))
        assertFalse(source.contains("Pantry Items"))
        assertFalse(source.contains("RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)"))
    }

    @Test
    fun goals_useOneExclusiveSelectionAndProposedTargetArtwork() {
        val source = readMain("ui", "screens", "GoalSelectionScreen.kt")

        assertTrue(source.contains("Choose one. You can change this later."))
        assertTrue(source.contains("var selectedGoalName by rememberSaveable"))
        assertTrue(source.contains("selectedGoalName = GoalOption.WeightLoss.name"))
        assertTrue(source.contains("goalTextFromOptions(setOf(goal))"))
        assertTrue(source.contains("R.drawable.pcosina_svg_20_goal"))
        assertTrue(source.contains("Text(\"Save Goals\""))
        assertFalse(source.contains("var weightLoss by rememberSaveable"))
    }

    @Test
    fun dashboard_componentsUseLivePlanDataAndRealRoutes() {
        val dashboard = readMain("ui", "screens", "DashboardRefinedScreen.kt")
        val plan = readMain("ui", "screens", "MealPlanRefinedScreen.kt")
        val support = readMain("ui", "screens", "CommunityScreen.kt")

        assertFalse(dashboard.contains("dashboard_daily_intake_card"))
        assertFalse(dashboard.contains("dashboard_next_week_card"))
        assertFalse(dashboard.contains("dashboard_settings_card"))
        assertTrue(dashboard.contains("todaySnapshot.nextMeal != null -> \"Your next meal is ready\""))
        assertTrue(dashboard.contains("onNavigateToRoute(Routes.GroceryList)"))
        assertTrue(plan.contains("PlanEstimatedDailyIntakeCard("))
        assertTrue(plan.contains("selectedDayDetails.sumOf { it.proteinGrams ?: 0 }"))
        assertTrue(plan.contains("testTag(\"mealplan_daily_intake_card\")"))
        assertTrue(support.contains("SupportSettingsCard("))
        assertTrue(support.contains("text = \"Manage your experience\""))
        assertTrue(support.contains("Text(\"Go to Settings\""))
    }

    @Test
    fun recipeLoadingUsesProposedOfflineFirstArtworkTreatment() {
        val source = readMain("ui", "screens", "RecipeDetailsScreen.kt")

        assertTrue(source.contains("R.drawable.login_heart_hands"))
        assertTrue(source.contains("OFFLINE-FIRST FILIPINO PCOS MEAL\\nPLANNING"))
        assertTrue(source.contains("Getting the ingredients, nutrition, and\\ncooking steps ready."))
        assertTrue(source.contains("LinearProgressIndicator("))
        assertTrue(source.contains(".background(Color(0xFFF87588))"))
    }

    private fun readMain(vararg parts: String): String =
        String(Files.readAllBytes(resolve("app", "src", "main", "java", "com", "pcosina", "app", *parts)))

    private fun resolve(vararg parts: String): Path {
        val direct = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(direct)) return direct
        val parent = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(parent)) return parent
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
