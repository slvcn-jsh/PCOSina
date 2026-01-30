package com.pcosina.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pcosina.app.data.repository.AuthRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.BottomNavBar
import com.pcosina.app.ui.navigation.Routes.RecipeIdArg
import com.pcosina.app.ui.screens.DashboardScreen
import com.pcosina.app.ui.screens.GoalSelectionScreen
import com.pcosina.app.ui.screens.GroceryListScreen
import com.pcosina.app.ui.screens.IpoVisualizationScreen
import com.pcosina.app.ui.screens.LoginScreen
import com.pcosina.app.ui.screens.MealPlanScreen
import com.pcosina.app.ui.screens.OnboardingScreen
import com.pcosina.app.ui.screens.ProgressScreen
import com.pcosina.app.ui.screens.RecipeDetailsScreen
import com.pcosina.app.ui.screens.SignUpScreen
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
    val context = LocalContext.current
    
    // Repositories
    val userPrefsRepository = remember { UserPreferencesRepository(context) }
    val authRepository = remember { AuthRepository(context) }
    
    // ViewModels
    val userViewModel: UserViewModel = viewModel(
        factory = UserViewModel.Factory(userPrefsRepository)
    )
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(authRepository)
    )
    val groceryViewModel: GroceryViewModel = viewModel()

    val session by authViewModel.session.collectAsState()

    // Auth Guard: Redirect to Login if session expires or user is not logged in
    // This effect runs whenever the session changes
    LaunchedEffect(session.isLoggedIn) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        // If not logged in and not on an auth screen, redirect to login
        if (!session.isLoggedIn && 
            currentRoute != Routes.Login && 
            currentRoute != Routes.SignUp && 
            currentRoute != Routes.Splash) {
            navController.navigate(Routes.Login) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                onContinue = {
                    if (session.isLoggedIn) {
                        navController.navigate(Routes.Dashboard) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.Login) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate(Routes.SignUp) },
                modifier = Modifier.fillMaxSize()
            )
        }

        composable(Routes.SignUp) {
            SignUpScreen(
                authViewModel = authViewModel,
                onSignUpSuccess = {
                    navController.navigate(Routes.Onboarding) {
                        popUpTo(Routes.SignUp) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.navigate(Routes.Login) },
                modifier = Modifier.fillMaxSize()
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
                userViewModel = userViewModel,
                onNext = { navController.navigate(Routes.GoalSelection) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.GoalSelection) {
            GoalSelectionScreen(
                userViewModel = userViewModel,
                onFinish = {
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom tab destinations - Protected by Session check
        composable(Routes.Dashboard) {
            TabScaffold(navController = navController) { contentPadding ->
                DashboardScreen(
                    userViewModel = userViewModel,
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
                GroceryListScreen(
                    groceryViewModel = groceryViewModel,
                    modifier = Modifier.padding(contentPadding)
                )
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
                groceryViewModel = groceryViewModel,
                onBack = { navController.popBackStack() },
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
    popUpTo(Routes.Dashboard) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
