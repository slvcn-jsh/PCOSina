package com.pcosina.app.ui.screens

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
import com.pcosina.app.ui.components.MacroCircularGauge
import com.pcosina.app.ui.components.StatCard
import com.pcosina.app.ui.components.ExpandableSection
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
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
    val moreToolsSubtitle = if (showSecondaryCards) {
        "Open research center and support options."
    } else {
        "See how PCOSINA works, what stays offline, and where to get help."
    }
    val snapshotLockedCopy = remember { LockedFlowCopy.dashboardSnapshotLocked() }
    val advancedLockedCopy = remember { LockedFlowCopy.dashboardAdvancedLocked() }
    val dashboardChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 128.dp, medium = 192.dp)
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
    val primaryNextTitle = if (hasPlan) "Your Next Step" else "Start Your Plan"
    val primaryNextMessage = when {
        !hasPlan -> "Generate your first plan to unlock Grocery and Progress."
        nextUnloggedMeal != null -> "Open your next unlogged meal and keep today's streak moving."
        else -> "Review your current weekly plan and keep your progress consistent."
    }
    val primaryNextCta = when {
        !hasPlan -> "Generate My Plan"
        nextUnloggedMeal != null -> "Open Next Unlogged Meal"
        else -> "Review Week Progress"
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
                        title = "Hello, ${profile.displayName.ifBlank { "Warrior" }}! 👋",
                        subtitle = "Scientific Nutrition for PCOS",
                        containerHeight = 200,
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

        item {
            GuidedJourneyCard(
                step = guidedStep,
                onContinue = { step -> onNavigateToRoute(step.route) }
            )
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
            AssistChip(
                onClick = {
                    postDashboardFeedback(
                        tone = FeedbackBannerTone.Success,
                        message = if (isOnline) ActionFeedbackCopy.OnlineSync else ActionFeedbackCopy.OfflineSync
                    )
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { traversalIndex = 1f },
                label = { Text(if (isOnline) "Online" else "Offline", maxLines = 1) },
                leadingIcon = {
                    Icon(
                        imageVector = if (isOnline) Icons.Filled.Verified else Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isOnline) {
                        colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        colorScheme.surfaceVariant
                    },
                    labelColor = colorScheme.onSurface,
                    leadingIconContentColor = if (isOnline) colorScheme.primary else colorScheme.onSurfaceVariant
                )
            )
        }

        if (showPrimaryNextStepCard) {
            item {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 2f
                        }
                        .testTag("dashboard_primary_next_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            maxLines = helperCopyMaxLines,
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
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(primaryNextCta)
                        }
                    }
                }
            }
        }

        if (showTodayOutcomeCard) {
            item {
                DashboardTodayOutcomeCard(
                    progress = animatedTodayProgress,
                    completedMealsLabel = "$todayCompletedCount/${todayMeals.size} meals logged today",
                    nextLabel = nextUnloggedMeal?.let { "Next: ${it.mealLabel} • ${it.title}" }
                        ?: "All today’s meals logged. Review your week progress.",
                    deltaLabel = todayDeltaLabel,
                    timeline = todayTimeline,
                    ctaLabel = if (nextUnloggedMeal != null) "Open Next Unlogged Meal" else "Review Week Progress",
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

        item {
            DashboardWeekCloseoutCard(
                visible = hasPlan && sundayComplete,
                helperCopyMaxLines = helperCopyMaxLines,
                onReviewWeek = { onNavigateToRoute(Routes.Progress) }
            )
        }

        if (hasPlan && todayMeals.isNotEmpty() && todayProgress >= 1f && !sundayComplete) {
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

        item {
            if (hasPlan || shouldShowProgressSnapshot(guidedStep.stepIndex)) {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 5f
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Goal Progress Snapshot",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "This week supports: ${primaryGoalLabel(profile.goal)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurface,
                            maxLines = helperCopyMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                        val avgKcal = planExplanation?.avgCalories ?: 0
                        val estCost = planExplanation?.estimatedWeeklyCost
                        Text(
                            text = "Planned meals: ${if (hasPlan) 21 else 0} • Avg kcal/day: ${if (avgKcal > 0) avgKcal else "—"}",
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MacroCircularGauge(label = "Protein", currentValue = metrics.avgProtein, targetValue = 85, color = colorScheme.primary, modifier = Modifier.weight(1f))
                            MacroCircularGauge(label = "Carbs", currentValue = metrics.avgCarbs, targetValue = 220, color = colorScheme.tertiary, modifier = Modifier.weight(1f))
                            MacroCircularGauge(label = "Fiber", currentValue = metrics.avgFiber, targetValue = 25, color = colorScheme.secondary, modifier = Modifier.weight(1f))
                        }
                        TextButton(onClick = { showMarkersInfo.value = true }) {
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
                            TextButton(onClick = onViewPlan) { Text("Generate new week") }
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

        item {
            Card(
                onClick = onOpenMoreTools,
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                modifier = Modifier
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 8f
                    }
                    .testTag("dashboard_more_tools_card")
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "More Tools",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = moreToolsSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = helperCopyMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
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
private fun DashboardDialogGotItButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { traversalIndex = 1f }
    ) {
        Text("Got it")
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
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 3f
            }
            .testTag("dashboard_today_outcome_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 6.dp,
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant
                )
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Today Outcome",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = completedMealsLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = nextLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(timeline) { step ->
                        val (containerColor, labelColor) = when (step.state) {
                            TodayTimelineState.Done -> colorScheme.primary.copy(alpha = 0.16f) to colorScheme.primary
                            TodayTimelineState.Now -> colorScheme.secondaryContainer.copy(alpha = 0.7f) to colorScheme.onSurface
                            TodayTimelineState.Pending -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
                            TodayTimelineState.Locked -> colorScheme.surfaceVariant.copy(alpha = 0.6f) to colorScheme.onSurfaceVariant
                        }
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    text = "${step.label} • ${step.state.label}",
                                    maxLines = 1
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = containerColor,
                                disabledLabelColor = labelColor
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                deltaLabel?.let { label ->
                    Surface(
                        color = colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
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
            }
        }
        Button(
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .height(48.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
        ) {
            Text(ctaLabel)
        }
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
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 3.5f
            }
            .testTag("dashboard_week_closeout_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Week closeout ready",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "Sunday meals are complete. Review this week, then generate your next plan.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = helperCopyMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = onReviewWeek,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text("Review week & generate next plan")
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
