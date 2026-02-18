package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class CiVisualStrictSetCompletenessPolicyTest {

    @Test
    fun strictVisualSet_isConsistentAcrossCiScriptAndDocs() {
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

        val ciSource = read(resolve(".github", "workflows", "ci.yml"))
        val refreshSource = read(resolve("scripts", "refresh_visual_baselines.ps1"))
        val baselineReadme = read(resolve("app", "src", "androidTest", "assets", "visual_baselines", "README.md"))
        val requireIndex = ciSource.indexOf("require_visual_baseline=true")
        assertTrue("CI strict verification command should exist.", requireIndex >= 0)

        expected.forEach { name ->
            assertTrue(
                "CI workflow strict file check must include $name.",
                ciSource.contains(name)
            )
            assertTrue(
                "Refresh script baseline list must include $name.",
                refreshSource.contains(name)
            )
            assertTrue(
                "Baseline README expected list must include $name.",
                baselineReadme.contains(name)
            )
            assertTrue(
                "CI file checks should appear before strict verification command for $name.",
                ciSource.indexOf(name) in 0 until requireIndex
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

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
