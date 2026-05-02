package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityObserverPolicyTest {

    @Test
    fun dashboardGroceryMealPlanProgress_useSharedConnectivityObserver() {
        val dashboard = readMainSource("com", "pcosina", "app", "ui", "screens", "DashboardRefinedScreen.kt")
        val grocery = readMainSource("com", "pcosina", "app", "ui", "screens", "GroceryRefinedScreen.kt")
        val mealPlan = readMainSource("com", "pcosina", "app", "ui", "screens", "MealPlanRefinedScreen.kt")
        val progress = readMainSource("com", "pcosina", "app", "ui", "screens", "ProgressRefinedScreen.kt")
        val connectivityUtil = readMainSource("com", "pcosina", "app", "ui", "util", "ConnectivityState.kt")

        assertTrue("Dashboard should rely on shared online observer.", dashboard.contains("rememberIsOnline(context)"))
        assertTrue("Grocery should rely on shared online observer.", grocery.contains("rememberIsOnline(context)"))
        assertTrue("Meal Plan should rely on shared online observer.", mealPlan.contains("rememberIsOnline(context)"))
        assertTrue("Progress should rely on shared online observer.", progress.contains("rememberIsOnline(context)"))
        assertTrue("Shared utility should expose network callback observer.", connectivityUtil.contains("registerDefaultNetworkCallback"))
        assertTrue(
            "Meal Plan should avoid local network helpers after observer centralization.",
            !mealPlan.contains("private fun isNetworkAvailable(")
        )
    }

    @Test
    fun refinedConnectivityBranches_useCurrentScreenSpecificFeedback() {
        val dashboard = readMainSource("com", "pcosina", "app", "ui", "screens", "DashboardRefinedScreen.kt")
        val grocery = readMainSource("com", "pcosina", "app", "ui", "screens", "GroceryRefinedScreen.kt")
        val mealPlan = readMainSource("com", "pcosina", "app", "ui", "screens", "MealPlanRefinedScreen.kt")
        val progress = readMainSource("com", "pcosina", "app", "ui", "screens", "ProgressRefinedScreen.kt")

        assertTrue(
            "Dashboard should branch first-plan CTA on offline state with internet-required copy.",
            dashboard.contains("!hasPlan && !isOnline") &&
                dashboard.contains("ActionFeedbackCopy.InternetRequired")
        )
        assertTrue(
            "Grocery should propagate reactive online state into the refined overview shell.",
            grocery.contains("val isOnline = onlineStateOverride ?: observedOnline") &&
                grocery.contains("online = isOnline")
        )
        assertTrue(
            "Meal plan should block swap actions with explicit internet-required feedback.",
            mealPlan.contains("if (!isOnline)") &&
                mealPlan.contains("Internet required for meal swaps.")
        )
        assertTrue(
            "Progress should propagate reactive online state into the refined weekly hero.",
            progress.contains("val isOnline = onlineStateOverride ?: observedOnline") &&
                progress.contains("online = isOnline")
        )
    }

    @Test
    fun refinedCriticalScreens_avoidLegacyMinTouchTokenForPrimaryChips() {
        val dashboard = readMainSource("com", "pcosina", "app", "ui", "screens", "DashboardRefinedScreen.kt")
        val grocery = readMainSource("com", "pcosina", "app", "ui", "screens", "GroceryRefinedScreen.kt")
        val progress = readMainSource("com", "pcosina", "app", "ui", "screens", "ProgressRefinedScreen.kt")
        val tokenizedChips = readMainSource("com", "pcosina", "app", "ui", "components", "TokenizedChips.kt")

        assertTrue(
            "Dashboard critical controls should not use UiChipTokens.MinTouchHeight.",
            !dashboard.contains("UiChipTokens.MinTouchHeight")
        )
        assertTrue(
            "Grocery critical controls should not use UiChipTokens.MinTouchHeight.",
            !grocery.contains("UiChipTokens.MinTouchHeight")
        )
        assertTrue(
            "Progress critical controls should not use UiChipTokens.MinTouchHeight.",
            !progress.contains("UiChipTokens.MinTouchHeight")
        )
        assertTrue(
            "Tokenized chips should enforce 48dp minimum height.",
            tokenizedChips.contains("heightIn(min = 48.dp)")
        )
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
            ?: error("Could not locate main source root for connectivity observer policy test.")
    }
}
