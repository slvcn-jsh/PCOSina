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
}
