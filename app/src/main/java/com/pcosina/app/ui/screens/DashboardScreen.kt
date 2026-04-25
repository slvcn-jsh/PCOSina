package com.pcosina.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.BuildConfig
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GuidedJourneyCard
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.StatCard
import com.pcosina.app.ui.components.ExpandableSection
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.CompactWidgetGrid
import com.pcosina.app.ui.components.CompactWidgetSpec
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.FocusModePanel
import com.pcosina.app.ui.components.FriendlyEmptyStateCard
import com.pcosina.app.ui.components.LoadingActionButton
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip
import com.pcosina.app.ui.components.StatusCenterCard
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.util.ActionFeedbackCopy
import com.pcosina.app.ui.util.formatTodayKcalDeltaShort
import com.pcosina.app.ui.util.LockedFlowCopy
import com.pcosina.app.ui.util.shouldShowAdvancedMetrics
import com.pcosina.app.ui.util.shouldShowAdvancedTools
import com.pcosina.app.ui.util.shouldCompressSecondaryStats
import com.pcosina.app.ui.util.shouldUseFirstPlanUnlockCopy
import com.pcosina.app.ui.util.shouldShowProgressSnapshot
import com.pcosina.app.ui.util.GuidedJourneyInput
import com.pcosina.app.ui.util.TodayMealDescriptor
import com.pcosina.app.ui.util.buildTodayLogSnapshot
import com.pcosina.app.ui.util.remainingTodayMealSlots
import com.pcosina.app.ui.util.primaryGoalLabel
import com.pcosina.app.domain.householdSizeLabel
import com.pcosina.app.ui.util.primaryGoalShortLabel
import com.pcosina.app.ui.util.rememberIsOnline
import com.pcosina.app.ui.util.resolveGuidedJourneyStep
import com.pcosina.app.ui.util.sampleFrameTiming
import java.util.Locale
import android.util.Log
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import com.pcosina.app.ui.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DashboardFocus {
    Overview,
    Today,
    Insights,
    Support,
}

@Composable
fun DashboardScreen(
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
    val adminMode by userViewModel.adminMode.collectAsState()
    val dailyCalorieTarget = userViewModel.dailyCalorieTarget
    val calorieBreakdown = userViewModel.calorieTargetBreakdown
    val mealPlanState by mealPlanViewModel.uiState.collectAsState()
    val metrics by mealPlanViewModel.planMetrics.collectAsState()
    val planExpired by mealPlanViewModel.planExpired.collectAsState()
    val planExplanation = (mealPlanState as? MealPlanUiState.Success)?.response?.explanation
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val lastReviewedWeek by mealPlanViewModel.lastReviewedWeek.collectAsState()
    val groceryItems by groceryViewModel.groceryItems.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val showMarkersInfo = rememberSaveable { mutableStateOf(false) }
    val showTargetInfo = rememberSaveable { mutableStateOf(false) }
    val showBmiInfo = rememberSaveable { mutableStateOf(false) }
    val learnMoreCopy = rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var dashboardFeedbackBanner by remember { mutableStateOf<FeedbackBannerData?>(null) }
    var dashboardActionNoteTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var dashboardActionNoteDetail by rememberSaveable { mutableStateOf<String?>(null) }
    val dashboardHeroActionState = remember { mutableStateOf(FeedbackActionState.Idle) }
    val hasPlan = planHistory.isNotEmpty() || mealPlanState is MealPlanUiState.Success
    val activePlanId = mealPlanViewModel.activePlanId.collectAsState().value
    val hasReviewedWeek = activePlanId != null && activePlanId == lastReviewedWeek
    val hasGrocery = groceryItems.isNotEmpty()
    val hasTracked = logs.isNotEmpty()
    val guidedStep = resolveGuidedJourneyStep(
        GuidedJourneyInput(
            profileComplete = profile.isProfileCompleted,
            goal = profile.goal,
            hasPlan = hasPlan,
            hasReviewedWeek = hasReviewedWeek,
            hasGrocery = hasGrocery,
            hasTracked = hasTracked
        )
    )
    val showAdvancedInsights = shouldShowAdvancedMetrics(guidedStep.stepIndex, hasTracked)
    val showSecondaryCards = shouldShowAdvancedTools(guidedStep.stepIndex)
    val showJourneyCard = !hasPlan || !hasGrocery || !hasTracked
    val moreToolsSubtitle = if (showSecondaryCards) {
        "Open quick help, feedback, and extra support when you need it."
    } else {
        "Get simple help without leaving your week."
    }
    val snapshotLockedCopy = remember { LockedFlowCopy.dashboardSnapshotLocked() }
    val advancedLockedCopy = remember { LockedFlowCopy.dashboardAdvancedLocked() }
    val dashboardChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 128.dp, medium = 192.dp)
    var dashboardFocusKey by rememberSaveable { mutableStateOf(DashboardFocus.Overview.name) }
    val helperCopyMaxLines = if (screenWidthDp <= 360) 1 else 2
    val today = LocalDate.now()
    val dayLabelFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    val todayLabel = today.format(dayLabelFmt).lowercase(Locale.ENGLISH)
    val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val todayPlan = (mealPlanState as? MealPlanUiState.Success)?.response?.days?.firstOrNull {
        it.dayLabel.lowercase(Locale.ENGLISH) == todayLabel
    }
    val todayMeals = todayPlan?.meals.orEmpty()
    val todayCompletedIds = logs[todayKey]?.completedMealIds.orEmpty()
    val todayMealDescriptors = remember(todayMeals) {
        todayMeals.map { meal ->
            TodayMealDescriptor(
                mealLabel = meal.mealLabel,
                title = meal.title,
                recipeId = meal.recipeId
            )
        }
    }
    val todaySnapshot = remember(todayMealDescriptors, todayCompletedIds) {
        buildTodayLogSnapshot(
            todayMeals = todayMealDescriptors,
            completedMealIds = todayCompletedIds
        )
    }
    val todayCompletedCount = todaySnapshot.completedCount
    val todayProgress = if (todayMeals.isNotEmpty()) {
        (todayCompletedCount.toFloat() / todayMeals.size.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val todayCalorieTarget = (todayPlan?.totalCalories ?: 0).takeIf { it > 0 }
        ?: dailyCalorieTarget.takeIf { it > 0 }
    val todayEstimatedCalories = todayCalorieTarget?.let { (it * todayProgress).toInt() }
    val todayCalorieDelta = if (todayCalorieTarget != null && todayEstimatedCalories != null) {
        todayCalorieTarget - todayEstimatedCalories
    } else null
    val todayDeltaLabel = todayCalorieDelta?.let(::formatTodayKcalDeltaShort)
    val animatedTodayProgress by animateFloatAsState(
        targetValue = todayProgress,
        animationSpec = tween(UiMotionTokens.DashboardRingProgressMs),
        label = "todayProgressRing"
    )
    val nextUnloggedMeal = todaySnapshot.nextMeal?.let { next ->
        todayMeals.firstOrNull { meal ->
            meal.recipeId == next.recipeId &&
                meal.mealLabel.equals(next.mealLabel, ignoreCase = true)
        }
    }
    val todayTimeline = remember(todayMeals, todayCompletedIds, nextUnloggedMeal?.mealLabel, nextUnloggedMeal?.recipeId) {
        buildTodayTimelineSteps(
            todayMeals = todayMeals.map { it.mealLabel to it.recipeId },
            completedIds = todayCompletedIds,
            nextMealSlot = nextUnloggedMeal?.let { it.mealLabel to it.recipeId }
        )
    }
    val showTodayOutcomeCard = hasPlan && todayMeals.isNotEmpty()
    val showPrimaryNextStepCard = !showTodayOutcomeCard
    LaunchedEffect(showTodayOutcomeCard) {
        if (showTodayOutcomeCard && dashboardFocusKey == DashboardFocus.Overview.name) {
            dashboardFocusKey = DashboardFocus.Today.name
        } else if (!showTodayOutcomeCard && dashboardFocusKey == DashboardFocus.Today.name) {
            dashboardFocusKey = DashboardFocus.Overview.name
        }
    }
    val dashboardFocus = remember(dashboardFocusKey) {
        DashboardFocus.valueOf(dashboardFocusKey)
    }
    val dashboardFocusOptions = remember(showTodayOutcomeCard, showAdvancedInsights, showSecondaryCards) {
        buildList {
            add(
                ScreenFocusOption(
                    key = DashboardFocus.Overview.name,
                    label = "Home",
                    summary = "See the main step and week summary."
                )
            )
            if (showTodayOutcomeCard) {
                add(
                    ScreenFocusOption(
                        key = DashboardFocus.Today.name,
                        label = "Today",
                        summary = "See only today’s meals and progress."
                    )
                )
            }
            add(
                ScreenFocusOption(
                    key = DashboardFocus.Insights.name,
                    label = "Plan",
                    summary = if (showAdvancedInsights) {
                        "See your week summary and why it was picked."
                    } else {
                        "See your week summary first."
                    }
                )
            )
            add(
                ScreenFocusOption(
                    key = DashboardFocus.Support.name,
                    label = "Help",
                    summary = if (showSecondaryCards) {
                        "Open tips and help."
                    } else {
                        "Keep help separate from today’s tasks."
                    }
                )
            )
        }
    }
    val primaryNextTitle = if (hasPlan) "What to do now" else "Start here"
    val primaryNextMessage = when {
        !hasPlan -> "Make your first week to unlock shopping and progress."
        nextUnloggedMeal != null -> "Open your next meal and check it off when you're done."
        else -> "Open Progress to see how this week is going."
    }
    val primaryNextCta = when {
        !hasPlan -> "Create My Plan"
        nextUnloggedMeal != null -> "Open Next Meal"
        else -> "Open Progress"
    }
    val weekStart = activeWeekStart?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    } ?: today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
    val sundayDate = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val sundayKey = sundayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val sundayPlanMeals = (mealPlanState as? MealPlanUiState.Success)
        ?.response
        ?.days
        ?.firstOrNull { it.dayLabel.lowercase(Locale.ENGLISH) == "sun" }
        ?.meals
        .orEmpty()
    val sundayComplete = isSundayCloseoutReady(
        sundayKey = sundayKey,
        sundayPlanMeals = sundayPlanMeals.map { it.mealLabel to it.recipeId },
        logs = logs
    )
    val weekRangeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    val statusSummaryLabel = when {
        planExpired -> "Your saved week has ended. Make a new one when you're ready."
        !hasPlan -> "Make your first week to unlock Grocery and Progress."
        hasGrocery && hasTracked -> "Your week, shopping list, and progress are all active."
        hasGrocery -> "Your week and grocery list are ready."
        hasTracked -> "Your week and progress log are active."
        else -> "Your week is ready. Review today’s meals next."
    }
    val syncSummaryLabel = if (isOnline) {
        "Online: saved changes can sync when needed."
    } else {
        "Offline-safe: using your saved local data."
    }
    val planRangeLabel = if (hasPlan) {
        "Saved week: ${weekStart.format(weekRangeFormatter)} to ${weekStart.plusDays(6).format(weekRangeFormatter)}"
    } else {
        "No saved week yet"
    }
    val nextFocusLabel = when {
        !hasPlan -> "Next: create your first week"
        nextUnloggedMeal != null -> "Next: ${nextUnloggedMeal.mealLabel} check-in"
        todayMeals.isNotEmpty() -> "Next: open this week’s progress"
        else -> "Next: review your saved week"
    }
    val dashboardHeaderColors = remember(dashboardFocus, colorScheme) {
        when (dashboardFocus) {
            DashboardFocus.Overview -> listOf(
                colorScheme.primary,
                colorScheme.secondary,
                colorScheme.tertiary
            )
            DashboardFocus.Today -> listOf(
                colorScheme.secondary,
                colorScheme.tertiary,
                colorScheme.primary.copy(alpha = 0.92f)
            )
            DashboardFocus.Insights -> listOf(
                colorScheme.tertiary,
                colorScheme.primary.copy(alpha = 0.88f),
                colorScheme.secondary.copy(alpha = 0.92f)
            )
            DashboardFocus.Support -> listOf(
                colorScheme.primary.copy(alpha = 0.85f),
                colorScheme.tertiary.copy(alpha = 0.95f),
                colorScheme.secondary.copy(alpha = 0.82f)
            )
        }
    }
    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) {
            val stats = sampleFrameTiming(
                windowMs = UiMotionTokens.MotionFrameProbeWindowMs,
                jankThresholdMs = UiMotionTokens.FrameJankThresholdMs
            )
            Log.i(
                "DashboardMotion",
                "frames=${stats.frames} avg=${"%.1f".format(Locale.ENGLISH, stats.avgFrameMs)}ms " +
                    "p95=${"%.1f".format(Locale.ENGLISH, stats.p95FrameMs)}ms " +
                    "max=${"%.1f".format(Locale.ENGLISH, stats.worstFrameMs)}ms " +
                    "jank=${stats.jankFrames}/${stats.frames}"
            )
        }
    }
    fun postDashboardFeedback(
        tone: FeedbackBannerTone,
        message: String
    ) {
        dashboardFeedbackBanner = FeedbackBannerData(
            tone = tone,
            message = message
        )
        Log.i("DashboardUX", message)
        if (tone != FeedbackBannerTone.Loading) {
            coroutineScope.launch {
                delay(2200)
                if (dashboardFeedbackBanner?.message == message) {
                    dashboardFeedbackBanner = null
                }
            }
        }
    }
    fun noteDashboardAction(title: String, detail: String) {
        dashboardActionNoteTitle = title
        dashboardActionNoteDetail = detail
    }
    fun runDashboardHeroAction(
        loadingMessage: String,
        successMessage: String,
        action: () -> Unit
    ) {
        if (dashboardHeroActionState.value == FeedbackActionState.Loading) return
        coroutineScope.launch {
            dashboardHeroActionState.value = FeedbackActionState.Loading
            postDashboardFeedback(
                tone = FeedbackBannerTone.Loading,
                message = loadingMessage
            )
            delay(140)
            dashboardHeroActionState.value = FeedbackActionState.Success
            postDashboardFeedback(
                tone = FeedbackBannerTone.Success,
                message = successMessage
            )
            delay(110)
            action()
            delay(500)
            dashboardHeroActionState.value = FeedbackActionState.Idle
        }
    }
    LaunchedEffect(dashboardActionNoteTitle, dashboardActionNoteDetail) {
        val currentTitle = dashboardActionNoteTitle ?: return@LaunchedEffect
        val currentDetail = dashboardActionNoteDetail
        delay(2800)
        if (dashboardActionNoteTitle == currentTitle && dashboardActionNoteDetail == currentDetail) {
            dashboardActionNoteTitle = null
            dashboardActionNoteDetail = null
        }
    }
    LaunchedEffect(dashboardFocusKey) {
        dashboardHeroActionState.value = FeedbackActionState.Idle
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = UiSpacingTokens.SectionGap),
        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
    ) {
        item {
            Box {
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                val enabled = !adminMode
                                userViewModel.toggleAdminMode()
                                noteDashboardAction(
                                    title = if (enabled) "Admin tools on" else "Admin tools off",
                                    detail = if (enabled) {
                                        "Extra system tools are now available in Settings."
                                    } else {
                                        "The app is back to the regular user view."
                                    }
                                )
                                postDashboardFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = if (enabled) {
                                        "Admin mode enabled. Open Settings to access system tools."
                                    } else {
                                        "Admin mode disabled. System tools are now hidden."
                                    }
                                )
                            }
                        )
                    }
                ) {
                    GradientHeader(
                        title = "Today, ${profile.displayName.ifBlank { "there" }}",
                        subtitle = "Your week, shopping list, and progress in one place.",
                        containerHeight = 118,
                        colors = dashboardHeaderColors,
                        trailing = {
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                )
                            }
                        },
                    )
                }
            }
        }

        dashboardFeedbackBanner?.let { banner ->
            item {
                AppFeedbackBanner(
                    data = banner,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            ScreenFocusStrip(
                title = "Show",
                options = dashboardFocusOptions,
                selectedKey = dashboardFocusKey,
                onSelect = { dashboardFocusKey = it },
                labelMaxWidth = dashboardChipLabelWidth
            )
        }
        if (dashboardFocus == DashboardFocus.Overview) {
            item {
                StatusCenterCard(
                    queuedActionsLabel = statusSummaryLabel,
                    syncLabel = syncSummaryLabel,
                    planRangeLabel = planRangeLabel,
                    nextReminderLabel = nextFocusLabel,
                    modifier = Modifier.testTag("dashboard_status_center_card")
                )
            }
        }
        if (showJourneyCard) {
            item {
                GuidedJourneyCard(
                    step = guidedStep,
                    onContinue = { step -> onNavigateToRoute(step.route) }
                )
            }
        }

        item {
            FocusModePanel(
                targetKey = dashboardFocusKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_focus_panel")
            ) { focusKey ->
                when (DashboardFocus.valueOf(focusKey)) {
                    DashboardFocus.Overview -> DashboardOverviewHero(
                        hasPlan = hasPlan,
                        isOnline = isOnline,
                        primaryNextTitle = primaryNextTitle,
                        primaryNextMessage = primaryNextMessage,
                        primaryNextCta = primaryNextCta,
                        primaryActionState = dashboardHeroActionState.value,
                        onPrimaryAction = {
                            when {
                                !hasPlan && !isOnline -> {
                                    postDashboardFeedback(
                                        tone = FeedbackBannerTone.Error,
                                        message = "${ActionFeedbackCopy.InternetRequired} Connect to generate your first plan."
                                    )
                                }
                                !hasPlan -> {
                                    postDashboardFeedback(
                                        tone = FeedbackBannerTone.Loading,
                                        message = "Opening plan generator…"
                                    )
                                    onViewPlan()
                                }
                                nextUnloggedMeal != null -> {
                                    runDashboardHeroAction(
                                        loadingMessage = "Opening next meal…",
                                        successMessage = "Next meal opened."
                                    ) {
                                        onRecipeClick(
                                            nextUnloggedMeal.recipeId,
                                            nextUnloggedMeal.mealLabel
                                        )
                                    }
                                }
                                else -> {
                                    runDashboardHeroAction(
                                        loadingMessage = "Opening progress…",
                                        successMessage = "Progress opened."
                                    ) {
                                        onNavigateToRoute(Routes.Progress)
                                    }
                                }
                            }
                        },
                        todayCompletedCount = todayCompletedCount,
                        todayMealsCount = todayMeals.size,
                        groceryCount = groceryItems.size,
                        hasTracked = hasTracked,
                        goalLabel = primaryGoalLabel(profile.goal),
                        householdLabel = householdSizeLabel(profile.householdSize),
                        estimatedWeeklyCost = planExplanation?.estimatedWeeklyCost,
                        dailyCalorieTarget = dailyCalorieTarget,
                        helperCopyMaxLines = helperCopyMaxLines
                    )

                    DashboardFocus.Today -> Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardTodayOutcomeCard(
                            progress = animatedTodayProgress,
                            completedMealsLabel = "$todayCompletedCount/${todayMeals.size} meals logged today",
                            nextLabel = nextUnloggedMeal?.let { "Next: ${it.mealLabel} • ${it.title}" }
                                ?: "All today’s meals logged. Review your week progress.",
                            deltaLabel = todayDeltaLabel,
                            timeline = todayTimeline,
                            ctaLabel = if (nextUnloggedMeal != null) "Open Next Unlogged Meal" else "Review Week Progress",
                            primaryActionState = dashboardHeroActionState.value,
                            primaryLoadingLabel = if (nextUnloggedMeal != null) "Opening next meal…" else "Opening progress…",
                            primarySuccessLabel = if (nextUnloggedMeal != null) "Next meal opened" else "Progress opened",
                            followUpLabel = when {
                                hasPlan && sundayComplete -> "Sunday is done. Review this week before you make the next one."
                                hasPlan && todayMeals.isNotEmpty() && todayProgress >= 1f ->
                                    "All meals are logged. Add one short reflection to help next week fit better."
                                else -> null
                            },
                            followUpActionLabel = when {
                                hasPlan && sundayComplete -> "Review week"
                                hasPlan && todayMeals.isNotEmpty() && todayProgress >= 1f -> "Add reflection"
                                else -> null
                            },
                            onFollowUpAction = when {
                                hasPlan && sundayComplete -> ({ onNavigateToRoute(Routes.Progress) })
                                hasPlan && todayMeals.isNotEmpty() && todayProgress >= 1f ->
                                    ({ onNavigateToRoute(Routes.Progress) })
                                else -> null
                            },
                            onPrimaryAction = {
                                val nextMeal = nextUnloggedMeal
                                if (nextMeal != null) {
                                    runDashboardHeroAction(
                                        loadingMessage = "Opening next meal…",
                                        successMessage = "Next meal opened."
                                    ) {
                                        onRecipeClick(nextMeal.recipeId, nextMeal.mealLabel)
                                    }
                                } else {
                                    runDashboardHeroAction(
                                        loadingMessage = "Opening progress…",
                                        successMessage = "Progress opened."
                                    ) {
                                        onNavigateToRoute(Routes.Progress)
                                    }
                                }
                            }
                        )
                    }

                    DashboardFocus.Insights -> DashboardInsightsHero(
                        hasPlan = hasPlan || shouldShowProgressSnapshot(guidedStep.stepIndex),
                        goalLabel = primaryGoalLabel(profile.goal),
                        goalShortLabel = primaryGoalShortLabel(profile.goal),
                        householdLabel = householdSizeLabel(profile.householdSize),
                        estimatedWeeklyCost = planExplanation?.estimatedWeeklyCost,
                        averageCalories = planExplanation?.avgCalories ?: 0,
                        dailyCalorieTarget = dailyCalorieTarget,
                        adminMode = adminMode,
                        showSecondaryCards = showSecondaryCards,
                        onLearnMore = {
                            noteDashboardAction(
                                title = "Week snapshot opened",
                                detail = "This explains the simple week numbers shown on this screen."
                            )
                            learnMoreCopy.value = if (hasPlan) {
                                LockedFlowCopy.DashboardQuickSnapshotHintAfterPlan
                            } else {
                                snapshotLockedCopy.dialogBody
                            }
                        },
                        onExplainTarget = {
                            noteDashboardAction(
                                title = "Goal math opened",
                                detail = "You can now see how your daily target was worked out."
                            )
                            showTargetInfo.value = true
                        },
                        onExplainBmi = {
                            noteDashboardAction(
                                title = "BMI help opened",
                                detail = "This explains the BMI formula used in the app."
                            )
                            showBmiInfo.value = true
                        },
                        schemaVersion = BuildConfig.SCHEMA_VERSION,
                        lockedMessage = snapshotLockedCopy.cardText
                    )

                    DashboardFocus.Support -> DashboardSupportHero(
                        subtitle = moreToolsSubtitle,
                        actionState = dashboardHeroActionState.value,
                        onOpenMoreTools = {
                            runDashboardHeroAction(
                                loadingMessage = "Opening help…",
                                successMessage = "Help opened."
                            ) {
                                onOpenMoreTools()
                            }
                        }
                    )
                }
            }
        }
        if (!dashboardActionNoteTitle.isNullOrBlank() && !dashboardActionNoteDetail.isNullOrBlank()) {
            item {
                AnimatedVisibility(visible = true) {
                    DashboardActionNoteCard(
                        title = dashboardActionNoteTitle.orEmpty(),
                        detail = dashboardActionNoteDetail.orEmpty()
                    )
                }
            }
        }

        if (false && showPrimaryNextStepCard && dashboardFocus == DashboardFocus.Overview) {
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 2f
                        }
                        .testTag("dashboard_primary_next_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = primaryNextTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.primary
                        )
                        Text(
                            text = primaryNextMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!hasPlan && !isOnline) {
                            Text(
                                text = "${ActionFeedbackCopy.InternetRequired} Connect to generate your first plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.error,
                                maxLines = helperCopyMaxLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                when {
                                    !hasPlan && !isOnline -> {
                                        postDashboardFeedback(
                                            tone = FeedbackBannerTone.Error,
                                            message = "${ActionFeedbackCopy.InternetRequired} Connect to generate your first plan."
                                        )
                                    }
                                    !hasPlan -> {
                                        postDashboardFeedback(
                                            tone = FeedbackBannerTone.Loading,
                                            message = "Opening plan generator…"
                                        )
                                        onViewPlan()
                                    }
                                    nextUnloggedMeal != null -> {
                                        postDashboardFeedback(
                                            tone = FeedbackBannerTone.Success,
                                            message = "Opening next unlogged meal."
                                        )
                                        onRecipeClick(
                                            nextUnloggedMeal.recipeId,
                                            nextUnloggedMeal.mealLabel
                                        )
                                    }
                                    else -> {
                                        postDashboardFeedback(
                                            tone = FeedbackBannerTone.Success,
                                            message = "Opening week progress."
                                        )
                                        onNavigateToRoute(Routes.Progress)
                                    }
                                }
                            },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(primaryNextCta)
                        }
                    }
                }
            }
        }

        if (false && showTodayOutcomeCard && dashboardFocus == DashboardFocus.Today) {
            item {
                DashboardTodayOutcomeCard(
                    progress = animatedTodayProgress,
                    completedMealsLabel = "$todayCompletedCount/${todayMeals.size} meals logged today",
                    nextLabel = nextUnloggedMeal?.let { "Next: ${it.mealLabel} • ${it.title}" }
                        ?: "All today’s meals logged. Review your week progress.",
                    deltaLabel = todayDeltaLabel,
                    timeline = todayTimeline,
                    ctaLabel = if (nextUnloggedMeal != null) "Open Next Unlogged Meal" else "Review Week Progress",
                    primaryActionState = dashboardHeroActionState.value,
                    primaryLoadingLabel = if (nextUnloggedMeal != null) "Opening next meal…" else "Opening progress…",
                    primarySuccessLabel = if (nextUnloggedMeal != null) "Next meal opened" else "Progress opened",
                    onPrimaryAction = {
                        val nextMeal = nextUnloggedMeal
                        if (nextMeal != null) {
                            postDashboardFeedback(
                                tone = FeedbackBannerTone.Success,
                                message = "Opening next unlogged meal."
                            )
                            onRecipeClick(nextMeal.recipeId, nextMeal.mealLabel)
                        } else {
                            postDashboardFeedback(
                                tone = FeedbackBannerTone.Success,
                                message = "Opening week progress."
                            )
                            onNavigateToRoute(Routes.Progress)
                        }
                    }
                )
            }
        }

        if (false && dashboardFocus == DashboardFocus.Today) {
            item {
                DashboardWeekCloseoutCard(
                    visible = hasPlan && sundayComplete,
                    helperCopyMaxLines = helperCopyMaxLines,
                    onReviewWeek = { onNavigateToRoute(Routes.Progress) }
                )
            }
        }

        if (
            false &&
            dashboardFocus == DashboardFocus.Today &&
            hasPlan &&
            todayMeals.isNotEmpty() &&
            todayProgress >= 1f &&
            !sundayComplete
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 4f
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Daily closeout ready",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "All meals logged today. Add a short reflection to lock in today’s insights.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = helperCopyMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = { onNavigateToRoute(Routes.Progress) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("Add Daily Reflection")
                        }
                    }
                }
            }
        }

        if (false && (dashboardFocus == DashboardFocus.Overview || dashboardFocus == DashboardFocus.Insights)) {
            item {
            if (hasPlan || shouldShowProgressSnapshot(guidedStep.stepIndex)) {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 5f
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Goal Progress Snapshot",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            DashboardStatusPill(
                                text = if (hasPlan) "Plan snapshot" else "Preview",
                                emphasized = hasPlan
                            )
                        }
                        Text(
                            text = "This week supports: ${primaryGoalLabel(profile.goal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                            maxLines = helperCopyMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        val avgKcal = planExplanation?.avgCalories ?: 0
                        val householdLabel = householdSizeLabel(profile.householdSize)
                        val estCost = planExplanation?.estimatedWeeklyCost
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                DashboardStatusPill(
                                    text = "${if (hasPlan) 21 else 0} planned meals",
                                    emphasized = hasPlan
                                )
                            }
                            item {
                                DashboardStatusPill(
                                    text = if (avgKcal > 0) "Avg $avgKcal kcal/day" else "Avg kcal pending",
                                    emphasized = avgKcal > 0
                                )
                            }
                            item {
                                DashboardStatusPill(
                                    text = householdLabel,
                                    emphasized = false
                                )
                            }
                            estCost?.let { cost ->
                                item {
                                    DashboardStatusPill(
                                        text = "Est ₱$cost",
                                        emphasized = true
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Shopping totals are scaled for $householdLabel, and snapshot values update as your week changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = helperCopyMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (estCost != null) {
                            Text(
                                text = "Estimated weekly cost: ₱$estCost",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = helperCopyMaxLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 5f
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardStatusPill(
                                text = "Goal snapshot",
                                emphasized = false
                            )
                            DashboardStatusPill(
                                text = "Unlock later",
                                emphasized = false
                            )
                        }
                        Text(
                            text = snapshotLockedCopy.cardText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = helperCopyMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        AssistChip(
                            onClick = {
                                learnMoreCopy.value = snapshotLockedCopy.dialogBody
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = {
                                Text(
                                    text = LockedFlowCopy.LearnMoreLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = dashboardChipLabelWidth)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
        }

        if (false && dashboardFocus == DashboardFocus.Insights) {
            item {
            val compressSecondaryStats = shouldCompressSecondaryStats(guidedStep.stepIndex)
            if (compressSecondaryStats) {
                val quickSnapshotHint = if (shouldUseFirstPlanUnlockCopy(guidedStep.stepIndex)) {
                    LockedFlowCopy.DashboardQuickSnapshotHintBeforePlan
                } else {
                    LockedFlowCopy.DashboardQuickSnapshotHintAfterPlan
                }
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 6f
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DashboardStatusPill(
                                text = primaryGoalShortLabel(profile.goal),
                                emphasized = true
                            )
                            DashboardStatusPill(
                                text = "${dailyCalorieTarget} kcal/day",
                                emphasized = false
                            )
                        }
                        Text(
                            text = "Quick Snapshot",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "${primaryGoalLabel(profile.goal)} • ${dailyCalorieTarget} kcal/day target",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                            maxLines = helperCopyMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        AssistChip(
                            onClick = {
                                learnMoreCopy.value = quickSnapshotHint
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = {
                                Text(
                                    text = LockedFlowCopy.LearnMoreLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = dashboardChipLabelWidth)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val goalLabel = primaryGoalShortLabel(profile.goal)
                    StatCard(title = "Goal", value = goalLabel, subtitle = "Focus", modifier = Modifier.weight(1f))
                    StatCard(title = "Target", value = dailyCalorieTarget.toString(), subtitle = "kcal/day", modifier = Modifier.weight(1f))
                    val weightLabel = if (profile.weightUnit == UnitConverter.WEIGHT_LB) {
                        "${UnitConverter.kgToLb(profile.weightKg)}"
                    } else {
                        "${profile.weightKg}"
                    }
                    val weightUnit = if (profile.weightUnit == UnitConverter.WEIGHT_LB) "lb" else "kg"
                    StatCard(title = "Current", value = weightLabel, subtitle = weightUnit, modifier = Modifier.weight(1f))
                }
                val bmi = HealthMetrics.bmi(profile.weightKg, profile.heightCm)
                if (bmi > 0) {
                    Text(
                        text = "BMI: ${String.format(Locale.ENGLISH, "%.1f", bmi)} (${HealthMetrics.bmiCategory(bmi)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                        maxLines = helperCopyMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (adminMode && !compressSecondaryStats) {
                AssistChip(
                    onClick = { },
                    label = { Text("Schema v${BuildConfig.SCHEMA_VERSION}") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = colorScheme.surfaceVariant,
                        labelColor = colorScheme.onSurfaceVariant,
                        leadingIconContentColor = colorScheme.secondary
                    )
                )
            }
            if (showSecondaryCards && !compressSecondaryStats) {
                Column(
                    modifier = Modifier.padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { showTargetInfo.value = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = {
                            Text(
                                text = "Target kcal formula",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = dashboardChipLabelWidth)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    AssistChip(
                        onClick = { showBmiInfo.value = true },
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = {
                            Text(
                                text = "BMI formula",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = dashboardChipLabelWidth)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            } else if (!compressSecondaryStats) {
                Text(
                    text = "Formula details unlock in Step 6.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    maxLines = helperCopyMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        }

        if (dashboardFocus == DashboardFocus.Insights) {
            item {
            if (showAdvancedInsights) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 7f
                        }
                        .testTag("dashboard_advanced_metrics_card")
                ) {
                    ExpandableSection(
                        title = "Advanced Metrics",
                        subtitle = "Macro averages and solver signals",
                        defaultExpanded = false
                    ) {
                        CompactWidgetGrid(
                            modifier = Modifier.padding(top = 8.dp),
                            widgets = listOf(
                                CompactWidgetSpec(
                                    title = "Protein",
                                    value = "${metrics.avgProtein}g",
                                    hint = "Average per day. Target 85g.",
                                    accentColor = colorScheme.primary
                                ),
                                CompactWidgetSpec(
                                    title = "Carbs",
                                    value = "${metrics.avgCarbs}g",
                                    hint = "Average per day. Target 220g.",
                                    accentColor = colorScheme.tertiary
                                ),
                                CompactWidgetSpec(
                                    title = "Fiber",
                                    value = "${metrics.avgFiber}g",
                                    hint = "Average per day. Target 25g.",
                                    accentColor = colorScheme.secondary
                                )
                            )
                        )
                        OutlinedButton(
                            onClick = { showMarkersInfo.value = true },
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("How these are computed")
                        }
                        if (planExpired) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Your plan is expired. Generate a new week to stay current.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = helperCopyMaxLines,
                                overflow = TextOverflow.Ellipsis
                            )
                            FilledTonalButton(
                                onClick = onViewPlan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text("Generate new week")
                            }
                        }
                    }
                }
            } else {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 7f
                        }
                        .testTag("dashboard_advanced_metrics_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = advancedLockedCopy.cardText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = helperCopyMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        AssistChip(
                            onClick = {
                                learnMoreCopy.value = advancedLockedCopy.dialogBody
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = {
                                Text(
                                    text = LockedFlowCopy.LearnMoreLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = dashboardChipLabelWidth)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
        }

        if (false && dashboardFocus == DashboardFocus.Support) {
            item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 8f
                    }
                    .testTag("dashboard_more_tools_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = colorScheme.primary)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Support & Guides",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DashboardStatusPill(
                                    text = "Guides",
                                    emphasized = true
                                )
                                DashboardStatusPill(
                                    text = "Feedback",
                                    emphasized = false
                                )
                            }
                            Text(
                                text = moreToolsSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = helperCopyMaxLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onOpenMoreTools,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Open support & guides")
                    }
                }
            }
        }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showMarkersInfo.value) {
        AlertDialog(
            onDismissRequest = { showMarkersInfo.value = false },
            confirmButton = {
                DashboardDialogGotItButton(onClick = { showMarkersInfo.value = false })
            },
            title = { Text("Live Metabolic Markers", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "These values are computed from your weekly plan averages (21 meals). " +
                    "If you log meal completion in Progress, the app uses completed meals for these averages."
                )
            }
        )
    }

    if (showTargetInfo.value) {
        AlertDialog(
            onDismissRequest = { showTargetInfo.value = false },
            confirmButton = {
                DashboardDialogGotItButton(onClick = { showTargetInfo.value = false })
            },
            title = { Text("Target kcal/day", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "Computed using Mifflin-St Jeor (female):\n" +
                    "BMR = 10×weight + 6.25×height − 5×age − 161 = ${calorieBreakdown.bmr}.\n" +
                    "Activity multiplier (${profile.activityLevel}) = ${calorieBreakdown.activityMultiplier}.\n" +
                    "TDEE ≈ ${calorieBreakdown.tdee} kcal/day.\n" +
                    "Goal adjustment → ${calorieBreakdown.goalAdjustment} kcal/day.\n" +
                    "Target = ${calorieBreakdown.target} kcal/day."
                )
            }
        )
    }

    if (showBmiInfo.value) {
        val bmi = HealthMetrics.bmi(profile.weightKg, profile.heightCm)
        val category = HealthMetrics.bmiCategory(bmi)
        AlertDialog(
            onDismissRequest = { showBmiInfo.value = false },
            confirmButton = {
                DashboardDialogGotItButton(onClick = { showBmiInfo.value = false })
            },
            title = { Text("BMI", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "BMI = weight(kg) / height(m)^2.\n" +
                    "Example: 65 kg and 1.60 m → 65 / 1.6^2 = 25.4.\n" +
                    "Your BMI ≈ ${"%.1f".format(bmi)} ($category)."
                )
            }
        )
    }

    learnMoreCopy.value?.let { message ->
        AlertDialog(
            onDismissRequest = { learnMoreCopy.value = null },
            confirmButton = {
                DashboardDialogGotItButton(onClick = { learnMoreCopy.value = null })
            },
            title = { Text(LockedFlowCopy.LearnMoreTitle, modifier = Modifier.semantics { heading() }) },
            text = { Text(message) }
        )
    }
}

@Composable
private fun DashboardStatusPill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (emphasized) {
            colorScheme.primary.copy(alpha = 0.10f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        contentColor = if (emphasized) {
            colorScheme.primary
        } else {
            colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DashboardOverviewHero(
    hasPlan: Boolean,
    isOnline: Boolean,
    primaryNextTitle: String,
    primaryNextMessage: String,
    primaryNextCta: String,
    primaryActionState: FeedbackActionState,
    onPrimaryAction: () -> Unit,
    todayCompletedCount: Int,
    todayMealsCount: Int,
    groceryCount: Int,
    hasTracked: Boolean,
    goalLabel: String,
    householdLabel: String,
    estimatedWeeklyCost: Int?,
    dailyCalorieTarget: Int,
    helperCopyMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (!hasPlan) {
        FriendlyEmptyStateCard(
            title = "Start your first week",
            message = if (isOnline) {
                "Create one weekly plan to unlock shopping, progress, and easier daily check-offs."
            } else {
                "${ActionFeedbackCopy.InternetRequired} Connect once to create your first week."
            },
            actionLabel = if (isOnline) primaryNextCta else null,
            onAction = if (isOnline) onPrimaryAction else null,
            accentColor = colorScheme.primary,
            modifier = modifier
        )
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 2f
            },
        shape = RoundedCornerShape(30.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = primaryNextTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.primary
                )
                Text(
                    text = primaryNextMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = helperCopyMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "This week supports: $goalLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurface
                )
            }
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Plan",
                        value = "Ready",
                        hint = "Meals are ready for $householdLabel.",
                        accentColor = colorScheme.primary,
                        badge = "Live"
                    ),
                    CompactWidgetSpec(
                        title = "Today",
                        value = if (todayMealsCount > 0) "$todayCompletedCount/$todayMealsCount" else "No meals",
                        hint = if (todayMealsCount > 0) "Meals checked off today." else "Nothing is assigned today yet.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Shopping",
                        value = if (groceryCount > 0) "$groceryCount items" else "Start list",
                        hint = if (groceryCount > 0) "Your grocery list is ready." else "Your shopping list comes next.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Week cost",
                        value = estimatedWeeklyCost?.let { "₱$it" } ?: "Target $dailyCalorieTarget",
                        hint = estimatedWeeklyCost?.let { "Estimated for the whole week." } ?: "Daily calorie target.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f),
                        badge = if (hasTracked) "Tracked" else null
                    )
                )
            )
            LoadingActionButton(
                state = primaryActionState,
                idleLabel = primaryNextCta,
                loadingLabel = if (primaryNextCta.contains("Meal", ignoreCase = true)) {
                    "Opening next meal…"
                } else {
                    "Opening progress…"
                },
                successLabel = if (primaryNextCta.contains("Meal", ignoreCase = true)) {
                    "Next meal opened"
                } else {
                    "Progress opened"
                },
                errorLabel = "Try again",
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            )
        }
    }
}

@Composable
private fun DashboardInsightsHero(
    hasPlan: Boolean,
    goalLabel: String,
    goalShortLabel: String,
    householdLabel: String,
    estimatedWeeklyCost: Int?,
    averageCalories: Int,
    dailyCalorieTarget: Int,
    adminMode: Boolean,
    showSecondaryCards: Boolean,
    onLearnMore: () -> Unit,
    onExplainTarget: () -> Unit,
    onExplainBmi: () -> Unit,
    schemaVersion: String,
    lockedMessage: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (!hasPlan) {
        FriendlyEmptyStateCard(
            title = "Insights open after your first plan",
            message = lockedMessage,
            actionLabel = "Why it shows later",
            onAction = onLearnMore,
            accentColor = colorScheme.tertiary,
            modifier = modifier
        )
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 5f
            },
        shape = RoundedCornerShape(30.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Week snapshot",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.tertiary
                )
                Text(
                    text = "Simple week numbers for $goalLabel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Focus",
                        value = goalShortLabel,
                        hint = "Your main goal right now.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Avg per day",
                        value = if (averageCalories > 0) "$averageCalories kcal" else "$dailyCalorieTarget kcal",
                        hint = if (averageCalories > 0) "Based on this saved week." else "Daily target while planning.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Household",
                        value = householdLabel,
                        hint = "Shopping scales to this size.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Week cost",
                        value = estimatedWeeklyCost?.let { "₱$it" } ?: "Pending",
                        hint = "Estimated cost for the whole week.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f),
                        badge = if (adminMode) "v$schemaVersion" else null
                    )
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onLearnMore,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Read this")
                }
                if (showSecondaryCards) {
                    OutlinedButton(
                        onClick = onExplainTarget,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Goal math")
                    }
                } else {
                    OutlinedButton(
                        onClick = onExplainBmi,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("BMI help")
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardSupportHero(
    subtitle: String,
    actionState: FeedbackActionState,
    onOpenMoreTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 8f
            }
            .testTag("dashboard_more_tools_card"),
        shape = RoundedCornerShape(30.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardStatusPill(
                    text = "Guides",
                    emphasized = true
                )
                DashboardStatusPill(
                    text = "Feedback",
                    emphasized = false
                )
                DashboardStatusPill(
                    text = "Help",
                    emphasized = false
                )
            }
            Text(
                text = "Need help?",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            LoadingActionButton(
                state = actionState,
                idleLabel = "Open help",
                loadingLabel = "Opening help…",
                successLabel = "Help opened",
                errorLabel = "Try again",
                onClick = onOpenMoreTools,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
private fun DashboardDialogGotItButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.semantics { traversalIndex = 1f },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Text(
            "Got it",
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DashboardActionNoteCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.primaryContainer.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = colorScheme.primary.copy(alpha = 0.12f),
                contentColor = colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.primary
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DashboardTodayOutcomeCard(
    progress: Float,
    completedMealsLabel: String,
    nextLabel: String,
    deltaLabel: String?,
    timeline: List<TodayTimelineStep>,
    ctaLabel: String,
    primaryActionState: FeedbackActionState,
    primaryLoadingLabel: String,
    primarySuccessLabel: String,
    followUpLabel: String? = null,
    followUpActionLabel: String? = null,
    onFollowUpAction: (() -> Unit)? = null,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(UiMotionTokens.ExpandableContentMs))
            .semantics {
                isTraversalGroup = true
                traversalIndex = 3f
            }
            .testTag("dashboard_today_outcome_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = colorScheme.primary.copy(alpha = 0.06f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(46.dp),
                    strokeWidth = 5.dp,
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant
                )
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardStatusPill(
                        text = when {
                            progress >= 1f -> "Day complete"
                            progress > 0f -> "In progress"
                            else -> "Start today"
                        },
                        emphasized = progress > 0f
                    )
                    DashboardStatusPill(
                        text = completedMealsLabel,
                        emphasized = progress >= 1f
                    )
                }
                Text(
                    text = nextLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(timeline) { step ->
                        DashboardStatusPill(
                            text = "${step.label} • ${step.state.label}",
                            emphasized = step.state == TodayTimelineState.Done || step.state == TodayTimelineState.Now
                        )
                    }
                }
                deltaLabel?.let { label ->
                    Surface(
                        color = colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!followUpLabel.isNullOrBlank()) {
                    Surface(
                        color = colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.14f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = followUpLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!followUpActionLabel.isNullOrBlank() && onFollowUpAction != null) {
                                TextButton(onClick = onFollowUpAction) {
                                    Text(followUpActionLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
        LoadingActionButton(
            state = primaryActionState,
            idleLabel = ctaLabel,
            loadingLabel = primaryLoadingLabel,
            successLabel = primarySuccessLabel,
            errorLabel = "Try again",
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .height(44.dp)
        )
    }
}

@Composable
fun DashboardWeekCloseoutCard(
    visible: Boolean,
    helperCopyMaxLines: Int,
    onReviewWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.18f)),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(UiMotionTokens.ExpandableContentMs))
            .semantics {
                isTraversalGroup = true
                traversalIndex = 3.5f
            }
            .testTag("dashboard_week_closeout_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Week wrap-up ready",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "Sunday is done. Review this week, then make the next one.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = onReviewWeek,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text("Review week")
            }
        }
    }
}

enum class TodayTimelineState(val label: String) {
    Done("Done"),
    Now("Now"),
    Pending("Later"),
    Locked("Locked")
}

data class TodayTimelineStep(
    val label: String,
    val state: TodayTimelineState
)

fun buildTodayTimelineSteps(
    todayMeals: List<Pair<String, String>>,
    completedIds: List<String>,
    nextMealSlot: Pair<String, String>?
): List<TodayTimelineStep> {
    val canonical = listOf("Breakfast", "Lunch", "Dinner")
    val todayDescriptors = todayMeals.map { (mealLabel, recipeId) ->
        TodayMealDescriptor(
            mealLabel = mealLabel,
            title = mealLabel,
            recipeId = recipeId
        )
    }
    val remaining = remainingTodayMealSlots(todayDescriptors, completedIds)
    return canonical.map { label ->
        val meal = todayMeals.firstOrNull { it.first.equals(label, ignoreCase = true) }
        val state = when {
            meal == null -> TodayTimelineState.Locked
            remaining.none { slot ->
                slot.mealLabel.equals(meal.first, ignoreCase = true) &&
                    slot.recipeId == meal.second
            } -> TodayTimelineState.Done
            nextMealSlot != null &&
                meal.first.equals(nextMealSlot.first, ignoreCase = true) &&
                meal.second == nextMealSlot.second -> TodayTimelineState.Now
            else -> TodayTimelineState.Pending
        }
        TodayTimelineStep(label = label, state = state)
    }
}

fun isSundayCloseoutReady(
    sundayKey: String?,
    sundayPlanMeals: List<Pair<String, String>>,
    logs: Map<String, com.pcosina.app.data.model.DailyLog>
): Boolean {
    if (sundayKey.isNullOrBlank()) return false
    if (sundayPlanMeals.isEmpty()) return false
    val descriptors = sundayPlanMeals.map { (mealLabel, recipeId) ->
        TodayMealDescriptor(
            mealLabel = mealLabel,
            title = mealLabel,
            recipeId = recipeId
        )
    }
    val snapshot = buildTodayLogSnapshot(
        todayMeals = descriptors,
        completedMealIds = logs[sundayKey]?.completedMealIds.orEmpty()
    )
    return snapshot.completedCount >= snapshot.plannedCount
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    composed {
        this.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    }
