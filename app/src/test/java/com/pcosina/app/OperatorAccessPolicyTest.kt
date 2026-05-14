package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorAccessPolicyTest {

    @Test
    fun loginScreen_usesGoogleSignInForBackendResolvedOperatorAccess() {
        val text = readScreen("LoginScreen.kt")
        assertTrue("Login should keep the Google sign-in CTA.", text.contains("Continue with Google"))
        assertTrue("Login should preserve operator access intent for backend resolution.", text.contains("operatorAccessRequested = true"))
        assertTrue("Login success should pass operator intent to navigation.", text.contains("onLoginSuccess(operatorAccessRequested)"))
    }

    @Test
    fun appNavHost_routesPendingOperatorAccess_toHiddenOperatorScreens() {
        val text = readNavigation("AppNavHost.kt")
        assertTrue("Navigation should remember pending operator access intent.", text.contains("pendingOperatorAccess"))
        assertTrue("Authorized operator login should land on the operator dashboard.", text.contains("activateOperatorMode -> Routes.OperatorDashboard"))
        assertTrue("Operator-only screens should be guarded for unauthorized accounts.", text.contains("Admin access is only available for authorized accounts."))
        assertTrue("Operator access should be checked through the repository endpoint flow.", text.contains("getCurrentUserOperatorAccess(forceRefresh = true)"))
        assertTrue(
            "Operator access resolution should rerun when pending operator mode is requested.",
            text.contains("LaunchedEffect(") &&
                text.contains("pendingOperatorAccess.value,") &&
                text.contains("adminMode")
        )
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
