package com.pcosina.app.ui.navigation

/**
 * Navigation routes for the app.
 *
 * Note: This project is being PORTED from a React+TS prototype into native
 * Jetpack Compose. These routes are intentionally explicit and stable to
 * mirror the screen flow of the prototype.
 */
object Routes {
    // Flow
    const val Splash = "splash"
    const val Onboarding = "onboarding"
    const val UserProfile = "user_profile"
    const val GoalSelection = "goal_selection"
    const val Dashboard = "dashboard"

    // Bottom tabs (Dashboard hosts these)
    const val MealPlan = "meal_plan"
    const val GroceryList = "grocery_list"
    const val Progress = "progress"
    const val Ipo = "ipo"

    // Details
    const val RecipeDetails = "recipe_details"
    const val RecipeIdArg = "recipeId"
    const val RecipeDetailsRoutePattern = "$RecipeDetails/{$RecipeIdArg}"
    fun recipeDetailsRoute(recipeId: String): String = "$RecipeDetails/$recipeId"
}

