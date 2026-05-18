package com.pcosina.app

import com.pcosina.app.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesClassificationTest {
    @Test
    fun knownBaseRoutes_matchExpectedSet() {
        val expected = setOf(
            Routes.Splash,
            Routes.Login,
            Routes.SignUp,
            Routes.UserProfile,
            Routes.UserProfileEdit,
            Routes.GoalSelection,
            Routes.Dashboard,
            Routes.Settings,
            Routes.Notifications,
            Routes.AdminMethodology,
            Routes.OperatorDashboard,
            Routes.OperatorRecipes,
            Routes.OperatorPrices,
            Routes.OperatorGroceryPantry,
            Routes.OperatorRules,
            Routes.OperatorSystemInfo,
            Routes.OperatorAdminSettings,
            Routes.MealPlan,
            Routes.GroceryList,
            Routes.Progress,
            Routes.Ipo,
            Routes.MoreTools,
            Routes.RecipeDetails
        )
        assertEquals(expected, Routes.knownBaseRoutes())
    }

    @Test
    fun knownRoutes_areClassified() {
        val known = listOf(
            Routes.Splash,
            Routes.Login,
            Routes.SignUp,
            Routes.UserProfile,
            Routes.UserProfileEdit,
            Routes.GoalSelection,
            Routes.Dashboard,
            Routes.Settings,
            Routes.Notifications,
            Routes.AdminMethodology,
            Routes.OperatorDashboard,
            Routes.OperatorRecipes,
            Routes.OperatorPrices,
            Routes.OperatorGroceryPantry,
            Routes.OperatorRules,
            Routes.OperatorSystemInfo,
            Routes.OperatorAdminSettings,
            Routes.MealPlan,
            Routes.GroceryList,
            Routes.Progress,
            Routes.Ipo,
            Routes.MoreTools,
            Routes.RecipeDetailsRoutePattern,
            Routes.recipeDetailsRoute("abc123"),
            Routes.recipeDetailsRoute("abc123", "Lunch")
        )
        known.forEach { route ->
            assertTrue("Expected route '$route' to be known", Routes.isKnownRoute(route))
        }
    }

    @Test
    fun requiresPlan_blocksOnlyPlanDependentRoutes() {
        assertTrue(Routes.requiresPlan(Routes.GroceryList))
        assertTrue(Routes.requiresPlan(Routes.Progress))
        assertTrue(Routes.requiresPlan(Routes.recipeDetailsRoute("id1")))

        assertFalse(Routes.requiresPlan(Routes.Dashboard))
        assertFalse(Routes.requiresPlan(Routes.MealPlan))
        assertFalse(Routes.requiresPlan(Routes.Settings))
        assertFalse(Routes.requiresPlan(Routes.Notifications))
        assertFalse(Routes.requiresPlan(Routes.AdminMethodology))
        assertFalse(Routes.requiresPlan(Routes.OperatorDashboard))
        assertFalse(Routes.requiresPlan(Routes.OperatorRecipes))
        assertFalse(Routes.requiresPlan(Routes.OperatorPrices))
        assertFalse(Routes.requiresPlan(Routes.OperatorGroceryPantry))
        assertFalse(Routes.requiresPlan(Routes.OperatorRules))
        assertFalse(Routes.requiresPlan(Routes.OperatorSystemInfo))
        assertFalse(Routes.requiresPlan(Routes.OperatorAdminSettings))
        assertFalse(Routes.requiresPlan(Routes.UserProfile))
        assertFalse(Routes.requiresPlan(Routes.GoalSelection))
        assertFalse(Routes.requiresPlan(Routes.Ipo))
        assertFalse(Routes.requiresPlan(Routes.MoreTools))
    }

    @Test
    fun requiresPlan_unknownRouteDefaultsToLocked() {
        assertTrue(Routes.requiresPlan("new_feature_route"))
    }

    @Test
    fun baseRoute_extractsRecipeBase() {
        assertEquals(Routes.RecipeDetails, Routes.baseRoute(Routes.recipeDetailsRoute("xyz")))
    }

    @Test
    fun recipeDetailsRoute_encodesDynamicSegments() {
        val route = Routes.recipeDetailsRoute("admin/recipe 1", "Lunch / Dinner")

        assertEquals("recipe_details/admin%2Frecipe+1?mealLabel=Lunch+%2F+Dinner", route)
        assertEquals(Routes.RecipeDetails, Routes.baseRoute(route))
    }
}
