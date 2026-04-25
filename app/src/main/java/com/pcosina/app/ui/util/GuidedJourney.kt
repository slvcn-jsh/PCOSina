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
    return when {
        !input.profileComplete -> GuidedJourneyStep(
            stepIndex = 2,
            title = "Complete Profile",
            rationale = "This matters because your nutrition targets and hard food rules come from your profile data.",
            route = Routes.UserProfile,
            ctaLabel = "Open Profile"
        )
        !hasGoalSelection(input.goal) -> GuidedJourneyStep(
            stepIndex = 3,
            title = "Choose Your Focus",
            rationale = "This matters because your goal tells the planner what your first week should optimize for.",
            route = Routes.GoalSelection,
            ctaLabel = "Choose Goals"
        )
        !input.hasPlan -> GuidedJourneyStep(
            stepIndex = 4,
            title = "Build Your Week",
            rationale = "This matters because it turns your saved profile and goal into a usable weekly plan.",
            route = Routes.MealPlan,
            requiresInternet = true,
            ctaLabel = "Open Meal Plan"
        )
        !input.hasReviewedWeek -> GuidedJourneyStep(
            stepIndex = 4,
            title = "Review Week (Mon–Sun)",
            rationale = "This matters because seeing the full week helps you stay consistent.",
            route = Routes.MealPlan,
            ctaLabel = "Review This Week"
        )
        !input.hasGrocery -> GuidedJourneyStep(
            stepIndex = 5,
            title = "Review Grocery List",
            rationale = "This matters because your ingredients are prepared automatically from this week's meals.",
            route = Routes.GroceryList,
            ctaLabel = "Open Grocery List"
        )
        !input.hasTracked -> GuidedJourneyStep(
            stepIndex = 6,
            title = "Track (check-offs + weight/reflection)",
            rationale = "This matters because tracking turns plans into measurable progress.",
            route = Routes.Progress,
            ctaLabel = "Open Progress"
        )
        else -> GuidedJourneyStep(
            stepIndex = 6,
            title = "Continue your week",
            rationale = "This matters because staying on today’s meals keeps momentum.",
            route = Routes.MealPlan,
            ctaLabel = "Open Meal Plan"
        )
    }
}
