package com.pcosina.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pcosina.app.R
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.FriendlyEmptyStateCard
import com.pcosina.app.ui.components.LoadingActionButton
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.theme.PcosinaBlush
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaLightPink
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaRoseShadow
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.theme.PcosinaSurface
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt
import com.pcosina.app.ui.util.ActionFeedbackCopy
import com.pcosina.app.ui.util.GoalOption
import com.pcosina.app.ui.util.GuidedJourneyInput
import com.pcosina.app.ui.util.TodayMealDescriptor
import com.pcosina.app.ui.util.buildTodayLogSnapshot
import com.pcosina.app.ui.util.goalShoppingTips
import com.pcosina.app.ui.util.hasGoalSelection
import com.pcosina.app.ui.util.parseGoalOptions
import com.pcosina.app.ui.util.rememberIsOnline
import com.pcosina.app.ui.util.resolveGuidedJourneyStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private data class HomeMealCard(
    val mealLabel: String,
    val title: String,
    val recipeId: String,
    val calories: Int?,
    val isLogged: Boolean
)

private enum class HomeWeekState {
    Complete,
    Partial,
    Pending,
    Empty
}

private data class HomeWeekProgress(
    val label: String,
    val progressLabel: String,
    val state: HomeWeekState
)

@Composable
fun DashboardRefinedScreen(
    userViewModel: UserViewModel,
    authViewModel: AuthViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    onRecipeClick: (String, String?) -> Unit,
    onViewPlan: () -> Unit = {},
    onOpenMoreTools: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    onlineStateOverride: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    val dailyCalorieTarget = userViewModel.dailyCalorieTarget
    val calorieBreakdown = userViewModel.calorieTargetBreakdown
    val mealPlanState by mealPlanViewModel.uiState.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val planExpired by mealPlanViewModel.planExpired.collectAsState()
    val lastReviewedWeek by mealPlanViewModel.lastReviewedWeek.collectAsState()
    val groceryItems by groceryViewModel.groceryItems.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val goalInfoState = remember { mutableStateOf<GoalOption?>(null) }
    val feedbackBanner = remember { mutableStateOf<FeedbackBannerData?>(null) }
    val primaryActionState = remember { mutableStateOf(FeedbackActionState.Idle) }
    val coroutineScope = rememberCoroutineScope()
    val today = LocalDate.now()

    val activePlanResponse = remember(mealPlanState, planHistory, activePlanId) {
        (mealPlanState as? MealPlanUiState.Success)?.response
            ?: planHistory.firstOrNull { it.id == activePlanId }?.response
            ?: planHistory.maxByOrNull { it.generatedAt }?.response
    }
    val planExplanation = activePlanResponse?.explanation
    val hasPlan = activePlanResponse != null || planHistory.isNotEmpty()
    val hasReviewedWeek = activePlanId != null && activePlanId == lastReviewedWeek
    val guidedStep = resolveGuidedJourneyStep(
        GuidedJourneyInput(
            profileComplete = profile.isProfileCompleted,
            goal = profile.goal,
            hasPlan = hasPlan,
            hasReviewedWeek = hasReviewedWeek,
            hasGrocery = groceryItems.isNotEmpty(),
            hasTracked = logs.isNotEmpty()
        )
    )
    val weekStart = remember(activeWeekStart, today) {
        activeWeekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
    }
    val homeGoalOptions = remember(profile.goal) { parseGoalOptions(profile.goal).toList() }
    val todayLabel = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).lowercase(Locale.ENGLISH)
    }
    val tipLines = remember(profile.goal, profile.householdSize, today.dayOfYear) {
        val availableTips = goalShoppingTips(profile.goal, profile.householdSize)
        if (availableTips.isEmpty()) {
            emptyList()
        } else {
            List(minOf(2, availableTips.size)) { index ->
                availableTips[(today.dayOfYear - 1 + index) % availableTips.size]
            }
        }
    }
    val todayKey = remember(today) { today.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val todayPlan = activePlanResponse?.days?.firstOrNull {
        it.dayLabel.lowercase(Locale.ENGLISH) == todayLabel
    }
    val todayMeals = todayPlan?.meals.orEmpty()
    val todayCompletedIds = logs[todayKey]?.completedMealIds.orEmpty()
    val todayRecipeIds = remember(todayMeals) { todayMeals.map { it.recipeId }.distinct() }
    val mealCaloriesState = remember(todayRecipeIds) { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(todayRecipeIds) {
        if (todayRecipeIds.isEmpty()) {
            mealCaloriesState.value = emptyMap()
            return@LaunchedEffect
        }
        val resolved = mutableMapOf<String, Int>()
        todayRecipeIds.forEach { recipeId ->
            mealPlanViewModel.getRecipeDetails(recipeId).getOrNull()?.calories?.let { calories ->
                resolved[recipeId] = calories
            }
        }
        mealCaloriesState.value = resolved
    }

    val mealCaloriesByRecipeId = mealCaloriesState.value
    val todaySnapshot = remember(todayMeals, todayCompletedIds) {
        buildTodayLogSnapshot(
            todayMeals = todayMeals.map { meal ->
                TodayMealDescriptor(
                    mealLabel = meal.mealLabel,
                    title = meal.title,
                    recipeId = meal.recipeId
                )
            },
            completedMealIds = todayCompletedIds
        )
    }
    val mealCards = remember(todayMeals, todayCompletedIds, mealCaloriesByRecipeId) {
        todayMeals.map { meal ->
            HomeMealCard(
                mealLabel = meal.mealLabel,
                title = meal.title,
                recipeId = meal.recipeId,
                calories = mealCaloriesByRecipeId[meal.recipeId],
                isLogged = todayCompletedIds.contains("${meal.mealLabel}::${meal.recipeId}")
            )
        }
    }
    val weekProgress = remember(activePlanResponse, weekStart, today, logs) {
        val dayMealCounts = activePlanResponse
            ?.days
            ?.associate { it.dayLabel.lowercase(Locale.ENGLISH) to it.meals.size }
            .orEmpty()
        buildHomeWeekProgress(
            weekStart = weekStart,
            today = today,
            mealCountsByDayLabel = dayMealCounts,
            logs = logs
        )
    }
    val dateHeader = remember(today) {
        buildString {
            append(today.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)))
            append('\n')
            append(today.format(DateTimeFormatter.ofPattern("dd", Locale.ENGLISH)))
            append('\n')
            append(today.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)))
        }
    }
    val compactHomeLayout = screenWidthDp < 380
    val welcomeSubline = when {
        !hasPlan -> "You're one thoughtful step away from your first weekly meal plan."
        todaySnapshot.nextMeal != null -> "You're doing well today! ${todaySnapshot.nextMeal?.mealLabel} is your next focus."
        todaySnapshot.completedCount > 0 -> "You're doing well today! Ready for your next goal?"
        else -> "Your saved plan and progress are ready when you are."
    }
    val dualColumnCards = screenWidthDp >= 390
    val primaryActionTitle = when {
        !profile.isProfileCompleted -> "Finish your profile first"
        !hasGoalSelection(profile.goal) -> "Choose the goals you want to follow"
        !hasPlan -> "Ready to start your meal plan?"
        todaySnapshot.nextMeal != null -> "Your next meal is ready"
        else -> "Keep your week moving"
    }
    val primaryActionMessage = when {
        !profile.isProfileCompleted -> guidedStep.rationale
        !hasGoalSelection(profile.goal) -> guidedStep.rationale
        !hasPlan && isOnline -> "Let's generate a weekly plan that unlocks groceries, progress, and your daily meal flow."
        !hasPlan -> "${ActionFeedbackCopy.InternetRequired} Connect once to generate your first week."
        planExpired -> "Your saved week is no longer current. Open Meal Plan to generate the next week."
        todaySnapshot.nextMeal != null -> "Open ${todaySnapshot.nextMeal?.mealLabel?.lowercase(Locale.ENGLISH)} and keep today's routine visible."
        else -> "Review your progress or reopen this week's plan whenever you need a quick reset."
    }
    val primaryActionLabel = when {
        !profile.isProfileCompleted -> "Open Profile"
        !hasGoalSelection(profile.goal) -> "Choose Goals"
        !hasPlan -> "Go to Plan"
        todaySnapshot.nextMeal != null -> "Open ${todaySnapshot.nextMeal?.mealLabel.orEmpty()}"
        else -> "Open Progress"
    }
    val todayMealSubtitle = if (hasPlan) {
        "Good food, good mood."
    } else {
        "Create a plan to reveal today's assigned meals."
    }

    LaunchedEffect(feedbackBanner.value?.message) {
        val message = feedbackBanner.value?.message ?: return@LaunchedEffect
        delay(if (feedbackBanner.value?.tone == FeedbackBannerTone.Loading) 1200 else 2400)
        if (feedbackBanner.value?.message == message) {
            feedbackBanner.value = null
        }
    }

    fun postBanner(tone: FeedbackBannerTone, message: String) {
        feedbackBanner.value = FeedbackBannerData(tone = tone, message = message)
    }

    fun runPrimaryAction(loadingMessage: String, successMessage: String, action: () -> Unit) {
        if (primaryActionState.value == FeedbackActionState.Loading) return
        coroutineScope.launch {
            primaryActionState.value = FeedbackActionState.Loading
            postBanner(FeedbackBannerTone.Loading, loadingMessage)
            delay(150)
            action()
            primaryActionState.value = FeedbackActionState.Success
            postBanner(FeedbackBannerTone.Success, successMessage)
            delay(650)
            primaryActionState.value = FeedbackActionState.Idle
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(PcosinaSurface)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            RefinedBrandHeader(
                online = isOnline,
                onOpenSettings = onNavigateToSettings,
                onOpenSupport = onOpenMoreTools
            )
        }

        feedbackBanner.value?.let { banner ->
            item {
                AppFeedbackBanner(
                    data = banner,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            RefinedWelcomeCard(
                displayName = profile.displayName.ifBlank { "there" },
                dateHeader = dateHeader,
                welcomeSubline = welcomeSubline,
                nextFocusLabel = when {
                    !hasPlan -> guidedStep.ctaLabel
                    todaySnapshot.nextMeal != null -> "Next: ${todaySnapshot.nextMeal?.mealLabel}"
                    else -> "Week active"
                },
                compactLayout = compactHomeLayout,
            )
        }

        item {
            if (dualColumnCards) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RefinedGoalsCard(
                        goalOptions = homeGoalOptions,
                        modifier = Modifier.weight(1.25f),
                        onEditGoals = {
                            onNavigateToRoute(
                                if (profile.isProfileCompleted) Routes.GoalSelection else Routes.UserProfile
                            )
                        },
                        onOpenGoalInfo = { goalInfoState.value = it }
                    )
                    RefinedTipCard(
                        tipLines = tipLines,
                        modifier = Modifier.weight(0.85f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RefinedGoalsCard(
                        goalOptions = homeGoalOptions,
                        onEditGoals = {
                            onNavigateToRoute(
                                if (profile.isProfileCompleted) Routes.GoalSelection else Routes.UserProfile
                            )
                        },
                        onOpenGoalInfo = { goalInfoState.value = it }
                    )
                    RefinedTipCard(tipLines = tipLines)
                }
            }
        }

        item {
            RefinedSectionHeader(
                title = "Your Meal Plan for Today",
                subtitle = todayMealSubtitle
            )
        }

        item {
            if (!hasPlan) {
                FriendlyEmptyStateCard(
                    title = "No plan yet",
                    message = if (isOnline) {
                        "Generate your first week to fill today's meals and grocery list."
                    } else {
                        "${ActionFeedbackCopy.InternetRequired} Connect once to generate your first week."
                    },
                    actionLabel = if (isOnline) "Open Meal Plan" else null,
                    onAction = if (isOnline) onViewPlan else null,
                    accentColor = PcosinaPink
                )
            } else if (mealCards.isEmpty()) {
                FriendlyEmptyStateCard(
                    title = "No meals assigned today",
                    message = "Your week is saved, but there are no meal cards for today's date yet. Open Meal Plan to review the full week.",
                    actionLabel = "Open Meal Plan",
                    onAction = { onNavigateToRoute(Routes.MealPlan) },
                    accentColor = PcosinaPink
                )
            } else if (mealCards.size <= 2 || (mealCards.size == 3 && screenWidthDp >= 360)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mealCards.forEach { meal ->
                        RefinedMealCard(
                            meal = meal,
                            modifier = Modifier.weight(1f),
                            onClick = { onRecipeClick(meal.recipeId, meal.mealLabel) }
                        )
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mealCards) { meal ->
                        RefinedMealCard(
                            meal = meal,
                            modifier = Modifier.width((screenWidthDp * 0.38f).dp.coerceAtLeast(144.dp)),
                            onClick = { onRecipeClick(meal.recipeId, meal.mealLabel) }
                        )
                    }
                }
            }
        }

        item {
            RefinedSectionHeader(
                title = "Your Weekly Progress",
                subtitle = if (hasPlan) {
                    "${todaySnapshot.completedCount}/${todaySnapshot.plannedCount} meals logged today."
                } else {
                    "Generate one week first, then your day-by-day progress will appear here."
                }
            )
        }

        item {
            RefinedWeekProgressCard(
                entries = weekProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            RefinedPrimaryActionCard(
                title = primaryActionTitle,
                message = primaryActionMessage,
                buttonLabel = primaryActionLabel,
                state = primaryActionState.value,
                onClick = {
                    when {
                        !profile.isProfileCompleted -> onNavigateToRoute(Routes.UserProfile)
                        !hasGoalSelection(profile.goal) -> onNavigateToRoute(Routes.GoalSelection)
                        !hasPlan && !isOnline -> {
                            primaryActionState.value = FeedbackActionState.Error
                            postBanner(
                                tone = FeedbackBannerTone.Error,
                                message = "${ActionFeedbackCopy.InternetRequired} Connect once to generate your first week."
                            )
                            coroutineScope.launch {
                                delay(900)
                                if (primaryActionState.value == FeedbackActionState.Error) {
                                    primaryActionState.value = FeedbackActionState.Idle
                                }
                            }
                        }
                        !hasPlan -> runPrimaryAction(
                            loadingMessage = "Opening meal plan…",
                            successMessage = "Meal Plan opened."
                        ) {
                            onViewPlan()
                        }
                        todaySnapshot.nextMeal != null -> runPrimaryAction(
                            loadingMessage = "Opening next meal…",
                            successMessage = "Next meal opened."
                        ) {
                            onRecipeClick(
                                todaySnapshot.nextMeal!!.recipeId,
                                todaySnapshot.nextMeal!!.mealLabel
                            )
                        }
                        else -> runPrimaryAction(
                            loadingMessage = "Opening progress…",
                            successMessage = "Progress opened."
                        ) {
                            onNavigateToRoute(Routes.Progress)
                        }
                    }
                }
            )
        }
    }

    if (goalInfoState.value != null) {
        val option = goalInfoState.value!!
        AlertDialog(
            onDismissRequest = { goalInfoState.value = null },
            confirmButton = {
                TextButton(onClick = { goalInfoState.value = null }) {
                    Text("Back")
                }
            },
            title = {
                Text(
                    text = option.label,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(
                    text = goalInfoCopy(option),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }

}

@Composable
private fun RefinedBrandHeader(
    online: Boolean,
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = PcosinaBlush.copy(alpha = 0.24f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pcosina_logo),
                        contentDescription = "PCOSina",
                        modifier = Modifier
                            .size(48.dp)
                            .padding(5.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "PCOSina",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = PcosinaPink,
                            fontWeight = FontWeight.ExtraBold,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = PcosinaRoseShadow.copy(alpha = 0.3f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 4f),
                                blurRadius = 6f
                            )
                        )
                    )
                    Text(
                        text = "“Take the first step toward smarter PCOS nutrition.”",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = PcosinaMuted,
                        maxLines = 2
                    )
                }
            }
            RefinedIconAction(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings
            )
            Spacer(Modifier.width(8.dp))
            RefinedIconAction(
                icon = Icons.Filled.Info,
                contentDescription = "Support",
                onClick = onOpenSupport
            )
        }
        if (!online) {
            Surface(
                color = PcosinaSurfaceAlt,
                contentColor = PcosinaMuted,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Offline-safe mode: using saved local data.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun RefinedIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.28f)),
        shadowElevation = 6.dp
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = PcosinaPink,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun RefinedWelcomeCard(
    displayName: String,
    dateHeader: String,
    welcomeSubline: String,
    nextFocusLabel: String,
    compactLayout: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(PcosinaBlush)
            .border(2.dp, Color(0xFF30181E), RoundedCornerShape(26.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (compactLayout) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFFD6E1),
                            border = BorderStroke(1.5.dp, Color(0xFF30181E))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.pcosina_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(58.dp)
                                    .padding(6.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Welcome, $displayName!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = Color(0xFF662532),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFF662532).copy(alpha = 0.45f))
                            )
                            Text(
                                text = welcomeSubline,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF662532)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.42f)
                    ) {
                        Text(
                            text = dateHeader,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFF2B1B20),
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFFD6E1),
                        border = BorderStroke(1.5.dp, Color(0xFF30181E))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.pcosina_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .padding(6.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Welcome, $displayName!",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = Color(0xFF662532),
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFF662532).copy(alpha = 0.45f))
                        )
                        Text(
                            text = welcomeSubline,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF662532)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.42f)
                    ) {
                        Text(
                            text = dateHeader,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = Color(0xFF2B1B20),
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RefinedMetaPill(text = nextFocusLabel)
            }
        }
    }
}

@Composable
private fun RefinedMetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0xFF662532).copy(alpha = 0.14f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFF662532),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RefinedMiniStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(min = 90.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = PcosinaMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RefinedGoalsCard(
    goalOptions: List<GoalOption>,
    onEditGoals: () -> Unit,
    onOpenGoalInfo: (GoalOption) -> Unit,
    modifier: Modifier = Modifier
) {
    RefinedContentCard(
        modifier = modifier,
        containerColor = Color(0xFFF27693),
        title = "Your Goals",
        titleColor = Color(0xFF682937),
        titleTextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        contentPadding = 12.dp,
        contentSpacing = 8.dp,
        trailing = {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, Color(0xFF682937).copy(alpha = 0.25f)),
                modifier = Modifier.clickable(onClick = onEditGoals)
            ) {
                Text(
                    "Edit",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color(0xFF682937),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    ) {
        if (goalOptions.isEmpty()) {
            Text(
                text = "Choose at least one goal so the planner knows what to prioritize.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                goalOptions.forEach { option ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.86f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PcosinaPink
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                            Text(
                                text = option.label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = PcosinaDeepRose
                            )
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { onOpenGoalInfo(option) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "About ${option.label}",
                                    tint = PcosinaPink
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefinedTipCard(
    tipLines: List<String>,
    modifier: Modifier = Modifier
) {
    RefinedContentCard(
        modifier = modifier,
        containerColor = Color(0xFFE7B18C),
        title = "Daily Tips",
        titleColor = Color(0xFF6B2D24),
        titleTextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        contentPadding = 12.dp,
        contentSpacing = 8.dp
    ) {
        Text(
            text = tipLines.firstOrNull().orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF4B2E28)
        )
        tipLines.getOrNull(1)?.let { footer ->
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6C514C)
            )
        }
    }
}

@Composable
private fun RefinedContentCard(
    title: String,
    titleColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    titleTextStyle: TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    contentPadding: Dp = 14.dp,
    contentSpacing: Dp = 10.dp,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, PcosinaDeepRose.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = titleTextStyle,
                        color = titleColor
                    )
                    trailing?.invoke()
                }
                content()
            }
        )
    }
}

@Composable
private fun RefinedSectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = PcosinaDeepRose
            )
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted,
        )
    }
}

@Composable
private fun RefinedMealCard(
    meal: HomeMealCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (containerColor, accentColor) = mealPalette(meal.mealLabel)
    Card(
        modifier = modifier
            .heightIn(min = 136.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, Color(0xFF30181E).copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.58f)
                    ) {
                        Text(
                            text = mealIcon(meal.mealLabel),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Text(
                        text = meal.mealLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = accentColor
                    )
                }
                if (meal.isLogged) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.78f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Logged today",
                            tint = accentColor,
                            modifier = Modifier.padding(6.dp).size(14.dp)
                        )
                    }
                }
            }
            Text(
                text = meal.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF39212B),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f, fill = true))
            if (meal.calories != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = meal.calories.toString(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF2F1A21)
                    )
                    Text(
                        text = "kcal",
                        modifier = Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accentColor
                    )
                }
            } else {
                Text(
                    text = "Open recipe",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accentColor
                )
            }
        }
    }
}

@Composable
private fun RefinedWeekProgressCard(
    entries: List<HomeWeekProgress>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(2.dp, Color(0xFF30181E).copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            entries.forEachIndexed { index, entry ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = when (entry.state) {
                            HomeWeekState.Complete -> PcosinaPink
                            HomeWeekState.Partial -> PcosinaSoftPink
                            HomeWeekState.Pending -> Color.White
                            HomeWeekState.Empty -> PcosinaSurfaceAlt
                        },
                        border = BorderStroke(
                            2.dp,
                            when (entry.state) {
                                HomeWeekState.Complete -> PcosinaDeepRose
                                HomeWeekState.Partial -> PcosinaPink
                                HomeWeekState.Pending -> PcosinaMuted.copy(alpha = 0.5f)
                                HomeWeekState.Empty -> PcosinaMuted.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (entry.state) {
                                HomeWeekState.Complete -> Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                HomeWeekState.Partial -> Text(
                                    text = entry.progressLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PcosinaDeepRose
                                )
                                HomeWeekState.Pending -> Text(
                                    text = entry.progressLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PcosinaMuted
                                )
                                HomeWeekState.Empty -> Text(
                                    text = "--",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PcosinaMuted
                                )
                            }
                        }
                    }
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PcosinaDeepRose
                    )
                }
                if (index != entries.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .height(2.dp)
                            .background(PcosinaMuted.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}

@Composable
private fun RefinedPrimaryActionCard(
    title: String,
    message: String,
    buttonLabel: String,
    state: FeedbackActionState,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(colors = listOf(PcosinaLightPink.copy(alpha = 0.92f), PcosinaBlush.copy(alpha = 0.94f))))
            .border(2.dp, Color(0xFF30181E).copy(alpha = 0.72f), RoundedCornerShape(28.dp))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 10.dp)
                .size(108.dp)
                .clip(CircleShape)
                .background(PcosinaDeepRose.copy(alpha = 0.08f))
        )
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF682937)
                )
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF45232C)
            )
            LoadingActionButton(
                state = state,
                idleLabel = buttonLabel,
                loadingLabel = buttonLabel,
                successLabel = buttonLabel,
                errorLabel = "Try again",
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = PcosinaDeepRose,
                    contentColor = Color.White
                )
            )
        }
    }
}

private fun mealPalette(mealLabel: String): Pair<Color, Color> = when {
    mealLabel.equals("Breakfast", ignoreCase = true) -> Color(0xFFFFD4B2) to Color(0xFFC86C2B)
    mealLabel.equals("Lunch", ignoreCase = true) -> Color(0xFFFFC9DA) to Color(0xFFC14E7B)
    mealLabel.equals("Dinner", ignoreCase = true) -> Color(0xFFD8D9FF) to Color(0xFF6268D9)
    else -> PcosinaSurfaceAlt to PcosinaDeepRose
}

private fun mealIcon(mealLabel: String): String = when {
    mealLabel.equals("Breakfast", ignoreCase = true) -> "☀"
    mealLabel.equals("Lunch", ignoreCase = true) -> "🍴"
    mealLabel.equals("Dinner", ignoreCase = true) -> "☾"
    else -> "•"
}

private fun goalInfoCopy(option: GoalOption): String = when (option) {
    GoalOption.WeightLoss ->
        "Prioritizes calorie balance, satisfying meals, and realistic weekly adherence."
    GoalOption.SymptomManagement ->
        "Prioritizes symptom-aware nudges, steadier meals, and metabolic support."
    GoalOption.GeneralHealth ->
        "Balances overall nutrition quality, consistency, and everyday wellness."
}

private fun buildHomeWeekProgress(
    weekStart: LocalDate,
    today: LocalDate,
    mealCountsByDayLabel: Map<String, Int>,
    logs: Map<String, com.pcosina.app.data.model.DailyLog>
): List<HomeWeekProgress> {
    val formatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    return (0..6).map { offset ->
        val date = weekStart.plusDays(offset.toLong())
        val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dayToken = date.format(formatter).lowercase(Locale.ENGLISH)
        val plannedMeals = mealCountsByDayLabel[dayToken] ?: 0
        val completedMeals = logs[dateKey]?.completedMealIds?.size ?: 0
        val state = when {
            plannedMeals <= 0 -> HomeWeekState.Empty
            completedMeals >= plannedMeals -> HomeWeekState.Complete
            completedMeals > 0 -> HomeWeekState.Partial
            date.isAfter(today) -> HomeWeekState.Pending
            else -> HomeWeekState.Pending
        }
        HomeWeekProgress(
            label = dayToken.uppercase(Locale.ENGLISH),
            progressLabel = if (plannedMeals > 0 && completedMeals > 0) {
                "${(completedMeals.toFloat() / plannedMeals.toFloat() * 100).toInt()}%"
            } else {
                if (plannedMeals > 0) "0%" else "--"
            },
            state = state
        )
    }
}
