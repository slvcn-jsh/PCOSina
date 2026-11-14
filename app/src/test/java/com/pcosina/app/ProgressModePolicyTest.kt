package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressModePolicyTest {

    @Test
    fun progressScreen_exposesWeeklyDashboardSections_andPrimaryCta() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "ProgressRefinedScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue("Progress screen should surface the production weekly dashboard.", text.contains("This week"))
        assertTrue("Progress screen should surface the simplified weekly budget section.", text.contains("Weekly budget"))
        assertTrue("Progress screen should surface logged nutrition instead of planned macro progress.", text.contains("Logged nutrition"))
        assertTrue("Progress screen should replace symptom trend claims with meal response.", text.contains("Meal response"))
        assertFalse("Progress should not repeat the vague For review label on every card.", text.contains("For review"))
        assertFalse(
            "Progress should not show developer-oriented review-only explanation before the history calendar.",
            text.contains("They do not change the next plan by themselves.")
        )
        assertTrue("Progress should keep the history calendar always visible.", text.contains("text = \"History Calendar\""))
        assertTrue("Progress should avoid trend bars until enough check-ins exist.", text.contains("Complete at least 3 meal check-ins"))
        assertTrue("Progress screen should clearly label actual spend when present.", text.contains("Actual spend"))
        assertTrue("Progress budget summary should label estimated cost.", text.contains("Plan estimate"))
        assertTrue("Progress budget summary should label estimated remaining budget.", text.contains("Estimated remaining"))
        assertTrue(
            "Progress nutrition should be explicit that logged nutrition is estimated from logged planned meals.",
            text.contains("Estimated from \${summary.loggedMeals} logged planned meal")
        )
        assertFalse("Progress screen should not show the confusing standalone next-plan adjustment card.", text.contains("progress_next_plan_adjustment_card"))
        assertFalse("Progress screen should not show the confusing standalone next-plan adjustment title.", text.contains("Next-plan adjustments"))
        assertTrue("Progress screen should keep plan feedback inside weekly review.", text.contains("Help tune your next plan"))
        assertTrue("Progress weekly review should still persist feedback tags.", text.contains("togglePlanFeedbackTag(tag)"))
        assertFalse("Progress screen should not render the Today/Week toggle.", text.contains("ProgressModeSelector("))
        assertFalse("Progress screen should not expose the old Today mode test tag.", text.contains("progress_mode_today"))
        assertFalse("Progress screen should not expose the old Week mode test tag.", text.contains("progress_mode_week"))
        assertTrue(
            "Standalone plan feedback card should not return as a separate progress card.",
            !text.contains("progress_plan_feedback_card")
        )
        assertTrue(
            "Advanced week analytics should not auto-expand only because Week mode is selected.",
            !text.contains("savedAdvancedWeekAnalyticsExpanded || showWeekMode")
        )
        assertFalse("Progress screen should not expose the removed Today check-in CTA.", text.contains("text = \"Check in\""))
        assertTrue("Progress screen should expose the weekly review CTA.", text.contains("else \"Review week\""))
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for progress mode policy test.")
    }
}
