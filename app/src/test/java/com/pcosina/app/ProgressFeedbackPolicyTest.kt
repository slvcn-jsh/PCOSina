package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressFeedbackPolicyTest {

    @Test
    fun progressScreen_usesFeedbackKitForSaveAndQueueActions() {
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
                "ProgressScreen.kt"
            )
        )
        assertTrue(
            "Progress screen should render AppFeedbackBanner for save/log feedback visibility.",
            source.contains("AppFeedbackBanner(")
        )
        assertTrue(
            "Progress screen should use LoadingActionButton for reflection saves.",
            source.contains("idleLabel = \"Save Reflection\"")
        )
        assertTrue(
            "Progress screen should use LoadingActionButton for weight saves.",
            source.contains("idleLabel = \"Save Weight\"")
        )
        assertTrue(
            "Progress screen should use LoadingActionButton for queued feedback send.",
            source.contains("idleLabel = \"Send (Queued if offline)\"")
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
