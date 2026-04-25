package com.pcosina.app

import com.pcosina.app.data.api.PlanExplanation
import com.pcosina.app.ui.util.buildMealReasons
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRationaleTest {

    @Test
    fun buildMealReasons_stays_generic_even_when_backend_has_internal_selection_signals() {
        val explanation = PlanExplanation(
            maxPerWeek = 2,
            budgetWeekly = 1500.0,
            budgetHardCapApplied = true,
            targetProtein = 90,
            selectionReasonsByRecipeId = mapOf(
                "r1" to listOf("high_fiber_preferred", "steady_carb_preferred")
            ),
            symptomStrategy = listOf("Acne symptom reduces sugar allowance.")
        )

        val reasons = buildMealReasons(
            recipeId = "r1",
            recipeCounts = mapOf("r1" to 1),
            explanation = explanation,
            budgetPhp = 1500
        )

        assertTrue(reasons.contains("Constraints met"))
        assertTrue(reasons.contains("Budget-capped"))
        assertTrue(reasons.contains("Macro-aligned"))
        assertTrue(reasons.contains("Symptom-aware"))
        assertFalse(reasons.contains("Higher-fiber fit"))
        assertFalse(reasons.contains("Steadier-carb fit"))
    }
}
