package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityObserverPolicyTest {

    @Test
    fun dashboardGroceryProgress_useSharedConnectivityObserver() {
        val dashboard = readMainSource("com", "pcosina", "app", "ui", "screens", "DashboardScreen.kt")
        val grocery = readMainSource("com", "pcosina", "app", "ui", "screens", "GroceryListScreen.kt")
        val mealPlan = readMainSource("com", "pcosina", "app", "ui", "screens", "MealPlanScreen.kt")
        val progress = readMainSource("com", "pcosina", "app", "ui", "screens", "ProgressScreen.kt")
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
    fun offlineBranching_usesStandardizedCopyForBlockedAndQueuedActions() {
        val dashboard = readMainSource("com", "pcosina", "app", "ui", "screens", "DashboardScreen.kt")
        val grocery = readMainSource("com", "pcosina", "app", "ui", "screens", "GroceryListScreen.kt")
        val progress = readMainSource("com", "pcosina", "app", "ui", "screens", "ProgressScreen.kt")

        assertTrue(
            "Dashboard should branch first-plan CTA on offline state with internet-required copy.",
            dashboard.contains("!hasPlan && !isOnline") &&
                dashboard.contains("ActionFeedbackCopy.InternetRequired")
        )
        assertTrue(
            "Grocery should display standardized offline and internet-required action copy.",
            grocery.contains("if (!isOnline)") &&
                grocery.contains("ActionFeedbackCopy.OfflineSync") &&
                grocery.contains("ActionFeedbackCopy.InternetRequired")
        )
        assertTrue(
            "Progress retry/queue logic should branch on offline state with queue copy.",
            progress.contains("if (!isOnline)") &&
                progress.contains("ActionFeedbackCopy.QueueSaved") &&
                progress.contains("ActionFeedbackCopy.QueueRetrying")
        )
    }

    @Test
    fun criticalScreens_avoidLegacyMinTouchTokenForPrimaryChips() {
        val dashboard = readMainSource("com", "pcosina", "app", "ui", "screens", "DashboardScreen.kt")
        val grocery = readMainSource("com", "pcosina", "app", "ui", "screens", "GroceryListScreen.kt")
        val progress = readMainSource("com", "pcosina", "app", "ui", "screens", "ProgressScreen.kt")
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
