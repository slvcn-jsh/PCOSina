package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedProfileRoutePolicyTest {

    @Test
    fun guidedProfileRoute_doesNotRedirectToEditMode() {
        val appNavHostPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "navigation", "AppNavHost.kt"
        )
        val source = read(appNavHostPath)

        assertTrue(
            "Guided route mapping should keep incomplete users on onboarding profile route.",
            source.contains("!inferredProfileCompleted && !Routes.isProfileRoute(route) && !Routes.isGoalRoute(route)") &&
                source.contains("navigateInternal(Routes.UserProfile)")
        )
        assertFalse(
            "Guided profile route must not redirect onboarding users to edit mode.",
            source.contains("Routes.UserProfile -> navigateInternal(Routes.UserProfileEdit)")
        )
        assertTrue(
            "Edit mode should stay available only from the settings profile edit action.",
            source.contains("onNavigateToProfileEdit = { navigateInternal(Routes.UserProfileEdit) }")
        )
    }

    @Test
    fun goalSelectionCompletion_clearsSetupRoutesForBothEntryPaths() {
        val appNavHostPath = resolve(
            "app", "src", "main", "java", "com", "pcosina", "app",
            "ui", "navigation", "AppNavHost.kt"
        )
        val source = read(appNavHostPath)

        assertTrue(
            "Goal selection completion should use shared setup back-stack clear helper.",
            source.contains("navController.clearSetupFlowBackStack()")
        )
        assertTrue(
            "Setup clear helper should remove profile route when present.",
            source.contains("popBackStack(Routes.UserProfile, inclusive = true)")
        )
        assertTrue(
            "Setup clear helper should remove goal route when profile route is absent.",
            source.contains("popBackStack(Routes.GoalSelection, inclusive = true)")
        )
        assertTrue(
            "Goal selection completion should return to dashboard tab flow.",
            source.contains("tabNavigationOptions()")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallbackParts = parts.drop(1).toTypedArray()
        val second = Paths.get(fallbackParts.first(), *fallbackParts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
