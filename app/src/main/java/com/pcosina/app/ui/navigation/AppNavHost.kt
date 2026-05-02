package com.pcosina.app.ui.navigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.pcosina.app.data.repository.UserPreferencesGroceryLocalRepository
import com.pcosina.app.data.repository.MealPlanRepository
import com.pcosina.app.data.repository.UserPreferencesNotificationLocalRepository
import com.pcosina.app.data.repository.UserPreferencesProgressLocalRepository
import com.pcosina.app.data.repository.UserPreferencesPlannerLocalRepository
import com.pcosina.app.data.repository.UserPreferencesRepository
import com.pcosina.app.data.repository.UserPreferencesUserProfileLocalRepository
import com.pcosina.app.data.repository.ReflectionStore
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.BottomNavBar
import com.pcosina.app.ui.components.DefaultBottomNavItems
import com.pcosina.app.ui.navigation.Routes.MealLabelArg
import com.pcosina.app.ui.navigation.Routes.RecipeIdArg
import com.pcosina.app.ui.screens.DashboardRefinedScreen
import com.pcosina.app.ui.screens.CommunityScreen
import com.pcosina.app.ui.screens.GoalSelectionScreen
import com.pcosina.app.ui.screens.GroceryRefinedScreen
import com.pcosina.app.ui.screens.IpoVisualizationScreen
import com.pcosina.app.ui.screens.LoginScreen
import com.pcosina.app.ui.screens.MealPlanRefinedScreen
import com.pcosina.app.ui.screens.MoreToolsScreen
import com.pcosina.app.ui.screens.ProgressRefinedScreen
import com.pcosina.app.ui.screens.RecipeDetailsScreen
import com.pcosina.app.ui.screens.SettingsScreen
import com.pcosina.app.ui.screens.SignUpScreen
import com.pcosina.app.ui.screens.SplashScreen
import com.pcosina.app.ui.screens.UserProfileScreen
import com.pcosina.app.ui.util.hasGoalSelection
import com.pcosina.app.ui.util.unknownGoalTokens
import com.pcosina.app.data.repository.FeedbackRepository
import com.pcosina.app.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import android.widget.Toast
import android.util.Log
import com.pcosina.app.notifications.NotificationScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
        } else {
            Toast.makeText(context, "No email app found. Use Progress → Send Feedback.", Toast.LENGTH_LONG).show()
        }
    }
    
    // Repositories
    val userPrefsRepository = remember { UserPreferencesRepository(context) }
    val userProfileLocalRepository = remember { UserPreferencesUserProfileLocalRepository(userPrefsRepository) }
    val groceryLocalRepository = remember { UserPreferencesGroceryLocalRepository(userPrefsRepository) }
    val plannerLocalRepository = remember { UserPreferencesPlannerLocalRepository(userPrefsRepository) }
    val progressLocalRepository = remember { UserPreferencesProgressLocalRepository(userPrefsRepository) }
    val notificationLocalRepository = remember { UserPreferencesNotificationLocalRepository(userPrefsRepository) }
    val authRepository = remember { AuthRepository(context) }
    val mealPlanRepository = remember { MealPlanRepository() }
    val feedbackRepository = remember { FeedbackRepository(BuildConfig.BASE_URL) }
    val reflectionStore = remember { ReflectionStore(context) }
    
    // ViewModels
    val userViewModel: UserViewModel = viewModel(
        factory = UserViewModel.Factory(userProfileLocalRepository, notificationLocalRepository)
    )
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(authRepository)
    )
    val mealPlanViewModel: MealPlanViewModel = viewModel(
        factory = MealPlanViewModel.Factory(
            repository = mealPlanRepository,
            plannerLocalRepository = plannerLocalRepository
        )
    )
    val progressViewModel: ProgressViewModel = viewModel(
        factory = ProgressViewModel.Factory(progressLocalRepository, reflectionStore, feedbackRepository)
    )
    // FIXED: Use Factory to prevent RuntimeException (NoSuchMethodException)
    val groceryViewModel: GroceryViewModel = viewModel(
        factory = GroceryViewModel.Factory(groceryLocalRepository)
    )

    val session by authViewModel.session.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    val adminMode by userViewModel.adminMode.collectAsState()
    val isProfileLoading by userViewModel.isProfileLoading.collectAsState()
    val currentRoute by navController.currentBackStackEntryAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val notificationPrefs by userViewModel.notificationPreferences.collectAsState()

    val splashReady = remember { mutableStateOf(false) }
    val hasNavigated = remember { mutableStateOf(false) }
    val profileCloudSyncInProgress = remember { mutableStateOf(false) }
    val unknownRouteWarnings = remember { mutableSetOf<String>() }
    val unknownGoalWarnings = remember { mutableSetOf<String>() }
    val pendingOperatorAccess = remember { mutableStateOf(false) }
    val operatorAuthorized = remember { mutableStateOf(false) }
    val operatorAccessResolved = remember { mutableStateOf(false) }
    val operatorAccessWarning = remember { mutableStateOf<String?>(null) }

    fun navigateInternal(route: String, options: (NavOptionsBuilder.() -> Unit)? = null) {
        val base = Routes.baseRoute(route).orEmpty()
        if (!Routes.isKnownRoute(route)) {
            if (BuildConfig.DEBUG && base.isNotBlank() && unknownRouteWarnings.add(base)) {
                Log.w("PCOSINA", "Blocked non-app route: '$route'. Use Routes constants/helpers.")
            }
            return
        }
        navController.navigateKnown(route) {
            options?.invoke(this)
        }
    }

    fun navigateFromRefinedShell(route: String) {
        when (Routes.baseRoute(route)) {
            Routes.Dashboard,
            Routes.MealPlan,
            Routes.GroceryList,
            Routes.Progress,
            Routes.Ipo -> navigateInternal(route) { tabNavigationOptions() }
            else -> navigateInternal(route)
        }
    }

    fun isOperatorRoute(route: String?): Boolean {
        return when (Routes.baseRoute(route)) {
            Routes.MoreTools,
            Routes.AdminMethodology -> true
            else -> false
        }
    }

    // Sync session to user data loading
    LaunchedEffect(Unit) {
        authRepository.syncSessionFromFirebase()
        userPrefsRepository.migrateAllProfileCompletionAliases()
        val legacyCount = userPrefsRepository.countLegacyProfileCompletionAliases()
        analytics.logEvent(
            "legacy_completion_alias_count",
            Bundle().apply { putLong("count", legacyCount.toLong()) }
        )
        if (BuildConfig.DEBUG && legacyCount > 0) {
            Log.w("PCOSINA", "Legacy onboarding completion keys still present: $legacyCount")
        }
    }

    // Sync session to user data loading
    LaunchedEffect(session.currentUserUid, session.currentUserEmail) {
        val userId = session.currentUserUid
        if (userId.isNullOrBlank()) {
            userViewModel.reset()
            userViewModel.setAdminMode(false)
            operatorAuthorized.value = false
            mealPlanViewModel.reset()
            groceryViewModel.reset()
            progressViewModel.reset()
            NotificationScheduler.cancelAllForSession(context)
            profileCloudSyncInProgress.value = false
            splashReady.value = false
            hasNavigated.value = false
            pendingOperatorAccess.value = false
            operatorAccessResolved.value = false
            operatorAccessWarning.value = null
        } else {
            if (!pendingOperatorAccess.value && !adminMode) {
                operatorAuthorized.value = false
                operatorAccessResolved.value = true
                operatorAccessWarning.value = null
            } else {
                operatorAccessResolved.value = false
                val operatorAccessResult = runCatching {
                    authRepository.getCurrentUserOperatorAccess(forceRefresh = true)
                }
                val operatorAccess = operatorAccessResult.getOrNull()
                val operatorAccessGranted = operatorAccess?.allowed == true
                operatorAuthorized.value = operatorAccessGranted
                operatorAccessWarning.value = when {
                    !pendingOperatorAccess.value -> null
                    operatorAccessResult.isFailure ->
                        "Couldn't verify operator access right now. Try signing in again when the connection is stable."
                    operatorAccessGranted -> null
                    else -> operatorAccess?.message ?: "This account can sign in, but it doesn't have operator access."
                }
                operatorAccessResolved.value = true
            }
            session.currentUserEmail?.let { email ->
                userPrefsRepository.migrateFromEmailIfNeeded(userId, email)
            }
            userPrefsRepository.migrateProfileCompletionKeyIfNeeded(userId)
            userViewModel.loadProfileForUser(userId)
            profileCloudSyncInProgress.value = true
            try {
                userPrefsRepository.syncProfileWithCloud(userId)
            } finally {
                profileCloudSyncInProgress.value = false
            }
            mealPlanViewModel.loadSavedPlan(userId)
            groceryViewModel.loadGroceryForUser(userId)
        }
    }

    LaunchedEffect(session.currentUserUid, notificationPrefs) {
        val uid = session.currentUserUid
        if (uid.isNullOrBlank()) {
            NotificationScheduler.cancelAllForSession(context)
            return@LaunchedEffect
        }
        NotificationScheduler.rescheduleAll(
            context = context,
            userId = uid,
            prefs = notificationPrefs
        )
    }

    LaunchedEffect(session.currentUserUid, userProfile.goal) {
        if (session.currentUserUid.isNullOrBlank()) return@LaunchedEffect
        val unknown = unknownGoalTokens(userProfile.goal)
        if (unknown.isEmpty()) return@LaunchedEffect
        val key = unknown.sorted().joinToString(",")
        if (unknownGoalWarnings.add(key)) {
            analytics.logEvent(
                "unknown_goal_tokens",
                Bundle().apply { putLong("count", unknown.size.toLong()) }
            )
            if (BuildConfig.DEBUG) {
                Log.w("PCOSINA", "Unknown goal tokens encountered: $key")
            }
        }
    }

    LaunchedEffect(activePlanId) {
        groceryViewModel.setActivePlan(activePlanId)
    }

    LaunchedEffect(session.currentUserUid, activeWeekStart) {
        val userId = session.currentUserUid
        if (userId.isNullOrBlank()) return@LaunchedEffect
        val weekStart = activeWeekStart
            ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        progressViewModel.loadForUser(userId, weekStart)
    }

    // Auth Guard
    LaunchedEffect(session.isLoggedIn) {
        val currentRoute = Routes.baseRoute(navController.currentBackStackEntry?.destination?.route)
        if (!session.isLoggedIn && 
            currentRoute != Routes.Login && 
            currentRoute != Routes.SignUp && 
            currentRoute != Routes.Splash) {
            userViewModel.reset()
            mealPlanViewModel.reset()
            groceryViewModel.reset()
            navigateInternal(Routes.Login) {
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
    val profileBoundToSession = session.currentUserUid?.let { uid ->
        userViewModel.activeUserId == uid
    } ?: true
    val profileReadyForRouting = !session.isLoggedIn ||
        (profileBoundToSession && !isProfileLoading && !profileCloudSyncInProgress.value)
    val hasPlan = planHistory.isNotEmpty() || mealPlanViewModel.uiState.value is com.pcosina.app.ui.MealPlanUiState.Success
    val enabledRoutes = remember(hasPlan) {
        val base = mutableSetOf(
            Routes.Dashboard,
            Routes.MealPlan,
            Routes.GroceryList,
            Routes.Progress,
            Routes.Ipo
        )
        if (!hasPlan) {
            base.remove(Routes.GroceryList)
            base.remove(Routes.Progress)
        }
        base
    }

    // Migrate legacy profiles to completed to avoid forcing setup loops
    LaunchedEffect(session.currentUserUid, inferredProfileCompleted) {
        val userId = session.currentUserUid
        if (!userId.isNullOrBlank() && inferredProfileCompleted && !userProfile.isProfileCompleted) {
            userViewModel.setProfileCompleted(true)
        }
    }

    // Splash gate: only navigate once splash delay finished and profile load complete
    LaunchedEffect(
        splashReady.value,
        profileReadyForRouting,
        session.isLoggedIn,
        inferredProfileCompleted,
        pendingOperatorAccess.value,
        operatorAccessResolved.value,
        adminMode,
        operatorAuthorized.value
    ) {
        if (!splashReady.value || !profileReadyForRouting || hasNavigated.value) return@LaunchedEffect
        if (pendingOperatorAccess.value && session.isLoggedIn && !operatorAccessResolved.value) return@LaunchedEffect
        val activateOperatorMode = pendingOperatorAccess.value && operatorAuthorized.value
        userViewModel.setAdminMode(activateOperatorMode)
        val target = when {
            !session.isLoggedIn -> Routes.Login
            activateOperatorMode -> Routes.MoreTools
            !inferredProfileCompleted -> Routes.UserProfile
            else -> Routes.Dashboard
        }
        val warnMessage = operatorAccessWarning.value
        val shouldWarn = pendingOperatorAccess.value && !activateOperatorMode && !warnMessage.isNullOrBlank()
        hasNavigated.value = true
        pendingOperatorAccess.value = false
        operatorAccessWarning.value = null
        navigateInternal(target) {
            popUpTo(Routes.Splash) { inclusive = true }
        }
        if (shouldWarn) {
            Toast.makeText(context, warnMessage, Toast.LENGTH_LONG).show()
        }
    }

    // Guided guardrails: always route users to the next required step.
    LaunchedEffect(
        session.isLoggedIn,
        profileReadyForRouting,
        inferredProfileCompleted,
        userProfile.goal,
        hasPlan,
        currentRoute?.destination?.route
    ) {
        if (!session.isLoggedIn || !profileReadyForRouting) return@LaunchedEffect
        val route = currentRoute?.destination?.route ?: return@LaunchedEffect
        val baseRoute = Routes.baseRoute(route)
        if (Routes.isAuthRoute(route)) return@LaunchedEffect
        if (!Routes.isKnownRoute(route)) {
            val base = Routes.baseRoute(route).orEmpty()
            if (BuildConfig.DEBUG && base.isNotBlank() && unknownRouteWarnings.add(base)) {
                Log.w("PCOSINA", "Unclassified route '$base'. Add it to Routes routeAccessByBase.")
            }
            return@LaunchedEffect
        }
        if (isOperatorRoute(route) && (!operatorAuthorized.value || !adminMode)) {
            val message = if (operatorAuthorized.value) {
                "Use operator access from the login screen to open operator tools."
            } else {
                "Operator access is only available for authorized accounts."
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            userViewModel.setAdminMode(false)
            navigateInternal(Routes.Dashboard) {
                tabNavigationOptions()
            }
            return@LaunchedEffect
        }
        if (adminMode && isOperatorRoute(route)) return@LaunchedEffect

        when {
            !inferredProfileCompleted && !Routes.isProfileRoute(route) && !Routes.isGoalRoute(route) -> {
                navigateInternal(Routes.UserProfile) {
                    launchSingleTop = true
                }
            }
            inferredProfileCompleted &&
                hasGoalSelection(userProfile.goal) &&
                (baseRoute == Routes.UserProfile || baseRoute == Routes.GoalSelection) -> {
                navController.clearSetupFlowBackStack()
                navigateInternal(Routes.MealPlan) {
                    tabNavigationOptions()
                }
            }
            !hasGoalSelection(userProfile.goal) && !Routes.isProfileRoute(route) && !Routes.isGoalRoute(route) -> {
                navigateInternal(Routes.GoalSelection) {
                    launchSingleTop = true
                }
            }
            !hasPlan && Routes.requiresPlan(route) -> {
                Toast.makeText(context, "Generate your plan first to unlock this step.", Toast.LENGTH_SHORT).show()
                navigateInternal(Routes.MealPlan) {
                    tabNavigationOptions()
                }
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
                    splashReady.value = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.Login) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = { operatorRequested ->
                    pendingOperatorAccess.value = operatorRequested
                    operatorAccessResolved.value = false
                    operatorAccessWarning.value = null
                    navigateInternal(Routes.Splash) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navigateInternal(Routes.SignUp) },
                modifier = Modifier.fillMaxSize()
            )
        }

        composable(Routes.SignUp) {
            SignUpScreen(
                authViewModel = authViewModel,
                onSignUpSuccess = {
                    navigateInternal(Routes.Login) {
                        popUpTo(Routes.SignUp) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navigateInternal(Routes.Login) },
                modifier = Modifier.fillMaxSize()
            )
        }

        composable(Routes.UserProfile) {
            UserProfileScreen(
                userViewModel = userViewModel,
                onNext = { navigateInternal(Routes.GoalSelection) },
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
                    navController.clearSetupFlowBackStack()
                    navigateInternal(Routes.MealPlan) {
                        tabNavigationOptions()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom tab destinations
        composable(Routes.Dashboard) {
            TabScaffold(navController = navController, enabledRoutes = enabledRoutes) { contentPadding ->
                DashboardRefinedScreen(
                    userViewModel = userViewModel,
                    authViewModel = authViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    groceryViewModel = groceryViewModel,
                    progressViewModel = progressViewModel,
                    onRecipeClick = { id, mealLabel -> navigateInternal(Routes.recipeDetailsRoute(id, mealLabel)) },
                    onViewPlan = { navigateInternal(Routes.MealPlan) { tabNavigationOptions() } },
                    onOpenMoreTools = { navigateInternal(Routes.Ipo) { tabNavigationOptions() } },
                    onNavigateToSettings = { navigateInternal(Routes.Settings) },
                    onNavigateToRoute = ::navigateFromRefinedShell,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        composable(Routes.MealPlan) {
            TabScaffold(navController = navController, enabledRoutes = enabledRoutes) { contentPadding ->
                MealPlanRefinedScreen(
                    userViewModel = userViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    groceryViewModel = groceryViewModel,
                    progressViewModel = progressViewModel,
                    onRecipeClick = { id, mealLabel -> navigateInternal(Routes.recipeDetailsRoute(id, mealLabel)) },
                    onViewProgress = { navigateInternal(Routes.Progress) { tabNavigationOptions() } },
                    onNavigateToRoute = ::navigateFromRefinedShell,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        composable(Routes.GroceryList) {
            TabScaffold(navController = navController, enabledRoutes = enabledRoutes) { contentPadding ->
                GroceryRefinedScreen(
                    groceryViewModel = groceryViewModel,
                    userViewModel = userViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    progressViewModel = progressViewModel,
                    onNavigateToRoute = ::navigateFromRefinedShell,
                    modifier = Modifier.padding(contentPadding)
                )
            }
        }
        composable(Routes.Progress) {
            TabScaffold(navController = navController, enabledRoutes = enabledRoutes) { contentPadding ->
                ProgressRefinedScreen(
                    userViewModel = userViewModel,
                    mealPlanViewModel = mealPlanViewModel,
                    progressViewModel = progressViewModel,
                    groceryViewModel = groceryViewModel,
                    userId = session.currentUserUid ?: "",
                    onBackToDashboard = {
                        navigateInternal(Routes.Dashboard) {
                            tabNavigationOptions()
                        }
                    },
                    onNavigateToRoute = ::navigateFromRefinedShell,
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
        composable(Routes.Ipo) {
            TabScaffold(navController = navController, enabledRoutes = enabledRoutes) { contentPadding ->
                CommunityScreen(
                    onFeedback = onFeedback,
                    onBack = {
                        navigateInternal(Routes.Dashboard) {
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
                onNavigateToProfileEdit = { navigateInternal(Routes.UserProfileEdit) },
                onOpenAdminMethodology = { navigateInternal(Routes.AdminMethodology) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.MoreTools) {
            MoreToolsScreen(
                onBack = { navController.popBackStack() },
                onOpenSupport = {
                    navigateInternal(Routes.Ipo) {
                        tabNavigationOptions()
                    }
                },
                onOpenMethodology = {
                    navigateInternal(Routes.AdminMethodology)
                },
                onFeedback = onFeedback,
                showAdminTools = adminMode,
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.AdminMethodology) {
            IpoVisualizationScreen(
                onBackToDashboard = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(
            route = Routes.RecipeDetailsRoutePattern,
            arguments = listOf(
                navArgument(RecipeIdArg) { type = NavType.StringType },
                navArgument(MealLabelArg) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString(RecipeIdArg).orEmpty()
            val mealLabelHint = backStackEntry.arguments?.getString(MealLabelArg)
            RecipeDetailsScreen(
                recipeId = recipeId,
                plannedMealLabelHint = mealLabelHint,
                mealPlanViewModel = mealPlanViewModel,
                groceryViewModel = groceryViewModel,
                progressViewModel = progressViewModel,
                goal = userProfile.goal,
                adminMode = adminMode,
                householdSize = userProfile.householdSize,
                onBack = { navController.popBackStack() },
                onAddToGrocery = {
                    navigateInternal(Routes.GroceryList) {
                        tabNavigationOptions()
                    }
                },
                onNavigateToRoute = { route ->
                    when (route) {
                        Routes.Dashboard,
                        Routes.MealPlan,
                        Routes.GroceryList,
                        Routes.Progress,
                        Routes.Ipo -> navigateInternal(route) { tabNavigationOptions() }
                        else -> navigateInternal(route)
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
    enabledRoutes: Set<String> = DefaultBottomNavItems.map { it.route }.toSet(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentBackStackEntry?.destination
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentDestination = currentDestination,
                onNavigateToRoute = { route ->
                    if (route == currentDestination?.route) return@BottomNavBar
                    if (!Routes.isKnownRoute(route)) {
                        Log.w("PCOSINA", "Blocked non-app tab route: '$route'")
                        return@BottomNavBar
                    }
                    navController.navigateKnown(route) {
                        tabNavigationOptions()
                    }
                },
                enabledRoutes = enabledRoutes,
                onDisabledRouteClick = {
                    Toast.makeText(context, "Generate a plan to unlock this tab.", Toast.LENGTH_SHORT).show()
                    navController.navigateKnown(Routes.MealPlan) { tabNavigationOptions() }
                }
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

private fun NavHostController.clearSetupFlowBackStack() {
    popBackStack(Routes.GoalSelection, inclusive = true)
    popBackStack(Routes.UserProfile, inclusive = true)
}

private fun NavHostController.navigateKnown(
    route: String,
    options: NavOptionsBuilder.() -> Unit = {}
) {
    if (!Routes.isKnownRoute(route)) return
    navigate(route, options)
}
