package com.pcosina.app.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
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
import com.pcosina.app.ui.screens.SettingsScreen
import com.pcosina.app.ui.screens.SignUpScreen
import com.pcosina.app.ui.screens.SplashScreen
import com.pcosina.app.ui.screens.UserProfileScreen
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics

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
    val analytics = FirebaseAnalytics.getInstance(context)
    val feedbackEmail = "salvacion.jsh@gmail.com"
    val feedbackSubject = "PCOSINA Feedback"
    val feedbackBody = "Tell us what happened (steps, screen, and any errors):\n\n"
    val onFeedback: () -> Unit = {
        analytics.logEvent("feedback_tap", null)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$feedbackEmail")
            putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
            putExtra(Intent.EXTRA_TEXT, feedbackBody)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        }
    }
    
    // Repositories
    val userPrefsRepository = remember { UserPreferencesRepository(context) }
    val authRepository = remember { AuthRepository(context) }
    val mealPlanRepository = remember { MealPlanRepository() }
    val feedbackRepository = remember { FeedbackRepository(BuildConfig.BASE_URL) }
    val reflectionStore = remember { ReflectionStore(context) }
    
    // ViewModels
    val userViewModel: UserViewModel = viewModel(
        factory = UserViewModel.Factory(userPrefsRepository)
    )
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(authRepository)
    )
    val mealPlanViewModel: MealPlanViewModel = viewModel(
        factory = MealPlanViewModel.Factory(
            repository = mealPlanRepository,
            userPrefsRepository = userPrefsRepository
        )
    )
    val progressViewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.Factory(userPrefsRepository, reflectionStore, feedbackRepository)
    )
    // FIXED: Use Factory to prevent RuntimeException (NoSuchMethodException)
    val groceryViewModel: GroceryViewModel = viewModel(
        factory = GroceryViewModel.Factory(userPrefsRepository)
    )

    val session by authViewModel.session.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    val isProfileLoading by userViewModel.isProfileLoading.collectAsState()
    val currentRoute by navController.currentBackStackEntryAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()

    val splashReady = remember { mutableStateOf(false) }
    val hasNavigated = remember { mutableStateOf(false) }

    // Sync session to user data loading
    LaunchedEffect(Unit) {
        authRepository.syncSessionFromFirebase()
    }

    // Sync session to user data loading
    LaunchedEffect(session.currentUserUid, session.currentUserEmail) {
        val userId = session.currentUserUid
        if (userId.isNullOrBlank()) {
            userViewModel.reset()
            mealPlanViewModel.reset()
            groceryViewModel.reset()
            progressViewModel.reset()
            splashReady.value = false
            hasNavigated.value = false
        } else {
            session.currentUserEmail?.let { email ->
                userPrefsRepository.migrateFromEmailIfNeeded(userId, email)
            }
            userViewModel.loadProfileForUser(userId)
            mealPlanViewModel.loadSavedPlan(userId)
            groceryViewModel.loadGroceryForUser(userId)
        }
    }

    LaunchedEffect(activePlanId) {
        groceryViewModel.setActivePlan(activePlanId)
    }

    // Auth Guard
    LaunchedEffect(session.isLoggedIn) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        if (!session.isLoggedIn && 
            currentRoute != Routes.Login && 
            currentRoute != Routes.SignUp && 
            currentRoute != Routes.Splash) {
            userViewModel.reset()
            mealPlanViewModel.reset()
            groceryViewModel.reset()
            navController.navigate(Routes.Login) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Reset splash gate when navigating back to splash
    LaunchedEffect(currentRoute?.destination?.route) {
        if (currentRoute?.destination?.route == Routes.Splash) {
            splashReady.value = false
            hasNavigated.value = false
        }
    }

    fun isLegacyProfileComplete(profile: com.pcosina.app.data.model.UserProfile): Boolean {
        return profile.displayName.isNotBlank() ||
            profile.age > 0 ||
            profile.heightCm > 0 ||
            profile.weightKg > 0 ||
            profile.symptoms.isNotEmpty() ||
            profile.comorbidities.isNotEmpty() ||
            profile.dietaryRestrictions.isNotEmpty()
    }

    val inferredProfileCompleted = userProfile.isProfileCompleted || isLegacyProfileComplete(userProfile)

    // Migrate legacy profiles to completed to avoid forcing onboarding
    LaunchedEffect(session.currentUserUid, inferredProfileCompleted) {
        val userId = session.currentUserUid
        if (!userId.isNullOrBlank() && inferredProfileCompleted && !userProfile.isProfileCompleted) {
            userViewModel.setProfileCompleted(true)
        }
    }

    // Splash gate: only navigate once splash delay finished and profile load complete
    LaunchedEffect(
        splashReady.value,
        isProfileLoading,
        session.isLoggedIn,
        inferredProfileCompleted
    ) {
        if (!splashReady.value || isProfileLoading || hasNavigated.value) return@LaunchedEffect
        val target = when {
            !session.isLoggedIn -> Routes.Login
            !inferredProfileCompleted -> Routes.Onboarding
            else -> Routes.Dashboard
        }
        hasNavigated.value = true
        navController.navigate(target) {
            popUpTo(Routes.Splash) { inclusive = true }
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
                    splashReady.value = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.Login) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Routes.Splash) {
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
                    navController.navigate(Routes.Login) {
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
                isEditMode = false,
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.UserProfileEdit) {
            UserProfileScreen(
                userViewModel = userViewModel,
                onNext = { navController.popBackStack(Routes.Settings, false) },
                isEditMode = true,
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.GoalSelection) {
            GoalSelectionScreen(
                userViewModel = userViewModel,
                onFinish = {
                    userViewModel.setProfileCompleted(true)
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom tab destinations
        composable(Routes.Dashboard) {
            TabScaffold(navController = navController) { contentPadding ->
                DashboardScreen(
                    userViewModel = userViewModel,
                    authViewModel = authViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    onRecipeClick = { id -> navController.navigate(Routes.recipeDetailsRoute(id)) },
                    onViewPlan = { navController.navigate(Routes.MealPlan) { tabNavigationOptions() } },
                    onViewIpo = { navController.navigate(Routes.Ipo) { tabNavigationOptions() } },
                    onNavigateToSettings = { navController.navigate(Routes.Settings) },
                    onFeedback = onFeedback,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        composable(Routes.MealPlan) {
            TabScaffold(navController = navController) { contentPadding ->
                MealPlanScreen(
                    userViewModel = userViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    groceryViewModel = groceryViewModel,
                    onRecipeClick = { id -> navController.navigate(Routes.recipeDetailsRoute(id)) },
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        composable(Routes.GroceryList) {
            TabScaffold(navController = navController) { contentPadding ->
                GroceryListScreen(
                    groceryViewModel = groceryViewModel,
                    userViewModel = userViewModel,
                    modifier = Modifier.padding(contentPadding)
                )
            }
        }
        composable(Routes.Progress) {
            TabScaffold(navController = navController) { contentPadding ->
                ProgressScreen(
                    userViewModel = userViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    progressViewModel = progressViewModel,
                    userId = session.currentUserUid ?: "",
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

        composable(Routes.Settings) {
            SettingsScreen(
                userViewModel = userViewModel,
                authViewModel = authViewModel,
                mealPlanViewModel = mealPlanViewModel,
                groceryViewModel = groceryViewModel,
                progressViewModel = progressViewModel,
                userId = session.currentUserUid ?: "",
                onNavigateToProfileEdit = { navController.navigate(Routes.UserProfileEdit) },
                modifier = Modifier.fillMaxSize(),
            )
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
                mealPlanViewModel = mealPlanViewModel,
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
