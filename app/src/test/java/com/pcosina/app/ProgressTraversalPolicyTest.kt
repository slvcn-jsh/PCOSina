package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTraversalPolicyTest {

    @Test
    fun progressWeekCards_defineTraversalGroupsForTalkBackOrder() {
        val progressText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "ProgressScreen.kt"
        )
        assertTrue(progressText.contains("progress_week_insights_card"))
        assertTrue(progressText.contains("progress_week_spending_card"))
        assertTrue(progressText.contains("progress_week_macro_card"))
        assertTrue(progressText.contains("progress_plan_feedback_card"))
        assertTrue(progressText.contains("traversalIndex = 5f"))
        assertTrue(progressText.contains("traversalIndex = 6f"))
        assertTrue(progressText.contains("traversalIndex = 7f"))
        assertTrue(progressText.contains("traversalIndex = 8f"))
    }

    @Test
    fun dashboardSecondaryCards_defineTraversalGroups() {
        val dashboardText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "DashboardScreen.kt"
        )
        assertTrue(dashboardText.contains("dashboard_advanced_metrics_card"))
        assertTrue(dashboardText.contains("dashboard_more_tools_card"))
        assertTrue(dashboardText.contains("traversalIndex = 7f"))
        assertTrue(dashboardText.contains("traversalIndex = 8f"))
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
            ?: error("Could not locate main source root for progress traversal policy test.")
    }
}
