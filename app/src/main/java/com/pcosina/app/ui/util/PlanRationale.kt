package com.pcosina.app.ui.util

import com.pcosina.app.data.api.PlanExplanation

fun buildMealReasons(
    recipeId: String,
    recipeCounts: Map<String, Int>,
    explanation: PlanExplanation?,
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
        reasons.add("Budget-aware")
    }
    if (explanation?.targetProtein != null || explanation?.targetCarbs != null || explanation?.targetFats != null) {
        reasons.add("Macro-aligned")
    }
    return reasons
}
