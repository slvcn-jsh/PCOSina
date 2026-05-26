package com.pcosina.app.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Navigation routes for the app.
 */
object Routes {
    private enum class RouteAccess {
        Auth,
        ProfileSetup,
        GoalSetup,
        GuidedCore,
        PlanRequired
    }

    private val routeAccessByBase = linkedMapOf<String, RouteAccess>()

    private fun defineRoute(base: String, access: RouteAccess): String {
        require(base.isNotBlank()) { "Route base cannot be blank." }
        check(routeAccessByBase.put(base, access) == null) { "Duplicate route base: $base" }
        return base
    }

    // Auth
    val Splash = defineRoute("splash", RouteAccess.Auth)
    val Login = defineRoute("login", RouteAccess.Auth)
    val SignUp = defineRoute("signup", RouteAccess.Auth)

    // Setup flow
    val UserProfile = defineRoute("user_profile", RouteAccess.ProfileSetup)
    val UserProfileEdit = defineRoute("user_profile_edit", RouteAccess.ProfileSetup)
    val GoalSelection = defineRoute("goal_selection", RouteAccess.GoalSetup)
    
    // Main App
    val Dashboard = defineRoute("dashboard", RouteAccess.GuidedCore)
    val Settings = defineRoute("settings", RouteAccess.GuidedCore)
    val Notifications = defineRoute("notifications", RouteAccess.GuidedCore)
    val MoreTools = defineRoute("more_tools", RouteAccess.GuidedCore)

    // Bottom tabs
    val MealPlan = defineRoute("meal_plan", RouteAccess.GuidedCore)
    val GroceryList = defineRoute("grocery_list", RouteAccess.PlanRequired)
    val Progress = defineRoute("progress", RouteAccess.PlanRequired)
    val Ipo = defineRoute("ipo", RouteAccess.GuidedCore)

    // Details
    val RecipeDetails = defineRoute("recipe_details", RouteAccess.PlanRequired)
    const val RecipeIdArg = "recipeId"
    const val MealLabelArg = "mealLabel"
    val RecipeDetailsRoutePattern: String
        get() = "$RecipeDetails/{$RecipeIdArg}?$MealLabelArg={$MealLabelArg}"
    fun recipeDetailsRoute(recipeId: String, mealLabel: String? = null): String {
        val encodedRecipeId = URLEncoder.encode(recipeId, StandardCharsets.UTF_8.toString())
        val encodedMealLabel = mealLabel?.takeIf { it.isNotBlank() }?.let {
            URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
        }
        return if (encodedMealLabel == null) {
            "$RecipeDetails/$encodedRecipeId"
        } else {
            "$RecipeDetails/$encodedRecipeId?$MealLabelArg=$encodedMealLabel"
        }
    }

    fun baseRoute(route: String?): String? = route?.substringBefore("/")

    fun isAuthRoute(route: String?): Boolean =
        routeAccessByBase[baseRoute(route)] == RouteAccess.Auth

    fun isProfileRoute(route: String?): Boolean =
        routeAccessByBase[baseRoute(route)] == RouteAccess.ProfileSetup

    fun isGoalRoute(route: String?): Boolean =
        routeAccessByBase[baseRoute(route)] == RouteAccess.GoalSetup

    fun isKnownRoute(route: String?): Boolean =
        routeAccessByBase.containsKey(baseRoute(route))

    fun knownBaseRoutes(): Set<String> = routeAccessByBase.keys.toSet()

    fun requiresPlan(route: String?): Boolean {
        val base = baseRoute(route) ?: return false
        val access = routeAccessByBase[base] ?: return true // safe default
        return access == RouteAccess.PlanRequired
    }
}
