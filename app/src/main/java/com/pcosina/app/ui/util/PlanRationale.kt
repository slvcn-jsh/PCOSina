package com.pcosina.app.ui.util

import com.pcosina.app.data.model.PlannerPlanExplanation

fun buildMealReasons(
    recipeId: String,
    recipeCounts: Map<String, Int>,
    explanation: PlannerPlanExplanation?,
    budgetPhp: Int
): List<String> {
    val reasons = mutableListOf<String>()
    reasons.add("Constraints met")
    val count = recipeCounts[recipeId] ?: 1
    val maxPerWeek = explanation?.maxPerWeek
    if (maxPerWeek != null) {
        if (count <= maxPerWeek) {
            reasons.add("Variety-safe ($count/$maxPerWeek)")
        } else {
            reasons.add("Repeat allowed ($count/$maxPerWeek)")
        }
    } else if (count == 1) {
        reasons.add("Variety-boost")
    }
    if ((explanation?.pantryMatches ?: 0) > 0) {
        reasons.add("Pantry-aware")
    }
    if (budgetPhp > 0 || explanation?.budgetWeekly != null) {
        reasons.add(if (explanation?.budgetHardCapApplied == true) "Budget-capped" else "Budget-aware")
    }
    if (explanation?.targetProtein != null || explanation?.targetCarbs != null || explanation?.targetFats != null) {
        reasons.add("Macro-aligned")
    }
    if (!explanation?.symptomStrategy.isNullOrEmpty()) {
        reasons.add("Symptom-aware")
    }
    return reasons.distinct()
}
