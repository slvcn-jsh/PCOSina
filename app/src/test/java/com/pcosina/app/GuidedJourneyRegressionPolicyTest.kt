package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedJourneyRegressionPolicyTest {

    @Test
    fun clearPlanHistory_clearsPersistedReviewedWeekKey() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
            "com",
            "pcosina",
            "app",
            "data",
            "repository",
            "UserPreferencesRepository.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue(
            "clearPlanHistory must remove persisted last reviewed week key.",
            text.contains("preferences.remove(Keys.lastReviewedWeek(userId))")
        )
    }

    @Test
    fun guidedProfileNavigation_doesNotUseEditRouteMapping() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
            "com",
            "pcosina",
            "app",
            "ui",
            "navigation",
            "AppNavHost.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertFalse(
            "Guided route mapping must not send Routes.UserProfile to Routes.UserProfileEdit.",
            text.contains("Routes.UserProfile -> navigateInternal(Routes.UserProfileEdit)")
        )
        assertTrue(
            "Guided route guardrail should send incomplete users to the non-edit profile route.",
            text.contains("!inferredProfileCompleted && !Routes.isProfileRoute(route) && !Routes.isGoalRoute(route)") &&
                text.contains("navigateInternal(Routes.UserProfile)")
        )
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for guided journey regression tests.")
    }
}
