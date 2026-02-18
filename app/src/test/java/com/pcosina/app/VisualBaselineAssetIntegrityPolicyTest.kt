package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualBaselineAssetIntegrityPolicyTest {

    @Test
    fun compactVisualBaselines_arePresentAndNonPlaceholderSized() {
        val baselineDir = resolve("app", "src", "androidTest", "assets", "visual_baselines")
        val expected = listOf(
            "compact_profile_step3.png",
            "compact_chip_row.png",
            "compact_dashboard_today_outcome.png",
            "compact_progress_top_section.png",
            "compact_mealplan_top_section.png",
            "compact_login_first_win_card.png",
            "compact_profile_first_win_card.png",
            "compact_goal_handoff_card.png"
        )

        expected.forEach { fileName ->
            val path = baselineDir.resolve(fileName)
            assertTrue("Missing visual baseline asset: $fileName", Files.exists(path))
            val size = Files.size(path)
            assertTrue(
                "Baseline appears to be placeholder-sized: $fileName ($size bytes)",
                size >= 20_000L
            )
        }
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val second = Paths.get("..", parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }
}
