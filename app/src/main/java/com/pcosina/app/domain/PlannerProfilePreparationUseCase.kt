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
        if (normalizedTags.any { it.equals("Too repetitive", ignoreCase = true) }) {
            tunedProfile = tunedProfile.copy(varietyPreference = "High")
            applied += "Too repetitive"
        }
        if (normalizedTags.any { it.equals("Too expensive", ignoreCase = true) }) {
            val loweredBudget = (tunedProfile.weeklyBudgetPhp * 0.9f).toInt()
            tunedProfile = tunedProfile.copy(weeklyBudgetPhp = loweredBudget.coerceAtLeast(0))
            applied += "Too expensive"
        }
        if (normalizedTags.any { it.equals("Too hard to cook", ignoreCase = true) }) {
            tunedProfile = tunedProfile.copy(
                maxCookingTimeMinutes = (tunedProfile.maxCookingTimeMinutes - 10).coerceAtLeast(10)
            )
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
}
