package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualBaselineManifestRefreshPolicyTest {

    @Test
    fun refreshScript_updatesManifestAfterBaselineCapture() {
        val source = read(resolve("scripts", "refresh_visual_baselines.ps1"))
        assertTrue(
            "refresh_visual_baselines script should update manifest.sha256 after refresh.",
            source.contains("update_visual_baseline_manifest.ps1")
        )
    }

    @Test
    fun manifestUpdaterScript_exists() {
        val script = resolve("scripts", "update_visual_baseline_manifest.ps1")
        assertTrue("Missing manifest updater script.", Files.exists(script))
    }

    @Test
    fun baselineReadme_mentionsManualManifestUpdateStep() {
        val readme = read(
            resolve(
                "app",
                "src",
                "androidTest",
                "assets",
                "visual_baselines",
                "README.md"
            )
        )
        assertTrue(
            "Baseline README should mention manual manifest updater command.",
            readme.contains("update_visual_baseline_manifest.ps1")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
