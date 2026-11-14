package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class CiUxCriticalFlowPolicyTest {

    @Test
    fun ciWorkflow_runsCurrentCoreAndReminderCriticalInstrumentation() {
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
            "Critical flow gate should run current core instrumentation test.",
            source.contains("com.pcosina.app.CurrentCoreFlowUiTest")
        )
        assertTrue(
            "Critical flow gate should run reminder-enabled proof instrumentation test.",
            source.contains(
                "com.pcosina.app.MealPlanSwapBannerUiTest#statusCenter_showsReminderSummaryWhenNotificationsEnabled"
            )
        )
        assertTrue(
            "Emulator-runner Gradle commands must not pass a literal line-continuation token as a task.",
            !source.contains(":app:connectedDebugAndroidTest \\")
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
