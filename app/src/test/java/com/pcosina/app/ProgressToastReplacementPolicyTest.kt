package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressToastReplacementPolicyTest {

    @Test
    fun progressValidationAndSaveFeedback_useInlineCopyInsteadOfToast() {
        val progressText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "ProgressRefinedScreen.kt"
        )
        assertTrue(
            "Progress should not rely on Toast for validation/save feedback.",
            !progressText.contains("Toast.makeText")
        )
        assertTrue(
            "Weekly spending validation should use current inline feedback copy.",
            progressText.contains("Weekly spend must be a whole number in pesos.")
        )
        assertTrue(
            "Refined check-in lock state should use inline feedback copy instead of toast.",
            progressText.contains("Check-ins can only be saved for today.")
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
            ?: error("Could not locate main source root for progress toast replacement policy test.")
    }
}
