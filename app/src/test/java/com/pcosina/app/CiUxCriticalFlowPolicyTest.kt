package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class CiUxCriticalFlowPolicyTest {

    @Test
    fun ciWorkflow_runsFirstWinAndReminderCriticalInstrumentation() {
        val source = read(resolve(".github", "workflows", "ci.yml"))

        assertTrue(
            "CI workflow should define dedicated UX critical flow gates.",
            source.contains("ux-critical-flow:")
        )
        assertTrue(
            "Critical flow gate should run on pinned API 34 emulator.",
            source.contains("name: UX Critical Flow Gates (API 34)") &&
                source.contains("api-level: 34")
        )
        assertTrue(
            "Critical flow gate should run first-win end-to-end instrumentation test.",
            source.contains("com.pcosina.app.FirstWinFlowUiTest")
        )
        assertTrue(
            "Critical flow gate should provide explicit first-win timeout argument.",
            source.contains("first_win_timeout_ms=90000")
        )
        assertTrue(
            "Critical flow gate should run reminder-enabled proof instrumentation test.",
            source.contains(
                "com.pcosina.app.MealPlanSwapBannerUiTest#statusCenter_showsReminderSummaryWhenNotificationsEnabled"
            )
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
