package com.pcosina.app.ui.util

import com.pcosina.app.data.model.MealCheckIn
import com.pcosina.app.domain.householdSizeLabel

enum class GoalOption(
    val label: String,
    val shortLabel: String,
    val apiValue: String,
    val aliases: Set<String>
) {
    WeightLoss(
        label = "Weight Loss",
        shortLabel = "Loss",
        apiValue = "Weight Loss",
        aliases = setOf("weight loss")
    ),
    SymptomManagement(
        label = "Symptom Management",
        shortLabel = "Manage",
        apiValue = "Symptom Management",
        aliases = setOf(
            "symptom management",
            "pcos symptom management",
            "support pcos symptom management"
        )
    ),
    GeneralHealth(
        label = "General Health",
        shortLabel = "Balance",
        apiValue = "General Health",
        aliases = setOf(
            "general health",
            "general health improvement"
        )
    );
}

private fun normalizeGoalToken(value: String): String =
    value.lowercase().trim().replace(Regex("\\s+"), " ")

private val goalAliasMap: Map<String, GoalOption> = buildMap {
    GoalOption.entries.forEach { option ->
        (option.aliases + option.label + option.apiValue).forEach { alias ->
            put(normalizeGoalToken(alias), option)
        }
    }
}

fun supportedGoalApiValues(): Set<String> =
    GoalOption.entries.map { it.apiValue }.toSet()

private fun tokenizeGoal(goal: String): List<String> =
    goal.split(",")
        .map { normalizeGoalToken(it) }
        .filter { it.isNotEmpty() }

fun unknownGoalTokens(goal: String): Set<String> =
    tokenizeGoal(goal).filter { token -> token !in goalAliasMap.keys }.toSet()

fun parseGoalOptions(goal: String): Set<GoalOption> {
    val normalized = tokenizeGoal(goal)
    if (normalized.isEmpty()) return emptySet()
    return normalized.mapNotNullTo(linkedSetOf()) { token -> goalAliasMap[token] }
}

fun hasGoalSelection(goal: String): Boolean = tokenizeGoal(goal).isNotEmpty()

fun hasKnownGoalSelection(goal: String): Boolean = parseGoalOptions(goal).isNotEmpty()

fun goalTextFromOptions(options: Set<GoalOption>): String =
    options.sortedBy { it.ordinal }.joinToString(", ") { it.label }

fun goalApiTextFromOptions(options: Set<GoalOption>): String =
    options.sortedBy { it.ordinal }.joinToString(", ") { it.apiValue }

fun goalTextForApi(goal: String): String {
    val parsed = parseGoalOptions(goal)
    return if (parsed.isNotEmpty()) {
        goalApiTextFromOptions(parsed)
    } else {
        goal.trim()
    }
}

fun primaryGoalLabel(goal: String): String =
    parseGoalOptions(goal).firstOrNull()?.label
        ?: goal.trim().takeIf { it.isNotEmpty() }
        ?: "Not set"

fun primaryGoalShortLabel(goal: String): String =
    parseGoalOptions(goal).firstOrNull()?.shortLabel
        ?: if (goal.trim().isNotEmpty()) "Custom" else "Focus"

fun supportsLowGiGuidance(goal: String): Boolean =
    parseGoalOptions(goal).contains(GoalOption.SymptomManagement)

fun goalPlanFocusCopy(goal: String): String = when (parseGoalOptions(goal).firstOrNull()) {
    GoalOption.WeightLoss ->
        "This week favors steady portions, satisfying protein, and meals that are easier to repeat."
    GoalOption.SymptomManagement ->
        "This week leans toward steadier carbs, fiber-rich meals, and routines that are easier to stick with."
    GoalOption.GeneralHealth ->
        "This week focuses on balanced meals, practical variety, and an easier everyday rhythm."
    null -> "This week is built around the preferences saved in your profile."
}

fun goalReflectionSupportCopy(goal: String): String = when (parseGoalOptions(goal).firstOrNull()) {
    GoalOption.WeightLoss ->
        "Use this check-in to notice which meals felt filling, steady, and easier to repeat."
    GoalOption.SymptomManagement ->
        "Use this check-in to notice which meals helped you feel steadier through the day."
    GoalOption.GeneralHealth ->
        "Use this check-in to notice which meals felt balanced, satisfying, and easy to keep doing."
    null -> "Use this check-in to notice patterns you want to keep for next week."
}

fun goalMealCheckInPrompt(goal: String): String = when (parseGoalOptions(goal).firstOrNull()) {
    GoalOption.WeightLoss ->
        "A quick check-in helps you spot which meals kept you full and steady."
    GoalOption.SymptomManagement ->
        "A quick check-in helps you spot which meals felt steadier and easier on your day."
    GoalOption.GeneralHealth ->
        "A quick check-in helps you spot which meals felt balanced and easy to keep doing."
    null -> "A quick check-in helps you notice what worked well for you."
}

fun goalMealReasonCopy(goal: String, reasons: List<String>): List<String> {
    if (reasons.isEmpty()) return emptyList()
    val mapped = linkedSetOf<String>()
    reasons.forEach { reason ->
        when {
            reason.equals("Constraints met", ignoreCase = true) ->
                mapped += "Fits the preferences and limits you saved."
            reason.startsWith("Variety-safe", ignoreCase = true) ||
                reason.equals("Variety-boost", ignoreCase = true) ||
                reason.startsWith("Repeat allowed", ignoreCase = true) ->
                mapped += "Keeps the week from feeling too repetitive."
            reason.equals("Pantry-aware", ignoreCase = true) ->
                mapped += "Uses ingredients you may already have at home."
            reason.equals("Budget-aware", ignoreCase = true) ->
                mapped += "Keeps your weekly grocery spend in mind."
            reason.equals("Macro-aligned", ignoreCase = true) -> {
                mapped += when (parseGoalOptions(goal).firstOrNull()) {
                    GoalOption.WeightLoss ->
                        "Supports a filling plate that matches your weight goal."
                    GoalOption.SymptomManagement ->
                        "Supports steadier energy with balanced meals."
                    GoalOption.GeneralHealth ->
                        "Supports a more balanced everyday routine."
                    null -> "Supports the goal saved in your profile."
                }
            }
        }
    }
    return mapped.toList().ifEmpty {
        listOf("Fits the preferences and limits you saved.")
    }
}

fun goalMealCheckInInsight(goal: String, checkIn: MealCheckIn): String {
    val lowEnergy = (checkIn.energyLevel ?: 3) <= 2
    val lowFullness = (checkIn.fullnessLevel ?: 3) <= 2
    val highCravings = (checkIn.cravingsLevel ?: 3) >= 4
    val lowSatisfaction = (checkIn.satisfactionLevel ?: 3) <= 2
    return when (parseGoalOptions(goal).firstOrNull()) {
        GoalOption.WeightLoss -> when {
            lowFullness || highCravings ->
                "If this meal did not feel filling enough, try pairing the next meal with more protein or fiber."
            lowEnergy ->
                "If your energy dipped after this meal, keep the next one simpler and more balanced."
            lowSatisfaction ->
                "If this meal felt unsatisfying, keep a more familiar option in rotation next time."
            else ->
                "This meal looks like a good candidate to repeat on busy days."
        }
        GoalOption.SymptomManagement -> when {
            lowEnergy || highCravings ->
                "If this meal felt less steady, try pairing carbs with protein or fiber in the next meal."
            lowFullness ->
                "If you felt hungry again quickly, add a steadier side or snack next time."
            lowSatisfaction ->
                "If this meal felt off, keep the note and compare it with your next few check-ins."
            else ->
                "This meal looks like one that may support a steadier day for you."
        }
        GoalOption.GeneralHealth -> when {
            lowFullness || lowSatisfaction ->
                "If this meal did not feel balanced enough, try adding a more filling side next time."
            lowEnergy ->
                "If your energy felt flat after this meal, compare it with the meals that feel easier to sustain."
            else ->
                "This meal looks like a strong fit for your regular routine."
        }
        null -> when {
            lowFullness || highCravings ->
                "Keep noticing which meals help you stay full and steady for longer."
            else ->
                "Keep saving the meals that feel easiest to repeat."
        }
    }
}

fun goalShoppingTips(goal: String, householdSize: Int): List<String> {
    val homeLabel = householdSizeLabel(householdSize)
    val goalSpecific = when (parseGoalOptions(goal).firstOrNull()) {
        GoalOption.WeightLoss -> listOf(
            "Shop with protein first so each meal stays filling for $homeLabel.",
            "Keep produce and simple breakfast staples visible so the easiest meal is still a good fit."
        )
        GoalOption.SymptomManagement -> listOf(
            "Prioritize high-fiber staples and steadier-carb swaps for $homeLabel.",
            "Prep simple add-ons like eggs, greens, and yogurt so symptom-friendly meals stay easy."
        )
        GoalOption.GeneralHealth -> listOf(
            "Aim for a balanced cart with produce, protein, and pantry staples for $homeLabel.",
            "Choose a few repeat ingredients you can use across more than one meal this week."
        )
        null -> listOf(
            "Start with the ingredients you will use first so shopping stays simple for $homeLabel.",
            "Use pantry items before buying duplicates when you can."
        )
    }
    return goalSpecific + "Fresh market prices change week to week, so totals here are best-used as a guide."
}
