package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class CiVisualEnvironmentPolicyTest {

    @Test
    fun visualRegressionWorkflow_usesPinnedCompactEmulatorConfig() {
        val source = read(resolve(".github", "workflows", "ci.yml"))

        assertTrue(
            "Compact visual regression should run on a single pinned API level.",
            source.contains("compact-visual-regression:") &&
                source.contains("api-level: [34]")
        )
        assertTrue(
            "Visual regression should pin emulator profile and locale.",
            source.contains("profile: pixel_4") &&
                source.contains("locale: en_US")
        )
        assertTrue(
            "Visual regression should normalize font scale for baseline stability.",
            source.contains("adb shell settings put system font_scale 1.0")
        )
        assertTrue(
            "Visual regression should keep animations disabled for determinism.",
            source.contains("disable-animations: true")
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
