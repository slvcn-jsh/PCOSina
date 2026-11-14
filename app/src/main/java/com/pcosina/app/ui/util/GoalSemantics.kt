package com.pcosina.app.ui.util

import com.pcosina.app.data.model.MealCheckIn

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
    parseGoalOptions(goal).takeIf { it.isNotEmpty() }?.let(::goalTextFromOptions)
        ?: goal.trim().takeIf { it.isNotEmpty() }
        ?: "Not set"

fun primaryGoalShortLabel(goal: String): String =
    parseGoalOptions(goal).takeIf { it.size > 1 }?.let { "${it.size} goals" }
        ?: parseGoalOptions(goal).firstOrNull()?.shortLabel
        ?: if (goal.trim().isNotEmpty()) "Custom" else "Focus"

fun supportsLowGiGuidance(goal: String): Boolean =
    parseGoalOptions(goal).contains(GoalOption.SymptomManagement)

fun goalPlanFocusCopy(goal: String): String {
    val goals = parseGoalOptions(goal)
    return when {
        goals.hasWeightAndSymptom() && goals.contains(GoalOption.GeneralHealth) ->
            "This week balances calorie fit, steadier-carb support, and practical variety without weakening your hard food rules."
        goals.hasWeightAndSymptom() ->
            "This week balances calorie fit with steadier-carb, fiber-rich meals that stay satisfying."
        goals.hasWeightAndGeneral() ->
            "This week favors steady portions, satisfying protein, and practical variety for a sustainable routine."
        goals.hasSymptomAndGeneral() ->
            "This week leans toward steadier carbs, fiber-rich meals, and balanced everyday variety."
        goals.contains(GoalOption.WeightLoss) ->
            "This week favors steady portions, satisfying protein, and meals that are easier to repeat."
        goals.contains(GoalOption.SymptomManagement) ->
            "This week leans toward steadier carbs, fiber-rich meals, and routines that are easier to stick with."
        goals.contains(GoalOption.GeneralHealth) ->
            "This week focuses on balanced meals, practical variety, and an easier everyday rhythm."
        else -> "This week is built around the preferences saved in your profile."
    }
}

fun goalReflectionSupportCopy(goal: String): String {
    val goals = parseGoalOptions(goal)
    return when {
        goals.hasWeightAndSymptom() ->
            "Use this check-in to notice which meals felt filling, steady, and easier on cravings or energy."
        goals.hasWeightAndGeneral() ->
            "Use this check-in to notice which meals felt filling, balanced, and easy to repeat."
        goals.hasSymptomAndGeneral() ->
            "Use this check-in to notice which meals felt steady, balanced, and easy to keep doing."
        goals.contains(GoalOption.WeightLoss) ->
            "Use this check-in to notice which meals felt filling, steady, and easier to repeat."
        goals.contains(GoalOption.SymptomManagement) ->
            "Use this check-in to notice which meals helped you feel steadier through the day."
        goals.contains(GoalOption.GeneralHealth) ->
            "Use this check-in to notice which meals felt balanced, satisfying, and easy to keep doing."
        else -> "Use this check-in to notice patterns you want to keep for next week."
    }
}

fun goalMealCheckInPrompt(goal: String): String {
    val goals = parseGoalOptions(goal)
    return when {
        goals.hasWeightAndSymptom() ->
            "A quick check-in helps you spot which meals kept you full, steady, and easier on cravings."
        goals.hasWeightAndGeneral() ->
            "A quick check-in helps you spot which meals felt filling, balanced, and repeatable."
        goals.hasSymptomAndGeneral() ->
            "A quick check-in helps you spot which meals felt steady, balanced, and sustainable."
        goals.contains(GoalOption.WeightLoss) ->
            "A quick check-in helps you spot which meals kept you full and steady."
        goals.contains(GoalOption.SymptomManagement) ->
            "A quick check-in helps you spot which meals felt steadier and easier on your day."
        goals.contains(GoalOption.GeneralHealth) ->
            "A quick check-in helps you spot which meals felt balanced and easy to keep doing."
        else -> "A quick check-in helps you notice what worked well for you."
    }
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
            reason.equals("Macro-aligned", ignoreCase = true) ->
                mapped += goalMacroAlignedCopy(goal)
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
    val goals = parseGoalOptions(goal)
    return when {
        goals.hasWeightAndSymptom() -> when {
            lowFullness || highCravings ->
                "If this meal did not feel filling or steady enough, try pairing the next meal with more protein, fiber, and slower carbs."
            lowEnergy ->
                "If your energy dipped after this meal, keep the next one balanced with protein, fiber, and a steadier carb portion."
            lowSatisfaction ->
                "If this meal felt unsatisfying, keep a familiar option in rotation while preserving steadier meal balance."
            else ->
                "This meal looks like a useful repeat candidate for both fullness and steadier-day support."
        }
        goals.hasWeightAndGeneral() -> when {
            lowFullness || highCravings ->
                "If this meal did not feel filling enough, try a more balanced plate with protein, fiber, and familiar produce."
            lowEnergy || lowSatisfaction ->
                "If this meal felt harder to sustain, compare it with meals that feel filling and balanced."
            else ->
                "This meal looks like a practical repeat candidate for your balanced weight-support routine."
        }
        goals.hasSymptomAndGeneral() -> when {
            lowEnergy || highCravings ->
                "If this meal felt less steady, try a balanced next meal with protein, fiber, and a steadier carb choice."
            lowFullness || lowSatisfaction ->
                "If this meal felt incomplete, keep the note and look for steadier balanced meals next time."
            else ->
                "This meal looks like a good candidate for a steadier and balanced routine."
        }
        goals.contains(GoalOption.WeightLoss) -> when {
            lowFullness || highCravings ->
                "If this meal did not feel filling enough, try pairing the next meal with more protein or fiber."
            lowEnergy ->
                "If your energy dipped after this meal, keep the next one simpler and more balanced."
            lowSatisfaction ->
                "If this meal felt unsatisfying, keep a more familiar option in rotation next time."
            else ->
                "This meal looks like a good candidate to repeat on busy days."
        }
        goals.contains(GoalOption.SymptomManagement) -> when {
            lowEnergy || highCravings ->
                "If this meal felt less steady, try pairing carbs with protein or fiber in the next meal."
            lowFullness ->
                "If you felt hungry again quickly, add a steadier side or snack next time."
            lowSatisfaction ->
                "If this meal felt off, keep the note and compare it with your next few check-ins."
            else ->
                "This meal looks like one that may support a steadier day for you."
        }
        goals.contains(GoalOption.GeneralHealth) -> when {
            lowFullness || lowSatisfaction ->
                "If this meal did not feel balanced enough, try adding a more filling side next time."
            lowEnergy ->
                "If your energy felt flat after this meal, compare it with the meals that feel easier to sustain."
            else ->
                "This meal looks like a strong fit for your regular routine."
        }
        else -> when {
            lowFullness || highCravings ->
                "Keep noticing which meals help you stay full and steady for longer."
            else ->
                "Keep saving the meals that feel easiest to repeat."
        }
    }
}

fun goalShoppingTips(goal: String): List<String> {
    val goals = parseGoalOptions(goal)
    val goalSpecific = when {
        goals.hasWeightAndSymptom() && goals.contains(GoalOption.GeneralHealth) -> listOf(
            "Shop protein, high-fiber staples, and produce first so the cart supports all selected goals.",
            "Use pantry matches before buying duplicates, then choose familiar ingredients you can repeat safely."
        )
        goals.hasWeightAndSymptom() -> listOf(
            "Shop with protein and high-fiber staples first so meals stay filling and steadier.",
            "Choose slower-carb swaps and simple produce that fit your weekly plan."
        )
        goals.hasWeightAndGeneral() -> listOf(
            "Shop with protein first, then add produce and repeatable staples for balanced meals.",
            "Choose familiar ingredients you can use across more than one filling meal."
        )
        goals.hasSymptomAndGeneral() -> listOf(
            "Prioritize high-fiber staples, produce, and steadier-carb swaps for a balanced cart.",
            "Prep simple add-ons like eggs, greens, and yogurt so supportive meals stay easy."
        )
        goals.contains(GoalOption.WeightLoss) -> listOf(
            "Shop with protein first so each meal stays filling for your plan.",
            "Keep produce and simple breakfast staples visible so the easiest meal is still a good fit."
        )
        goals.contains(GoalOption.SymptomManagement) -> listOf(
            "Prioritize high-fiber staples and steadier-carb swaps for your plan.",
            "Prep simple add-ons like eggs, greens, and yogurt so symptom-friendly meals stay easy."
        )
        goals.contains(GoalOption.GeneralHealth) -> listOf(
            "Aim for a balanced cart with produce, protein, and pantry staples for your plan.",
            "Choose a few repeat ingredients you can use across more than one meal this week."
        )
        else -> listOf(
            "Start with the ingredients you will use first so shopping stays simple.",
            "Use pantry items before buying duplicates when you can."
        )
    }
    return goalSpecific + "Fresh market prices change week to week, so totals here are best-used as a guide."
}

private fun Set<GoalOption>.hasWeightAndSymptom(): Boolean =
    contains(GoalOption.WeightLoss) && contains(GoalOption.SymptomManagement)

private fun Set<GoalOption>.hasWeightAndGeneral(): Boolean =
    contains(GoalOption.WeightLoss) && contains(GoalOption.GeneralHealth)

private fun Set<GoalOption>.hasSymptomAndGeneral(): Boolean =
    contains(GoalOption.SymptomManagement) && contains(GoalOption.GeneralHealth)

private fun goalMacroAlignedCopy(goal: String): String {
    val goals = parseGoalOptions(goal)
    return when {
        goals.hasWeightAndSymptom() ->
            "Supports a filling, steadier plate that matches your selected goals."
        goals.hasWeightAndGeneral() ->
            "Supports a filling and balanced plate for your routine."
        goals.hasSymptomAndGeneral() ->
            "Supports steadier energy with balanced meals."
        goals.contains(GoalOption.WeightLoss) ->
            "Supports a filling plate that matches your weight goal."
        goals.contains(GoalOption.SymptomManagement) ->
            "Supports steadier energy with balanced meals."
        goals.contains(GoalOption.GeneralHealth) ->
            "Supports a more balanced everyday routine."
        else -> "Supports the goal saved in your profile."
    }
}
