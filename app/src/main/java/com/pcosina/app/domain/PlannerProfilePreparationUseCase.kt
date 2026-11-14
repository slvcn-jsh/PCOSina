package com.pcosina.app.domain

import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.ui.util.goalTextForApi
import com.pcosina.app.ui.util.hasGoalSelection

data class PreparedPlannerProfile(
    val requestedProfile: UserProfile,
    val resolvedProfile: UserProfile,
    val plannerProfile: UserProfile,
    val usedStoredProfileFallback: Boolean,
    val appliedFeedbackTags: List<String>,
)

class PlannerProfilePreparationUseCase {

    companion object {
        const val GoalWeightTrendSupportTag = "Goal: Weight trend support"
        const val GoalCravingSupportTag = "Goal: Craving support"
        const val GoalEnergySupportTag = "Goal: Energy support"
    }

    operator fun invoke(
        requestedProfile: UserProfile,
        storedProfile: UserProfile? = null,
        feedbackTags: List<String> = emptyList(),
    ): PreparedPlannerProfile {
        val resolvedProfile = if (isProfileValid(requestedProfile)) {
            requestedProfile
        } else {
            storedProfile ?: requestedProfile
        }
        val normalizedTags = feedbackTags
            .map { it.trim() }
            .filter { it.isNotBlank() }
        var tunedProfile = resolvedProfile
        val applied = mutableListOf<String>()
        if (normalizedTags.any { it.equals(GoalWeightTrendSupportTag, ignoreCase = true) }) {
            tunedProfile = tunedProfile.copy(
                symptoms = appendProfileToken(tunedProfile.symptoms, "Weight gain"),
                planningPriority = "Nutrition Tight"
            )
            applied += GoalWeightTrendSupportTag
        }
        if (normalizedTags.any { it.equals(GoalCravingSupportTag, ignoreCase = true) }) {
            tunedProfile = tunedProfile.copy(
                goal = appendGoalToken(tunedProfile.goal, "Symptom Management"),
                planningPriority = "Nutrition Tight"
            )
            applied += GoalCravingSupportTag
        }
        if (normalizedTags.any { it.equals(GoalEnergySupportTag, ignoreCase = true) }) {
            tunedProfile = tunedProfile.copy(planningPriority = "Nutrition Tight")
            applied += GoalEnergySupportTag
        }
        if (normalizedTags.any { it.equals("Too repetitive", ignoreCase = true) }) {
            tunedProfile = tunedProfile.copy(varietyPreference = "High")
            applied += "Too repetitive"
        }
        if (normalizedTags.any { it.equals("Too expensive", ignoreCase = true) }) {
            if (tunedProfile.weeklyBudgetPhp > 0) {
                tunedProfile = tunedProfile.copy(planningPriority = "Budget First")
                applied += "Too expensive"
            }
        }
        if (normalizedTags.any { it.equals("Too hard to cook", ignoreCase = true) }) {
            val priority = if (tunedProfile.planningPriority.contains("Budget", ignoreCase = true)) {
                "Budget First Quick Prep"
            } else {
                "Quick Prep"
            }
            tunedProfile = tunedProfile.copy(planningPriority = priority)
            applied += "Too hard to cook"
        }
        val plannerProfile = tunedProfile.copy(goal = goalTextForApi(tunedProfile.goal))
        return PreparedPlannerProfile(
            requestedProfile = requestedProfile,
            resolvedProfile = resolvedProfile,
            plannerProfile = plannerProfile,
            usedStoredProfileFallback = resolvedProfile != requestedProfile,
            appliedFeedbackTags = applied,
        )
    }

    fun isProfileValid(profile: UserProfile): Boolean {
        return profile.age > 0 &&
            profile.heightCm > 0 &&
            profile.weightKg > 0 &&
            profile.activityLevel.isNotBlank() &&
            hasGoalSelection(profile.goal)
    }

    private fun appendProfileToken(tokens: List<String>, token: String): List<String> =
        if (tokens.any { it.equals(token, ignoreCase = true) }) {
            tokens
        } else {
            tokens + token
        }

    private fun appendGoalToken(goal: String, token: String): String {
        val existing = goal.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return if (existing.any { it.equals(token, ignoreCase = true) }) {
            goal
        } else {
            (existing + token).joinToString(", ")
        }
    }
}
