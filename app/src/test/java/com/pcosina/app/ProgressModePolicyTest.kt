package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
        assertTrue(
            "Progress tuning cards should say when they can affect the next plan.",
            text.contains("Can affect next plan after you apply")
        )
        assertTrue("Progress screen should clearly label actual spend when present.", text.contains("Actual spend"))
        assertTrue("Progress savings graph should label the plan estimate.", text.contains("Plan estimate"))
        assertTrue("Progress screen should clearly label estimated cost fallback.", text.contains("Estimated cost"))
        assertTrue(
            "Progress macros should explain guide ranges instead of exact pass/fail targets.",
            text.contains("guide ranges, not exact pass/fail")
        )
        assertTrue("Progress screen should expose opt-in next-plan adjustments.", text.contains("Next-plan adjustments"))
        assertTrue("Progress screen should persist approved next-plan adjustments.", text.contains("savePlanFeedbackTags(nextTags)"))
        assertTrue(
            "Standalone plan feedback card should stay merged into next-plan adjustments.",
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
