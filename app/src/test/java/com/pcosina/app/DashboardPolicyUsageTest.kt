package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPolicyUsageTest {
    @Test
    fun dashboard_usesDisclosureHelpersForStepGating() {
        val file = resolveDashboardFile()
        val text = String(Files.readAllBytes(file))

        assertTrue(text.contains("shouldShowAdvancedMetrics("))
        assertTrue(text.contains("shouldShowAdvancedTools("))
        assertFalse(
            "Use DashboardDisclosure helpers instead of direct guidedStep.stepIndex comparisons.",
            Regex("""guidedStep\.stepIndex\s*[<>!=]=?\s*\d+""").containsMatchIn(text)
        )
    }

    private fun resolveDashboardFile() =
        listOf(
            Paths.get("app", "src", "main", "java", "com", "pcosina", "app", "ui", "screens", "DashboardScreen.kt"),
            Paths.get("src", "main", "java", "com", "pcosina", "app", "ui", "screens", "DashboardScreen.kt")
        ).firstOrNull { Files.exists(it) }
            ?: error("Could not locate DashboardScreen.kt for policy test.")
}
