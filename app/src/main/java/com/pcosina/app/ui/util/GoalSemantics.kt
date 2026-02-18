package com.pcosina.app.ui.util

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
