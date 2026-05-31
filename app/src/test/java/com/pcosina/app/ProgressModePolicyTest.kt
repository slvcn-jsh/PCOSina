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
        assertTrue("Progress screen should surface weekly savings.", text.contains("Weekly savings"))
        assertTrue("Progress screen should surface average daily macros.", text.contains("Average daily macros"))
        assertTrue("Progress tracking cards should be labeled as review-only.", text.contains("For review"))
        assertTrue("Progress screen should clearly label actual spend when present.", text.contains("Actual spend"))
        assertTrue("Progress savings graph should label the plan estimate.", text.contains("Plan estimate"))
        assertTrue("Progress screen should clearly label estimated cost fallback.", text.contains("Estimated cost"))
        assertTrue(
            "Progress macros should explain guide ranges instead of exact pass/fail targets.",
            text.contains("guide ranges, not exact pass/fail")
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
        assertTrue("Progress screen should expose a primary check-in CTA.", text.contains("text = \"Check in\""))
        assertTrue("Progress screen should expose the weekly review CTA.", text.contains("text = \"Review week\""))
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
