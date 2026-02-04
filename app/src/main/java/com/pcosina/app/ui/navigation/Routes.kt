package com.pcosina.app.ui.navigation

/**
 * Navigation routes for the app.
 */
object Routes {
    // Auth
    const val Splash = "splash"
    const val Login = "login"
    const val SignUp = "signup"
    
    // Onboarding flow
    const val Onboarding = "onboarding"
    const val UserProfile = "user_profile"
    const val GoalSelection = "goal_selection"
    
    // Main App
    const val Dashboard = "dashboard"
    const val Settings = "settings"

    // Bottom tabs
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
