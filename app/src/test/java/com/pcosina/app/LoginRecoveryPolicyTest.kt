package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRecoveryPolicyTest {

    @Test
    fun loginScreen_supportsForgotPassword_andPasswordVisibilityToggle() {
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
        assertTrue("Login should include a forgot-password action.", text.contains("Forgot password?"))
        assertTrue("Login should support password visibility toggle.", text.contains("VisibilityOff"))
        assertTrue("Login should trigger password reset through the view model.", text.contains("sendPasswordReset(email)"))
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
