package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardHomeCardPolicyTest {

    @Test
    fun dashboard_usesDailyTipsAndKcalFirstMealCards() {
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

        assertTrue(
            "Dashboard should keep the Home tip card aligned to the Daily Tips wording from the mobile design.",
            text.contains("title = \"Daily Tips\"")
        )
        assertTrue(
            "Home meal cards should emphasize kcal values when recipe details are available.",
            text.contains("text = meal.calories.toString()")
        )
        assertFalse(
            "Legacy Logged today/Open recipe footer copy should no longer be the main meal card emphasis.",
            text.contains("text = if (meal.isLogged) \"Logged today\" else \"Open recipe\"")
        )
    }

    @Test
    fun dashboard_usesSharedHeaderChromeForSettingsAndNotifications() {
        val sourceRoot = resolveMainSourceRoot()
        val dashboard = String(
            Files.readAllBytes(
                sourceRoot.resolve(
                    Paths.get(
                        "com",
                        "pcosina",
                        "app",
                        "ui",
                        "screens",
                        "DashboardRefinedScreen.kt"
                    )
                )
            )
        )
        val navHost = String(
            Files.readAllBytes(
                sourceRoot.resolve(
                    Paths.get(
                        "com",
                        "pcosina",
                        "app",
                        "ui",
                        "navigation",
                        "AppNavHost.kt"
                    )
                )
            )
        )

        assertTrue(
            "Dashboard should use the same top header chrome as Meal Plan, Grocery, Progress, and Notifications.",
            dashboard.contains("SharedTopHeader(")
        )
        assertTrue(
            "Dashboard header notification action should be explicit instead of reusing the support/More Tools route.",
            dashboard.contains("onNotifications = onOpenNotifications")
        )
        assertFalse(
            "Dashboard should not keep the legacy private brand header that drifted from shared icon assets.",
            dashboard.contains("RefinedBrandHeader(")
        )
        assertFalse(
            "Dashboard should not use the old small settings asset in the shared header position.",
            dashboard.contains("pcosina_svg_44_settings")
        )
        assertFalse(
            "Dashboard should not render the support tab icon as the top notification action.",
            dashboard.contains("pcosina_nav_support_clean")
        )
        assertTrue(
            "Home header notification button should open the Notifications route.",
            navHost.contains("onOpenNotifications = { navigateInternal(Routes.Notifications) }")
        )
        assertFalse(
            "Home header notification button should not route through the Support/IPO tab.",
            navHost.contains("onOpenMoreTools = { navigateInternal(Routes.Ipo)")
        )
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
