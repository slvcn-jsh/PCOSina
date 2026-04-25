package com.pcosina.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.pcosina.app.BuildConfig
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.util.buildMealReasons
import com.pcosina.app.ui.util.householdPlanningSummary
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.RecipeDetailsUiState
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.MealCheckInDialog
import com.pcosina.app.ui.components.MealCheckInDraft
import com.pcosina.app.ui.components.StatusCenterCard
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.util.formatFiberProgressShort
import com.pcosina.app.ui.util.formatKcalProgressShort
import com.pcosina.app.ui.util.formatProteinProgressShort
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.util.TodayMealDescriptor
import com.pcosina.app.ui.util.buildTodayLogSnapshot
import com.pcosina.app.ui.util.goalMealReasonCopy
import com.pcosina.app.ui.util.rememberIsOnline
import com.pcosina.app.ui.util.remainingTodayMealSlots
import com.pcosina.app.ui.util.mealImpactNextSuggestion
import com.pcosina.app.ui.util.normalizeMealLabel
import com.pcosina.app.ui.util.sampleFrameTiming
import com.pcosina.app.domain.householdSizeLabel
import com.pcosina.app.domain.scaleNutritionPerMeal
import com.pcosina.app.domain.scaleQuantityText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RecipeDetailsScreen(
    recipeId: String,
    plannedMealLabelHint: String? = null,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    goal: String = "",
    adminMode: Boolean = false,
    householdSize: Int = 1,
    onBack: () -> Unit,
    onAddToGrocery: () -> Unit,
    onNavigateToRoute: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Observe the centralized state from the ViewModel
    val state by mealPlanViewModel.recipeState.collectAsState()
    val planState by mealPlanViewModel.uiState.collectAsState()
    val planMetrics by mealPlanViewModel.planMetrics.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val isOnline = observedOnline

    // Trigger the fetch when the screen opens or ID changes
    LaunchedEffect(recipeId) {
        mealPlanViewModel.loadRecipeDetails(recipeId)
    }

    when (state) {
        is RecipeDetailsUiState.Loading -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)
            ) {
                item {
                    GradientHeader(
                        title = "Opening recipe details",
                        subtitle = "Ingredients, nutrition, and meal logging context are loading.",
                        containerHeight = 180,
                        trailing = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                    )
                }
                item {
                    StatusCenterCard(
                        queuedActionsLabel = "Recipe details are still loading.",
                        syncLabel = if (isOnline) {
                            "Online: recipe and plan data can refresh when needed."
                        } else {
                            "Offline-safe: waiting on saved recipe data."
                        },
                        planRangeLabel = "Today's meal context will appear here",
                        nextReminderLabel = "Next focus: wait for ingredients and steps",
                        modifier = Modifier.testTag("recipe_loading_status_center_card")
                    )
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.CardContentGap)
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                        Text(
                            text = "Loading recipe",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Getting ingredients, nutrition, and cooking steps ready.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(74.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    repeat(4) { NutrientSkeletonTile() }
                                }
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.CardContentGap)
                            ) {
                                LoadingSkeletonBar(
                                    modifier = Modifier
                                        .fillMaxWidth(0.72f)
                                        .height(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        is RecipeDetailsUiState.Error -> {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)
            ) {
                item {
                    GradientHeader(
                        title = "Recipe unavailable",
                        subtitle = "The saved recipe context could not be opened right now.",
                        containerHeight = 180,
                        trailing = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                    )
                }
                item {
                    StatusCenterCard(
                        queuedActionsLabel = "Recipe details could not be loaded.",
                        syncLabel = if (isOnline) {
                            "Online: retry can request the recipe again."
                        } else {
                            "Offline-safe: reconnect later if this recipe is not cached."
                        },
                        planRangeLabel = "Back navigation is still available",
                        nextReminderLabel = "Next focus: retry or return to your plan",
                        modifier = Modifier.testTag("recipe_error_status_center_card")
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionHeaderGap)
                        ) {
                            Text(
                                text = "Couldn’t load recipe",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = (state as RecipeDetailsUiState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (isOnline) {
                                    "Retry now or return to the previous screen."
                                } else {
                                    "Reconnect later or return to the previous screen."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { mealPlanViewModel.loadRecipeDetails(recipeId) },
                                enabled = isOnline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                            ) {
                                Text(if (isOnline) "Retry loading recipe" else "Reconnect to retry")
                            }
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text("Go back")
                            }
                        }
                    }
                }
            }
        }
        is RecipeDetailsUiState.Success -> {
            val r = (state as RecipeDetailsUiState.Success).recipe
            val safeHouseholdSize = householdSize.coerceIn(1, 6)
            val householdLabel = remember(safeHouseholdSize) { householdSizeLabel(safeHouseholdSize) }
            val plan = (planState as? MealPlanUiState.Success)?.response
            val today = LocalDate.now()
            val todayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val dayLabelFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
            val todayDayLabel = today.format(dayLabelFmt).lowercase(Locale.ENGLISH)
            val todayPlan = plan?.days?.firstOrNull {
                it.dayLabel.lowercase(Locale.ENGLISH) == todayDayLabel
            }
            val todayPlannedMeals = todayPlan?.meals.orEmpty()
            val todayCompletedIds = logs[todayKey]?.completedMealIds.orEmpty()
            val todayMealDescriptors = remember(todayPlannedMeals) {
                todayPlannedMeals.map { meal ->
                    TodayMealDescriptor(
                        mealLabel = meal.mealLabel,
                        title = meal.title,
                        recipeId = meal.recipeId
                    )
                }
            }
            val todaySnapshot = remember(todayMealDescriptors, todayCompletedIds) {
                buildTodayLogSnapshot(todayMealDescriptors, todayCompletedIds)
            }
            val loggedTodayCount = todaySnapshot.completedCount
            val remainingTodaySlots = remember(todayMealDescriptors, todayCompletedIds) {
                remainingTodayMealSlots(
                    todayMeals = todayMealDescriptors,
                    completedMealIds = todayCompletedIds
                )
            }
            val remainingRecipeSlots = remember(remainingTodaySlots, recipeId) {
                remainingTodaySlots.filter { it.recipeId == recipeId }
            }
            val normalizedHint = normalizeMealLabel(plannedMealLabelHint)
            val mealLabelHint = plannedMealLabelHint?.takeIf { normalizedHint.isNotBlank() }
            val normalizedMealType = normalizeMealLabel(r.mealType)
            val hintedRemainingSlot = remember(remainingTodaySlots, recipeId, mealLabelHint) {
                if (normalizedHint.isNotBlank()) {
                    remainingTodaySlots.firstOrNull { slot ->
                        slot.recipeId == recipeId &&
                            normalizeMealLabel(slot.mealLabel) == normalizedHint
                    }
                } else {
                    null
                }
            }
            val slotToLog = hintedRemainingSlot ?: remainingRecipeSlots.firstOrNull { slot ->
                normalizeMealLabel(slot.mealLabel) == normalizedMealType
            } ?: remainingRecipeSlots.firstOrNull()
            val plannedTodayMeal = slotToLog?.let { pending ->
                todayPlannedMeals.firstOrNull { meal ->
                    meal.recipeId == pending.recipeId &&
                        meal.mealLabel.equals(pending.mealLabel, ignoreCase = true)
                }
            } ?: mealLabelHint?.let { hint ->
                todayPlannedMeals.firstOrNull { meal ->
                    meal.recipeId == recipeId &&
                        normalizeMealLabel(meal.mealLabel) == normalizeMealLabel(hint)
                }
            } ?: todayPlannedMeals.firstOrNull { it.recipeId == recipeId }
            val isRecipeInTodayPlan = if (mealLabelHint != null) {
                todayPlannedMeals.any { meal ->
                    meal.recipeId == recipeId &&
                        normalizeMealLabel(meal.mealLabel) == normalizedHint
                }
            } else {
                todayPlannedMeals.any { it.recipeId == recipeId }
            }
            val alreadyLoggedToday = if (!isRecipeInTodayPlan) {
                false
            } else if (mealLabelHint != null) {
                remainingTodaySlots.none { slot ->
                    slot.recipeId == recipeId &&
                        normalizeMealLabel(slot.mealLabel) == normalizedHint
                }
            } else {
                remainingRecipeSlots.isEmpty()
            }
            val recipeCounts = remember(plan) {
                plan?.days?.flatMap { it.meals }?.groupingBy { it.recipeId }?.eachCount() ?: emptyMap()
            }
            val reasons = remember(recipeId, plan?.explanation, recipeCounts) {
                buildMealReasons(
                    recipeId = recipeId,
                    recipeCounts = recipeCounts,
                    explanation = plan?.explanation,
                    budgetPhp = 0
                )
            }
            val displayedReasons = remember(goal, adminMode, reasons) {
                if (adminMode) reasons else goalMealReasonCopy(goal, reasons)
            }
            val recipeMinutesLabel = "${r.minutes ?: 20} min"
            val mealTypeLabel = remember(r.mealType) {
                r.mealType
                    ?.lowercase(Locale.ENGLISH)
                    ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
                    ?: "Recipe"
            }
            val heroSubtitle = when {
                isRecipeInTodayPlan -> "${plannedTodayMeal?.mealLabel ?: mealTypeLabel} • $recipeMinutesLabel • $householdLabel"
                else -> "$mealTypeLabel • $recipeMinutesLabel • $householdLabel"
            }
            val recipeStatusSummary = when {
                alreadyLoggedToday -> "${r.title} is already logged for today."
                isRecipeInTodayPlan -> "Ready to log as ${plannedTodayMeal?.mealLabel ?: mealTypeLabel}."
                plan == null -> "Recipe loaded. Create a weekly plan to unlock check-ins."
                todayPlannedMeals.isEmpty() -> "Recipe loaded. No meals are scheduled for today yet."
                else -> "Recipe loaded. Logging stays tied to the meals in today's plan."
            }
            val recipeSyncSummary = if (isOnline) {
                "Online: grocery adds and progress updates can sync when needed."
            } else {
                "Offline-safe: using your saved recipe, plan, and log state."
            }
            val recipePlanRangeLabel = when {
                todayPlannedMeals.isNotEmpty() -> "Today progress: $loggedTodayCount/${todayPlannedMeals.size} meals logged"
                plan != null -> "No meals scheduled for today yet"
                else -> "No active weekly plan yet"
            }
            val recipeNextFocusLabel = when {
                alreadyLoggedToday && todaySnapshot.nextMeal != null ->
                    "Next focus: ${todaySnapshot.nextMeal?.mealLabel} check-in"
                alreadyLoggedToday -> "Next focus: open Progress to review today"
                isRecipeInTodayPlan -> "Next focus: mark this meal after you eat"
                else -> "Next focus: open today's plan before logging"
            }
            var showLoadedContent by remember(r.id) { mutableStateOf(false) }
            var impactSummary by remember(r.id, todayKey) { mutableStateOf<RecipeImpactSummary?>(null) }
            var impactDetailsExpanded by remember(r.id, todayKey) { mutableStateOf(false) }
            var mealCheckInPrompt by remember(r.id, todayKey) { mutableStateOf<RecipeMealCheckInPrompt?>(null) }
            LaunchedEffect(r.id) {
                showLoadedContent = false
                delay(UiMotionTokens.RecipeInitialRevealDelayMs.toLong())
                showLoadedContent = true
                if (BuildConfig.DEBUG) {
                    val stats = sampleFrameTiming(
                        windowMs = UiMotionTokens.MotionFrameProbeWindowMs,
                        jankThresholdMs = UiMotionTokens.FrameJankThresholdMs
                    )
                    Log.i(
                        "RecipeMotion",
                        "frames=${stats.frames} avg=${"%.1f".format(Locale.ENGLISH, stats.avgFrameMs)}ms " +
                            "p95=${"%.1f".format(Locale.ENGLISH, stats.p95FrameMs)}ms " +
                            "max=${"%.1f".format(Locale.ENGLISH, stats.worstFrameMs)}ms " +
                            "jank=${stats.jankFrames}/${stats.frames}"
                    )
                }
            }

            val coroutineScope = rememberCoroutineScope()
            var recipeFeedbackBanner by remember { mutableStateOf<FeedbackBannerData?>(null) }
            fun postRecipeFeedback(
                tone: FeedbackBannerTone,
                message: String,
                autoClearMs: Long = 2200L
            ) {
                recipeFeedbackBanner = FeedbackBannerData(
                    tone = tone,
                    message = message
                )
                Log.i("RecipeDetailsUX", message)
                if (tone != FeedbackBannerTone.Loading && autoClearMs > 0L) {
                    coroutineScope.launch {
                        delay(autoClearMs)
                        if (recipeFeedbackBanner?.message == message) {
                            recipeFeedbackBanner = null
                        }
                    }
                }
            }
            val addToGroceryAction: () -> Unit = {
                val items = r.ingredients.map {
                    DummyData.GroceryItem(
                        it.name,
                        it.quantity,
                        0,
                        "Needed"
                    )
                }
                groceryViewModel.addItems(items)
                postRecipeFeedback(
                    tone = FeedbackBannerTone.Success,
                    message = "Added ${items.size} ingredients from ${r.title} to Grocery."
                )
                onAddToGrocery()
            }
            val markAsEatenAction: () -> Unit = {
                if (!isRecipeInTodayPlan) {
                    impactSummary = RecipeImpactSummary(
                        headline = "Logging is locked for this recipe today.",
                        detailLine = "",
                        nextSuggestion = "Only meals in today’s plan can be logged. Open your next planned meal.",
                        nextRoute = Routes.Dashboard,
                        nextCtaLabel = "Open Today Hub"
                    )
                    impactDetailsExpanded = false
                    postRecipeFeedback(
                        tone = FeedbackBannerTone.Error,
                        message = "No change: this recipe is not in today’s plan."
                    )
                    Unit
                } else {
                    val saved = progressViewModel.markMealAsEaten(
                        date = today,
                        recipeId = recipeId,
                        mealLabel = plannedTodayMeal?.mealLabel
                    )
                    if (!saved) {
                    impactSummary = RecipeImpactSummary(
                        headline = progressViewModel.loggingLockReason(today),
                        detailLine = "",
                        nextSuggestion = "",
                        nextRoute = null,
                        nextCtaLabel = null
                    )
                    impactDetailsExpanded = false
                    postRecipeFeedback(
                        tone = FeedbackBannerTone.Error,
                        message = "No change: logging is locked for this date."
                    )
                    Unit
                } else {
                    mealPlanViewModel.trackMlEvent(
                        eventName = "meal_accepted",
                        requestId = plan?.requestId,
                        payload = mapOf(
                            "meal_label" to (plannedTodayMeal?.mealLabel ?: (r.mealType ?: "Unknown")),
                            "recipe_id" to recipeId,
                            "source" to "recipe_details"
                        )
                    )
                    mealPlanViewModel.trackMlEvent(
                        eventName = "cook_completed",
                        requestId = plan?.requestId,
                        payload = mapOf(
                            "meal_label" to (plannedTodayMeal?.mealLabel ?: (r.mealType ?: "Unknown")),
                            "recipe_id" to recipeId,
                            "source" to "recipe_details"
                        )
                    )
                    val nextDoneCount = if (alreadyLoggedToday) loggedTodayCount else loggedTodayCount + 1
                    val mealsPlanned = todayPlannedMeals.size
                    val ratio = if (mealsPlanned > 0) {
                        (nextDoneCount.toFloat() / mealsPlanned.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val status = when {
                        mealsPlanned == 0 -> "Logged for today."
                        ratio >= 1f -> "You're on track today."
                        ratio >= 0.66f -> "Nice progress—you're close for today."
                        else -> "Great start—keep going today."
                    }
                    val caloriesToday = todayPlan?.totalCalories ?: 0
                    val estCalories = (caloriesToday * ratio).toInt()
                    val estProtein = (planMetrics.avgProtein * ratio).toInt()
                    val estFiber = (planMetrics.avgFiber * ratio).toInt()
                    val completedIdsAfterLog = if (alreadyLoggedToday || plannedTodayMeal == null) {
                        todayCompletedIds
                    } else {
                        todayCompletedIds + ProgressViewModel.buildMealKey(
                            mealLabel = plannedTodayMeal.mealLabel,
                            recipeId = plannedTodayMeal.recipeId
                        )
                    }
                    val nextMeal = buildTodayLogSnapshot(
                        todayMeals = todayMealDescriptors,
                        completedMealIds = completedIdsAfterLog
                    ).nextMeal?.let { next ->
                        todayPlannedMeals.firstOrNull { meal ->
                            meal.recipeId == next.recipeId &&
                                meal.mealLabel.equals(next.mealLabel, ignoreCase = true)
                        }
                    }
                    val nextSuggestion = mealImpactNextSuggestion(
                        completedMealLabel = r.mealType,
                        nextMeal = nextMeal?.let { "${it.mealLabel} • ${it.title}" },
                        noNextFallback = "add a short reflection for today."
                    )
                    impactSummary = RecipeImpactSummary(
                        headline = "$status Today $nextDoneCount/$mealsPlanned meals • ${formatKcalProgressShort(estCalories, caloriesToday)}.",
                        detailLine = "${formatProteinProgressShort(estProtein, planMetrics.avgProtein)} • " +
                            formatFiberProgressShort(estFiber, planMetrics.avgFiber),
                        nextSuggestion = nextSuggestion,
                        nextRoute = nextMeal?.let { Routes.recipeDetailsRoute(it.recipeId, it.mealLabel) } ?: Routes.Progress,
                        nextCtaLabel = if (nextMeal != null) "Open Next Meal" else "Open Progress"
                    )
                    impactDetailsExpanded = false
                    mealCheckInPrompt = RecipeMealCheckInPrompt(
                        recipeId = recipeId,
                        mealLabel = plannedTodayMeal?.mealLabel ?: (r.mealType ?: "Meal"),
                        mealTitle = r.title
                    )
                    val loggedLabel = plannedTodayMeal?.mealLabel ?: r.mealType ?: "meal"
                    postRecipeFeedback(
                        tone = FeedbackBannerTone.Success,
                        message = "Logged $loggedLabel. Today progress is now $nextDoneCount/$mealsPlanned meals."
                    )
                    Unit
                }
                }
            }

            Scaffold(
                containerColor = colorScheme.background,
                bottomBar = {
                    Surface(
                        shadowElevation = 6.dp,
                        tonalElevation = 2.dp,
                        color = colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recipeFeedbackBanner?.let { banner ->
                                AppFeedbackBanner(
                                    data = banner,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            impactSummary?.let { summary ->
                                RecipeImpactSummarySection(
                                    summary = summary,
                                    detailsExpanded = impactDetailsExpanded,
                                    onToggleDetails = { impactDetailsExpanded = !impactDetailsExpanded },
                                    onNavigateToRoute = onNavigateToRoute
                                )
                            }
                            Button(
                                onClick = markAsEatenAction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                                enabled = !alreadyLoggedToday && isRecipeInTodayPlan
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (alreadyLoggedToday) Icons.Filled.CheckCircle else Icons.Filled.Restaurant,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = when {
                                            alreadyLoggedToday -> "Already logged for today"
                                            !isRecipeInTodayPlan -> "Not in today’s plan"
                                            else -> "Log this meal for today"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (!isRecipeInTodayPlan) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = "You can only log meals that appear in today’s plan. Add ingredients now, then return when this meal is scheduled.",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = addToGroceryAction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text("Add ingredients to grocery list")
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                AnimatedVisibility(
                    visible = showLoadedContent,
                    enter = fadeIn(animationSpec = tween(UiMotionTokens.RecipeFadeInMs))
                ) {
                    LazyColumn(
                        modifier = modifier.fillMaxSize().background(colorScheme.background).statusBarsPadding(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = padding.calculateBottomPadding() + 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
                    ) {
                        // 1. Header with Title & Emoji
                        item {
                            StaggeredRecipeItem(index = 0, trigger = showLoadedContent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(colorScheme.primary, colorScheme.tertiary)
                                            )
                                        )
                                        .padding(16.dp),
                                ) {
                                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colorScheme.onPrimary)
                                    }
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = mealTypeLabel.uppercase(Locale.ENGLISH),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 0.8.sp),
                                            color = colorScheme.onPrimary.copy(alpha = 0.92f)
                                        )
                                        Text(
                                            text = r.title,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                            color = colorScheme.onPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = heroSubtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onPrimary.copy(alpha = 0.9f),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            StaggeredRecipeItem(index = 1, trigger = showLoadedContent) {
                                StatusCenterCard(
                                    queuedActionsLabel = recipeStatusSummary,
                                    syncLabel = recipeSyncSummary,
                                    planRangeLabel = recipePlanRangeLabel,
                                    nextReminderLabel = recipeNextFocusLabel,
                                    modifier = Modifier.testTag("recipe_status_center_card")
                                )
                            }
                        }

                        // 2. Meal Type & Time Card
                        item {
                            StaggeredRecipeItem(index = 2, trigger = showLoadedContent) {
                                Card(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (isRecipeInTodayPlan) "How this recipe fits today" else "How this recipe fits",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        )
                                        Text(
                                            text = "$mealTypeLabel • $recipeMinutesLabel • $householdLabel",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        if (isRecipeInTodayPlan) {
                                            Text(
                                                text = "Today's slot: ${plannedTodayMeal?.mealLabel ?: mealTypeLabel}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.primary
                                            )
                                        }
                                        if (safeHouseholdSize > 1) {
                                            Text(
                                                text = householdPlanningSummary(safeHouseholdSize),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (displayedReasons.isNotEmpty()) {
                                            Text(
                                                text = if (adminMode) "Why selected" else "Why it fits this week",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                            displayedReasons.forEach { line ->
                                                Text(
                                                    text = "• $line",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Nutritional Information Card
                        item {
                            StaggeredRecipeItem(index = 3, trigger = showLoadedContent) {
                                Card(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(text = "Nutrition per person", style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
                                        if (safeHouseholdSize > 1) {
                                            Text(
                                                text = "Whole recipe for $householdLabel: ${scaleNutritionPerMeal(r.calories, safeHouseholdSize) ?: 0} kcal • ${scaleNutritionPerMeal(r.proteinGrams, safeHouseholdSize) ?: 0}g protein",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            NutrientTile("Calories", "${r.calories ?: 0}", colorScheme.onSurface)
                                            NutrientTile("Protein", "${r.proteinGrams ?: 0}g", colorScheme.primary)
                                            NutrientTile("Carbs", "${r.carbsGrams ?: 0}g", colorScheme.primary)
                                            NutrientTile("Fiber", "${r.fiberGrams ?: 0}g", colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Ingredients List
                        item {
                            StaggeredRecipeItem(index = 4, trigger = showLoadedContent) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RecipeSectionHeader(
                                        title = "Ingredients",
                                        subtitle = "${r.ingredients.size} item(s) scaled for $householdLabel."
                                    )
                                    Card(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            r.ingredients.forEachIndexed { index, ing ->
                                                IngredientRow(ing.name, scaleQuantityText(ing.quantity, safeHouseholdSize))
                                                if (index < r.ingredients.lastIndex) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(vertical = 4.dp),
                                                        color = colorScheme.outlineVariant.copy(alpha = 0.60f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = colorScheme.primary.copy(alpha = 0.08f),
                                        contentColor = colorScheme.primary
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ShoppingCart,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Ready to shop? Use the fixed grocery button below.",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Cooking Steps
                        item {
                            StaggeredRecipeItem(index = 5, trigger = showLoadedContent) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RecipeSectionHeader(
                                        title = "Cooking Steps",
                                        subtitle = "${r.steps.size} step(s). Follow in order for the intended result."
                                    )
                                    r.steps.forEachIndexed { index, step ->
                                        InstructionRow(index + 1, step)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            mealCheckInPrompt?.let { prompt ->
                val existing = logs[todayKey]?.mealCheckIns.orEmpty().firstOrNull { checkIn ->
                    checkIn.mealKey == ProgressViewModel.buildMealKey(prompt.mealLabel, prompt.recipeId) ||
                        (checkIn.recipeId == prompt.recipeId &&
                            checkIn.mealLabel.equals(prompt.mealLabel, ignoreCase = true))
                }
                MealCheckInDialog(
                    goal = goal,
                    mealTitle = prompt.mealTitle,
                    mealLabel = prompt.mealLabel,
                    initial = existing,
                    onDismiss = { mealCheckInPrompt = null },
                    onSave = { draft: MealCheckInDraft ->
                        val saved = progressViewModel.saveMealCheckIn(
                            date = today,
                            recipeId = prompt.recipeId,
                            mealLabel = prompt.mealLabel,
                            energyLevel = draft.energyLevel,
                            fullnessLevel = draft.fullnessLevel,
                            cravingsLevel = draft.cravingsLevel,
                            satisfactionLevel = draft.satisfactionLevel,
                            note = draft.note
                        )
                        postRecipeFeedback(
                            tone = if (saved) FeedbackBannerTone.Success else FeedbackBannerTone.Error,
                            message = if (saved) "Meal check-in saved." else "Couldn’t save this meal check-in right now."
                        )
                        if (saved) {
                            mealCheckInPrompt = null
                        }
                    }
                )
            }
        }
        else -> {
            // Idle state - can show placeholder or empty screen
            Box(modifier = modifier.fillMaxSize())
        }
    }
}

private data class RecipeMealCheckInPrompt(
    val recipeId: String,
    val mealLabel: String,
    val mealTitle: String
)

@Composable
internal fun RecipeImpactSummarySection(
    summary: RecipeImpactSummary,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onNavigateToRoute: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val impactKey = remember(summary) {
        "${summary.headline}|${summary.detailLine}|${summary.nextSuggestion}"
    }
    var impactVisible by remember(impactKey) { mutableStateOf(false) }
    LaunchedEffect(impactKey) {
        impactVisible = true
    }
    Text(
        text = summary.headline,
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    AnimatedVisibility(
        visible = impactVisible,
        enter = fadeIn(animationSpec = tween(UiMotionTokens.RecipeImpactRevealMs)) +
            scaleIn(
                initialScale = 0.985f,
                animationSpec = tween(UiMotionTokens.RecipeImpactRevealMs)
            )
    ) {
        Column {
            OutlinedButton(
                onClick = onToggleDetails,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(if (detailsExpanded) "Hide impact details" else "View impact details")
            }
            if (detailsExpanded) {
                if (summary.detailLine.isNotBlank()) {
                    Text(
                        text = summary.detailLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (summary.nextSuggestion.isNotBlank()) {
                    Text(
                        text = summary.nextSuggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                summary.nextRoute?.let { route ->
                    val cta = summary.nextCtaLabel ?: "Continue"
                    FilledTonalButton(
                        onClick = { onNavigateToRoute(route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("recipe_impact_next_cta"),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(cta)
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredRecipeItem(
    index: Int,
    trigger: Boolean,
    content: @Composable () -> Unit
) {
    var visible by remember(index, trigger) { mutableStateOf(false) }
    LaunchedEffect(index, trigger) {
        visible = false
        if (trigger) {
            val stagger = (index * UiMotionTokens.RecipeItemStaggerStepMs.toLong())
                .coerceAtMost(UiMotionTokens.RecipeItemStaggerMaxMs.toLong())
            delay(stagger)
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = UiMotionTokens.RecipeItemFadeInMs))
    ) {
        content()
    }
}

@Composable
private fun RecipeSectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NutrientTile(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NutrientSkeletonTile() {
    val pulse by rememberInfiniteTransition(label = "nutrientPulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = UiMotionTokens.SkeletonPulseMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nutrientPulseAlpha"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(18.dp)
                .alpha(pulse)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape = MaterialTheme.shapes.small)
        )
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(8.dp)
                .alpha(pulse)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape = MaterialTheme.shapes.small)
        )
    }
}

@Composable
private fun LoadingSkeletonBar(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "barPulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = UiMotionTokens.SkeletonPulseMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barPulseAlpha"
    )
    Box(
        modifier = modifier
            .alpha(pulse)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape = MaterialTheme.shapes.small)
    )
}

@Composable
private fun IngredientRow(name: String, amount: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            Text(
                text = amount,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InstructionRow(step: Int, text: String) {
    val stepHelper = if (step == 1) {
        "Start here before moving through the rest of the recipe."
    } else {
        "Finish this part cleanly before moving on."
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = step.toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = "Step",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Do this next",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stepHelper,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

internal data class RecipeImpactSummary(
    val headline: String,
    val detailLine: String,
    val nextSuggestion: String,
    val nextRoute: String?,
    val nextCtaLabel: String?
)
