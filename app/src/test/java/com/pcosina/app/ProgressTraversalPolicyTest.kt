package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTraversalPolicyTest {

    @Test
    fun progressRefinedSections_defineCurrentWeeklyDashboardStructure() {
        val progressText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "ProgressRefinedScreen.kt"
        )
        assertTrue(progressText.contains("This week"))
        assertTrue(progressText.contains("Weekly budget"))
        assertTrue(progressText.contains("Logged nutrition"))
        assertTrue(progressText.contains("Meal response"))
        assertTrue(!progressText.contains("progress_week_insights_card"))
    }

    @Test
    fun dashboardRefinedHomeCards_replaceLegacyTraversalCards() {
        val dashboardText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "DashboardRefinedScreen.kt"
        )
        assertTrue(dashboardText.contains("Daily Tips"))
        assertTrue(dashboardText.contains("Open Progress"))
        assertTrue(!dashboardText.contains("dashboard_advanced_metrics_card"))
        assertTrue(!dashboardText.contains("dashboard_more_tools_card"))
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
