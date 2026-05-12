package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAnchoringPolicyTest {

    @Test
    fun repository_sendsStartDateWithGeneratePlanRequests() {
        val repo = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "repository", "MealPlanRepository.kt"
            )
        )
        val api = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "data", "api", "PcosinaApiService.kt"
            )
        )

        assertTrue("GeneratePlanRequest should expose startDate for anchored 7-day plans.", api.contains("val startDate: String? = null"))
        assertTrue(
            "MealPlanRepository should send today's ISO date as the plan start anchor.",
            repo.contains("startDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)") &&
                repo.contains("startDate = attempt.startDate")
        )
    }

    @Test
    fun mealPlanViewModel_anchorsFreshPlansFromRequestedTimestamp_andPrefersNewestOverlap() {
        val viewModel = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "MealPlanViewModel.kt"
            )
        )

        assertTrue(
            "Fresh plans should anchor from backend-requested time to avoid midnight rollover drift.",
            viewModel.contains("response.timestamps?.requestedAtMs")
        )
        assertTrue(
            "Overlapping plans should prefer the newest generated plan instead of the first overlap.",
            viewModel.contains("overlappingPlans.maxByOrNull { it.generatedAt }")
        )
    }

    @Test
    fun progressScreen_keepsTransientImpactStateOutOfRememberSaveable() {
        val progress = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "ProgressRefinedScreen.kt"
            )
        )

        assertFalse(
            "Progress screen should not keep the removed legacy MealImpactSummary saveable state.",
            progress.contains("rememberSaveable { mutableStateOf<MealImpactSummary?>(null) }")
        )
        assertTrue(
            "Progress screen should keep the current weekly dashboard sections in code.",
            progress.contains("Weekly savings") &&
                progress.contains("Average daily macros")
        )
    }

    @Test
    fun mealPlanScreen_usesFullWeekStripInsteadOfJumpChips() {
        val mealPlan = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "MealPlanRefinedScreen.kt"
            )
        )

        assertTrue("MealPlan screen should render the whole week strip directly.", mealPlan.contains("dates.forEachIndexed"))
        assertTrue(
            "MealPlan screen should mark selected and today within the strip using compact indicator bars.",
            mealPlan.contains("isToday -> PcosinaSoftPink") &&
                mealPlan.contains("selected -> PcosinaPink")
        )
        assertFalse("MealPlan screen should not keep legacy Jump to Today copy.", mealPlan.contains("Jump to Today"))
    }

    @Test
    fun mealPlanScreen_avoidsConfusingTopStats_andRestoresMealCheckIns() {
        val mealPlan = read(
            resolve(
                "app", "src", "main", "java", "com", "pcosina", "app",
                "ui", "screens", "MealPlanRefinedScreen.kt"
            )
        )

        assertFalse(
            "MealPlan screen should not keep the old fit-confidence or estimated-cost chips at the top.",
            mealPlan.contains("text = \"Fit \$it%\"") ||
                mealPlan.contains("text = \"₱\$it est.\"")
        )
        assertTrue(
            "MealPlan screen should bring the meal check-in dialog back into direct meal logging.",
            mealPlan.contains("MealCheckInDialog(") &&
                mealPlan.contains("saveMealCheckIn(")
        )
        assertTrue(
            "MealPlan screen should steer users back to weekly progress before replacing an in-progress week.",
            mealPlan.contains("Review this week in Progress before starting a new one.")
        )
        assertTrue(
            "MealPlan logging should require confirmation before persistence.",
            mealPlan.contains("mealLogConfirmationPrompt") &&
                mealPlan.contains("Log this meal?") &&
                mealPlan.contains("markMealAsEaten(")
        )
        assertTrue(
            "MealPlan should not allow logged meals to be swapped.",
            mealPlan.contains("Logged meals are locked and cannot be swapped.") &&
                mealPlan.contains("modifier = Modifier.clickable(enabled = !logged, onClick = onSwap)")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallback = Paths.get(parts.drop(1).first(), *parts.drop(2).toTypedArray())
        if (Files.exists(fallback)) return fallback
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
