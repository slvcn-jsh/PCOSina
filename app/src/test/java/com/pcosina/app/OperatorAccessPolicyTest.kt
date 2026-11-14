package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAdminTransitionPolicyTest {

    @Test
    fun loginScreen_keepsGoogleSignInWithoutMobileAdminIntent() {
        val text = readScreen("LoginScreen.kt")
        assertTrue("Login should keep the Google sign-in CTA.", text.contains("Continue with Google"))
        assertFalse("Login should not request hidden mobile operator/admin access.", text.contains("operatorAccessRequested"))
        assertFalse("Login success should not pass a mobile operator intent to navigation.", text.contains("onLoginSuccess(operatorAccessRequested)"))
    }

    @Test
    fun appNavHost_hasNoMobileOperatorRoutesOrGuards() {
        val text = readNavigation("AppNavHost.kt")
        assertFalse("Navigation should not remember mobile operator access intent.", text.contains("pendingOperatorAccess"))
        assertFalse("Authorized login should not land on a mobile operator dashboard.", text.contains("Routes.OperatorDashboard"))
        assertFalse("Mobile operator-only guards should be removed.", text.contains("Admin access is only available for authorized accounts."))
        assertFalse("Navigation should not call the retired mobile operator endpoint flow.", text.contains("getCurrentUserOperatorAccess"))
    }

    @Test
    fun authRepository_noLongerCallsMobileOperatorAccessEndpoint() {
        val text = readRepository("AuthRepository.kt")
        assertFalse("Android auth should not call the retired mobile operator endpoint.", text.contains("/mobile/operator/access"))
        assertFalse("Android auth should not build mobile operator App Check requests.", text.contains("X-Firebase-AppCheck"))
    }

    @Test
    fun routesAndScreens_noLongerExposeMobileAdminDashboard() {
        val routes = readNavigation("Routes.kt")
        val screenDir = resolve("app", "src", "main", "java", "com", "pcosina", "app", "ui", "screens")
        assertFalse("Routes should not define mobile operator destinations.", routes.contains("OperatorDashboard"))
        assertFalse("Routes should not define mobile admin methodology.", routes.contains("AdminMethodology"))
        assertFalse(
            "The retired mobile operator dashboard file should be removed.",
            Files.exists(screenDir.resolve("OperatorDashboardScreen.kt"))
        )
    }

    @Test
    fun backendDocs_keepBrowserAdminAsAuthoritativeAdminSurface() {
        val docs = readText("docs", "backend-config.md")
        assertTrue("Backend docs should keep the browser admin content console.", docs.contains("/admin/content"))
        assertTrue("Backend docs should keep the browser admin ops console.", docs.contains("/admin/ops"))
        assertTrue("Backend docs should describe admin allowlist access.", docs.contains("PCOSINA_ADMIN_EMAILS"))
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

    private fun readText(vararg parts: String): String =
        String(Files.readAllBytes(resolve(*parts)))

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val parent = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(parent)) return parent
        val fallbackParts = parts.drop(1).toTypedArray()
        val second = Paths.get(fallbackParts.first(), *fallbackParts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
