package com.pcosina.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.GuidedJourneyCard
import com.pcosina.app.ui.components.MacroProgressBar
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
import com.pcosina.app.ui.components.MealCheckInDialog
import com.pcosina.app.ui.components.MealCheckInDraft
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip
import com.pcosina.app.ui.components.StatusCenterCard
import com.pcosina.app.ui.components.TokenizedFilterChip
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.util.buildTodayLogSnapshot
import com.pcosina.app.ui.util.ActionFeedbackCopy
import com.pcosina.app.ui.util.GuidedJourneyInput
import com.pcosina.app.ui.util.LockedFlowCopy
import com.pcosina.app.ui.util.TodayMealDescriptor
import com.pcosina.app.ui.util.formatFiberProgressShort
import com.pcosina.app.ui.util.formatKcalProgressShort
import com.pcosina.app.ui.util.formatProteinProgressShort
import com.pcosina.app.ui.util.goalMealCheckInInsight
import com.pcosina.app.ui.util.goalPlanFocusCopy
import com.pcosina.app.ui.util.goalReflectionSupportCopy
import com.pcosina.app.ui.util.mealImpactNextSuggestion
import com.pcosina.app.ui.util.rememberIsOnline
import com.pcosina.app.ui.util.resolveGuidedJourneyStep
import com.pcosina.app.domain.householdSizeLabel
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.ui.navigation.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ProgressScreenFocus {
    Track,
    Review,
    Insights,
}

private enum class ProgressReviewPanel {
    Summary,
    Spending,
    Feedback,
}

private enum class ProgressInsightsPanel {
    Trends,
    Highlights,
    Weight,
    Advanced,
}

private enum class ProgressAdvancedPanel {
    Goal,
    Meals,
    Macros,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    progressViewModel: ProgressViewModel,
    groceryViewModel: GroceryViewModel,
    userId: String,
    onBackToDashboard: () -> Unit,
    onNavigateToRoute: (String) -> Unit = {},
    onlineStateOverride: Boolean? = null,
    feedbackSectionExpandedByDefault: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val weekChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 112.dp, medium = 168.dp)
    val dayChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 102.dp, medium = 146.dp)
    val feedbackChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 122.dp, medium = 176.dp)
    val symptomChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 112.dp, medium = 160.dp)
    val collapseWeekHistoryOnCompact = screenWidthDp <= 360
    val context = LocalContext.current
    val planState by mealPlanViewModel.uiState.collectAsState()
    val planMetrics by mealPlanViewModel.planMetrics.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()
    val lastReviewedWeek by mealPlanViewModel.lastReviewedWeek.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val weeklyJournal by progressViewModel.weeklyJournal.collectAsState()
    val weeklySpend by progressViewModel.weeklySpend.collectAsState()
    val feedbackQueue by progressViewModel.feedbackQueue.collectAsState()
    val planFeedbackTags by progressViewModel.planFeedbackTags.collectAsState()
    val savedProgressMode by progressViewModel.savedProgressMode.collectAsState()
    val savedAdvancedWeekAnalyticsExpanded by progressViewModel.savedAdvancedWeekAnalyticsExpanded.collectAsState()
    val profile by userViewModel.userProfile.collectAsState()
    val adminMode by userViewModel.adminMode.collectAsState()
    val groceryItems by groceryViewModel.groceryItems.collectAsState()
    var showConfidenceInfo by remember { mutableStateOf(false) }
    var showMacroInfo by remember { mutableStateOf(false) }
    var showSpendInfo by remember { mutableStateOf(false) }
    var showLowGiInfo by remember { mutableStateOf(false) }
    var showLockedInfo by remember { mutableStateOf(false) }
    var showLoggingPolicyInfo by remember { mutableStateOf(false) }
    var weekHistoryExpanded by remember(collapseWeekHistoryOnCompact) {
        mutableStateOf(!collapseWeekHistoryOnCompact)
    }
    var mealImpactSummary by remember { mutableStateOf<MealImpactSummary?>(null) }
    var impactDetailsExpanded by remember { mutableStateOf(false) }
    var showImpactSheet by remember { mutableStateOf(false) }
    var dailyReflectionExpanded by remember { mutableStateOf(false) }
    var advancedWeekAnalyticsExpanded by remember { mutableStateOf(false) }
    var progressMode by remember { mutableStateOf(ProgressMode.Today) }
    var progressFocusKey by rememberSaveable { mutableStateOf(ProgressScreenFocus.Track.name) }
    var reviewPanelKey by rememberSaveable { mutableStateOf(ProgressReviewPanel.Summary.name) }
    var insightsPanelKey by rememberSaveable { mutableStateOf(ProgressInsightsPanel.Trends.name) }
    var advancedPanelKey by rememberSaveable { mutableStateOf(ProgressAdvancedPanel.Goal.name) }
    var mealCheckInPrompt by remember { mutableStateOf<ProgressMealCheckInPrompt?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var progressFeedbackBanner by remember { mutableStateOf<FeedbackBannerData?>(null) }
    var reflectionSaveState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var weeklyReflectionSaveState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var weightSaveState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var feedbackSendState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var spendSaveState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var progressHeroActionState by remember { mutableStateOf(FeedbackActionState.Idle) }
    val showTodayMode = progressMode == ProgressMode.Today
    val showWeekMode = progressMode == ProgressMode.Week
    val progressFocus = remember(progressFocusKey) {
        ProgressScreenFocus.valueOf(progressFocusKey)
    }
    val showTrackFocus = progressFocus == ProgressScreenFocus.Track
    val showReviewFocus = progressFocus == ProgressScreenFocus.Review
    val showInsightsFocus = progressFocus == ProgressScreenFocus.Insights
    val hasPlan = planHistory.isNotEmpty() || planState is MealPlanUiState.Success
    val hasReviewedWeek = activePlanId != null && activePlanId == lastReviewedWeek
    val hasGrocery = groceryItems.isNotEmpty()
    val hasTracked = logs.isNotEmpty()
    val showJourneyCard = !hasTracked
    val progressLockedCopy = remember { LockedFlowCopy.progressLocked() }
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

    val planTimestamp = (planState as? MealPlanUiState.Success)?.timestamp
    val householdLabel = remember(profile.householdSize) {
        householdSizeLabel(profile.householdSize)
    }
    val weekStart = remember(activeWeekStart, planTimestamp) {
        activeWeekStart?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
            ?: weekStartDate(planTimestamp)
    }
    val weekLabel = remember(weekStart) { weekLabelFor(weekStart) }
    val weekStartKey = weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val fallbackWeekStartKey = remember(planTimestamp) {
        weekStartDate(planTimestamp).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    var selectedDayIndex by remember(weekStartKey) {
        mutableStateOf(initialSelectedDayIndex(weekStart))
    }
    val selectedDate = weekStart.plusDays(selectedDayIndex.toLong())
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val dayLabelFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    val todayLabel = LocalDate.now().format(dayLabelFmt)

    LaunchedEffect(savedProgressMode) {
        val savedMode = progressModeFromSavedValue(savedProgressMode)
        progressMode = savedMode
        if (savedMode == ProgressMode.Today) {
            progressFocusKey = ProgressScreenFocus.Track.name
        } else if (progressFocusKey == ProgressScreenFocus.Track.name) {
            progressFocusKey = ProgressScreenFocus.Review.name
        }
    }
    LaunchedEffect(savedAdvancedWeekAnalyticsExpanded) {
        advancedWeekAnalyticsExpanded = savedAdvancedWeekAnalyticsExpanded
        if (savedAdvancedWeekAnalyticsExpanded) {
            insightsPanelKey = ProgressInsightsPanel.Advanced.name
        }
    }
    LaunchedEffect(progressFocusKey) {
        val nextMode = if (progressFocusKey == ProgressScreenFocus.Track.name) {
            ProgressMode.Today
        } else {
            ProgressMode.Week
        }
        if (progressMode != nextMode) {
            progressMode = nextMode
            progressViewModel.setProgressModePreference(nextMode.label)
        }
        if (progressFocusKey == ProgressScreenFocus.Review.name && reviewPanelKey.isBlank()) {
            reviewPanelKey = ProgressReviewPanel.Summary.name
        }
        if (progressFocusKey == ProgressScreenFocus.Insights.name && insightsPanelKey.isBlank()) {
            insightsPanelKey = ProgressInsightsPanel.Trends.name
        }
    }
    val progressFocusOptions = remember {
        listOf(
            ScreenFocusOption(
                key = ProgressScreenFocus.Track.name,
                label = "Track",
                summary = "Log meals and notes for today."
            ),
            ScreenFocusOption(
                key = ProgressScreenFocus.Review.name,
                label = "Week",
                summary = "Review this week’s progress."
            ),
            ScreenFocusOption(
                key = ProgressScreenFocus.Insights.name,
                label = "Insights",
                summary = "See the deeper trends."
            )
        )
    }
    val progressHeaderColors = remember(progressFocus, colorScheme) {
        when (progressFocus) {
            ProgressScreenFocus.Track -> listOf(
                colorScheme.primary,
                colorScheme.secondary,
                colorScheme.tertiary
            )
            ProgressScreenFocus.Review -> listOf(
                colorScheme.secondary,
                colorScheme.tertiary,
                colorScheme.primary.copy(alpha = 0.90f)
            )
            ProgressScreenFocus.Insights -> listOf(
                colorScheme.tertiary,
                colorScheme.primary.copy(alpha = 0.88f),
                colorScheme.secondary.copy(alpha = 0.92f)
            )
        }
    }

    LaunchedEffect(userId, weekStartKey, fallbackWeekStartKey) {
        if (userId.isNotBlank()) {
            progressViewModel.loadForUser(userId, weekStartKey, fallbackWeekStartKey)
        }
    }

    val planDays = (planState as? MealPlanUiState.Success)?.response?.days.orEmpty()
    val planExplanation = (planState as? MealPlanUiState.Success)?.response?.explanation
    val sortedHistory = remember(planHistory) { planHistory.sortedBy { it.weekStart } }
    val activeIndex = remember(activePlanId, sortedHistory) {
        sortedHistory.indexOfFirst { it.id == activePlanId }.takeIf { it >= 0 }
            ?: (sortedHistory.size - 1)
    }
    val previousPlan = sortedHistory.getOrNull(activeIndex - 1)
    val currentPlanInstance = sortedHistory.getOrNull(activeIndex)
    val planByLabel = planDays.associateBy { it.dayLabel.lowercase(Locale.ENGLISH) }
    val selectedDayLabel = selectedDate.format(dayLabelFmt).lowercase(Locale.ENGLISH)
    val selectedPlanDay = planByLabel[selectedDayLabel]
    val plannedMealsForDay = selectedPlanDay?.meals.orEmpty()
    val selectedDateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val selectedLog = logs[selectedDateKey]
    val selectedCompletedIds = logs[selectedDateKey]?.completedMealIds.orEmpty()
    val selectedMealCheckIns = selectedLog?.mealCheckIns.orEmpty().sortedByDescending { it.timestamp }
    val selectedDayDescriptors = remember(plannedMealsForDay) {
        plannedMealsForDay.map { meal ->
            TodayMealDescriptor(
                mealLabel = meal.mealLabel,
                title = meal.title,
                recipeId = meal.recipeId
            )
        }
    }
    val selectedDaySnapshot = remember(selectedDayDescriptors, selectedCompletedIds) {
        buildTodayLogSnapshot(
            todayMeals = selectedDayDescriptors,
            completedMealIds = selectedCompletedIds
        )
    }
    val selectedCompletedCount = selectedDaySnapshot.completedCount
    val selectedRemainingCount = (plannedMealsForDay.size - selectedCompletedCount).coerceAtLeast(0)
    val selectedCompletionRatio = if (plannedMealsForDay.isNotEmpty()) {
        selectedCompletedCount.toFloat() / plannedMealsForDay.size.toFloat()
    } else {
        0f
    }
    val selectedTrackingHeadline = when {
        plannedMealsForDay.isEmpty() -> "No meals planned for this day yet."
        selectedCompletedCount >= plannedMealsForDay.size -> "Everything planned for this day is logged."
        selectedCompletedCount == 0 -> "Nothing logged yet. Start after your first completed meal."
        else -> "$selectedRemainingCount meal(s) still waiting to be logged."
    }
    val plannedMealsCount = planDays.sumOf { it.meals.size }
    val completedMealsCount = logs.filterKeys { isInWeek(it, weekStart) }
        .values.sumOf { it.completedMealIds.size }
    val adherence = if (plannedMealsCount > 0) completedMealsCount.toFloat() / plannedMealsCount else 0f
    val lastLogDate = logs.keys.mapNotNull(::parseProgressDateOrNull)
        .maxOrNull()
    val daysSinceLog = lastLogDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) } ?: Long.MAX_VALUE
    val showStaleBanner = daysSinceLog >= 3

    val goalType = HealthMetrics.goalTypeFromText(profile.goal)
    val weightEntries = logs.filterKeys { isInWeek(it, weekStart) }.toSortedMap()
        .values.mapNotNull { it.weightKg }
    val weightDelta = if (weightEntries.size >= 2) {
        (weightEntries.last() - weightEntries.first())
    } else null
    val weightStart = weightEntries.firstOrNull()
    val weightEnd = weightEntries.lastOrNull()
    val allWeights = logs.toSortedMap()
        .mapNotNull { (dateKey, log) ->
            val parsedDate = parseProgressDateOrNull(dateKey) ?: return@mapNotNull null
            log.weightKg?.let { parsedDate to it }
        }
    val startingWeight = allWeights.firstOrNull()?.second
    val latestWeight = allWeights.lastOrNull()?.second
    val totalDelta = if (startingWeight != null && latestWeight != null) latestWeight - startingWeight else null
    val today = LocalDate.now()
    val monthWeights = allWeights.filter { it.first.year == today.year && it.first.month == today.month }
    val monthDelta = if (monthWeights.size >= 2) monthWeights.last().second - monthWeights.first().second else null
    val targetWeightKg = if (goalType == com.pcosina.app.domain.GoalType.WEIGHT_LOSS && startingWeight != null) {
        (startingWeight - 5f).coerceAtLeast(40f)
    } else null
    val weightProgress = if (startingWeight != null && latestWeight != null && targetWeightKg != null) {
        val total = startingWeight - targetWeightKg
        if (total <= 0f) 0f else ((startingWeight - latestWeight) / total).coerceIn(0f, 1f)
    } else null

    var weightInput by remember { mutableStateOf("") }
    var weightNote by remember { mutableStateOf("") }
    var weeklySpendInput by remember { mutableStateOf("") }
    var energyLevel by remember { mutableStateOf<Int?>(null) }
    var cravingsLevel by remember { mutableStateOf<Int?>(null) }
    var moodLevel by remember { mutableStateOf<Int?>(null) }
    var symptomTags by remember { mutableStateOf<List<String>>(emptyList()) }
    var symptomNote by remember { mutableStateOf("") }
    LaunchedEffect(logs, selectedDate, profile.weightUnit) {
        runCatching {
            val key = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val log = logs[key]
            weightInput = log?.weightKg?.let { kg ->
                if (profile.weightUnit == UnitConverter.WEIGHT_LB) {
                    String.format(Locale.ENGLISH, "%.1f", UnitConverter.kgToLb(kg))
                } else {
                    String.format(Locale.ENGLISH, "%.1f", kg)
                }
            } ?: ""
            weightNote = log?.weightNote ?: ""
            energyLevel = log?.energyLevel
            cravingsLevel = log?.cravingsLevel
            moodLevel = log?.moodLevel
            symptomTags = log?.symptomTags ?: emptyList()
            symptomNote = log?.symptomsNote ?: ""
        }.onFailure { error ->
            Log.e("ProgressScreen", "Failed to hydrate progress inputs safely.", error)
            weightInput = ""
            weightNote = ""
            energyLevel = null
            cravingsLevel = null
            moodLevel = null
            symptomTags = emptyList()
            symptomNote = ""
        }
    }
    LaunchedEffect(weeklySpend, weekStartKey) {
        runCatching {
            weeklySpendInput = weeklySpend?.toString() ?: ""
        }.onFailure { error ->
            Log.e("ProgressScreen", "Failed to hydrate weekly spend safely.", error)
            weeklySpendInput = ""
        }
    }

    var journalText by remember { mutableStateOf("") }
    LaunchedEffect(weeklyJournal) {
        runCatching {
            journalText = weeklyJournal
        }.onFailure { error ->
            Log.e("ProgressScreen", "Failed to hydrate weekly journal safely.", error)
            journalText = ""
        }
    }

    var feedbackText by remember { mutableStateOf("") }
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val showWeightEntryAtTop = goalType == com.pcosina.app.domain.GoalType.WEIGHT_LOSS
    val weightUnitLabel = if (profile.weightUnit == UnitConverter.WEIGHT_LB) "lb" else "kg"
    val displayWeight: (Float) -> String = { kg ->
        val value = if (profile.weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.kgToLb(kg) else kg
        String.format(Locale.ENGLISH, "%.1f", value)
    }
    val symptomOptions = remember {
        listOf("Bloating", "Cramps", "Acne", "Headache", "Fatigue", "Mood swings")
    }
    val planFeedbackOptions = remember {
        listOf("Too repetitive", "Too expensive", "Too hard to cook")
    }
    val selectedDateLabel = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH))
    }
    val todayDate = LocalDate.now()
    val todayIndexInWeek = remember(weekDays, todayDate) {
        weekDays.indexOfFirst { it == todayDate }
    }
    val todayDayLabel = remember(todayDate, dayLabelFmt) {
        todayDate.format(dayLabelFmt).lowercase(Locale.ENGLISH)
    }
    val todayDateKey = remember(todayDate) {
        todayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val todayMeals = remember(planByLabel, todayDayLabel) {
        planByLabel[todayDayLabel]?.meals.orEmpty()
    }
    val todaySnapshot = remember(todayMeals, logs, todayDateKey) {
        buildTodayLogSnapshot(
            todayMeals = todayMeals.map { meal ->
                TodayMealDescriptor(
                    mealLabel = meal.mealLabel,
                    title = meal.title,
                    recipeId = meal.recipeId
                )
            },
            completedMealIds = logs[todayDateKey]?.completedMealIds.orEmpty()
        )
    }
    val todayStatusLine = remember(todaySnapshot.plannedCount, todaySnapshot.completionRatio) {
        when {
            todaySnapshot.plannedCount == 0 -> "No meals are scheduled for today yet."
            todaySnapshot.completionRatio >= 1f -> "All meals logged today. Close your loop with reflection."
            todaySnapshot.completionRatio >= 0.66f -> "You are close to today’s target."
            todaySnapshot.completionRatio > 0f -> "Good momentum. Keep logging meals after you eat."
            else -> "Start with your next meal to build today’s progress."
        }
    }
    val isSelectedDateLoggable = remember(selectedDate, todayDate) {
        progressViewModel.isDateLoggable(selectedDate, todayDate)
    }
    val selectedDateLoggingLockReason = remember(selectedDate, todayDate) {
        progressViewModel.loggingLockReason(selectedDate, todayDate)
    }
    val sundayDate = remember(weekDays, dayLabelFmt) {
        weekDays.firstOrNull { it.format(dayLabelFmt).equals("Sun", ignoreCase = true) }
    }
    val sundayKey = sundayDate?.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val sundayPlanMeals = planByLabel["sun"]?.meals.orEmpty()
    val sundayDescriptors = remember(sundayPlanMeals) {
        sundayPlanMeals.map { meal ->
            TodayMealDescriptor(
                mealLabel = meal.mealLabel,
                title = meal.title,
                recipeId = meal.recipeId
            )
        }
    }
    val sundaySnapshot = remember(sundayDescriptors, logs, sundayKey) {
        if (sundayKey == null) {
            buildTodayLogSnapshot(emptyList(), emptyList())
        } else {
            buildTodayLogSnapshot(
                todayMeals = sundayDescriptors,
                completedMealIds = logs[sundayKey]?.completedMealIds.orEmpty()
            )
        }
    }
    val sundayCompletedCount = sundaySnapshot.completedCount
    val sundayComplete = sundaySnapshot.plannedCount > 0 && sundaySnapshot.completedCount >= sundaySnapshot.plannedCount
    val projectedWeeklyCost = planExplanation?.estimatedWeeklyCost
    val budgetTarget = planExplanation?.budgetWeekly?.toInt()
        ?: profile.weeklyBudgetPhp.takeIf { it > 0 }
    fun parseCurrencyInput(raw: String): Int? {
        val clean = raw.replace(",", "").trim()
        if (clean.isBlank()) return null
        val value = clean.toDoubleOrNull() ?: return null
        return value.roundToInt()
    }

    // Macro aggregation
    var macroLabel by remember { mutableStateOf("Planned average (per day)") }
    var avgProtein by remember { mutableStateOf(0) }
    var avgCarbs by remember { mutableStateOf(0) }
    var avgFats by remember { mutableStateOf(0) }
    var completedMacroAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(logs, planState) {
        runCatching {
            val completedMealKeys = logs.filterKeys { isInWeek(it, weekStart) }
                .values.flatMap { it.completedMealIds }
            val completedCountsByRecipe = completedMealKeys
                .groupingBy { ProgressViewModel.extractRecipeId(it) }
                .eachCount()
            val plannedCountsByRecipe = planDays.flatMap { it.meals }
                .map { it.recipeId }
                .groupingBy { it }
                .eachCount()
            val useCompleted = completedCountsByRecipe.isNotEmpty()
            val countsByRecipe = if (useCompleted) completedCountsByRecipe else plannedCountsByRecipe
            completedMacroAvailable = useCompleted
            macroLabel = if (completedMacroAvailable) "Completed average (per day)" else "Planned average (per day)"
            val details = countsByRecipe.keys.mapNotNull { id ->
                mealPlanViewModel.getRecipeDetails(id).getOrNull()
            }
            val dayDivisor = if (planDays.isNotEmpty()) planDays.size else 7
            if (details.isNotEmpty()) {
                avgProtein = details.sumOf { (it.proteinGrams ?: 0) * (countsByRecipe[it.id] ?: 1) } / dayDivisor
                avgCarbs = details.sumOf { (it.carbsGrams ?: 0) * (countsByRecipe[it.id] ?: 1) } / dayDivisor
                avgFats = details.sumOf { (it.fatsGrams ?: 0) * (countsByRecipe[it.id] ?: 1) } / dayDivisor
            } else {
                avgProtein = 0
                avgCarbs = 0
                avgFats = 0
            }
        }.onFailure { error ->
            Log.e("ProgressScreen", "Failed to compute progress macro summary safely.", error)
            macroLabel = "Planned average (per day)"
            avgProtein = 0
            avgCarbs = 0
            avgFats = 0
            completedMacroAvailable = false
        }
    }
    LaunchedEffect(selectedDate) {
        mealImpactSummary = null
        impactDetailsExpanded = false
        showImpactSheet = false
    }

    fun scheduleReset(stateSetter: (FeedbackActionState) -> Unit, delayMs: Long = 1400L) {
        coroutineScope.launch {
            delay(delayMs)
            stateSetter(FeedbackActionState.Idle)
        }
    }

    fun postProgressFeedback(
        tone: FeedbackBannerTone,
        message: String,
        autoClearMs: Long = 2200L
    ) {
        progressFeedbackBanner = FeedbackBannerData(
            tone = tone,
            message = message
        )
        Log.i("ProgressUX", message)
        if (tone != FeedbackBannerTone.Loading && autoClearMs > 0L) {
            coroutineScope.launch {
                delay(autoClearMs)
                if (progressFeedbackBanner?.message == message) {
                    progressFeedbackBanner = null
                }
            }
        }
    }

    fun saveWeightWithFeedback() {
        if (!isSelectedDateLoggable) {
            showLoggingPolicyInfo = true
            weightSaveState = FeedbackActionState.Error
            postProgressFeedback(
                tone = FeedbackBannerTone.Error,
                message = "Weight save blocked for this date."
            )
            scheduleReset({ weightSaveState = it })
            return
        }
        weightSaveState = FeedbackActionState.Loading
        postProgressFeedback(
            tone = FeedbackBannerTone.Loading,
            message = "Saving weight entry…"
        )
        val value = weightInput.toFloatOrNull()
        val kgValue = value?.let {
            if (profile.weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
        }
        val saved = progressViewModel.setWeight(selectedDate, kgValue, weightNote)
        if (!saved) {
            showLoggingPolicyInfo = true
            weightSaveState = FeedbackActionState.Error
            postProgressFeedback(
                tone = FeedbackBannerTone.Error,
                message = "Couldn’t save weight. Try again for a loggable day."
            )
        } else {
            weightSaveState = FeedbackActionState.Success
            postProgressFeedback(
                tone = FeedbackBannerTone.Success,
                message = "Weight saved for today."
            )
        }
        scheduleReset({ weightSaveState = it })
    }

    fun saveReflectionWithFeedback() {
        if (!isSelectedDateLoggable) {
            showLoggingPolicyInfo = true
            reflectionSaveState = FeedbackActionState.Error
            postProgressFeedback(
                tone = FeedbackBannerTone.Error,
                message = "Reflection save blocked for this date."
            )
            scheduleReset({ reflectionSaveState = it })
            return
        }
        reflectionSaveState = FeedbackActionState.Loading
        postProgressFeedback(
            tone = FeedbackBannerTone.Loading,
            message = "Saving reflection…"
        )
        val saved = progressViewModel.saveReflection(
            selectedDate,
            energyLevel,
            cravingsLevel,
            moodLevel,
            symptomTags,
            symptomNote
        )
        if (!saved) {
            showLoggingPolicyInfo = true
            reflectionSaveState = FeedbackActionState.Error
            postProgressFeedback(
                tone = FeedbackBannerTone.Error,
                message = "Couldn’t save reflection. Try again for a loggable day."
            )
        } else {
            reflectionSaveState = FeedbackActionState.Success
            postProgressFeedback(
                tone = FeedbackBannerTone.Success,
                message = "Reflection saved for today."
            )
        }
        scheduleReset({ reflectionSaveState = it })
    }

    fun saveWeeklyJournalWithFeedback() {
        weeklyReflectionSaveState = FeedbackActionState.Loading
        postProgressFeedback(
            tone = FeedbackBannerTone.Loading,
            message = "Saving weekly reflection…"
        )
        progressViewModel.saveWeeklyJournal(weekStartKey, journalText)
        weeklyReflectionSaveState = FeedbackActionState.Success
        postProgressFeedback(
            tone = FeedbackBannerTone.Success,
            message = "Weekly reflection saved."
        )
        scheduleReset({ weeklyReflectionSaveState = it })
    }

    fun submitFeedbackWithBanner() {
        if (feedbackText.isBlank()) return
        feedbackSendState = FeedbackActionState.Loading
        postProgressFeedback(
            tone = FeedbackBannerTone.Loading,
            message = if (isOnline) "Sending feedback…" else "Saving feedback locally…"
        )
        progressViewModel.queueFeedback(feedbackText)
        progressViewModel.trySendQueuedFeedback(isOnline)
        feedbackText = ""
        feedbackSendState = FeedbackActionState.Success
        postProgressFeedback(
            tone = FeedbackBannerTone.Success,
            message = if (isOnline) "Feedback sent." else ActionFeedbackCopy.OfflineSync
        )
        scheduleReset({ feedbackSendState = it })
    }

    val primaryCtaLabel = when {
        !hasPlan -> "Generate My First Plan"
        showTodayMode && todaySnapshot.plannedCount == 0 -> "Generate Today's Plan"
        showTodayMode && isSelectedDateLoggable && todaySnapshot.plannedCount > 0 -> "Log Today's Meals"
        showTodayMode && todaySnapshot.nextMeal != null -> "Open Next Meal"
        showTodayMode -> "Open Daily Reflection"
        sundayComplete -> "Generate Next Week Plan"
        else -> "Review Week Insights"
    }
    val primaryCtaLoadingLabel = when {
        !hasPlan -> "Opening plan…"
        showTodayMode && todaySnapshot.nextMeal != null -> "Opening next meal…"
        showTodayMode && todaySnapshot.plannedCount == 0 -> "Opening plan…"
        showTodayMode && isSelectedDateLoggable && todaySnapshot.plannedCount > 0 -> "Opening meal check-off…"
        showTodayMode -> "Opening reflection…"
        sundayComplete -> "Opening plan…"
        else -> "Opening week review…"
    }
    val primaryCtaSuccessLabel = when {
        !hasPlan -> "Plan opened"
        showTodayMode && todaySnapshot.nextMeal != null -> "Next meal opened"
        showTodayMode && todaySnapshot.plannedCount == 0 -> "Plan opened"
        showTodayMode && isSelectedDateLoggable && todaySnapshot.plannedCount > 0 -> "Check-off ready"
        showTodayMode -> "Reflection opened"
        sundayComplete -> "Plan opened"
        else -> "Week review ready"
    }
    fun runProgressHeroAction(action: () -> Unit) {
        if (progressHeroActionState == FeedbackActionState.Loading) return
        coroutineScope.launch {
            progressHeroActionState = FeedbackActionState.Loading
            postProgressFeedback(
                tone = FeedbackBannerTone.Loading,
                message = primaryCtaLoadingLabel
            )
            delay(140)
            progressHeroActionState = FeedbackActionState.Success
            postProgressFeedback(
                tone = FeedbackBannerTone.Success,
                message = primaryCtaSuccessLabel
            )
            delay(110)
            action()
            delay(500)
            progressHeroActionState = FeedbackActionState.Idle
        }
    }

    val onPrimaryCta: () -> Unit = {
        when {
            !hasPlan -> {
                runProgressHeroAction {
                    onNavigateToRoute(Routes.MealPlan)
                }
            }
            showTodayMode && todaySnapshot.nextMeal != null -> {
                todaySnapshot.nextMeal?.let { nextMeal ->
                    runProgressHeroAction {
                        onNavigateToRoute(Routes.recipeDetailsRoute(nextMeal.recipeId, nextMeal.mealLabel))
                    }
                }
            }
            showTodayMode && todaySnapshot.plannedCount == 0 -> {
                runProgressHeroAction {
                    onNavigateToRoute(Routes.MealPlan)
                }
            }
            showTodayMode && isSelectedDateLoggable && todaySnapshot.plannedCount > 0 -> {
                runProgressHeroAction {
                    if (todayIndexInWeek >= 0) {
                        selectedDayIndex = todayIndexInWeek
                    }
                }
            }
            showTodayMode -> {
                runProgressHeroAction {
                    if (todayIndexInWeek >= 0) selectedDayIndex = todayIndexInWeek
                    dailyReflectionExpanded = true
                }
            }
            sundayComplete -> {
                runProgressHeroAction {
                    onNavigateToRoute(Routes.MealPlan)
                }
            }
            else -> {
                runProgressHeroAction {
                    weekHistoryExpanded = true
                }
            }
        }
    }
    LaunchedEffect(progressFocusKey) {
        progressHeroActionState = FeedbackActionState.Idle
    }
    val queuedFeedbackCount = feedbackQueue.count { it.status != "Sent" }
    val weekRangeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    val progressStatusSummary = when {
        !hasPlan -> "No active week yet. Generate a plan to unlock check-ins and insights."
        showTodayMode && showStaleBanner && lastLogDate != null ->
            "Last check-in was $daysSinceLog day(s) ago. Restart with today's meals."
        showTodayMode -> todayStatusLine
        adherence >= 1f -> "Week complete. Review reflections and prepare your next cycle."
        adherence >= 0.66f -> "Strong week so far. Keep logging the last meals and notes."
        adherence > 0f -> "Week in progress. Daily check-ins still shape your insights."
        else -> "Week is planned. Start logging meals after you eat."
    }
    val progressSyncSummary = when {
        queuedFeedbackCount > 0 && isOnline -> "Queue has $queuedFeedbackCount item(s). Retry is available now."
        queuedFeedbackCount > 0 -> "Offline-safe: $queuedFeedbackCount feedback item(s) saved locally."
        isOnline -> "Online: logs and feedback can sync when needed."
        else -> "Offline-safe: using your saved logs and weekly plan."
    }
    val progressNextFocusLabel = when {
        !hasPlan -> "Next focus: generate your first weekly plan"
        showTodayMode && todaySnapshot.nextMeal != null ->
            "Next focus: ${todaySnapshot.nextMeal?.mealLabel} check-in"
        showTodayMode && !isSelectedDateLoggable ->
            "Next focus: return to today before logging meals"
        else -> "Next focus: $primaryCtaLabel"
    }

    LaunchedEffect(progressMode, hasPlan, todaySnapshot.plannedCount) {
        Log.i(
            "ProgressUX",
            "Mode=${progressMode.label} hasPlan=$hasPlan todayPlanned=${todaySnapshot.plannedCount}"
        )
    }
    LaunchedEffect(advancedWeekAnalyticsExpanded, showWeekMode) {
        if (!showWeekMode) return@LaunchedEffect
        Log.i(
            "ProgressUX",
            "Advanced week analytics expanded=$advancedWeekAnalyticsExpanded"
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .statusBarsPadding()
                .testTag("progress_content_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
        ) {
        item {
            Box {
                GradientHeader(
                    title = "Progress",
                    subtitle = "Log meals, add notes, and review your week.",
                    containerHeight = 116,
                    colors = progressHeaderColors,
                )
                IconButton(
                    onClick = onBackToDashboard,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.primary,
                    )
                }
            }
        }

        progressFeedbackBanner?.let { banner ->
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
                options = progressFocusOptions,
                selectedKey = progressFocusKey,
                onSelect = { progressFocusKey = it },
                labelMaxWidth = feedbackChipLabelWidth
            )
        }
        if (progressFocus != ProgressScreenFocus.Track) {
            item {
                StatusCenterCard(
                    queuedActionsLabel = progressStatusSummary,
                    syncLabel = progressSyncSummary,
                    planRangeLabel = "Tracking week: ${weekStart.format(weekRangeFormatter)} to ${weekStart.plusDays(6).format(weekRangeFormatter)}",
                    nextReminderLabel = progressNextFocusLabel,
                    modifier = Modifier.testTag("progress_status_center_card")
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
                targetKey = progressFocusKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_top_section_capture")
            ) { focusKey ->
                when (ProgressScreenFocus.valueOf(focusKey)) {
                    ProgressScreenFocus.Track -> ProgressTrackHero(
                        hasPlan = hasPlan,
                        lockedMessage = progressLockedCopy.cardText,
                        todayCompletedCount = todaySnapshot.completedCount,
                        todayPlannedCount = todaySnapshot.plannedCount,
                        todayStatusLine = todayStatusLine,
                        nextMealLabel = todaySnapshot.nextMeal?.let { "${it.mealLabel} • ${it.title}" },
                        queuedFeedbackCount = queuedFeedbackCount,
                        isOnline = isOnline,
                        adherence = adherence,
                        primaryCtaLabel = primaryCtaLabel,
                        primaryActionState = progressHeroActionState,
                        primaryLoadingLabel = primaryCtaLoadingLabel,
                        primarySuccessLabel = primaryCtaSuccessLabel,
                        onPrimaryAction = onPrimaryCta,
                        onOpenPlan = { onNavigateToRoute(Routes.MealPlan) }
                    )

                    ProgressScreenFocus.Review -> ProgressReviewHero(
                        hasPlan = hasPlan,
                        lockedMessage = progressLockedCopy.cardText,
                        completedMealsCount = completedMealsCount,
                        plannedMealsCount = plannedMealsCount,
                        adherence = adherence,
                        weeklySpend = weeklySpend,
                        projectedWeeklyCost = projectedWeeklyCost,
                        queuedFeedbackCount = queuedFeedbackCount,
                        showStaleBanner = showStaleBanner,
                        primaryCtaLabel = primaryCtaLabel,
                        primaryActionState = progressHeroActionState,
                        primaryLoadingLabel = primaryCtaLoadingLabel,
                        primarySuccessLabel = primaryCtaSuccessLabel,
                        onPrimaryAction = onPrimaryCta,
                        onOpenPlan = { onNavigateToRoute(Routes.MealPlan) }
                    )

                    ProgressScreenFocus.Insights -> ProgressInsightsHero(
                        hasPlan = hasPlan,
                        lockedMessage = progressLockedCopy.cardText,
                        averageCalories = if (planDays.isNotEmpty()) {
                            planDays.sumOf { it.totalCalories } / planDays.size
                        } else {
                            0
                        },
                        averageProtein = planMetrics.avgProtein,
                        projectedWeeklyCost = projectedWeeklyCost,
                        previousPlanReady = previousPlan != null,
                        macroLabel = macroLabel,
                        primaryCtaLabel = primaryCtaLabel,
                        primaryActionState = progressHeroActionState,
                        primaryLoadingLabel = primaryCtaLoadingLabel,
                        primarySuccessLabel = primaryCtaSuccessLabel,
                        onPrimaryAction = onPrimaryCta,
                        onOpenPlan = { onNavigateToRoute(Routes.MealPlan) }
                    )
                }
            }
        }

        if (false) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("progress_top_section_capture")
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 1f
                        },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Do this next",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = when (progressFocus) {
                                ProgressScreenFocus.Track ->
                                    "Open today’s meal, log it, then add a short reflection only if needed."
                                ProgressScreenFocus.Review ->
                                    "Review completion, queue, and weekly adherence in one place."
                                ProgressScreenFocus.Insights ->
                                    "Use insights only when you want deeper explanation, not during daily logging."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = onPrimaryCta,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text(primaryCtaLabel)
                        }
                    }
                }
            }
        }

        if (false && (progressFocus != ProgressScreenFocus.Track || queuedFeedbackCount > 0 || !isOnline)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 2f
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {
                        postProgressFeedback(
                            tone = FeedbackBannerTone.Success,
                            message = if (isOnline) {
                                ActionFeedbackCopy.OnlineSync
                            } else {
                                ActionFeedbackCopy.OfflineSync
                            }
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = {
                        Text(if (isOnline) "Sync ready" else "Offline-safe", maxLines = 1)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isOnline) Icons.Filled.CheckCircle else Icons.Filled.Info,
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
                if (queuedFeedbackCount > 0) {
                    AssistChip(
                        onClick = {
                            if (isOnline) {
                                progressViewModel.trySendQueuedFeedback(isOnline = true)
                            }
                            postProgressFeedback(
                                tone = FeedbackBannerTone.Success,
                                message = if (isOnline) {
                                    ActionFeedbackCopy.QueueRetrying
                                } else {
                                    ActionFeedbackCopy.QueueSaved
                                }
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("Queue $queuedFeedbackCount", maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = colorScheme.surfaceVariant,
                            labelColor = colorScheme.onSurface,
                            leadingIconContentColor = colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        if (false && hasPlan && showTodayMode) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 3f
                        }
                        .testTag("progress_today_hub_card"),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Today Hub",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Meals completed today: ${todaySnapshot.completedCount}/${todaySnapshot.plannedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = todayStatusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        todaySnapshot.nextMeal?.let { nextMeal ->
                            Text(
                                text = "Next: ${nextMeal.mealLabel} • ${nextMeal.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                val nextMeal = todaySnapshot.nextMeal
                                when {
                                    nextMeal != null -> onNavigateToRoute(
                                        Routes.recipeDetailsRoute(nextMeal.recipeId, nextMeal.mealLabel)
                                    )
                                    todaySnapshot.plannedCount == 0 -> onNavigateToRoute(Routes.MealPlan)
                                    else -> {
                                        if (todayIndexInWeek >= 0) selectedDayIndex = todayIndexInWeek
                                        dailyReflectionExpanded = true
                                        postProgressFeedback(
                                            tone = FeedbackBannerTone.Success,
                                            message = "Today is complete. Add a short reflection."
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            val label = when {
                                todaySnapshot.nextMeal != null -> "Open Next Meal"
                                todaySnapshot.plannedCount == 0 -> "Generate Today's Plan"
                                else -> "Open Daily Reflection"
                            }
                            Text(label)
                        }
                    }
                }
            }
        }

        if (false && !hasPlan) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = progressLockedCopy.cardText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        AssistChip(
                            onClick = { showLockedInfo = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(LockedFlowCopy.LearnMoreLabel) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        OutlinedButton(
                            onClick = { onNavigateToRoute(Routes.MealPlan) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Go to Plan")
                        }
                    }
                }
            }
        }

        if (!showTrackFocus && sortedHistory.isNotEmpty()) {
            item {
                val currentLabel = currentPlanInstance?.response?.weekLabel ?: weekLabel
                Box(
                    modifier = Modifier.semantics {
                        isTraversalGroup = true
                        traversalIndex = 4f
                    }
                ) {
                    ExpandableSection(
                        title = "Week History",
                        subtitle = if (weekHistoryExpanded) {
                            "Selected: $currentLabel"
                        } else {
                            "Selected: $currentLabel • Tap to switch weeks"
                        },
                        defaultExpanded = !collapseWeekHistoryOnCompact,
                        expanded = weekHistoryExpanded,
                        onExpandedChange = { weekHistoryExpanded = it }
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Switch weeks to compare trends.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (sortedHistory.size > 3) {
                                Text(
                                    text = "Swipe left or right to view more weeks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                sortedHistory.forEach { instance ->
                                    TokenizedFilterChip(
                                        selected = instance.id == activePlanId,
                                        onClick = {
                                            mealPlanViewModel.selectPlan(instance.id)
                                            if (collapseWeekHistoryOnCompact) {
                                                weekHistoryExpanded = false
                                            }
                                        },
                                        text = instance.response.weekLabel,
                                        labelMaxWidth = weekChipLabelWidth,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colorScheme.primary,
                                            selectedLabelColor = colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        previousPlan?.let { mealPlanViewModel.selectPlan(it.id) }
                                        if (collapseWeekHistoryOnCompact && previousPlan != null) {
                                            weekHistoryExpanded = false
                                        }
                                    },
                                    enabled = previousPlan != null,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Prev")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val next = sortedHistory.getOrNull(activeIndex + 1)
                                        next?.let { mealPlanViewModel.selectPlan(it.id) }
                                        if (collapseWeekHistoryOnCompact && next != null) {
                                            weekHistoryExpanded = false
                                        }
                                    },
                                    enabled = activeIndex >= 0 && activeIndex < sortedHistory.lastIndex,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Next")
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showReviewFocus && showStaleBanner) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Text(
                        text = "You haven't logged anything in a few days. A quick check‑in helps keep trends accurate.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (!showTrackFocus) {
            item {
                ProgressSectionHeader(
                    title = if (showReviewFocus) "Week review" else "Week insights",
                    subtitle = if (showReviewFocus) {
                        "Review adherence, spending, and feedback without daily logging below."
                    } else {
                        "Inspect explanation, trends, and deeper analytics without the review forms."
                    }
                )
            }
        }
        if (showReviewFocus && sundayComplete) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Sunday complete. Weekly check-off finished.",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Review week summary, then generate your next plan to keep momentum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = { onNavigateToRoute(Routes.MealPlan) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("Review week & generate next plan")
                        }
                    }
                }
            }
        }

        if (showReviewFocus) {
            item {
                ExpandableSection(
                    title = "How this week went",
                    subtitle = if (reviewPanelKey == ProgressReviewPanel.Summary.name) {
                        "See your quick week story, weight change, and plan totals."
                    } else {
                        "Open the quick story for this week."
                    },
                    defaultExpanded = true,
                    expanded = reviewPanelKey == ProgressReviewPanel.Summary.name,
                    onExpandedChange = { reviewPanelKey = if (it) ProgressReviewPanel.Summary.name else "" }
                ) {
                    val avgKcal = if (planDays.isNotEmpty()) {
                        (planDays.sumOf { it.totalCalories } / planDays.size)
                    } else 0
                    val kcalText = if (avgKcal > 0) "${avgKcal} kcal/day" else "—"
                    val proteinText = if (planMetrics.avgProtein > 0) "${planMetrics.avgProtein}g protein/day" else "—"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProgressStoryCard(
                            title = when {
                                adherence >= 1f -> "Strong week"
                                adherence >= 0.66f -> "Good momentum"
                                adherence > 0f -> "Week still in progress"
                                else -> "Ready to restart"
                            },
                            detail = when {
                                adherence >= 1f -> "You finished the whole planned week. Review it, then keep the next one just as steady."
                                adherence >= 0.66f -> "Most of the plan is already followed. One more push will make the week summary stronger."
                                adherence > 0f -> "Some meals are logged already. Keep checking off meals so this summary becomes more accurate."
                                else -> "No meals are logged this week yet. Start with your next meal to wake this review up."
                            },
                            accentColor = colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            StatCard(
                                title = "Meals followed",
                                value = "${(adherence * 100).toInt()}%",
                                subtitle = weekLabel,
                                modifier = Modifier.weight(1f),
                            )
                            StatCard(
                                title = "Weight change",
                                value = weightDelta?.let { "${displayWeight(it)} $weightUnitLabel" } ?: "—",
                                subtitle = "This Week",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "At a glance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${(adherence * 100).toInt()}% followed • $kcalText • $proteinText",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                ExpandableSection(
                    title = "Money check",
                    subtitle = if (reviewPanelKey == ProgressReviewPanel.Spending.name) {
                        "Save what you really spent and compare it with the plan."
                    } else {
                        "Open your weekly spend check."
                    },
                    defaultExpanded = false,
                    expanded = reviewPanelKey == ProgressReviewPanel.Spending.name,
                    onExpandedChange = { reviewPanelKey = if (it) ProgressReviewPanel.Spending.name else "" }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                isTraversalGroup = true
                                traversalIndex = 6f
                            }
                            .testTag("progress_week_spending_card"),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Weekly Spending",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                IconButton(onClick = { showSpendInfo = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Spending info",
                                        tint = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            val projectedText = projectedWeeklyCost?.let { "₱$it" } ?: "—"
                            val budgetText = budgetTarget?.let { "₱$it" } ?: "Not set"
                            ProgressStoryCard(
                                title = if (weeklySpend != null) "Actual spending saved" else "Add your real total",
                                detail = if (weeklySpend != null) {
                                    "You already saved a real spend total for this week. Update it anytime if your receipts changed."
                                } else {
                                    "Save your real spending here so the app can compare it with the planned estimate."
                                },
                                accentColor = colorScheme.secondary
                            )
                            Text(
                                text = "Plan estimate for $householdLabel: $projectedText",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Your weekly budget: $budgetText",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = weeklySpendInput,
                                onValueChange = { weeklySpendInput = it },
                                label = { Text("Actual spending (₱)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            LoadingActionButton(
                                state = spendSaveState,
                                idleLabel = "Save this week's spending",
                                loadingLabel = "Saving your spending…",
                                successLabel = "Spending saved",
                                errorLabel = "Check the amount",
                                onClick = {
                                    val spendValue = parseCurrencyInput(weeklySpendInput)
                                    if (spendValue == null && weeklySpendInput.isNotBlank()) {
                                        spendSaveState = FeedbackActionState.Error
                                        postProgressFeedback(
                                            tone = FeedbackBannerTone.Error,
                                            message = "Enter a valid amount before saving actual spending."
                                        )
                                        scheduleReset({ spendSaveState = it })
                                        return@LoadingActionButton
                                    }
                                    spendSaveState = FeedbackActionState.Loading
                                    progressViewModel.saveWeeklySpend(weekStartKey, spendValue)
                                    spendSaveState = FeedbackActionState.Success
                                    postProgressFeedback(
                                        tone = FeedbackBannerTone.Success,
                                        message = if (spendValue == null) {
                                            "Weekly spending cleared for this week."
                                        } else {
                                            "Weekly spending saved for this week."
                                        }
                                    )
                                    scheduleReset({ spendSaveState = it })
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            )
                            val spendValue = weeklySpend
                            if (spendValue != null && projectedWeeklyCost != null) {
                                val variance = spendValue - projectedWeeklyCost
                                val varianceText = if (variance >= 0) "+₱$variance vs projected" else "-₱${-variance} vs projected"
                                Text(
                                    text = "Difference from the plan: $varianceText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Optional. This stays on your device for your own tracking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (showInsightsFocus) {
            item {
                ExpandableSection(
                    title = "Compared with last week",
                    subtitle = if (insightsPanelKey == ProgressInsightsPanel.Trends.name) {
                        "See the simple before-and-after story for this week."
                    } else {
                        "Open the week comparison."
                    },
                    defaultExpanded = true,
                    expanded = insightsPanelKey == ProgressInsightsPanel.Trends.name,
                    onExpandedChange = { insightsPanelKey = if (it) ProgressInsightsPanel.Trends.name else "" }
                ) {
                    val currentStats = currentPlanInstance?.let { computeWeekStats(it.response) }
                    val prevStats = previousPlan?.let { computeWeekStats(it.response) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                isTraversalGroup = true
                                traversalIndex = 5f
                            }
                            .testTag("progress_week_insights_card"),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "What changed this week",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (currentStats == null) {
                                Text(
                                    text = "Generate a plan first to unlock the week comparison.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            } else if (prevStats == null) {
                                ProgressStoryCard(
                                    title = "Your baseline week",
                                    detail = "This is your first saved week in the comparison view. The next week will unlock the real before-and-after story.",
                                    accentColor = colorScheme.tertiary
                                )
                                Text(
                                    text = "This week becomes the baseline for future comparisons.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            } else {
                                ProgressStoryCard(
                                    title = "Simple story",
                                    detail = "Use these changes as clues, not grades. Small shifts week to week are already useful.",
                                    accentColor = colorScheme.tertiary
                                )
                                val changes = buildWeekChanges(currentStats, prevStats)
                                changes.forEach { line ->
                                    Text(
                                        text = "• $line",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                val whyLines = buildWeekWhy(currentStats, prevStats)
                                if (whyLines.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Possible reasons",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colorScheme.onSurface
                                    )
                                    whyLines.forEach { line ->
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
            }
        }

        if (showInsightsFocus && planExplanation != null) {
            val projectedHouseholdCost = planExplanation.estimatedWeeklyCost
            item {
                ExpandableSection(
                    title = if (adminMode) "How the plan was built" else "Week highlights",
                    subtitle = if (insightsPanelKey == ProgressInsightsPanel.Highlights.name) {
                        if (adminMode) "Open now: see the planner's short explanation for this week." else "Open now: see what this week is designed to support."
                    } else {
                        if (adminMode) "Open the planner explanation for this week." else "Open a simple read on what this week is trying to support."
                    },
                    defaultExpanded = false,
                    expanded = insightsPanelKey == ProgressInsightsPanel.Highlights.name,
                    onExpandedChange = { insightsPanelKey = if (it) ProgressInsightsPanel.Highlights.name else "" }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (adminMode) {
                            ProgressStoryCard(
                                title = "Planner readout",
                                detail = "This is the short explanation of how the week was built. It shows the main tradeoffs, not a medical score.",
                                accentColor = colorScheme.primary
                            )
                            Text(
                                text = "The planner balanced nutrition, variety, pantry use, and budget. Grocery values here are still estimates.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            planExplanation.confidenceScore?.let { score ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Plan fit score: $score%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { showConfidenceInfo = true },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = "Plan fit info",
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            val items = mutableListOf<String>()
                            val avgDev = planExplanation.avgCaloriesDeviation
                            planExplanation.targetCalories?.let {
                                items.add("Daily calorie goal: ${it} kcal/day")
                            }
                            planExplanation.avgCalories?.let {
                                val devText = if (avgDev != null) " (±$avgDev)" else ""
                                items.add("Planned average: ${it} kcal/day$devText")
                            }
                            val targetMacros = listOf(
                                planExplanation.targetProtein?.let { "P ${it}g" },
                                planExplanation.targetCarbs?.let { "C ${it}g" },
                                planExplanation.targetFats?.let { "F ${it}g" }
                            ).filterNotNull()
                            if (targetMacros.isNotEmpty()) {
                                items.add("Daily macro goals: ${targetMacros.joinToString(" • ")}")
                            }
                            val avgMacros = listOf(
                                planExplanation.avgProtein?.let { "P ${it}g" },
                                planExplanation.avgCarbs?.let { "C ${it}g" },
                                planExplanation.avgFats?.let { "F ${it}g" }
                            ).filterNotNull()
                            if (avgMacros.isNotEmpty()) {
                                items.add("Planned macros: ${avgMacros.joinToString(" • ")}")
                            }
                            planExplanation.fiberMinTarget?.let {
                                items.add("Minimum fiber goal: ${it}g/day")
                            }
                            planExplanation.sugarMaxTarget?.let {
                                items.add("Sugar limit: ${it}g/day")
                            }
                            if (planExplanation.symptomSelections.isNotEmpty()) {
                                items.add("Symptoms considered: ${planExplanation.symptomSelections.joinToString(", ")}")
                            }
                            planExplanation.goalStrategy.forEach { items.add(it) }
                            planExplanation.symptomStrategy.forEach { items.add(it) }
                            val exclusionSummary = planExplanation.candidateExclusionSummary
                                ?.entries
                                ?.filter { it.value > 0 }
                                ?.joinToString(" • ") { "${it.key} ${it.value}" }
                            if (!exclusionSummary.isNullOrBlank()) {
                                items.add("Meals screened out early: $exclusionSummary")
                            }
                            val constraintItems = mutableListOf<String>()
                            planExplanation.toleranceUsed?.let {
                                val pct = String.format(Locale.ENGLISH, "%.0f", it * 100)
                                constraintItems.add("Flex used around the calorie goal: $pct%")
                            }
                            planExplanation.maxPerWeek?.let {
                                constraintItems.add("Most times one meal can repeat: $it")
                            }
                            if (planExplanation.budgetWeekly != null || planExplanation.estimatedWeeklyCost != null) {
                                val budget = planExplanation.budgetWeekly?.let {
                                    "₱" + String.format(Locale.ENGLISH, "%.0f", it)
                                }
                                val est = planExplanation.estimatedWeeklyCost?.let { "₱$it" }
                                val text = when {
                                    budget != null && est != null -> "Weekly budget: $budget (planned grocery estimate $est)"
                                    budget != null -> "Weekly budget: $budget"
                                    est != null -> "Planned grocery estimate: $est"
                                    else -> null
                                }
                                if (text != null) constraintItems.add(text)
                            }
                            if (planExplanation.budgetHardCapApplied == true) {
                                constraintItems.add("Weekly budget was treated as a hard limit")
                            }
                            planExplanation.goalValue
                                ?.takeIf { it.isNotBlank() }
                                ?.let { constraintItems.add("Goal focus: $it") }
                            if (!planExplanation.householdPlanningMode.isNullOrBlank()) {
                                constraintItems.add("Household shopping was scaled for everyone in the plan")
                            }
                            planExplanation.restrictionCount?.let {
                                constraintItems.add("Saved food rules used: $it")
                            }
                            planExplanation.pantryMatches?.let {
                                items.add("Saved ingredients matched: $it")
                            }
                            planExplanation.uniqueVegTokens?.let {
                                items.add("Produce variety markers: $it")
                            }
                            items.forEach { line ->
                                Text(
                                    text = "• $line",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            if (constraintItems.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Planner guardrails",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colorScheme.onSurface
                                )
                                constraintItems.forEach { line ->
                                    Text(
                                        text = "• $line",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            ProgressStoryCard(
                                title = "What this week leans toward",
                                detail = goalPlanFocusCopy(profile.goal),
                                accentColor = colorScheme.tertiary
                            )
                            val highlights = mutableListOf<String>()
                            planExplanation.avgCalories?.let {
                                highlights.add("Around $it kcal per person, per day.")
                            }
                            planExplanation.avgProtein?.let {
                                highlights.add("Protein stays near ${it}g per person each day.")
                            }
                            projectedHouseholdCost?.let {
                                highlights.add("Projected groceries: ₱$it for $householdLabel.")
                            }
                            planExplanation.pantryMatches?.takeIf { it > 0 }?.let {
                                highlights.add("Uses $it pantry matches from the ingredients you already saved.")
                            }
                            planExplanation.uniqueVegTokens?.takeIf { it > 0 }?.let {
                                highlights.add("Includes $it produce picks to keep the week more varied.")
                            }
                            if (highlights.isEmpty()) {
                                highlights.add("This week follows the preferences saved in your profile.")
                            }
                            highlights.forEach { line ->
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

        if (showReviewFocus) {
            item {
                ExpandableSection(
                    title = "Plan feedback",
                    subtitle = if (reviewPanelKey == ProgressReviewPanel.Feedback.name) {
                        "Open now: choose what did not work so the next plan can adjust."
                    } else {
                        "Open feedback for next week's tuning."
                    },
                    defaultExpanded = false,
                    expanded = reviewPanelKey == ProgressReviewPanel.Feedback.name,
                    onExpandedChange = { reviewPanelKey = if (it) ProgressReviewPanel.Feedback.name else "" }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                isTraversalGroup = true
                                traversalIndex = 8f
                            }
                            .testTag("progress_plan_feedback_card"),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Plan Feedback", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "Pick what didn’t work. This tunes your next plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                planFeedbackOptions.forEach { tag ->
                                    val selected = planFeedbackTags.contains(tag)
                                    TokenizedFilterChip(
                                        selected = selected,
                                        onClick = { progressViewModel.togglePlanFeedbackTag(tag) },
                                        text = tag,
                                        labelMaxWidth = feedbackChipLabelWidth
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showInsightsFocus) {
            item {
                ExpandableSection(
                    title = "Deeper look",
                    subtitle = if (insightsPanelKey == ProgressInsightsPanel.Advanced.name) {
                        "Open now: pick one deeper view below."
                    } else {
                        "Open one deeper review panel."
                    },
                    defaultExpanded = false,
                    expanded = insightsPanelKey == ProgressInsightsPanel.Advanced.name,
                    onExpandedChange = {
                        insightsPanelKey = if (it) ProgressInsightsPanel.Advanced.name else ""
                        advancedWeekAnalyticsExpanded = it
                        progressViewModel.setAdvancedWeekAnalyticsExpandedPreference(it)
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Choose one view at a time so this screen stays short and easier to read.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            run {
                                TokenizedFilterChip(
                                    selected = advancedPanelKey == ProgressAdvancedPanel.Goal.name,
                                    onClick = { advancedPanelKey = ProgressAdvancedPanel.Goal.name },
                                    text = "Goal",
                                    labelMaxWidth = weekChipLabelWidth
                                )
                            }
                            run {
                                TokenizedFilterChip(
                                    selected = advancedPanelKey == ProgressAdvancedPanel.Meals.name,
                                    onClick = { advancedPanelKey = ProgressAdvancedPanel.Meals.name },
                                    text = "Meals",
                                    labelMaxWidth = weekChipLabelWidth
                                )
                            }
                            run {
                                TokenizedFilterChip(
                                    selected = advancedPanelKey == ProgressAdvancedPanel.Macros.name,
                                    onClick = { advancedPanelKey = ProgressAdvancedPanel.Macros.name },
                                    text = "Nutrition",
                                    labelMaxWidth = weekChipLabelWidth
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showWeightEntryAtTop && showTodayMode) {
            item {
                ProgressSectionHeader(
                    title = "Today",
                    subtitle = "Quickly log today so your trends stay accurate."
                )
            }
        }

        if (showWeightEntryAtTop && showTodayMode) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Weight Entry (Selected Day)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            text = "Selected date: $selectedDateLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Stored in kg internally • shown in $weightUnitLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (!isSelectedDateLoggable) {
                            Text(
                                text = selectedDateLoggingLockReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.error,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            ProgressJumpToTodayAction(
                                todayIndexInWeek = todayIndexInWeek,
                                onJump = { selectedDayIndex = todayIndexInWeek }
                            )
                            ProgressLoggingPolicyLearnMoreChip(
                                onClick = { showLoggingPolicyInfo = true }
                            )
                        }
                        val startText = weightStart?.let { "${displayWeight(it)} $weightUnitLabel" } ?: "—"
                        val endText = weightEnd?.let { "${displayWeight(it)} $weightUnitLabel" } ?: "—"
                        Text(
                            text = "Start → End: $startText → $endText",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        val monthlyText = monthDelta?.let { "${displayWeight(it)} $weightUnitLabel" } ?: "—"
                        val totalText = totalDelta?.let { "${displayWeight(it)} $weightUnitLabel" } ?: "—"
                        Text(
                            text = "Monthly delta: $monthlyText • Total delta: $totalText",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (weightProgress != null && targetWeightKg != null) {
                            val targetText = "${displayWeight(targetWeightKg)} $weightUnitLabel"
                            Text(
                                text = "Progress toward target: $targetText",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { weightProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = colorScheme.primary
                            )
                        }
                        val recentWeights = logs.toSortedMap()
                            .filterKeys { isInWeek(it, weekStart) }
                            .mapNotNull { (dateKey, log) ->
                                log.weightKg?.let { w ->
                                    val label = parseProgressDateOrNull(dateKey)?.format(dayLabelFmt)
                                        ?: return@mapNotNull null
                                    "$label ${displayWeight(w)} $weightUnitLabel"
                                }
                            }
                            .takeLast(5)
                        if (recentWeights.isNotEmpty()) {
                            val recentText = recentWeights.joinToString(" • ")
                            Text(
                                text = "Recent: $recentText",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text("Weight ($weightUnitLabel)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = weightNote,
                            onValueChange = { weightNote = it },
                            label = { Text("Weight note (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        LoadingActionButton(
                            state = weightSaveState,
                            idleLabel = "Save Weight",
                            loadingLabel = "Saving…",
                            successLabel = "Saved",
                            errorLabel = "Try Again",
                            onClick = { saveWeightWithFeedback() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = isSelectedDateLoggable
                        )
                    }
                }
            }
        }

        if (showInsightsFocus) {
            item {
                ExpandableSection(
                    title = "Weight history",
                    subtitle = if (insightsPanelKey == ProgressInsightsPanel.Weight.name) {
                        "Open now: your latest logged weights are shown below."
                    } else {
                        "Open recent weight logs."
                    },
                    defaultExpanded = false,
                    expanded = insightsPanelKey == ProgressInsightsPanel.Weight.name,
                    onExpandedChange = { insightsPanelKey = if (it) ProgressInsightsPanel.Weight.name else "" }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Weight History",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (allWeights.isEmpty()) {
                                Text(
                                    text = "No weight logs yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            } else {
                                allWeights.takeLast(7).reversed().forEach { (date, kg) ->
                                    val label = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                                    val noteKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    val note = logs[noteKey]?.weightNote
                                    Text(
                                        text = "$label • ${displayWeight(kg)} $weightUnitLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    if (!note.isNullOrBlank()) {
                                        Text(
                                            text = "Note: $note",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showInsightsFocus && advancedWeekAnalyticsExpanded && advancedPanelKey == ProgressAdvancedPanel.Goal.name) {
            item {
                Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 6.7f
                    }
                    .testTag("progress_week_goal_focus_card"),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Goal check",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface
                    )
                    when (goalType) {
                        com.pcosina.app.domain.GoalType.WEIGHT_LOSS -> {
                            ProgressStoryCard(
                                title = "What the planner is watching",
                                detail = "For weight loss, the week tries to keep calories steadier while helping you stay closer to the meals you planned.",
                                accentColor = colorScheme.primary
                            )
                            val startText = weightStart?.let { String.format("%.1fkg", it) } ?: "—"
                            val endText = weightEnd?.let { String.format("%.1fkg", it) } ?: "—"
                            Text("This week: $startText -> $endText", color = colorScheme.onSurfaceVariant)
                            Text("Daily calorie goal: ${userViewModel.dailyCalorieTarget} kcal/day", color = colorScheme.onSurfaceVariant)
                        }
                        com.pcosina.app.domain.GoalType.SYMPTOM_MANAGEMENT -> {
                            ProgressStoryCard(
                                title = "What the planner is watching",
                                detail = "For symptom support, the week aims for steadier fiber, protein, and more balanced meals.",
                                accentColor = colorScheme.tertiary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Helpful reminder: choose lower-GI carbs and balanced meals.", color = colorScheme.onSurfaceVariant)
                                IconButton(
                                    onClick = { showLowGiInfo = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Low-GI guidance",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (planMetrics.avgProtein > 0 || planMetrics.avgFiber > 0) {
                                Text(
                                    text = "This week's averages: Protein ${planMetrics.avgProtein}g/day • Fiber ${planMetrics.avgFiber}g/day",
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        com.pcosina.app.domain.GoalType.GENERAL_HEALTH -> {
                            val totalMeals = planDays.sumOf { it.meals.size }
                            val distinctRecipes = planDays.flatMap { it.meals }.map { it.recipeId }.distinct().size
                            val variety = if (totalMeals > 0) (distinctRecipes * 100 / totalMeals) else 0
                            ProgressStoryCard(
                                title = "What the planner is watching",
                                detail = "For general health, the week tries to keep meals balanced while avoiding the same food over and over.",
                                accentColor = colorScheme.secondary
                            )
                            Text("Main focus: balanced meals and variety.", color = colorScheme.onSurfaceVariant)
                            Text("Variety this week: $variety%", color = colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        }

        if (showInsightsFocus && advancedWeekAnalyticsExpanded && advancedPanelKey == ProgressAdvancedPanel.Meals.name) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Meal follow-through",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.secondary
                            )
                        }
                        ProgressStoryCard(
                            title = "Quick read",
                            detail = "This shows how closely each day matched the meals in your plan.",
                            accentColor = colorScheme.primary
                        )
                        Text(
                            text = "Use this to spot which days felt easier to follow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        val days = listOf(
                            "Mon" to 0.0f, "Tue" to 0.0f, "Wed" to 0.0f,
                            "Thu" to 0.0f, "Fri" to 0.0f, "Sat" to 0.0f, "Sun" to 0.0f
                        )
                        days.forEachIndexed { index, (_, _) ->
                            val date = weekStart.plusDays(index.toLong())
                            val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val label = date.format(dayLabelFmt)
                            val dayKey = date.format(dayLabelFmt).lowercase(Locale.ENGLISH)
                            val planned = planByLabel[dayKey]?.meals?.size ?: 0
                            val completed = logs[dateKey]?.completedMealIds?.size ?: 0
                            val progress = if (planned > 0) completed.toFloat() / planned else 0f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.width(40.dp),
                                    color = colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                                    color = if (progress >= 0.9f) colorScheme.primary else colorScheme.primary.copy(alpha = 0.5f),
                                    trackColor = colorScheme.surfaceVariant,
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showInsightsFocus && advancedWeekAnalyticsExpanded && advancedPanelKey == ProgressAdvancedPanel.Macros.name) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 7f
                        }
                        .testTag("progress_week_macro_card"),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nutrition balance",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.onSurface
                            )
                            IconButton(onClick = { showMacroInfo = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Nutrition balance info",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(text = macroLabel, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        if (!isOnline) {
                            Text(
                                text = "Some nutrition details need internet so the app can load recipe nutrition.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        val macroBalance = when {
                            avgCarbs > avgProtein * 1.3 -> "Leans more toward carbs"
                            avgProtein > avgCarbs * 1.2 -> "Leans more toward protein"
                            avgProtein == 0 && avgCarbs == 0 -> "No completed meal data yet"
                            else -> "Fairly balanced"
                        }
                        ProgressStoryCard(
                            title = "Quick read",
                            detail = "This compares what the week planned with what you actually checked off, then gives a simple balance read.",
                            accentColor = colorScheme.tertiary
                        )
                        if (completedMacroAvailable) {
                            Text(
                                text = "Planned average per day: P ${planMetrics.avgProtein}g • C ${planMetrics.avgCarbs}g • F ${planMetrics.avgFats}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Checked off average per day: P ${avgProtein}g • C ${avgCarbs}g • F ${avgFats}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Overall balance: $macroBalance",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        MacroProgressBar(
                            label = "Protein",
                            progress = (avgProtein / 100f).coerceIn(0f, 1f),
                            valueText = "Avg ${avgProtein}g",
                            barColor = colorScheme.primary
                        )
                        MacroProgressBar(
                            label = "Carbs",
                            progress = (avgCarbs / 250f).coerceIn(0f, 1f),
                            valueText = "Avg ${avgCarbs}g",
                            barColor = colorScheme.tertiary
                        )
                        MacroProgressBar(
                            label = "Fats",
                            progress = (avgFats / 80f).coerceIn(0f, 1f),
                            valueText = "Avg ${avgFats}g",
                            barColor = colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (!showWeightEntryAtTop && showTodayMode) {
            item {
                ProgressSectionHeader(
                    title = "Today",
                    subtitle = "Quickly log today so your trends stay accurate."
                )
            }
        }

        if (showTodayMode) {
            item {
                Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Daily Meal Check-off", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Selected day: $selectedDateLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isSelectedDateLoggable) {
                            "Check off what you completed after each meal."
                        } else {
                            "Selected day is read-only. Meal logging is only available for today."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (plannedMealsForDay.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Today’s tracking progress",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    ProgressStatusPill(
                                        text = "$selectedCompletedCount/${plannedMealsForDay.size} logged",
                                        emphasized = selectedCompletedCount > 0
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { selectedCompletionRatio },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colorScheme.primary
                                )
                                Text(
                                    text = selectedTrackingHeadline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (!isSelectedDateLoggable) {
                        ProgressJumpToTodayAction(
                            todayIndexInWeek = todayIndexInWeek,
                            onJump = { selectedDayIndex = todayIndexInWeek }
                        )
                        ProgressLoggingPolicyLearnMoreChip(
                            onClick = { showLoggingPolicyInfo = true }
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        weekDays.forEachIndexed { idx, date ->
                            val label = date.format(dayLabelFmt)
                            val isToday = label == todayLabel
                            TokenizedFilterChip(
                                selected = selectedDayIndex == idx,
                                onClick = { selectedDayIndex = idx },
                                text = if (isToday) "$label • Today" else label,
                                labelMaxWidth = dayChipLabelWidth
                            )
                        }
                    }
                    if (screenWidthDp <= 380) {
                        Text(
                            text = "Choose a day below to switch dates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    if (plannedMealsForDay.isEmpty()) {
                        Text(
                            text = "No planned meals yet. Generate a plan first.",
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else {
                        plannedMealsForDay.forEachIndexed { mealIndex, meal ->
                            val mealKey = ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)
                            val checked = selectedCompletedIds.contains(mealKey) || selectedCompletedIds.contains(meal.recipeId)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (checked) {
                                        colorScheme.primaryContainer.copy(alpha = 0.30f)
                                    } else {
                                        colorScheme.surface
                                    }
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (checked) 1.dp else 0.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (checked) {
                                        colorScheme.primary.copy(alpha = 0.35f)
                                    } else {
                                        colorScheme.outlineVariant.copy(alpha = 0.65f)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ProgressMealCheckbox(
                                        checked = checked,
                                        loggable = isSelectedDateLoggable,
                                        testTag = "progress_meal_checkbox_${meal.recipeId}",
                                        onCheckedChange = {
                                            if (!isSelectedDateLoggable) {
                                                showLoggingPolicyInfo = true
                                                return@ProgressMealCheckbox
                                            }
                                            val updated = progressViewModel.toggleMeal(
                                                date = selectedDate,
                                                recipeId = meal.recipeId,
                                                mealLabel = meal.mealLabel
                                            )
                                            if (!updated) {
                                                showLoggingPolicyInfo = true
                                                postProgressFeedback(
                                                    tone = FeedbackBannerTone.Error,
                                                    message = "Couldn’t update meal log for this date."
                                                )
                                                return@ProgressMealCheckbox
                                            }
                                            if (!checked) {
                                                mealPlanViewModel.trackMlEvent(
                                                    eventName = "meal_accepted",
                                                    requestId = mealPlanViewModel.currentRequestId(),
                                                    payload = mapOf(
                                                        "week_start" to weekStartKey,
                                                        "day_index" to selectedDayIndex,
                                                        "meal_index" to mealIndex,
                                                        "slot_index" to ((selectedDayIndex * 3) + mealIndex),
                                                        "meal_label" to meal.mealLabel,
                                                        "recipe_id" to meal.recipeId
                                                    )
                                                )
                                                mealPlanViewModel.trackMlEvent(
                                                    eventName = "cook_completed",
                                                    requestId = mealPlanViewModel.currentRequestId(),
                                                    payload = mapOf(
                                                        "week_start" to weekStartKey,
                                                        "meal_label" to meal.mealLabel,
                                                        "recipe_id" to meal.recipeId,
                                                        "source" to "progress_toggle"
                                                    )
                                                )
                                                val completedIdsAfterToggle = (selectedCompletedIds + mealKey).distinct()
                                                val nextSnapshot = buildTodayLogSnapshot(
                                                    todayMeals = selectedDayDescriptors,
                                                    completedMealIds = completedIdsAfterToggle
                                                )
                                                val nextCompletedCount = nextSnapshot.completedCount
                                                val ratio = if (plannedMealsForDay.isNotEmpty()) {
                                                    nextCompletedCount.toFloat() / plannedMealsForDay.size.toFloat()
                                                } else {
                                                    0f
                                                }
                                                val dayCaloriesTarget = selectedPlanDay?.totalCalories ?: 0
                                                val estimatedCalories = (dayCaloriesTarget * ratio).toInt()
                                                val estimatedProtein = (planMetrics.avgProtein * ratio).toInt()
                                                val estimatedFiber = (planMetrics.avgFiber * ratio).toInt()
                                                val nextMealSlot = nextSnapshot.nextMeal
                                                val nextMeal = nextMealSlot?.let { pending ->
                                                    plannedMealsForDay.firstOrNull { planned ->
                                                        planned.recipeId == pending.recipeId &&
                                                            planned.mealLabel.equals(pending.mealLabel, ignoreCase = true)
                                                    }
                                                }
                                                val nextSuggestion = mealImpactNextSuggestion(
                                                    completedMealLabel = meal.mealLabel,
                                                    nextMeal = nextMeal?.let { "${it.mealLabel} • ${it.title}" }
                                                )
                                                val statusLine = when {
                                                    ratio >= 1f -> "You’re on track today."
                                                    ratio >= 0.66f -> "Nice progress—you're close to today’s goal."
                                                    else -> "Great start. Keep building momentum meal by meal."
                                                }
                                                mealImpactSummary = MealImpactSummary(
                                                    statusLine = statusLine,
                                                    mealsDone = nextCompletedCount,
                                                    mealsPlanned = plannedMealsForDay.size,
                                                    estimatedCalories = estimatedCalories,
                                                    caloriesTarget = dayCaloriesTarget,
                                                    estimatedProtein = estimatedProtein,
                                                    proteinTarget = planMetrics.avgProtein,
                                                    estimatedFiber = estimatedFiber,
                                                    fiberTarget = planMetrics.avgFiber,
                                                    nextSuggestion = nextSuggestion,
                                                    nextMealRoute = nextMeal?.let {
                                                        Routes.recipeDetailsRoute(it.recipeId, it.mealLabel)
                                                    }
                                                )
                                                impactDetailsExpanded = false
                                                showImpactSheet = true
                                                mealCheckInPrompt = ProgressMealCheckInPrompt(
                                                    recipeId = meal.recipeId,
                                                    mealLabel = meal.mealLabel,
                                                    mealTitle = meal.title
                                                )
                                                postProgressFeedback(
                                                    tone = FeedbackBannerTone.Success,
                                                    message = "Meal logged. Impact updated."
                                                )
                                            } else {
                                                mealPlanViewModel.trackMlEvent(
                                                    eventName = "meal_skipped",
                                                    requestId = mealPlanViewModel.currentRequestId(),
                                                    payload = mapOf(
                                                        "week_start" to weekStartKey,
                                                        "day_index" to selectedDayIndex,
                                                        "meal_index" to mealIndex,
                                                        "slot_index" to ((selectedDayIndex * 3) + mealIndex),
                                                        "meal_label" to meal.mealLabel,
                                                        "recipe_id" to meal.recipeId
                                                    )
                                                )
                                                mealPlanViewModel.trackMlEvent(
                                                    eventName = "why_skipped_submitted",
                                                    requestId = mealPlanViewModel.currentRequestId(),
                                                    payload = mapOf(
                                                        "plan_id" to weekStartKey,
                                                        "week_start" to weekStartKey,
                                                        "day_index" to selectedDayIndex,
                                                        "meal_index" to mealIndex,
                                                        "slot_index" to ((selectedDayIndex * 3) + mealIndex),
                                                        "meal_label" to meal.mealLabel,
                                                        "recipe_id" to meal.recipeId,
                                                        "reason_tag" to "unchecked_by_user"
                                                    )
                                                )
                                                mealImpactSummary = null
                                                impactDetailsExpanded = false
                                                showImpactSheet = false
                                                postProgressFeedback(
                                                    tone = FeedbackBannerTone.Success,
                                                    message = "Meal unchecked for today."
                                                )
                                            }
                                        }
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ProgressStatusPill(
                                                text = meal.mealLabel,
                                                emphasized = true
                                            )
                                            ProgressStatusPill(
                                                text = when {
                                                    checked -> "Logged"
                                                    isSelectedDateLoggable -> "Pending"
                                                    else -> "Read-only"
                                                },
                                                emphasized = checked
                                            )
                                        }
                                        Text(
                                            text = meal.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        mealImpactSummary?.let { summary ->
                            val compactSummary = screenWidthDp <= 360
                            val impactHeadline = if (compactSummary) {
                                "${summary.statusLine} ${summary.mealsDone}/${summary.mealsPlanned} meals."
                            } else {
                                "${summary.statusLine} ${summary.mealsDone}/${summary.mealsPlanned} meals • " +
                                    "${formatKcalProgressShort(summary.estimatedCalories, summary.caloriesTarget)}."
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = colorScheme.primaryContainer.copy(alpha = 0.35f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Impact Summary",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colorScheme.onSurface
                                    )
                                    Text(
                                        text = impactHeadline,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    OutlinedButton(
                                        onClick = { impactDetailsExpanded = !impactDetailsExpanded },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 44.dp),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(
                                            if (impactDetailsExpanded) {
                                                "Hide impact details"
                                            } else {
                                                "View impact details"
                                            }
                                        )
                                    }
                                    if (impactDetailsExpanded) {
                                        Text(
                                            text = "${formatProteinProgressShort(summary.estimatedProtein, summary.proteinTarget)} • " +
                                                formatFiberProgressShort(summary.estimatedFiber, summary.fiberTarget),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = summary.nextSuggestion,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.primary,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        summary.nextMealRoute?.let { route ->
                                            OutlinedButton(
                                                onClick = { onNavigateToRoute(route) },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text("Open Next Meal")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (showTodayMode && selectedMealCheckIns.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "After-meal check-ins",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = goalReflectionSupportCopy(profile.goal),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        ProgressStatusPill(
                            text = "${selectedMealCheckIns.size} meal insight(s) saved for $selectedDateLabel",
                            emphasized = selectedMealCheckIns.isNotEmpty()
                        )
                        selectedMealCheckIns.forEach { checkIn ->
                            val matchedMeal = plannedMealsForDay.firstOrNull { planned ->
                                ProgressViewModel.buildMealKey(planned.mealLabel, planned.recipeId) == checkIn.mealKey
                            }
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.60f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ProgressStatusPill(
                                            text = checkIn.mealLabel,
                                            emphasized = true
                                        )
                                        ProgressStatusPill(
                                            text = "Check-in saved",
                                            emphasized = false
                                        )
                                    }
                                    Text(
                                        text = matchedMeal?.title ?: "Meal",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    val summary = buildList {
                                        checkIn.energyLevel?.let { add("Energy $it/5") }
                                        checkIn.fullnessLevel?.let { add("Fullness $it/5") }
                                        checkIn.cravingsLevel?.let { add("Cravings $it/5") }
                                        checkIn.satisfactionLevel?.let { add("Satisfaction $it/5") }
                                    }
                                    if (summary.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colorScheme.surface.copy(alpha = 0.70f)
                                        ) {
                                            Text(
                                                text = summary.joinToString(" • "),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = goalMealCheckInInsight(profile.goal, checkIn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.primary
                                    )
                                    checkIn.note?.takeIf { it.isNotBlank() }?.let { note ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = colorScheme.surface.copy(alpha = 0.82f)
                                        ) {
                                            Text(
                                                text = note,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showTodayMode && isSelectedDateLoggable && plannedMealsForDay.isNotEmpty() && selectedCompletedCount >= plannedMealsForDay.size) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProgressStatusPill(
                            text = "Daily logging complete",
                            emphasized = true
                        )
                        Text(
                            text = "Daily closeout ready",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "All meals logged today. Add a short reflection to close today’s loop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Button(
                            onClick = { dailyReflectionExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("progress_open_daily_reflection_cta"),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("Open Daily Reflection")
                        }
                    }
                }
            }
        }

        if (showTodayMode) {
            item {
                ProgressSectionHeader(
                    title = "Reflection",
                    subtitle = "Capture patterns and notes to improve next week."
                )
            }
        }

        if (showTodayMode) {
            item {
                ExpandableSection(
                title = "Daily Reflection",
                subtitle = "Quick check-in for patterns",
                defaultExpanded = false,
                expanded = dailyReflectionExpanded,
                onExpandedChange = { dailyReflectionExpanded = it }
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = goalReflectionSupportCopy(profile.goal) + " This supports your own tracking and does not replace medical care.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (!isSelectedDateLoggable) {
                        Text(
                            text = selectedDateLoggingLockReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        ProgressJumpToTodayAction(
                            todayIndexInWeek = todayIndexInWeek,
                            onJump = { selectedDayIndex = todayIndexInWeek }
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        weekDays.forEachIndexed { idx, date ->
                            val label = date.format(dayLabelFmt)
                            val isToday = label == todayLabel
                            TokenizedFilterChip(
                                selected = selectedDayIndex == idx,
                                onClick = { selectedDayIndex = idx },
                                text = if (isToday) "$label • Today" else label,
                                labelMaxWidth = dayChipLabelWidth
                            )
                        }
                    }
                    if (screenWidthDp <= 380) {
                        Text(
                            text = "Choose a reflection date below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Text("Energy", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { value ->
                                TokenizedFilterChip(
                                    selected = energyLevel == value,
                                    onClick = { energyLevel = value },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = value.toString(),
                                    labelMaxWidth = 28.dp
                                )
                            }
                        }
                    Text("Cravings", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { value ->
                                TokenizedFilterChip(
                                    selected = cravingsLevel == value,
                                    onClick = { cravingsLevel = value },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = value.toString(),
                                    labelMaxWidth = 28.dp
                                )
                            }
                        }
                    Text("Mood", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { value ->
                                TokenizedFilterChip(
                                    selected = moodLevel == value,
                                    onClick = { moodLevel = value },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = value.toString(),
                                    labelMaxWidth = 28.dp
                                )
                            }
                        }
                    Text("Symptoms", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        symptomOptions.forEach { symptom ->
                            val selected = symptomTags.contains(symptom)
                            TokenizedFilterChip(
                                selected = selected,
                                onClick = {
                                    symptomTags = if (selected) {
                                        symptomTags.filterNot { it == symptom }
                                    } else {
                                        symptomTags + symptom
                                    }
                                },
                                text = symptom,
                                labelMaxWidth = symptomChipLabelWidth
                            )
                        }
                    }
                    OutlinedTextField(
                        value = symptomNote,
                        onValueChange = { symptomNote = it },
                        label = { Text("Symptoms / notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LoadingActionButton(
                        state = reflectionSaveState,
                        idleLabel = "Save Reflection",
                        loadingLabel = "Saving…",
                        successLabel = "Saved",
                        errorLabel = "Try Again",
                        onClick = { saveReflectionWithFeedback() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = isSelectedDateLoggable
                    )
                }
            }
        }

        if (showReviewFocus) {
            item {
                ExpandableSection(
                title = "Weekly Journal",
                subtitle = "Optional weekly reflection",
                defaultExpanded = false
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = journalText,
                        onValueChange = { journalText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("How do you feel this week? (e.g., Energy levels, symptoms)") },
                        shape = MaterialTheme.shapes.large,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorScheme.primary)
                    )
                    LoadingActionButton(
                        state = weeklyReflectionSaveState,
                        idleLabel = "Save Weekly Reflection",
                        loadingLabel = "Saving…",
                        successLabel = "Saved",
                        errorLabel = "Try Again",
                        onClick = { saveWeeklyJournalWithFeedback() },
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    )
                    FilledTonalButton(
                        onClick = {
                            val file = progressViewModel.exportReflections()
                            if (file == null) {
                                postProgressFeedback(
                                    tone = FeedbackBannerTone.Error,
                                    message = "No reflections to export yet."
                                )
                            } else {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export reflections"))
                                postProgressFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = "Export ready. Choose where to share your reflection file."
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("Export and share reflections")
                    }
                }
            }
        }
        }

        if (!showWeightEntryAtTop && showTodayMode) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Weight Entry (Selected Day)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        if (!isSelectedDateLoggable) {
                            Text(
                                text = selectedDateLoggingLockReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.error,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            ProgressJumpToTodayAction(
                                todayIndexInWeek = todayIndexInWeek,
                                onJump = { selectedDayIndex = todayIndexInWeek }
                            )
                            ProgressLoggingPolicyLearnMoreChip(
                                onClick = { showLoggingPolicyInfo = true }
                            )
                        }
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text("Weight ($weightUnitLabel)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = weightNote,
                            onValueChange = { weightNote = it },
                            label = { Text("Weight note (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        LoadingActionButton(
                            state = weightSaveState,
                            idleLabel = "Save Weight",
                            loadingLabel = "Saving…",
                            successLabel = "Saved",
                            errorLabel = "Try Again",
                            onClick = { saveWeightWithFeedback() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = isSelectedDateLoggable
                        )
                    }
                }
            }
        }

        item {
            ExpandableSection(
                title = "Send Feedback",
                subtitle = "Queued if offline",
                defaultExpanded = feedbackSectionExpandedByDefault
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text("Feedback") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LoadingActionButton(
                        state = feedbackSendState,
                        idleLabel = "Send (Queued if offline)",
                        loadingLabel = "Sending…",
                        successLabel = "Sent",
                        errorLabel = "Try Again",
                        onClick = { submitFeedbackWithBanner() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = feedbackText.isNotBlank()
                    )
                    if (feedbackQueue.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Queue Status",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colorScheme.onSurfaceVariant
                                )
                                if (feedbackQueue.any { it.status == "Failed" }) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (!isOnline) {
                                                postProgressFeedback(
                                                    tone = FeedbackBannerTone.Success,
                                                    message = ActionFeedbackCopy.QueueSaved
                                                )
                                            } else {
                                                postProgressFeedback(
                                                    tone = FeedbackBannerTone.Loading,
                                                    message = "Retrying all failed feedback…"
                                                )
                                                progressViewModel.retryAllFeedback(isOnline = true)
                                                postProgressFeedback(
                                                    tone = FeedbackBannerTone.Success,
                                                    message = ActionFeedbackCopy.QueueRetrying
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("progress_retry_all_feedback"),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text("Retry all")
                                    }
                                }
                            }
                            feedbackQueue.forEach { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.status,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when (entry.status) {
                                            "Sent" -> colorScheme.primary
                                            "Failed" -> colorScheme.error
                                            "Sending" -> colorScheme.secondary
                                            else -> colorScheme.onSurfaceVariant
                                        }
                                    )
                                    if (entry.status == "Failed") {
                                        OutlinedButton(
                                            onClick = {
                                                if (!isOnline) {
                                                    postProgressFeedback(
                                                        tone = FeedbackBannerTone.Success,
                                                        message = ActionFeedbackCopy.QueueSaved
                                                    )
                                                } else {
                                                    postProgressFeedback(
                                                        tone = FeedbackBannerTone.Loading,
                                                        message = "Retrying queued feedback…"
                                                    )
                                                    progressViewModel.retryFeedback(entry.id, isOnline = true)
                                                    postProgressFeedback(
                                                        tone = FeedbackBannerTone.Success,
                                                        message = ActionFeedbackCopy.QueueRetrying
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 40.dp)
                                                .testTag("progress_retry_feedback_${entry.id}"),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text("Retry")
                                        }
                                    }
                                }
                                if (!entry.lastError.isNullOrBlank()) {
                                    Text(
                                        text = entry.lastError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    }

    val impactSheetSummary = mealImpactSummary
    if (showImpactSheet && impactSheetSummary != null) {
        ModalBottomSheet(
            onDismissRequest = { showImpactSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Meal impact",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = impactSheetSummary.statusLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Today: ${impactSheetSummary.mealsDone}/${impactSheetSummary.mealsPlanned} meals • " +
                        formatKcalProgressShort(impactSheetSummary.estimatedCalories, impactSheetSummary.caloriesTarget),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${formatProteinProgressShort(impactSheetSummary.estimatedProtein, impactSheetSummary.proteinTarget)} • " +
                        formatFiberProgressShort(impactSheetSummary.estimatedFiber, impactSheetSummary.fiberTarget),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = impactSheetSummary.nextSuggestion,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.primary
                )
                impactSheetSummary.nextMealRoute?.let { route ->
                    Button(
                        onClick = {
                            showImpactSheet = false
                            onNavigateToRoute(route)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Open Next Meal")
                    }
                }
                OutlinedButton(
                    onClick = { showImpactSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .height(48.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Close impact summary")
                }
            }
        }
    }

    if (showConfidenceInfo) {
        AlertDialog(
            onDismissRequest = { showConfidenceInfo = false },
            confirmButton = {
                DialogGotItButton(onClick = { showConfidenceInfo = false })
            },
            title = { Text("Plan fit score", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "This is a simple planner fit score. It checks how closely the week stayed near calorie goals, repeat limits, and your saved food rules. Higher means the week stayed closer to the targets."
                )
            }
        )
    }

    if (showMacroInfo) {
        AlertDialog(
            onDismissRequest = { showMacroInfo = false },
            confirmButton = {
                DialogGotItButton(onClick = { showMacroInfo = false })
            },
            title = { Text("Nutrition balance", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "This compares the week's planned nutrition with the meals you actually checked off. If you are offline, some nutrition details may be missing."
                )
            }
        )
    }

    if (showSpendInfo) {
        AlertDialog(
            onDismissRequest = { showSpendInfo = false },
            confirmButton = {
                DialogGotItButton(onClick = { showSpendInfo = false })
            },
            title = { Text("Weekly spending", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "Projected cost is an estimate from the plan. " +
                    "Actual spending is optional and stored locally on your device."
                )
            }
        )
    }

    if (showLowGiInfo) {
        AlertDialog(
            onDismissRequest = { showLowGiInfo = false },
            confirmButton = {
                DialogGotItButton(onClick = { showLowGiInfo = false })
            },
            title = { Text("Low‑GI guidance", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "We lean toward higher-fiber, balanced meals to help the day feel steadier. " +
                    "Use it as general support, not as medical advice."
                )
            }
        )
    }

    mealCheckInPrompt?.let { prompt ->
        val existing = logs[selectedDateKey]?.mealCheckIns.orEmpty().firstOrNull { checkIn ->
            checkIn.mealKey == ProgressViewModel.buildMealKey(prompt.mealLabel, prompt.recipeId) ||
                (checkIn.recipeId == prompt.recipeId &&
                    checkIn.mealLabel.equals(prompt.mealLabel, ignoreCase = true))
        }
        MealCheckInDialog(
            goal = profile.goal,
            mealTitle = prompt.mealTitle,
            mealLabel = prompt.mealLabel,
            initial = existing,
            onDismiss = { mealCheckInPrompt = null },
            onSave = { draft: MealCheckInDraft ->
                val saved = progressViewModel.saveMealCheckIn(
                    date = selectedDate,
                    recipeId = prompt.recipeId,
                    mealLabel = prompt.mealLabel,
                    energyLevel = draft.energyLevel,
                    fullnessLevel = draft.fullnessLevel,
                    cravingsLevel = draft.cravingsLevel,
                    satisfactionLevel = draft.satisfactionLevel,
                    note = draft.note
                )
                postProgressFeedback(
                    tone = if (saved) FeedbackBannerTone.Success else FeedbackBannerTone.Error,
                    message = if (saved) "Meal check-in saved." else "Couldn’t save that meal check-in."
                )
                if (saved) {
                    mealCheckInPrompt = null
                }
            }
        )
    }

    if (showLockedInfo) {
        AlertDialog(
            onDismissRequest = { showLockedInfo = false },
            confirmButton = {
                DialogGotItButton(onClick = { showLockedInfo = false })
            },
            title = { Text(progressLockedCopy.dialogTitle, modifier = Modifier.semantics { heading() }) },
            text = {
                Text(progressLockedCopy.dialogBody)
            }
        )
    }

    if (showLoggingPolicyInfo) {
        ProgressLoggingPolicyDialog(
            lockReason = selectedDateLoggingLockReason,
            onDismiss = { showLoggingPolicyInfo = false }
        )
    }
}

}

@Composable
private fun ProgressTrackHero(
    hasPlan: Boolean,
    lockedMessage: String,
    todayCompletedCount: Int,
    todayPlannedCount: Int,
    todayStatusLine: String,
    nextMealLabel: String?,
    queuedFeedbackCount: Int,
    isOnline: Boolean,
    adherence: Float,
    primaryCtaLabel: String,
    primaryActionState: FeedbackActionState,
    primaryLoadingLabel: String,
    primarySuccessLabel: String,
    onPrimaryAction: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (!hasPlan) {
        FriendlyEmptyStateCard(
            title = "Track opens after your first plan",
            message = lockedMessage,
            actionLabel = "Open plan",
            onAction = onOpenPlan,
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
                traversalIndex = 1f
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
                    text = "Track today",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.primary
                )
                Text(
                    text = todayStatusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Checked off",
                        value = "$todayCompletedCount/$todayPlannedCount",
                        hint = if (todayPlannedCount > 0) "Meals logged for today." else "No meals planned for today yet.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Next",
                        value = nextMealLabel ?: "Reflection",
                        hint = if (nextMealLabel != null) "Open the next meal when you're ready." else "No next meal waiting right now.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Sync",
                        value = if (queuedFeedbackCount > 0) "$queuedFeedbackCount waiting" else if (isOnline) "Ready" else "Saved offline",
                        hint = if (queuedFeedbackCount > 0) "Queued notes and feedback." else "Your entries are stored safely.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Week done",
                        value = "${(adherence * 100).toInt()}%",
                        hint = "Meals checked off across the week so far.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
            LoadingActionButton(
                state = primaryActionState,
                idleLabel = primaryCtaLabel,
                loadingLabel = primaryLoadingLabel,
                successLabel = primarySuccessLabel,
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
private fun ProgressReviewHero(
    hasPlan: Boolean,
    lockedMessage: String,
    completedMealsCount: Int,
    plannedMealsCount: Int,
    adherence: Float,
    weeklySpend: Int?,
    projectedWeeklyCost: Int?,
    queuedFeedbackCount: Int,
    showStaleBanner: Boolean,
    primaryCtaLabel: String,
    primaryActionState: FeedbackActionState,
    primaryLoadingLabel: String,
    primarySuccessLabel: String,
    onPrimaryAction: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (!hasPlan) {
        FriendlyEmptyStateCard(
            title = "Week review opens after your first plan",
            message = lockedMessage,
            actionLabel = "Open plan",
            onAction = onOpenPlan,
            accentColor = colorScheme.secondary,
            modifier = modifier
        )
        return
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = 1f
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
                    text = "Week review",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.secondary
                )
                Text(
                    text = if (showStaleBanner) {
                        "A quick check-in will make this review more accurate again."
                    } else {
                        "Review meals, spending, and feedback without opening the full forms first."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Meals done",
                        value = "$completedMealsCount/$plannedMealsCount",
                        hint = "All checked-off meals this week.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Week done",
                        value = "${(adherence * 100).toInt()}%",
                        hint = "Overall completion for the current week.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Spend",
                        value = weeklySpend?.let { "₱$it" } ?: projectedWeeklyCost?.let { "₱$it" } ?: "—",
                        hint = if (weeklySpend != null) "Actual spending saved by you." else "Current plan estimate.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Feedback",
                        value = if (queuedFeedbackCount > 0) "$queuedFeedbackCount waiting" else "Up to date",
                        hint = if (queuedFeedbackCount > 0) "Some notes still need syncing." else "No pending feedback right now.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
            LoadingActionButton(
                state = primaryActionState,
                idleLabel = primaryCtaLabel,
                loadingLabel = primaryLoadingLabel,
                successLabel = primarySuccessLabel,
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
private fun ProgressInsightsHero(
    hasPlan: Boolean,
    lockedMessage: String,
    averageCalories: Int,
    averageProtein: Int,
    projectedWeeklyCost: Int?,
    previousPlanReady: Boolean,
    macroLabel: String,
    primaryCtaLabel: String,
    primaryActionState: FeedbackActionState,
    primaryLoadingLabel: String,
    primarySuccessLabel: String,
    onPrimaryAction: () -> Unit,
    onOpenPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (!hasPlan) {
        FriendlyEmptyStateCard(
            title = "Deeper insights open after your first plan",
            message = lockedMessage,
            actionLabel = "Open plan",
            onAction = onOpenPlan,
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
                traversalIndex = 1f
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
                    text = "Deeper look",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.tertiary
                )
                Text(
                    text = "Use this only when you want the simple story behind the week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Avg kcal",
                        value = if (averageCalories > 0) "$averageCalories" else "—",
                        hint = "Estimated calories per day.",
                        accentColor = colorScheme.primary,
                        badge = "per day"
                    ),
                    CompactWidgetSpec(
                        title = "Protein",
                        value = if (averageProtein > 0) "${averageProtein}g" else "—",
                        hint = macroLabel,
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Week cost",
                        value = projectedWeeklyCost?.let { "₱$it" } ?: "Pending",
                        hint = "Estimated household total for the week.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Compare",
                        value = if (previousPlanReady) "Ready" else "Baseline",
                        hint = if (previousPlanReady) "You can compare against the last week." else "One more week unlocks comparison.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
            LoadingActionButton(
                state = primaryActionState,
                idleLabel = primaryCtaLabel,
                loadingLabel = primaryLoadingLabel,
                successLabel = primarySuccessLabel,
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
private fun ProgressSectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.MicroGap)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProgressStoryCard(
    title: String,
    detail: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = accentColor.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = accentColor
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgressStatusPill(
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
            colorScheme.surfaceVariant.copy(alpha = 0.60f)
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
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

private data class MealImpactSummary(
    val statusLine: String,
    val mealsDone: Int,
    val mealsPlanned: Int,
    val estimatedCalories: Int,
    val caloriesTarget: Int,
    val estimatedProtein: Int,
    val proteinTarget: Int,
    val estimatedFiber: Int,
    val fiberTarget: Int,
    val nextSuggestion: String,
    val nextMealRoute: String?
)

private data class ProgressMealCheckInPrompt(
    val recipeId: String,
    val mealLabel: String,
    val mealTitle: String
)

private enum class ProgressMode(val label: String) {
    Today("Today"),
    Week("Week");
}

private fun progressModeFromSavedValue(value: String): ProgressMode {
    return when {
        value.equals("Week", ignoreCase = true) -> ProgressMode.Week
        else -> ProgressMode.Today
    }
}

@Composable
private fun DialogGotItButton(onClick: () -> Unit) {
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
internal fun ProgressLoggingPolicyLearnMoreChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("progress_logging_policy_learn_more"),
        label = { Text(LockedFlowCopy.LearnMoreLabel) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
internal fun ProgressJumpToTodayAction(
    todayIndexInWeek: Int,
    onJump: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(UiMotionTokens.JumpToTodayRevealMs)) +
            expandVertically(animationSpec = tween(UiMotionTokens.JumpToTodayRevealMs))
    ) {
        if (todayIndexInWeek >= 0) {
            OutlinedButton(
                onClick = onJump,
                modifier = modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("progress_jump_to_today"),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Jump to Today")
            }
        } else {
            Text(
                text = "Today is outside this selected week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
    }
}

@Composable
internal fun ProgressLoggingPolicyDialog(
    lockReason: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogGotItButton(onClick = onDismiss)
        },
        title = { Text("Logging policy", modifier = Modifier.semantics { heading() }) },
        text = {
            Text(
                "${ProgressViewModel.LoggingPolicySummary}\n\n$lockReason"
            )
        }
    )
}

@Composable
internal fun ProgressMealCheckbox(
    checked: Boolean,
    loggable: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Checkbox(
        checked = checked,
        enabled = loggable,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.testTag(testTag)
    )
}

private fun initialSelectedDayIndex(weekStart: LocalDate): Int {
    val todayOffset = java.time.temporal.ChronoUnit.DAYS.between(weekStart, LocalDate.now()).toInt()
    return todayOffset.coerceIn(0, 6)
}

private fun weekStartDate(timestamp: Long?): LocalDate {
    val zone = ZoneId.systemDefault()
    val base = if (timestamp != null) Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate() else LocalDate.now()
    return base
}

private fun weekLabelFor(start: LocalDate): String {
    val end = start.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("MMM dd")
    return "${start.format(fmt)} - ${end.format(fmt)}"
}

internal fun parseProgressDateOrNull(dateKey: String): LocalDate? {
    return runCatching { LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
}

internal fun isInWeek(dateKey: String, weekStart: LocalDate): Boolean {
    val parsedDate = parseProgressDateOrNull(dateKey) ?: return false
    return !parsedDate.isBefore(weekStart) && !parsedDate.isAfter(weekStart.plusDays(6))
}

private data class WeekStats(
    val avgCalories: Int,
    val varietyCount: Int,
    val estCost: Int?,
    val avgProtein: Int?,
    val avgCarbs: Int?,
    val avgFats: Int?,
    val pantryMatches: Int?
)

private fun computeWeekStats(plan: com.pcosina.app.data.model.PlannerPlanResponse): WeekStats {
    val avgCalories = if (plan.days.isNotEmpty()) {
        plan.days.sumOf { it.totalCalories } / plan.days.size
    } else 0
    val variety = plan.days.flatMap { it.meals }.map { it.recipeId }.distinct().size
    val explanation = plan.explanation
    return WeekStats(
        avgCalories = avgCalories,
        varietyCount = variety,
        estCost = explanation?.estimatedWeeklyCost,
        avgProtein = explanation?.avgProtein,
        avgCarbs = explanation?.avgCarbs,
        avgFats = explanation?.avgFats,
        pantryMatches = explanation?.pantryMatches
    )
}

private fun buildWeekChanges(current: WeekStats, previous: WeekStats): List<String> {
    val lines = mutableListOf<String>()
    val kcalDiff = current.avgCalories - previous.avgCalories
    val kcalText = when {
        kcalDiff > 0 -> "↑$kcalDiff"
        kcalDiff < 0 -> "↓${-kcalDiff}"
        else -> "no change"
    }
    lines.add("Avg kcal/day: ${current.avgCalories} ($kcalText)")
    val varietyDiff = current.varietyCount - previous.varietyCount
    val varietyText = when {
        varietyDiff > 0 -> "↑$varietyDiff"
        varietyDiff < 0 -> "↓${-varietyDiff}"
        else -> "no change"
    }
    lines.add("Variety count: ${current.varietyCount} ($varietyText)")
    if (current.estCost != null && previous.estCost != null) {
        val costDiff = current.estCost - previous.estCost
        val costText = when {
            costDiff > 0 -> "↑₱$costDiff"
            costDiff < 0 -> "↓₱${-costDiff}"
            else -> "no change"
        }
        lines.add("Estimated cost: ₱${current.estCost} ($costText)")
    }
    if (current.avgProtein != null && previous.avgProtein != null) {
        val diff = current.avgProtein - previous.avgProtein
        val diffText = when {
            diff > 0 -> "↑$diff"
            diff < 0 -> "↓${-diff}"
            else -> "no change"
        }
        lines.add("Avg protein: ${current.avgProtein}g ($diffText)")
    }
    return lines
}

private fun buildWeekWhy(current: WeekStats, previous: WeekStats): List<String> {
    val reasons = mutableListOf<String>()
    if (current.avgCalories < previous.avgCalories) {
        reasons.add("Calories lowered to stay closer to your target range.")
    }
    if (current.varietyCount > previous.varietyCount) {
        reasons.add("Variety increased to reduce repeats across the week.")
    }
    if (current.estCost != null && previous.estCost != null && current.estCost < previous.estCost) {
        reasons.add("Estimated cost decreased to align with budget control.")
    }
    val pantryCurrent = current.pantryMatches ?: 0
    val pantryPrev = previous.pantryMatches ?: 0
    if (pantryCurrent > pantryPrev) {
        reasons.add("More pantry items were prioritized to reduce waste.")
    }
    if (reasons.isEmpty()) {
        reasons.add("Changes are within normal optimization tolerance.")
    }
    return reasons
}
