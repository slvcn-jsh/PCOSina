package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiUxReportCleanupPolicyTest {

    @Test
    fun onboarding_keepsKeyboardSafeFooterAndChipOnlyAllergies() {
        val source = readMain("ui", "screens", "UserProfileScreen.kt")

        assertTrue("Onboarding should keep the footer visible above the keyboard.", source.contains(".imePadding()"))
        assertTrue("Onboarding first steps should use Continue.", source.contains("1 -> \"Continue\""))
        assertTrue("Onboarding final step should still complete the profile.", source.contains("\"Complete profile\""))
        assertTrue("Allergy saving should be constrained to supported chips.", source.contains("CommonAllergyTokens"))
        assertFalse("Onboarding should not keep a free-text allergy field.", source.contains("Add allergy not listed"))
    }

    @Test
    fun grocery_keepsPantryAddInsidePantryDialogOnly() {
        val source = readMain("ui", "screens", "GroceryRefinedScreen.kt")

        assertTrue("Grocery should expose one add/view pantry entry point.", source.contains("Add / View Pantry Items"))
        assertTrue("Pantry removal label should be centered.", source.contains("textAlign = TextAlign.Center"))
        assertTrue(
            "Pantry removal control should keep a bounded width so item details remain visible.",
            source.contains(".widthIn(min = 76.dp, max = 88.dp)")
        )
        assertFalse("Grocery should not keep the duplicate pantry quick-add card.", source.contains("GroceryBottomCtaCard"))
        assertFalse("Grocery should not keep the removed add-pantry section copy.", source.contains("Add an item to the pantry"))
    }

    @Test
    fun mealPlan_usesSelectedDayNutritionForEstimatedDailyIntake() {
        val screen = readMain("ui", "screens", "MealPlanRefinedScreen.kt")
        val viewModel = readMain("ui", "MealPlanViewModel.kt")
        val dialog = readMain("ui", "components", "MealCheckInDialog.kt")

        assertTrue("Meal Plan should label estimated intake as the selected day's total.", screen.contains("Total for this day's planned meals."))
        assertTrue("Meal Plan should calculate displayed macros from selected-day recipe details.", screen.contains("selectedDayDetails.sumOf { it.proteinGrams ?: 0 }"))
        assertTrue("Meal Plan should display selected-day kcal in a static value tile.", screen.contains("PlanIntakeTile(") && screen.contains("\"\$dayCalories kcal\""))
        assertFalse("Meal Plan should not keep the old unclear kcal ring.", screen.contains("RefinedRingMeter("))
        assertFalse("Meal check-in should not ask for actual spend.", dialog.contains("Actual spend"))
        assertFalse("Meal check-in should not ask for today's weight.", dialog.contains("Today's weight"))
        assertFalse("Meal check-in should not ask for optional notes.", dialog.contains("Optional note"))
        assertTrue("Meal Plan metrics should fall back to planner explanation values.", viewModel.contains("fallbackMetrics"))
        assertTrue("Fresh plans should use the exact startDate sent to the planner.", viewModel.contains("parsePlanDate(activeAttempt.startDate)"))
    }

    @Test
    fun settings_savedProfileListsCanBeInspectedWithoutEditing() {
        val source = readMain("ui", "screens", "SettingsScreen.kt")

        assertTrue("Saved profile lists should open a details dialog.", source.contains("SavedProfileDetailsDialog("))
        assertTrue("Food rules should expose their saved values.", source.contains("title = \"Saved food rules\""))
        assertTrue("Allergies should expose their saved values.", source.contains("title = \"Saved allergies\""))
        assertTrue("Pantry items should expose names, amounts, and dates when available.", source.contains("formatSavedPantryEntry"))
    }

    @Test
    fun progress_keepsOneClearPathToEachWeeklyDetail() {
        val source = readMain("ui", "screens", "ProgressRefinedScreen.kt")

        assertFalse("Progress should not repeat a separate legacy meal summary above the weekly dashboard.", source.contains("ProgressHeadlineCard("))
        assertFalse("Progress should not repeat Support when Support is already in navigation.", source.contains("ProgressSupportCtaCard("))
        assertFalse("Weekly highlights should not occupy a separate top-level card.", source.contains("ProgressWeeklyHighlightsLauncher("))
        assertTrue("Progress should show a direct weekly dashboard instead of hiding adherence inside dropdown details.", source.contains("ProgressThisWeekDashboard("))
        assertTrue("BMI marker color should follow the calculated category.", source.contains("indicatorColor = indicatorColor"))
    }

    private fun readMain(vararg parts: String): String =
        read(resolve("app", "src", "main", "java", "com", "pcosina", "app", *parts))

    private fun read(path: Path): String = String(Files.readAllBytes(path))

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
