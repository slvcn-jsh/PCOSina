package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRecoveryPolicyTest {

    @Test
    fun loginScreen_usesGoogleSignInAndLegalCopy() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "LoginScreen.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue("Login should show the Google sign-in CTA.", text.contains("Continue with Google"))
        assertTrue("Login should configure Google Sign-In.", text.contains("GoogleSignInOptions.Builder"))
        assertTrue("Login should hand the Google ID token to the view model.", text.contains("onGoogleLogin(account.idToken)"))
        assertTrue("Login should keep legal acceptance copy visible.", text.contains("Terms of Use and Privacy Policy"))
    }

    @Test
    fun termsOverlay_boundsOwnershipArtworkAndKeepsAcceptanceControlsAddressable() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "LoginScreen.kt"
            )
        )
        val termsOverlay = String(Files.readAllBytes(file))
            .substringAfter("private fun TermsOfServiceOverlay(")
            .substringBefore("private fun LegalSection(")

        assertTrue(
            "The Terms ownership artwork needs a fixed height so it cannot consume the screen.",
            termsOverlay.contains(".height(if (tightHeight) 40.dp else if (compact) 48.dp else 54.dp)")
        )
        assertTrue(
            "The Terms card should remain addressable in UI verification.",
            termsOverlay.contains(".testTag(\"login_terms_card\")")
        )
        assertTrue(
            "The Terms acceptance action should remain addressable in UI verification.",
            termsOverlay.contains(".testTag(\"login_terms_accept\")")
        )
    }

    @Test
    fun authViewModel_exposesPasswordResetHandler() {
        val file = resolveMainSourceRoot().resolve(
            Paths.get(
                "com",
                "pcosina",
                "app",
                "ui",
                "AuthViewModel.kt"
            )
        )
        val text = String(Files.readAllBytes(file))
        assertTrue("AuthViewModel should expose sendPasswordReset.", text.contains("fun sendPasswordReset("))
        assertTrue("AuthViewModel should call repository password reset.", text.contains("repository.sendPasswordReset"))
    }

    private fun resolveMainSourceRoot(): Path {
        val candidates = listOf(
            Paths.get("app", "src", "main", "java"),
            Paths.get("src", "main", "java")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Could not locate main source root for login recovery policy test.")
    }
}
