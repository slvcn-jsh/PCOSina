package com.pcosina.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pcosina.app.ui.components.BottomNavBar
import com.pcosina.app.ui.navigation.Routes.RecipeIdArg
import com.pcosina.app.ui.screens.DashboardScreen
import com.pcosina.app.ui.screens.GroceryListScreen
import com.pcosina.app.ui.screens.GoalSelectionScreen
import com.pcosina.app.ui.screens.IpoVisualizationScreen
import com.pcosina.app.ui.screens.MealPlanScreen
import com.pcosina.app.ui.screens.OnboardingScreen
import com.pcosina.app.ui.screens.ProgressScreen
import com.pcosina.app.ui.screens.RecipeDetailsScreen
import com.pcosina.app.ui.screens.SplashScreen
import com.pcosina.app.ui.screens.UserProfileScreen

/**
 * App navigation host.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.Splash,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                onContinue = {
                    navController.navigate(Routes.Onboarding) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.Onboarding) {
            OnboardingScreen(
                onNext = { navController.navigate(Routes.UserProfile) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.UserProfile) {
            UserProfileScreen(
                onNext = { navController.navigate(Routes.GoalSelection) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.GoalSelection) {
            GoalSelectionScreen(
                onFinish = {
                    navController.navigate(Routes.Dashboard) {
                        // Clear the flow off the back stack once done.
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom tab destinations (share the same bottom nav)
        composable(Routes.Dashboard) {
            TabScaffold(navController = navController) { contentPadding ->
                DashboardScreen(
                    onRecipeClick = { id -> navController.navigate(Routes.recipeDetailsRoute(id)) },
                    onViewPlan = { navController.navigate(Routes.MealPlan) { tabNavigationOptions() } },
                    onViewIpo = { navController.navigate(Routes.Ipo) { tabNavigationOptions() } },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        composable(Routes.MealPlan) {
            TabScaffold(navController = navController) { contentPadding ->
                MealPlanScreen(
                    onRecipeClick = { id -> navController.navigate(Routes.recipeDetailsRoute(id)) },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        composable(Routes.GroceryList) {
            TabScaffold(navController = navController) { contentPadding ->
                GroceryListScreen(modifier = Modifier.padding(contentPadding))
            }
        }
        composable(Routes.Progress) {
            TabScaffold(navController = navController) { contentPadding ->
                ProgressScreen(
                    onBackToDashboard = {
                        navController.navigate(Routes.Dashboard) {
                            tabNavigationOptions()
                        }
                    },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        composable(Routes.Ipo) {
            TabScaffold(navController = navController) { contentPadding ->
                IpoVisualizationScreen(
                    onBackToDashboard = {
                        navController.navigate(Routes.Dashboard) {
                            tabNavigationOptions()
                        }
                    },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }

        composable(
            route = Routes.RecipeDetailsRoutePattern,
            arguments = listOf(
                navArgument(RecipeIdArg) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString(RecipeIdArg).orEmpty()
            RecipeDetailsScreen(
                recipeId = recipeId,
                onBackToMealPlan = {
                    navController.navigate(Routes.MealPlan) {
                        tabNavigationOptions()
                    }
                },
                onAddToGrocery = {
                    navController.navigate(Routes.GroceryList) {
                        tabNavigationOptions()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TabScaffold(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit,
) {
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentDestination = currentDestination,
                onNavigateToRoute = { route ->
                    if (route == currentDestination?.route) return@BottomNavBar
                    navController.navigate(route) {
                        tabNavigationOptions()
                    }
                },
            )
        },
    ) { innerPadding ->
        content(innerPadding)
    }
}

private fun NavOptionsBuilder.tabNavigationOptions() {
    // Standard bottom-nav behavior
    popUpTo(Routes.Dashboard) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

