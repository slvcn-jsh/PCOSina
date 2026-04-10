package com.pcosina.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.LoadingActionButton
import com.pcosina.app.ui.components.TokenizedFilterChip
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.util.buildMealReasons
import com.pcosina.app.ui.util.buildTodayLogSnapshot
import com.pcosina.app.ui.util.ActionFeedbackCopy
import com.pcosina.app.ui.util.GuidedJourneyInput
import com.pcosina.app.ui.util.LockedFlowCopy
import com.pcosina.app.ui.util.TodayMealDescriptor
import com.pcosina.app.ui.util.formatFiberProgressShort
import com.pcosina.app.ui.util.formatKcalProgressShort
import com.pcosina.app.ui.util.formatProteinProgressShort
import com.pcosina.app.ui.util.mealImpactNextSuggestion
import com.pcosina.app.ui.util.rememberIsOnline
import com.pcosina.app.ui.util.resolveGuidedJourneyStep
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.ui.navigation.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val groceryItems by groceryViewModel.groceryItems.collectAsState()
    var showConfidenceInfo by rememberSaveable { mutableStateOf(false) }
    var showMacroInfo by rememberSaveable { mutableStateOf(false) }
    var showSpendInfo by rememberSaveable { mutableStateOf(false) }
    var showLowGiInfo by rememberSaveable { mutableStateOf(false) }
    var showLockedInfo by rememberSaveable { mutableStateOf(false) }
    var showLoggingPolicyInfo by rememberSaveable { mutableStateOf(false) }
    var weekHistoryExpanded by rememberSaveable(collapseWeekHistoryOnCompact) {
        mutableStateOf(!collapseWeekHistoryOnCompact)
    }
    var mealImpactSummary by rememberSaveable { mutableStateOf<MealImpactSummary?>(null) }
    var impactDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var showImpactSheet by rememberSaveable { mutableStateOf(false) }
    var dailyReflectionExpanded by rememberSaveable { mutableStateOf(false) }
    var advancedWeekAnalyticsExpanded by rememberSaveable { mutableStateOf(false) }
    var progressMode by rememberSaveable { mutableStateOf(ProgressMode.Today) }
    val coroutineScope = rememberCoroutineScope()
    var progressFeedbackBanner by remember { mutableStateOf<FeedbackBannerData?>(null) }
    var reflectionSaveState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var weeklyReflectionSaveState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var weightSaveState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var feedbackSendState by remember { mutableStateOf(FeedbackActionState.Idle) }
    val showTodayMode = progressMode == ProgressMode.Today
    val showWeekMode = progressMode == ProgressMode.Week
    val hasPlan = planHistory.isNotEmpty() || planState is MealPlanUiState.Success
    val hasReviewedWeek = activePlanId != null && activePlanId == lastReviewedWeek
    val hasGrocery = groceryItems.isNotEmpty()
    val hasTracked = logs.isNotEmpty()
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
    val weekStart = remember(activeWeekStart, planTimestamp) {
        activeWeekStart?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
            ?: weekStartDate(planTimestamp)
    }
    val weekLabel = remember(weekStart) { weekLabelFor(weekStart) }
    val weekStartKey = weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val fallbackWeekStartKey = remember(planTimestamp) {
        val primaryStart = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val fallbackStart = if (primaryStart == DayOfWeek.SUNDAY) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
        weekStartDate(planTimestamp, fallbackStart).format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    var selectedDayIndex by rememberSaveable(weekStartKey) {
        mutableStateOf(initialSelectedDayIndex(weekStart))
    }
    val selectedDate = weekStart.plusDays(selectedDayIndex.toLong())
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val dayLabelFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    val todayLabel = LocalDate.now().format(dayLabelFmt)

    LaunchedEffect(savedProgressMode) {
        progressMode = ProgressMode.fromSavedValue(savedProgressMode)
    }
    LaunchedEffect(savedAdvancedWeekAnalyticsExpanded) {
        advancedWeekAnalyticsExpanded = savedAdvancedWeekAnalyticsExpanded
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
    val recipeCounts = remember(planDays) {
        planDays.flatMap { it.meals }.groupingBy { it.recipeId }.eachCount()
    }
    val planByLabel = planDays.associateBy { it.dayLabel.lowercase(Locale.ENGLISH) }
    val selectedDayLabel = selectedDate.format(dayLabelFmt).lowercase(Locale.ENGLISH)
    val selectedPlanDay = planByLabel[selectedDayLabel]
    val plannedMealsForDay = selectedPlanDay?.meals.orEmpty()
    val selectedDateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val selectedCompletedIds = logs[selectedDateKey]?.completedMealIds.orEmpty()
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
    val plannedMealsCount = planDays.sumOf { it.meals.size }
    val completedMealsCount = logs.filterKeys { isInWeek(it, weekStart) }
        .values.sumOf { it.completedMealIds.size }
    val adherence = if (plannedMealsCount > 0) completedMealsCount.toFloat() / plannedMealsCount else 0f
    val lastLogDate = logs.keys.mapNotNull { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
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
            log.weightKg?.let { LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE) to it }
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

    var weightInput by rememberSaveable { mutableStateOf("") }
    var weightNote by rememberSaveable { mutableStateOf("") }
    var weeklySpendInput by rememberSaveable { mutableStateOf("") }
    var energyLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var cravingsLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var moodLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var symptomTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var symptomNote by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(logs, selectedDate, profile.weightUnit) {
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
    }
    LaunchedEffect(weeklySpend, weekStartKey) {
        weeklySpendInput = weeklySpend?.toString() ?: ""
    }

    var journalText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(weeklyJournal) {
        journalText = weeklyJournal
    }

    var feedbackText by rememberSaveable { mutableStateOf("") }
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

    val onPrimaryCta: () -> Unit = {
        when {
            !hasPlan -> {
                postProgressFeedback(
                    tone = FeedbackBannerTone.Loading,
                    message = "Opening plan generator…"
                )
                onNavigateToRoute(Routes.MealPlan)
            }
            showTodayMode && todaySnapshot.nextMeal != null -> {
                todaySnapshot.nextMeal?.let { nextMeal ->
                    postProgressFeedback(
                        tone = FeedbackBannerTone.Success,
                        message = "Opening next meal."
                    )
                    onNavigateToRoute(Routes.recipeDetailsRoute(nextMeal.recipeId, nextMeal.mealLabel))
                }
            }
            showTodayMode && todaySnapshot.plannedCount == 0 -> {
                postProgressFeedback(
                    tone = FeedbackBannerTone.Loading,
                    message = "Opening plan generator for today…"
                )
                onNavigateToRoute(Routes.MealPlan)
            }
            showTodayMode && isSelectedDateLoggable && todaySnapshot.plannedCount > 0 -> {
                if (todayIndexInWeek >= 0) {
                    selectedDayIndex = todayIndexInWeek
                }
                postProgressFeedback(
                    tone = FeedbackBannerTone.Success,
                    message = "Daily meal check-off is ready. Log meals after you eat."
                )
            }
            showTodayMode -> {
                if (todayIndexInWeek >= 0) selectedDayIndex = todayIndexInWeek
                dailyReflectionExpanded = true
                postProgressFeedback(
                    tone = FeedbackBannerTone.Success,
                    message = "Daily reflection opened. Add a quick note to close today's loop."
                )
            }
            sundayComplete -> {
                postProgressFeedback(
                    tone = FeedbackBannerTone.Loading,
                    message = "Opening plan generator for next week…"
                )
                onNavigateToRoute(Routes.MealPlan)
            }
            else -> {
                weekHistoryExpanded = true
                postProgressFeedback(
                    tone = FeedbackBannerTone.Success,
                    message = "Week mode active. Review trends and summary cards below."
                )
            }
        }
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
                    title = "Weekly Insights",
                    subtitle = "Tracking your meals and trends",
                    containerHeight = 180,
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
                        tint = colorScheme.onPrimary,
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

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_top_section_capture"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("progress_step4_card")
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 0.8f
                        },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Step 4 of 4: Track Progress",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colorScheme.primary
                        )
                        Text(
                            text = "First-win path: Login -> Profile -> Goal -> Generate Plan -> Track today.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 1f
                        }
                        .testTag("progress_focus_mode_card"),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Focus Mode",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (showTodayMode) {
                                "Today mode: meal check-off is the first action."
                            } else {
                                "Week mode: adherence, spending, and week-level trends."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.semantics {
                                isTraversalGroup = true
                                traversalIndex = 1.1f
                            },
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProgressMode.values().forEach { mode ->
                                FilterChip(
                                    selected = progressMode == mode,
                                    onClick = {
                                        progressMode = mode
                                        progressViewModel.setProgressModePreference(mode.label)
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("progress_mode_${mode.label.lowercase(Locale.ENGLISH)}"),
                                    label = { Text(mode.label) }
                                )
                            }
                        }
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

        progressFeedbackBanner?.let { banner ->
            item {
                AppFeedbackBanner(
                    data = banner,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            val queuedCount = feedbackQueue.count { it.status != "Sent" }
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
                        Text(if (isOnline) "Online" else "Offline", maxLines = 1)
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
                if (queuedCount > 0) {
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
                        label = { Text("Queue $queuedCount", maxLines = 1) },
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

        if (hasPlan && showTodayMode) {
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

        if (!hasPlan) {
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

        if (showWeekMode && sortedHistory.isNotEmpty()) {
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
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(sortedHistory) { instance ->
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

        if (showWeekMode && showStaleBanner) {
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

        if (showWeekMode) {
            item {
                ProgressSectionHeader(
                    title = "Week",
                    subtitle = "Review adherence, nutrition trends, and plan quality."
                )
            }
        }

        if (showWeekMode && sundayComplete) {
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

        if (showWeekMode) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        title = "Plan Adherence",
                        value = "${(adherence * 100).toInt()}%",
                        subtitle = weekLabel,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "Weight Delta",
                        value = weightDelta?.let { "${displayWeight(it)} $weightUnitLabel" } ?: "—",
                        subtitle = "This Week",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

        item {
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
                    Text("Insights: Week-over-Week", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (currentStats == null) {
                        Text(
                            text = "Generate a plan to see insights.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else if (prevStats == null) {
                        Text(
                            text = "Baseline week. Future weeks will compare here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    } else {
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
                                text = "Why this changed",
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

        item {
            val avgKcal = if (planDays.isNotEmpty()) {
                (planDays.sumOf { it.totalCalories } / planDays.size)
            } else 0
            val kcalText = if (avgKcal > 0) "${avgKcal} kcal/day" else "—"
            val proteinText = if (planMetrics.avgProtein > 0) "${planMetrics.avgProtein}g protein/day" else "—"
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
                    Text("Summary", style = MaterialTheme.typography.labelLarge, color = colorScheme.onSurfaceVariant)
                    Text("${(adherence * 100).toInt()}% • $kcalText • $proteinText", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
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
                    Text(
                        text = "Projected from plan: $projectedText",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Budget target: $budgetText",
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
                    Button(
                        onClick = {
                            val spendValue = parseCurrencyInput(weeklySpendInput)
                            if (spendValue == null && weeklySpendInput.isNotBlank()) {
                                postProgressFeedback(
                                    tone = FeedbackBannerTone.Error,
                                    message = "Enter a valid amount before saving actual spending."
                                )
                                return@Button
                            }
                            progressViewModel.saveWeeklySpend(weekStartKey, spendValue)
                            postProgressFeedback(
                                tone = FeedbackBannerTone.Success,
                                message = if (spendValue == null) {
                                    "Actual spending cleared for this week."
                                } else {
                                    "Actual spending saved for this week."
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                    ) {
                        Text("Save Actual Spending")
                    }
                    val spendValue = weeklySpend
                    if (spendValue != null && projectedWeeklyCost != null) {
                        val variance = spendValue - projectedWeeklyCost
                        val varianceText = if (variance >= 0) "+₱$variance vs projected" else "-₱${-variance} vs projected"
                        Text(
                            text = "Difference: $varianceText",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Optional. Stored locally for your tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (planExplanation != null) {
            item {
                ExpandableSection(
                    title = "Plan Explanation",
                    subtitle = "How this week was optimized",
                    defaultExpanded = false
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Signals used by the optimizer to balance nutrition, variety, and pantry use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Projected values are estimates. Log actual spending below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        planExplanation.confidenceScore?.let { score ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Confidence score: $score%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { showConfidenceInfo = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Confidence info",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        val items = mutableListOf<String>()
                        val avgDev = planExplanation.avgCaloriesDeviation
                        planExplanation.targetCalories?.let {
                            items.add("Target calories: ${it} kcal/day")
                        }
                        planExplanation.avgCalories?.let {
                            val devText = if (avgDev != null) " (±$avgDev)" else ""
                            items.add("Avg calories: ${it} kcal/day$devText")
                        }
                        val targetMacros = listOf(
                            planExplanation.targetProtein?.let { "P ${it}g" },
                            planExplanation.targetCarbs?.let { "C ${it}g" },
                            planExplanation.targetFats?.let { "F ${it}g" }
                        ).filterNotNull()
                        if (targetMacros.isNotEmpty()) {
                            items.add("Macro targets: ${targetMacros.joinToString(" • ")}")
                        }
                        val avgMacros = listOf(
                            planExplanation.avgProtein?.let { "P ${it}g" },
                            planExplanation.avgCarbs?.let { "C ${it}g" },
                            planExplanation.avgFats?.let { "F ${it}g" }
                        ).filterNotNull()
                        if (avgMacros.isNotEmpty()) {
                            items.add("Avg macros: ${avgMacros.joinToString(" • ")}")
                        }
                        val constraintItems = mutableListOf<String>()
                        planExplanation.toleranceUsed?.let {
                            val pct = String.format(Locale.ENGLISH, "%.0f", it * 100)
                            constraintItems.add("Tolerance used: $pct%")
                        }
                        planExplanation.maxPerWeek?.let {
                            constraintItems.add("Max repeats per recipe: $it")
                        }
                        if (planExplanation.budgetWeekly != null || planExplanation.estimatedWeeklyCost != null) {
                            val budget = planExplanation.budgetWeekly?.let {
                                "₱" + String.format(Locale.ENGLISH, "%.0f", it)
                            }
                            val est = planExplanation.estimatedWeeklyCost?.let { "₱$it" }
                            val text = when {
                                budget != null && est != null -> "Budget weekly: $budget (est $est)"
                                budget != null -> "Budget weekly: $budget"
                                est != null -> "Estimated weekly cost: $est"
                                else -> null
                            }
                            if (text != null) constraintItems.add(text)
                        }
                        planExplanation.restrictionCount?.let {
                            constraintItems.add("Restriction count: $it")
                        }
                        planExplanation.pantryMatches?.let {
                            items.add("Pantry matches used: $it")
                        }
                        planExplanation.uniqueVegTokens?.let {
                            items.add("Veg variety tokens: $it")
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
                                text = "Constraint Summary",
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
                    }
                }
            }
        }

        item {
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(planFeedbackOptions) { tag ->
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

        if (showWeekMode) {
            item {
                ExpandableSection(
                    title = "Advanced Week Analytics",
                    subtitle = if (advancedWeekAnalyticsExpanded) {
                        "Goal focus, completion, and macros are visible below."
                    } else {
                        "Collapsed by default. Expand for deeper analysis."
                    },
                    defaultExpanded = false,
                    expanded = advancedWeekAnalyticsExpanded,
                    onExpandedChange = {
                        advancedWeekAnalyticsExpanded = it
                        progressViewModel.setAdvancedWeekAnalyticsExpandedPreference(it)
                    }
                ) {
                    Text(
                        text = "Open this section when you want deeper analytics beyond core insights.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
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
                                    val label = LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE).format(dayLabelFmt)
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

        item {
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

        if (showWeekMode && advancedWeekAnalyticsExpanded) {
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
                        text = "Goal Focus",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface
                    )
                    when (goalType) {
                        com.pcosina.app.domain.GoalType.WEIGHT_LOSS -> {
                            Text("Emphasis: weekly weight trend and adherence.", color = colorScheme.onSurfaceVariant)
                            val startText = weightStart?.let { String.format("%.1fkg", it) } ?: "—"
                            val endText = weightEnd?.let { String.format("%.1fkg", it) } ?: "—"
                            Text("Start → End: $startText → $endText", color = colorScheme.onSurfaceVariant)
                            Text("Target: ${userViewModel.dailyCalorieTarget} kcal/day", color = colorScheme.onSurfaceVariant)
                        }
                        com.pcosina.app.domain.GoalType.SYMPTOM_MANAGEMENT -> {
                            Text("Emphasis: steady fiber + protein consistency.", color = colorScheme.onSurfaceVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Guidance: choose low‑GI carbs and balanced meals.", color = colorScheme.onSurfaceVariant)
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
                                    text = "Plan averages: Protein ${planMetrics.avgProtein}g/day • Fiber ${planMetrics.avgFiber}g/day",
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        com.pcosina.app.domain.GoalType.GENERAL_HEALTH -> {
                            val totalMeals = planDays.sumOf { it.meals.size }
                            val distinctRecipes = planDays.flatMap { it.meals }.map { it.recipeId }.distinct().size
                            val variety = if (totalMeals > 0) (distinctRecipes * 100 / totalMeals) else 0
                            Text("Emphasis: balanced macros + variety.", color = colorScheme.onSurfaceVariant)
                            Text("Variety score: $variety%", color = colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        }

        if (showWeekMode && advancedWeekAnalyticsExpanded) {
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
                            text = "Meal Completion",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.secondary
                        )
                    }
                    Text(
                        text = "Tracks how many planned meals you completed each day.",
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
                            text = "Macro Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                        IconButton(onClick = { showMacroInfo = true }) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Macro info",
                                tint = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(text = macroLabel, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                    if (!isOnline) {
                        Text(
                            text = "Macro details require internet to fetch recipe nutrition.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    if (completedMacroAvailable) {
                        Text(
                            text = "Planned avg/day: P ${planMetrics.avgProtein}g • C ${planMetrics.avgCarbs}g • F ${planMetrics.avgFats}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Completed avg/day: P ${avgProtein}g • C ${avgCarbs}g • F ${avgFats}g",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    val macroBalance = when {
                        avgCarbs > avgProtein * 1.3 -> "Carb-heavy"
                        avgProtein > avgCarbs * 1.2 -> "Protein-heavy"
                        avgProtein == 0 && avgCarbs == 0 -> "—"
                        else -> "Balanced"
                    }
                    Text(
                        text = "Macro balance: $macroBalance",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    MacroProgressBar(label = "Protein", progress = (avgProtein / 100f).coerceIn(0f, 1f), valueText = "Avg ${avgProtein}g")
                    MacroProgressBar(label = "Carbs", progress = (avgCarbs / 250f).coerceIn(0f, 1f), valueText = "Avg ${avgCarbs}g")
                    MacroProgressBar(label = "Fats", progress = (avgFats / 80f).coerceIn(0f, 1f), valueText = "Avg ${avgFats}g")
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
                    if (!isSelectedDateLoggable) {
                        ProgressJumpToTodayAction(
                            todayIndexInWeek = todayIndexInWeek,
                            onJump = { selectedDayIndex = todayIndexInWeek }
                        )
                        ProgressLoggingPolicyLearnMoreChip(
                            onClick = { showLoggingPolicyInfo = true }
                        )
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(weekDays) { idx, date ->
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
                            text = "Swipe the day chips to switch dates.",
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
                            val reasons = remember(meal.recipeId, planExplanation, recipeCounts, profile.weeklyBudgetPhp) {
                                buildMealReasons(
                                    recipeId = meal.recipeId,
                                    recipeCounts = recipeCounts,
                                    explanation = planExplanation,
                                    budgetPhp = profile.weeklyBudgetPhp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                Column {
                                    Text(meal.title)
                                    if (reasons.isNotEmpty()) {
                                        Text(
                                            text = "Why: " + reasons.joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 2,
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
                                    TextButton(
                                        onClick = { impactDetailsExpanded = !impactDetailsExpanded },
                                        modifier = Modifier.padding(horizontal = 0.dp)
                                    ) {
                                        Text(if (impactDetailsExpanded) "Hide details" else "View details")
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
                        text = "Quick check-in to observe patterns. Decision-support only; not medical treatment.",
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(weekDays) { idx, date ->
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
                            text = "Swipe the day chips to pick a reflection date.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Text("Energy", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { value ->
                            FilterChip(
                                selected = energyLevel == value,
                                onClick = { energyLevel = value },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(value.toString()) }
                            )
                        }
                    }
                    Text("Cravings", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { value ->
                            FilterChip(
                                selected = cravingsLevel == value,
                                onClick = { cravingsLevel = value },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(value.toString()) }
                            )
                        }
                    }
                    Text("Mood", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { value ->
                            FilterChip(
                                selected = moodLevel == value,
                                onClick = { moodLevel = value },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(value.toString()) }
                            )
                        }
                    }
                    Text("Symptoms", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(symptomOptions) { symptom ->
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

        if (showWeekMode) {
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
                    OutlinedButton(
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
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Export Reflections (Local)")
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
                                    TextButton(
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
                                        modifier = Modifier.testTag("progress_retry_all_feedback")
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
                                        TextButton(
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
                                            modifier = Modifier.testTag("progress_retry_feedback_${entry.id}")
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
                        .height(42.dp)
                ) {
                    Text("Done")
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
            title = { Text("Confidence score", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "Heuristic score based on how tightly the plan matches calorie targets, " +
                    "tolerance level used, repeat limits, and restriction complexity. " +
                    "Higher is better."
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
            title = { Text("Macro summary", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "This compares planned averages with what you actually checked off. " +
                    "If you’re offline, macro details may be incomplete."
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
                    "We favor higher‑fiber, balanced meals to support steadier energy. " +
                    "This is guidance only and not medical treatment."
                )
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

private enum class ProgressMode(val label: String) {
    Today("Today"),
    Week("Week");

    companion object {
        fun fromSavedValue(value: String): ProgressMode {
            return values().firstOrNull { it.label.equals(value, ignoreCase = true) } ?: Today
        }
    }
}

@Composable
private fun DialogGotItButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { traversalIndex = 1f }
    ) {
        Text("Got it")
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

private fun weekStartDate(
    timestamp: Long?,
    firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
): LocalDate {
    val zone = ZoneId.systemDefault()
    val base = if (timestamp != null) Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate() else LocalDate.now()
    return base.with(TemporalAdjusters.previousOrSame(firstDay))
}

private fun weekLabelFor(start: LocalDate): String {
    val end = start.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("MMM dd")
    return "${start.format(fmt)} - ${end.format(fmt)}"
}

private fun isInWeek(dateKey: String, weekStart: LocalDate): Boolean {
    val d = LocalDate.parse(dateKey, DateTimeFormatter.ISO_LOCAL_DATE)
    return !d.isBefore(weekStart) && !d.isAfter(weekStart.plusDays(6))
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

private fun computeWeekStats(plan: com.pcosina.app.data.api.GeneratePlanResponse): WeekStats {
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
