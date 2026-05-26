package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentCoreFlowPolicyTest {

    @Test
    fun currentCoreInstrumentation_usesSeededCurrentScreensWithoutRetiredBypass() {
        val source = read(resolve("app", "src", "androidTest", "java", "com", "pcosina", "app", "CurrentCoreFlowUiTest.kt"))
        assertTrue(
            "Current core instrumentation should exercise Meal Plan.",
            source.contains("MealPlanScreen")
        )
        assertTrue(
            "Current core instrumentation should exercise Grocery.",
            source.contains("GroceryListScreen")
        )
        assertTrue(
            "Current core instrumentation should exercise Progress.",
            source.contains("ProgressScreen")
        )
        assertTrue(
            "Current core instrumentation should not use the retired debug onboarding bypass.",
            !source.contains("Debug: Continue") &&
                !source.contains("login_debug_continue")
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
