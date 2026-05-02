package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressFeedbackPolicyTest {

    @Test
    fun progressScreen_usesInlineFeedbackStateForSaveAndReviewActions() {
        val source = read(
            resolve(
                "app",
                "src",
                "main",
                "java",
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "ProgressRefinedScreen.kt"
            )
        )
        assertTrue(
            "Progress screen should keep inline feedback message state in the refined shell.",
            source.contains("var feedbackMessage by remember { mutableStateOf<String?>(null) }")
        )
        assertTrue(
            "Progress screen should confirm saved daily check-ins.",
            source.contains("Today's check-in was saved.")
        )
        assertTrue(
            "Progress screen should validate weekly spend with inline feedback.",
            source.contains("Weekly spend must be a whole number in pesos.")
        )
        assertTrue(
            "Progress screen should confirm weekly review updates.",
            source.contains("Weekly review updated.")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallbackParts = parts.drop(1).toTypedArray()
        val second = Paths.get(fallbackParts.first(), *fallbackParts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
