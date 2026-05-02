package com.pcosina.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PlannerPlannedMeal(
    val mealLabel: String,
    val recipeId: String,
    val title: String
)

@Immutable
data class PlannerDayPlan(
    val dayLabel: String,
    val meals: List<PlannerPlannedMeal>,
    val totalCalories: Int
)

@Immutable
data class PlannerTimestamps(
    val requestedAtMs: Long? = null,
    val completedAtMs: Long? = null
)

@Immutable
data class PlannerPlanExplanation(
    val confidenceScore: Int? = null,
    val targetCalories: Int? = null,
    val avgCalories: Int? = null,
    val avgCaloriesDeviation: Int? = null,
    val targetProtein: Int? = null,
    val avgProtein: Int? = null,
    val targetCarbs: Int? = null,
    val avgCarbs: Int? = null,
    val targetFats: Int? = null,
    val avgFats: Int? = null,
    val toleranceUsed: Double? = null,
    val maxPerWeek: Int? = null,
    val pantryMatches: Int? = null,
    val uniqueVegTokens: Int? = null,
    val budgetWeekly: Double? = null,
    val estimatedWeeklyCost: Int? = null,
    val restrictionCount: Int? = null,
    val budgetHardCapApplied: Boolean? = null,
    val householdPlanningMode: String? = null,
    val goalValue: String? = null,
    val symptomSelections: List<String> = emptyList(),
    val profileRuleEffects: Map<String, List<String>>? = null,
    val symptomStrategy: List<String> = emptyList(),
    val candidateExclusionSummary: Map<String, Int>? = null,
    val selectionReasonsByRecipeId: Map<String, List<String>>? = null,
    val selectionReasonCounts: Map<String, Int>? = null,
    val fiberMinTarget: Int? = null,
    val sugarMaxTarget: Int? = null,
    val goalStrategy: List<String> = emptyList()
)

@Immutable
data class PlannerPlanResponse(
    val weekLabel: String,
    val days: List<PlannerDayPlan>,
    val status: String,
    val message: String,
    val explanation: PlannerPlanExplanation? = null,
    val requestId: String? = null,
    val planId: String? = null,
    val policyVersion: String? = null,
    val machineReasonCodes: List<String> = emptyList(),
    val humanGuidance: List<String> = emptyList(),
    val suggestedRelaxations: List<String> = emptyList(),
    val diagnosticsReference: String? = null,
    val timestamps: PlannerTimestamps? = null
)
