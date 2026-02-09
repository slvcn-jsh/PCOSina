package com.pcosina.app.ui.util

import com.pcosina.app.ui.components.GuidedJourneyStep
import com.pcosina.app.ui.navigation.Routes

data class GuidedJourneyInput(
    val profileComplete: Boolean,
    val goal: String,
    val hasPlan: Boolean,
    val hasReviewedWeek: Boolean,
    val hasGrocery: Boolean,
    val hasTracked: Boolean
)

fun resolveGuidedJourneyStep(input: GuidedJourneyInput): GuidedJourneyStep {
    val goalText = input.goal.ifBlank { "Your goal" }
    return when {
        !input.profileComplete -> GuidedJourneyStep(
            stepIndex = 1,
            title = "Complete your profile",
            rationale = "We need your basics to compute safe targets.",
            cta = "Complete Profile",
            route = Routes.UserProfile
        )
        input.goal.isBlank() -> GuidedJourneyStep(
            stepIndex = 2,
            title = "Select your goal",
            rationale = "Your plan will align with $goalText.",
            cta = "Choose Goal",
            route = Routes.GoalSelection
        )
        !input.hasPlan -> GuidedJourneyStep(
            stepIndex = 3,
            title = "Generate your first plan",
            rationale = "This creates your week in 1 click.",
            cta = "Generate Plan",
            route = Routes.MealPlan,
            requiresInternet = true
        )
        !input.hasReviewedWeek -> GuidedJourneyStep(
            stepIndex = 4,
            title = "Review this week",
            rationale = "See your Mon–Sun meals and adjust if needed.",
            cta = "Review Week",
            route = Routes.MealPlan
        )
        !input.hasGrocery -> GuidedJourneyStep(
            stepIndex = 5,
            title = "Build your grocery list",
            rationale = "Turn the plan into a ready-to-shop list.",
            cta = "Open Grocery",
            route = Routes.GroceryList
        )
        !input.hasTracked -> GuidedJourneyStep(
            stepIndex = 6,
            title = "Track your week",
            rationale = "Check off meals and log weight or notes.",
            cta = "Open Progress",
            route = Routes.Progress
        )
        else -> GuidedJourneyStep(
            stepIndex = 6,
            title = "You’re on track",
            rationale = "Keep logging to strengthen your weekly insights.",
            cta = "Continue Week",
            route = Routes.Progress
        )
    }
}
