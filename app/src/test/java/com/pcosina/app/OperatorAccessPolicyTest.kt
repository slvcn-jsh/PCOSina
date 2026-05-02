package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorAccessPolicyTest {

    @Test
    fun loginScreen_exposesExplicitOperatorAccessMode() {
        val text = readScreen("LoginScreen.kt")
        assertTrue("Login should expose an explicit operator mode entry.", text.contains("Use operator access"))
        assertTrue("Operator mode should keep its own primary CTA copy.", text.contains("Continue to operator tools"))
    }

    @Test
    fun appNavHost_routesPendingOperatorAccess_toHiddenOperatorScreens() {
        val text = readNavigation("AppNavHost.kt")
        assertTrue("Navigation should remember pending operator access intent.", text.contains("pendingOperatorAccess"))
        assertTrue("Authorized operator login should land on MoreTools.", text.contains("activateOperatorMode -> Routes.MoreTools"))
        assertTrue("Operator-only screens should be guarded for unauthorized accounts.", text.contains("Operator access is only available for authorized accounts."))
        assertTrue("Operator access should be checked through the repository endpoint flow.", text.contains("getCurrentUserOperatorAccess(forceRefresh = true)"))
    }

    @Test
    fun authRepository_usesBackendOperatorAccessEndpoint() {
        val text = readRepository("AuthRepository.kt")
        assertTrue("Operator access should call the dedicated backend endpoint.", text.contains("/mobile/operator/access"))
        assertTrue("Operator access verification should include App Check.", text.contains("X-Firebase-AppCheck"))
    }

    @Test
    fun refinedUserShell_noLongerContainsLocalAdminUnlockCopy() {
        val dashboard = readScreen("DashboardRefinedScreen.kt")
        val settings = readScreen("SettingsScreen.kt")
        assertFalse("Refined dashboard should not keep local admin unlock copy.", dashboard.contains("Admin mode enabled."))
        assertFalse("Settings should not instruct users to long-press the header for admin tools.", settings.contains("Long-press the header"))
    }

    private fun readScreen(fileName: String): String =
        String(Files.readAllBytes(resolve("app", "src", "main", "java", "com", "pcosina", "app", "ui", "screens", fileName)))

    private fun readNavigation(fileName: String): String =
        String(Files.readAllBytes(resolve("app", "src", "main", "java", "com", "pcosina", "app", "ui", "navigation", fileName)))

    private fun readRepository(fileName: String): String =
        String(Files.readAllBytes(resolve("app", "src", "main", "java", "com", "pcosina", "app", "data", "repository", fileName)))

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallbackParts = parts.drop(1).toTypedArray()
        val second = Paths.get(fallbackParts.first(), *fallbackParts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
