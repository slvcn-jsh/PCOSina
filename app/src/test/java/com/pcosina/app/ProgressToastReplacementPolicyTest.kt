package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressToastReplacementPolicyTest {

    @Test
    fun progressValidationAndExport_useBannerCopyInsteadOfToast() {
        val progressText = readMainSource(
            "com",
            "pcosina",
            "app",
            "ui",
            "screens",
            "ProgressScreen.kt"
        )
        assertTrue(
            "Progress should not rely on Toast for validation/export feedback.",
            !progressText.contains("Toast.makeText")
        )
        assertTrue(
            "Weekly spending validation should use banner copy.",
            progressText.contains("Enter a valid amount before saving actual spending.")
        )
        assertTrue(
            "Export reflections empty-state should use banner copy.",
            progressText.contains("No reflections to export yet.")
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
