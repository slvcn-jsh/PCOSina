package com.pcosina.app.domain

import com.pcosina.app.data.model.MealPlan
import com.pcosina.app.data.model.Recipe
import com.pcosina.app.data.model.UserProfile

/**
 * Placeholder only (non-goal: real generation algorithm).
 */
class GeneratePlanUseCase {
    fun generateWeeklyPlan(
        userProfile: UserProfile,
        availableRecipes: List<Recipe>,
    ): MealPlan? = null
}

