package com.pcosina.app.ui.util

import com.pcosina.app.domain.householdSizeLabel
import java.util.Locale

private val fishAllergyTokens = setOf(
    "fish", "isda", "bangus", "milkfish", "tilapia", "galunggong",
    "salmon", "tuna", "tambakol", "tulingan", "tanigue"
)

private val shellfishAllergyTokens = setOf(
    "shellfish", "shrimp", "hipon", "crab", "alimango", "alimasag",
    "squid", "pusit", "mussel", "mussels", "tahong", "clam", "clams",
    "oyster", "oysters", "lobster", "seafood"
)

private fun normalizeAllergyFamilies(allergiesText: String): Set<String> =
    allergiesText
        .split(',', ';', '\n')
        .flatMap { it.trim().lowercase(Locale.ENGLISH).split(Regex("\\s+")) }
        .filter { it.isNotBlank() }
        .mapNotNull { token ->
            when {
                token in fishAllergyTokens -> "fish"
                token in shellfishAllergyTokens -> "shellfish"
                else -> token
            }
        }
        .toSet()

fun profileConstraintConflictMessage(
    vegetarian: Boolean,
    pescatarian: Boolean,
    planningPriority: String,
    varietyPreference: String,
    allergiesText: String,
    budgetPhp: Int?,
): String? {
    val allergyFamilies = normalizeAllergyFamilies(allergiesText)
    if (vegetarian && pescatarian) {
        return "Vegetarian and Pescatarian cannot both be active."
    }
    if (pescatarian && allergyFamilies.contains("fish") && allergyFamilies.contains("shellfish")) {
        return "Pescatarian cannot be combined with both fish and shellfish allergies."
    }
    if (planningPriority.equals("Budget First", ignoreCase = true) && (budgetPhp ?: 0) <= 0) {
        return "Budget First requires a weekly budget."
    }
    if (planningPriority.equals("Variety First", ignoreCase = true) &&
        varietyPreference.equals("Low", ignoreCase = true)
    ) {
        return "Variety First conflicts with Low variety preference."
    }
    return null
}

fun householdPlanningSummary(householdSize: Int): String =
    "Nutrition stays per person. Ingredients and shopping totals are scaled for ${householdSizeLabel(householdSize)}."
