package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPrimaryActionPolicyTest {

    @Test
    fun dashboard_usesSinglePrimaryNextStepCardCopy() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "DashboardRefinedScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue("Dashboard should show a clear primary next-step card.", text.contains("RefinedPrimaryActionCard("))
        assertTrue("Dashboard should keep primary action copy centralized.", text.contains("val primaryActionTitle = when"))
        assertFalse("Legacy duplicate next-step title should be removed.", text.contains("NEXT OPTIMIZED MEAL"))
        assertFalse("Next-week planning copy belongs on Meal Plan, not Home.", text.contains("Plan your next week"))
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for dashboard policy test.")
    }
}
