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
            "Progress screen should frame review as next-plan tuning.",
            source.contains("Tune next plan")
        )
        assertTrue(
            "Progress screen should remove free-text and spend collection from the weekly review.",
            !source.contains("Total spent (₱)") && !source.contains("label = { Text(\"Notes\") }")
        )
        assertTrue(
            "Progress screen should confirm plan tuning updates inline.",
            source.contains("Next-plan preferences updated.")
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
