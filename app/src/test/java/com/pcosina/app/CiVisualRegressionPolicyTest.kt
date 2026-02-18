package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class CiVisualRegressionPolicyTest {

    @Test
    fun compactVisualWorkflow_verifiesCommittedBaselinesBeforeRefresh() {
        val ciPath = resolve(".github", "workflows", "ci.yml")
        val source = read(ciPath)

        val requireIndex = source.indexOf("require_visual_baseline=true")
        val thresholdIndex = source.indexOf("visual_delta_threshold=6.0")
        val refreshIndex = source.indexOf("refresh_visual_baseline=true")
        val mealPlanBaselineCheck = source.indexOf("compact_mealplan_top_section.png")

        assertTrue("CI workflow should run strict baseline verification.", requireIndex >= 0)
        assertTrue("CI workflow should enforce explicit visual threshold.", thresholdIndex >= 0)
        assertTrue("CI workflow should include baseline refresh path.", refreshIndex >= 0)
        assertTrue("CI workflow should include Meal Plan top-section baseline in strict set.", mealPlanBaselineCheck >= 0)
        assertTrue(
            "Strict baseline verification must run before baseline refresh in CI.",
            requireIndex < refreshIndex
        )
        assertTrue(
            "Strict threshold argument must be set during strict verification.",
            requireIndex < thresholdIndex && thresholdIndex < refreshIndex
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
