@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pcosina.app.ui.screens

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.MealCheckIn
import com.pcosina.app.data.model.PlannerPlannedMeal
import com.pcosina.app.data.model.PlannerPlanResponse
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.PlannedDayCount
import com.pcosina.app.domain.ProgressAdherencePoint
import com.pcosina.app.domain.ProgressCheckInHistoryDay
import com.pcosina.app.domain.ProgressDayStatus
import com.pcosina.app.domain.ProgressSummaryUseCase
import com.pcosina.app.domain.ProgressTrendSummary
import com.pcosina.app.domain.ProgressWeekNodeSummary
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.domain.WeeklyMealSummaryResult
import com.pcosina.app.R
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.PcosinaAvatarBadge
import com.pcosina.app.ui.components.PcosinaDesignIcon
import com.pcosina.app.ui.components.RefinedOverviewCard
import com.pcosina.app.ui.components.RefinedPrimaryButton
import com.pcosina.app.ui.components.RefinedStatusPill
import com.pcosina.app.ui.components.RefinedTabBrandHeader
import com.pcosina.app.ui.components.SharedAvatarHeader
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.theme.PcosinaBlush
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt
import com.pcosina.app.ui.util.rememberIsOnline
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private enum class ProgressNodeState {
    Complete,
    Partial,
    Pending,
    Future,
}

private data class ProgressWeekNode(
    val label: String,
    val valueText: String,
    val state: ProgressNodeState,
)

private data class ProgressChartPoint(
    val label: String,
    val value: Float,
    val valueText: String,
    val color: Color,
)

private data class ProgressSpendStat(
    val label: String,
    val value: String,
)

private data class WeeklyMealSummary(
    val plannedMeals: Int,
    val completedMeals: Int,
    val adherencePercent: Int,
    val chartPoints: List<ProgressChartPoint>,
)

private data class ProgressWeeklyHighlight(
    val label: String,
    val title: String,
    val body: String,
    val color: Color,
)

private data class ProgressSymptomMetric(
    val label: String,
    val valueText: String,
    val progress: Float,
    val color: Color,
)

private data class ProgressDraftInputs(
    val energyLevel: Int = 3,
    val moodLevel: Int = 3,
    val cravingsLevel: Int = 3,
    val noteText: String = "",
    val weightInput: String = "",
    val weeklyJournalDraft: String = "",
    val weeklySpendInput: String = "",
    val selectedFeedbackTags: Set<String> = emptySet(),
)

private enum class ProgressCalendarStatus {
    Completed,
    Partial,
    Missed,
    NoRecord,
    FutureLocked,
    Today,
}

private data class ProgressCalendarDay(
    val date: LocalDate,
    val isToday: Boolean,
    val isFuture: Boolean,
    val hasLog: Boolean,
    val hasMealCheckIn: Boolean,
    val hasReflection: Boolean,
    val hasWeeklyProgress: Boolean,
    val completedMealCount: Int,
    val plannedMealCount: Int,
    val completedMeals: List<String> = emptyList(),
    val missedMeals: List<String> = emptyList(),
    val pendingMeals: List<String> = emptyList(),
    val checkInSummaries: List<String> = emptyList(),
    val reflectionSummaries: List<String> = emptyList(),
    val status: ProgressCalendarStatus,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressRefinedScreen(
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
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val profile by userViewModel.userProfile.collectAsState()
    val planState by mealPlanViewModel.uiState.collectAsState()
    val planMetrics by mealPlanViewModel.planMetrics.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val weeklyJournal by progressViewModel.weeklyJournal.collectAsState()
    val weeklySpend by progressViewModel.weeklySpend.collectAsState()
    val planFeedbackTags by progressViewModel.planFeedbackTags.collectAsState()
    val today = LocalDate.now()
    val currentPlan = remember(planState, planHistory, activePlanId) {
        (planState as? MealPlanUiState.Success)?.response
            ?: planHistory.firstOrNull { it.id == activePlanId }?.response
            ?: planHistory.maxByOrNull { it.generatedAt }?.response
    }
    val progressSummaryUseCase = remember { ProgressSummaryUseCase() }
    val weekStart = remember(activeWeekStart, currentPlan?.weekLabel) {
        activeWeekStart?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        } ?: today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
    }
    val todayToken = today.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).lowercase(Locale.ENGLISH)
    val todayPlan = remember(currentPlan, todayToken) {
        currentPlan?.days?.firstOrNull { it.dayLabel.lowercase(Locale.ENGLISH) == todayToken }
    }
    val todayLog = logs[today.format(DateTimeFormatter.ISO_LOCAL_DATE)]
    val completedMealsToday = todayPlan?.meals?.count { meal ->
        todayLog?.completedMealIds?.contains(ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)) == true ||
            todayLog?.completedMealIds?.contains(meal.recipeId) == true
    } ?: 0
    val plannedMealsToday = todayPlan?.meals?.size ?: 0
    val plannedDayCounts = remember(currentPlan) {
        currentPlan?.days.orEmpty().map { PlannedDayCount(dayLabel = it.dayLabel, mealCount = it.meals.size) }
    }
    val weekNodes = remember(plannedDayCounts, logs, weekStart, today) {
        runCatching {
            progressSummaryUseCase.buildWeekNodes(plannedDayCounts, logs, weekStart, today)
                .map { it.toUiWeekNode() }
        }.onFailure { error ->
            Log.e("ProgressRefinedScreen", "Failed to compute progress macro summary safely.", error)
        }.getOrElse {
            emptyList()
        }
    }
    val weeklyMealSummary = remember(plannedDayCounts, logs, weekStart, today) {
        runCatching {
            progressSummaryUseCase.buildWeeklyMealSummary(plannedDayCounts, logs, weekStart, today)
                .toUiWeeklyMealSummary()
        }.onFailure { error ->
            Log.e("ProgressRefinedScreen", "Failed to compute progress macro summary safely.", error)
        }.getOrElse {
            WeeklyMealSummary(
                plannedMeals = 0,
                completedMeals = 0,
                adherencePercent = 0,
                chartPoints = emptyList()
            )
        }
    }
    val checkInHistory = remember(logs, today) {
        progressSummaryUseCase.buildCheckInHistory(logs, today)
    }
    val trendSummary = remember(logs, today) {
        progressSummaryUseCase.buildFourWeekTrendSummary(logs, today)
    }
    var showReflectionDialog by remember { mutableStateOf(false) }
    var showWeeklyReviewDialog by remember { mutableStateOf(false) }
    var showWeeklyHighlightsDialog by remember { mutableStateOf(false) }
    var weeklyAdherenceExpanded by remember { mutableStateOf(false) }
    var weeklySavingsExpanded by remember { mutableStateOf(false) }
    var averageMacrosExpanded by remember { mutableStateOf(false) }
    var symptomTrendsExpanded by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var visibleCalendarMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedCalendarDate by remember { mutableStateOf(today) }
    val weekStartKey = remember(weekStart) { weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val weightUnitLabel = if (profile.weightUnit == UnitConverter.WEIGHT_LB) "lb" else "kg"
    val draftInputs = remember(
        todayLog?.energyLevel,
        todayLog?.moodLevel,
        todayLog?.cravingsLevel,
        todayLog?.symptomsNote,
        todayLog?.weightKg,
        profile.weightUnit,
        weeklyJournal,
        weeklySpend,
        planFeedbackTags,
        weekStartKey
    ) {
        runCatching {
            val displayWeight = todayLog?.weightKg?.let { value ->
                val displayValue = if (profile.weightUnit == UnitConverter.WEIGHT_LB) {
                    UnitConverter.kgToLb(value)
                } else {
                    value
                }
                String.format(Locale.ENGLISH, "%.1f", displayValue)
            }.orEmpty()
            ProgressDraftInputs(
                energyLevel = todayLog?.energyLevel ?: 3,
                moodLevel = todayLog?.moodLevel ?: 3,
                cravingsLevel = todayLog?.cravingsLevel ?: 3,
                noteText = todayLog?.symptomsNote.orEmpty(),
                weightInput = displayWeight,
                weeklyJournalDraft = weeklyJournal,
                weeklySpendInput = weeklySpend?.toString().orEmpty(),
                selectedFeedbackTags = planFeedbackTags.toSet()
            )
        }.onFailure { error ->
            Log.e("ProgressRefinedScreen", "Failed to hydrate progress inputs safely.", error)
        }.getOrElse {
            ProgressDraftInputs()
        }
    }
    var energyLevel by remember(today.toString()) { mutableStateOf(draftInputs.energyLevel) }
    var moodLevel by remember(today.toString()) { mutableStateOf(draftInputs.moodLevel) }
    var cravingsLevel by remember(today.toString()) { mutableStateOf(draftInputs.cravingsLevel) }
    var noteText by remember(today.toString()) { mutableStateOf(draftInputs.noteText) }
    var weightInput by remember(today.toString(), profile.weightUnit) { mutableStateOf(draftInputs.weightInput) }
    var weeklyJournalDraft by remember(weekStartKey) { mutableStateOf(draftInputs.weeklyJournalDraft) }
    var weeklySpendInput by remember(weekStartKey) { mutableStateOf(draftInputs.weeklySpendInput) }
    var selectedFeedbackTags by remember(weekStartKey) { mutableStateOf(draftInputs.selectedFeedbackTags) }
    val planFeedbackOptions = remember { listOf("Too repetitive", "Too expensive", "Too hard to cook") }

    LaunchedEffect(
        draftInputs.energyLevel,
        draftInputs.moodLevel,
        draftInputs.cravingsLevel,
        draftInputs.noteText,
        draftInputs.weightInput,
        today.toString(),
        profile.weightUnit
    ) {
        energyLevel = draftInputs.energyLevel
        moodLevel = draftInputs.moodLevel
        cravingsLevel = draftInputs.cravingsLevel
        noteText = draftInputs.noteText
        weightInput = draftInputs.weightInput
    }
    LaunchedEffect(weeklyJournal, weekStartKey) {
        weeklyJournalDraft = weeklyJournal
    }
    LaunchedEffect(weeklySpend, weekStartKey) {
        weeklySpendInput = weeklySpend?.toString().orEmpty()
    }
    LaunchedEffect(planFeedbackTags, weekStartKey) {
        selectedFeedbackTags = planFeedbackTags.toSet()
    }

    if (showReflectionDialog) {
        AlertDialog(
            onDismissRequest = { showReflectionDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedWeight = weightInput.trim().takeIf { it.isNotBlank() }?.toFloatOrNull()
                        val normalizedWeightKg = parsedWeight?.let { value ->
                            if (profile.weightUnit == UnitConverter.WEIGHT_LB) {
                                UnitConverter.lbToKg(value)
                            } else {
                                value
                            }
                        }
                        if (progressViewModel.saveReflection(
                                date = today,
                                energyLevel = energyLevel,
                                cravingsLevel = cravingsLevel,
                                moodLevel = moodLevel,
                                symptomTags = todayLog?.symptomTags.orEmpty(),
                                symptomsNote = noteText
                            )
                        ) {
                            if (weightInput.isBlank()) {
                                feedbackMessage = "Today's check-in was saved."
                            } else if (normalizedWeightKg != null && progressViewModel.setWeight(today, normalizedWeightKg)) {
                                feedbackMessage = "Today's check-in and weight were saved."
                            } else {
                                feedbackMessage = "Check-in saved. Weight was not updated because the value was invalid."
                            }
                        } else {
                            feedbackMessage = "Check-ins can only be saved for today."
                        }
                        showReflectionDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showReflectionDialog = false }) { Text("Cancel") }
            },
            title = { Text("Today's check-in") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressLevelPicker(
                        label = "Energy",
                        value = energyLevel,
                        onSelect = { energyLevel = it }
                    )
                    ProgressLevelPicker(
                        label = "Mood",
                        value = moodLevel,
                        onSelect = { moodLevel = it }
                    )
                    ProgressLevelPicker(
                        label = "Cravings",
                        value = cravingsLevel,
                        onSelect = { cravingsLevel = it }
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes") },
                        maxLines = 3
                    )
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Weight ($weightUnitLabel, optional)") },
                        singleLine = true
                    )
                }
            }
        )
    }

    if (showWeeklyReviewDialog) {
        AlertDialog(
            onDismissRequest = { showWeeklyReviewDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedSpend = weeklySpendInput.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
                        if (weeklySpendInput.isNotBlank() && parsedSpend == null) {
                            feedbackMessage = "Weekly spend must be a whole number in pesos."
                        } else {
                            progressViewModel.saveWeeklyJournal(weekStartKey, weeklyJournalDraft.trim())
                            progressViewModel.saveWeeklySpend(weekStartKey, parsedSpend)
                            val existing = planFeedbackTags.toSet()
                            val toToggle = (existing - selectedFeedbackTags) + (selectedFeedbackTags - existing)
                            toToggle.forEach { tag -> progressViewModel.togglePlanFeedbackTag(tag) }
                            feedbackMessage = "Weekly review updated."
                            showWeeklyReviewDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showWeeklyReviewDialog = false }) { Text("Cancel") }
            },
            title = { Text("Weekly review") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = weeklyJournalDraft,
                        onValueChange = { weeklyJournalDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Weekly notes") },
                        maxLines = 3
                    )
                    OutlinedTextField(
                        value = weeklySpendInput,
                        onValueChange = { weeklySpendInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Actual weekly spend (PHP)") },
                        singleLine = true
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Help tune your next plan",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = PcosinaDeepRose
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            planFeedbackOptions.forEach { tag ->
                                val selected = selectedFeedbackTags.contains(tag)
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) PcosinaPink else Color.White,
                                    border = BorderStroke(1.dp, if (selected) PcosinaPink else PcosinaMuted.copy(alpha = 0.32f)),
                                    modifier = Modifier.clickable {
                                        selectedFeedbackTags = if (selected) {
                                            selectedFeedbackTags - tag
                                        } else {
                                            selectedFeedbackTags + tag
                                        }
                                    }
                                ) {
                                    Text(
                                        text = tag,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) Color.White else PcosinaDeepRose
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        val scrollState = rememberScrollState()
        val compact = maxHeight < 760.dp || maxWidth < 390.dp
        val budgetTarget = profile.weeklyBudgetPhp.takeIf { it > 0 }
        val weeklySpendValue = weeklySpend
        val estimatedWeeklyCost = currentPlan?.explanation?.estimatedWeeklyCost
        val bmiValue = remember(profile.weightKg, profile.heightCm) {
            HealthMetrics.bmi(profile.weightKg, profile.heightCm)
        }
        val bmiLabel = remember(bmiValue) {
            if (bmiValue > 0) {
                String.format(Locale.ENGLISH, "%.1f", bmiValue)
            } else {
                "--"
            }
        }
        val bmiCategory = remember(bmiValue) { HealthMetrics.bmiCategory(bmiValue) }
        val weeklySavings = when {
            budgetTarget != null && weeklySpendValue != null -> budgetTarget - weeklySpendValue
            budgetTarget != null && estimatedWeeklyCost != null -> budgetTarget - estimatedWeeklyCost
            else -> null
        }
        val savingsTileSubtitle = when {
            weeklySpendValue != null -> "actual spend vs budget"
            estimatedWeeklyCost != null -> "estimated cost vs budget"
            budgetTarget != null -> "budget ready for comparison"
            else -> "add a budget to track savings"
        }
        val savingsSummaryLabel = when {
            budgetTarget == null -> "Set a weekly budget to compare this plan against it."
            weeklySpendValue != null && weeklySavings != null && weeklySavings >= 0 -> "Saved ₱$weeklySavings this week."
            weeklySpendValue != null && weeklySavings != null -> "Over budget by ₱${kotlin.math.abs(weeklySavings)} this week."
            estimatedWeeklyCost != null && weeklySavings != null && weeklySavings >= 0 -> "Current plan is about ₱$weeklySavings under budget."
            estimatedWeeklyCost != null && weeklySavings != null -> "Current plan estimate is ₱${kotlin.math.abs(weeklySavings)} over budget."
            else -> "Add weekly spend to compare actual savings."
        }
        val spendStats = remember(budgetTarget, estimatedWeeklyCost, weeklySpendValue) {
            when {
                weeklySpendValue != null -> buildList {
                    add(ProgressSpendStat(label = "Actual spend", value = "₱$weeklySpendValue"))
                    estimatedWeeklyCost?.let { add(ProgressSpendStat(label = "Estimated cost", value = "₱$it")) }
                    budgetTarget?.let { add(ProgressSpendStat(label = "Weekly budget", value = "₱$it")) }
                }
                estimatedWeeklyCost != null -> buildList {
                    add(ProgressSpendStat(label = "Estimated cost", value = "₱$estimatedWeeklyCost"))
                    budgetTarget?.let { add(ProgressSpendStat(label = "Weekly budget", value = "₱$it")) }
                }
                budgetTarget != null -> listOf(
                    ProgressSpendStat(label = "Weekly budget", value = "₱$budgetTarget")
                )
                else -> emptyList()
            }
        }
        val savingsChartPoints = remember(budgetTarget, estimatedWeeklyCost, weeklySpendValue) {
            buildList {
                budgetTarget?.let {
                    add(
                        ProgressChartPoint(
                            label = "Budget",
                            value = it.toFloat(),
                            valueText = "₱$it",
                            color = PcosinaSoftPink
                        )
                    )
                }
                estimatedWeeklyCost?.let {
                    add(
                        ProgressChartPoint(
                            label = "Plan",
                            value = it.toFloat(),
                            valueText = "₱$it",
                            color = Color(0xFFFFB171)
                        )
                    )
                }
                weeklySpendValue?.let {
                    add(
                        ProgressChartPoint(
                            label = "Actual",
                            value = it.toFloat(),
                            valueText = "₱$it",
                            color = if (budgetTarget != null && it > budgetTarget) Color(0xFFE2526E) else PcosinaPink
                        )
                    )
                }
            }
        }
        val weeklySavingsValue = when {
            weeklySavings != null && weeklySavings >= 0 -> "₱$weeklySavings"
            weeklySavings != null -> "-₱${kotlin.math.abs(weeklySavings)}"
            else -> "--"
        }
        val mealsDoneLabel = "${weeklyMealSummary.completedMeals}/${weeklyMealSummary.plannedMeals}"
        val weeklyHighlights = remember(
            weeklySavings,
            weeklySpendValue,
            estimatedWeeklyCost,
            budgetTarget,
            planMetrics,
            weeklyMealSummary,
            weekNodes,
            logs,
        ) {
            buildProgressWeeklyHighlights(
                weeklySavings = weeklySavings,
                weeklySpendValue = weeklySpendValue,
                estimatedWeeklyCost = estimatedWeeklyCost,
                budgetTarget = budgetTarget,
                planMetrics = planMetrics,
                weeklyMealSummary = weeklyMealSummary,
                weekNodes = weekNodes,
                logs = logs,
            )
        }
        val symptomMetrics = remember(trendSummary) {
            buildProgressSymptomMetrics(trendSummary)
        }
        val progressCalendarDays = remember(visibleCalendarMonth, logs, weekStart, weeklyJournal, weeklySpend, currentPlan, today) {
            buildProgressCalendarDays(
                visibleMonth = visibleCalendarMonth,
                logs = logs,
                weekStart = weekStart,
                weeklyJournal = weeklyJournal,
                weeklySpend = weeklySpend,
                currentPlan = currentPlan,
                today = today,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            RefinedTabBrandHeader(
                online = isOnline,
                onSettings = { onNavigateToRoute(Routes.Settings) },
                onSupport = { onNavigateToRoute(Routes.Notifications) },
                compact = compact,
                avatarId = profile.avatarId
            )

            SharedAvatarHeader(
                title = "Progress",
                subtitle = "Track meal adherence and self-reported progress.",
                avatarId = profile.avatarId,
                dateLabel = today.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)),
                compact = compact,
            )

            ProgressHeadlineCard(
                bmiLabel = bmiLabel,
                bmiCategory = bmiCategory,
                mealsDoneLabel = mealsDoneLabel,
                weeklyMealSummary = weeklyMealSummary,
                compact = compact
            )

            ProgressWeeklyHighlightsLauncher(
                weekStart = weekStart,
                weeklyHighlights = weeklyHighlights,
                onClick = { showWeeklyHighlightsDialog = true },
                compact = compact
            )

            ProgressCalendarCard(
                visibleMonth = visibleCalendarMonth,
                today = today,
                selectedDate = selectedCalendarDate,
                days = progressCalendarDays,
                weeklyJournal = weeklyJournal,
                weeklySpend = weeklySpend,
                onPreviousMonth = {
                    val nextMonth = visibleCalendarMonth.minusMonths(1)
                    visibleCalendarMonth = nextMonth
                    selectedCalendarDate = nextMonth.atDay(1)
                },
                onNextMonth = {
                    val nextMonth = visibleCalendarMonth.plusMonths(1)
                    visibleCalendarMonth = nextMonth
                    selectedCalendarDate = if (nextMonth == YearMonth.from(today)) today else nextMonth.atDay(1)
                },
                onSelectDate = { selectedCalendarDate = it },
                compact = compact,
            )

            if (!feedbackMessage.isNullOrBlank()) {
                RefinedOverviewCard(
                    containerColor = Color(0xFFF3FFF7),
                    borderColor = PcosinaPink.copy(alpha = 0.18f),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = feedbackMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PcosinaDeepRose
                    )
                }
            }

            ProgressDropdownCard(
                title = "Weekly adherence",
                value = "${weeklyMealSummary.adherencePercent}%",
                subtitle = "$mealsDoneLabel meals complete this week.",
                expanded = weeklyAdherenceExpanded,
                onToggle = { weeklyAdherenceExpanded = !weeklyAdherenceExpanded },
                compact = compact,
            ) {
                ProgressWeeklyCard(
                    weekNodes = weekNodes,
                    weeklyMealSummary = weeklyMealSummary,
                    compact = compact
                )
            }

            ProgressDropdownCard(
                title = "Weekly savings",
                value = weeklySavingsValue,
                subtitle = savingsTileSubtitle,
                expanded = weeklySavingsExpanded,
                onToggle = { weeklySavingsExpanded = !weeklySavingsExpanded },
                compact = compact,
            ) {
                ProgressSavingsCard(
                    value = weeklySavingsValue,
                    subtitle = savingsTileSubtitle,
                    summary = savingsSummaryLabel,
                    chartPoints = savingsChartPoints,
                    spendStats = spendStats,
                    compact = compact
                )
            }

            ProgressDropdownCard(
                title = "Average macros",
                value = "${planMetrics.avgProtein}g protein",
                subtitle = "Plan averages and target balance.",
                expanded = averageMacrosExpanded,
                onToggle = { averageMacrosExpanded = !averageMacrosExpanded },
                compact = compact,
            ) {
                ProgressInsightsCard(
                    planMetrics = planMetrics,
                    currentPlan = currentPlan,
                    weeklyMealSummary = weeklyMealSummary,
                    planFeedbackTags = planFeedbackTags,
                    modifier = Modifier.fillMaxWidth(),
                    compact = compact
                )
            }

            ProgressDropdownCard(
                title = "Symptom trends",
                value = "${trendSummary.checkInDays} days",
                subtitle = trendSummary.headline,
                expanded = symptomTrendsExpanded,
                onToggle = { symptomTrendsExpanded = !symptomTrendsExpanded },
                compact = compact,
            ) {
                ProgressSymptomManagementCard(
                    metrics = symptomMetrics,
                    summary = trendSummary,
                    compact = compact
                )
            }

            ProgressBottomCtaCard(
                completedMealsToday = completedMealsToday,
                plannedMealsToday = plannedMealsToday,
                weeklyJournal = weeklyJournal,
                onCheckIn = { showReflectionDialog = true },
                onReviewWeek = { showWeeklyReviewDialog = true },
                compact = compact
            )

            ProgressSupportCtaCard(
                onSupport = { onNavigateToRoute(Routes.Ipo) },
                compact = compact
            )
        }

        if (showWeeklyHighlightsDialog) {
            Dialog(onDismissRequest = { showWeeklyHighlightsDialog = false }) {
                ProgressWeeklyHighlightsCard(
                    weekStart = weekStart,
                    weekNodes = weekNodes,
                    weeklyHighlights = weeklyHighlights,
                    avatarId = profile.avatarId,
                    compact = compact,
                    onDismiss = { showWeeklyHighlightsDialog = false }
                )
            }
        }
    }
}

@Composable
private fun ProgressCalendarCard(
    visibleMonth: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    days: List<ProgressCalendarDay>,
    weeklyJournal: String,
    weeklySpend: Int?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    compact: Boolean,
) {
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH) }
    val summaryFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH) }
    val dayByDate = remember(days) { days.associateBy { it.date } }
    val selectedDay = dayByDate[selectedDate] ?: ProgressCalendarDay(
        date = selectedDate,
        isToday = selectedDate == today,
        isFuture = selectedDate.isAfter(today),
        hasLog = false,
        hasMealCheckIn = false,
        hasReflection = false,
        hasWeeklyProgress = false,
        completedMealCount = 0,
        plannedMealCount = 0,
        status = if (selectedDate.isAfter(today)) ProgressCalendarStatus.FutureLocked else ProgressCalendarStatus.NoRecord,
    )
    var detailsExpanded by remember(selectedDate) { mutableStateOf(false) }
    val selectedDayHasDetails = selectedDay.completedMeals.isNotEmpty() ||
        selectedDay.missedMeals.isNotEmpty() ||
        selectedDay.pendingMeals.isNotEmpty() ||
        selectedDay.checkInSummaries.isNotEmpty() ||
        selectedDay.reflectionSummaries.isNotEmpty() ||
        selectedDay.hasWeeklyProgress
    val cells = remember(visibleMonth) { buildMonthCalendarCells(visibleMonth) }

    RefinedOverviewCard(
        containerColor = Color(0xFFFFF3F6),
        borderColor = PcosinaPink.copy(alpha = 0.22f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.pcosina_calendar),
                contentDescription = null,
                modifier = Modifier.size(if (compact) 54.dp else 64.dp),
                contentScale = ContentScale.Fit,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "History Calendar",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                Text(
                    text = "Tap a date to review meal adherence and self-reported progress.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPreviousMonth) {
                Text("<", fontWeight = FontWeight.ExtraBold, color = PcosinaPink)
            }
            Text(
                text = visibleMonth.format(monthFormatter),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
            )
            TextButton(onClick = onNextMonth) {
                Text(">", fontWeight = FontWeight.ExtraBold, color = PcosinaPink)
            }
        }

        ProgressCalendarGrid(
            cells = cells,
            dayByDate = dayByDate,
            today = today,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
            compact = compact,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProgressCalendarLegendDot(color = ProgressCalendarStatus.Completed.legendColor(), label = "All meals marked")
            ProgressCalendarLegendDot(color = ProgressCalendarStatus.Partial.legendColor(), label = "Partly recorded")
            ProgressCalendarLegendDot(color = ProgressCalendarStatus.Missed.legendColor(), label = "No meals marked")
            ProgressCalendarLegendDot(color = ProgressCalendarStatus.NoRecord.legendColor(), label = "No record")
            ProgressCalendarLegendDot(color = ProgressCalendarStatus.FutureLocked.legendColor(), label = "Future locked")
            ProgressCalendarLegendDot(color = PcosinaPink, label = "Check-in/reflection")
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.9f),
            border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.16f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = selectedDate.format(summaryFormatter),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                RefinedStatusPill(
                    text = selectedDay.status.displayLabel(),
                    containerColor = selectedDay.status.legendColor().copy(alpha = 0.16f),
                    contentColor = selectedDay.status.contentColor(),
                )
                Text(
                    text = selectedDay.insightMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted,
                )
                Text(
                    text = buildString {
                        if (selectedDay.plannedMealCount > 0) {
                            append("${selectedDay.completedMealCount}/${selectedDay.plannedMealCount} planned meals marked as eaten")
                        } else if (selectedDay.hasLog) {
                            append("Self-reported progress exists, but this date is not linked to the current planned-meal list")
                        } else {
                            append("No progress recorded for this date")
                        }
                        if (selectedDay.isFuture) append(". This date is unavailable until it arrives")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PcosinaDeepRose,
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = selectedDayHasDetails) {
                            detailsExpanded = !detailsExpanded
                        },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selectedDayHasDetails) {
                        PcosinaBlush.copy(alpha = 0.72f)
                    } else {
                        Color(0xFFF4EEF1)
                    },
                    border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.14f)),
                ) {
                    Text(
                        text = when {
                            !selectedDayHasDetails -> "No additional details for this date"
                            detailsExpanded -> "Hide details"
                            else -> "Show details"
                        },
                        modifier = Modifier.padding(vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (selectedDayHasDetails) PcosinaDeepRose else PcosinaMuted,
                        textAlign = TextAlign.Center,
                    )
                }

                if (detailsExpanded && selectedDayHasDetails) {
                    if (selectedDay.completedMeals.isNotEmpty()) {
                        ProgressCalendarTextGroup(
                            title = "Meals marked as eaten",
                            items = selectedDay.completedMeals,
                        )
                    }
                    if (selectedDay.missedMeals.isNotEmpty()) {
                        ProgressCalendarTextGroup(
                            title = "Meals not marked as eaten",
                            items = selectedDay.missedMeals,
                        )
                    }
                    if (selectedDay.pendingMeals.isNotEmpty()) {
                        ProgressCalendarTextGroup(
                            title = "Still open today",
                            items = selectedDay.pendingMeals,
                        )
                    }
                    if (selectedDay.checkInSummaries.isNotEmpty()) {
                        ProgressCalendarTextGroup(
                            title = "Self-reported meal check-ins",
                            items = selectedDay.checkInSummaries,
                        )
                    }
                    if (selectedDay.reflectionSummaries.isNotEmpty()) {
                        ProgressCalendarTextGroup(
                            title = "Reflection notes",
                            items = selectedDay.reflectionSummaries,
                        )
                    }
                    if (selectedDay.hasWeeklyProgress) {
                        Text(
                            text = buildString {
                                append("Weekly self-reported progress record available")
                                weeklySpend?.let { append(" with ₱$it actual spend") }
                                if (weeklyJournal.isNotBlank()) append(". Weekly note saved.")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = PcosinaDeepRose,
                        )
                    }
                }
                Text(
                    text = when {
                        selectedDay.isFuture -> "Future days are locked and cannot be edited."
                        selectedDay.isToday -> "Today can still be updated through the existing meal logging flow when sequence rules allow it."
                        else -> "Past days are shown for history review only. Meal logging remains today-only."
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                    color = PcosinaMuted,
                )
            }
        }
    }
}

@Composable
private fun ProgressCalendarTextGroup(
    title: String,
    items: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose,
        )
        items.take(4).forEach { item ->
            Text(
                text = "- $item",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
            )
        }
        if (items.size > 4) {
            Text(
                text = "${items.size - 4} more item(s) recorded.",
                style = MaterialTheme.typography.labelSmall,
                color = PcosinaMuted,
            )
        }
    }
}

@Composable
private fun ProgressCalendarGrid(
    cells: List<LocalDate?>,
    dayByDate: Map<LocalDate, ProgressCalendarDay>,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    compact: Boolean,
) {
    val weekLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaMuted,
                )
            }
        }
        cells.chunked(7).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { date ->
                    if (date == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (compact) 38.dp else 44.dp)
                        )
                    } else {
                        val day = dayByDate[date]
                        val selected = date == selectedDate
                        val isToday = date == today
                        ProgressCalendarDayCell(
                            date = date,
                            day = day,
                            selected = selected,
                            isToday = isToday,
                            onClick = { onSelectDate(date) },
                            modifier = Modifier.weight(1f),
                            compact = compact,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCalendarDayCell(
    date: LocalDate,
    day: ProgressCalendarDay?,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    val status = day?.status ?: if (date.isAfter(LocalDate.now())) ProgressCalendarStatus.FutureLocked else ProgressCalendarStatus.NoRecord
    val hasActivityMarker = day?.hasMealCheckIn == true || day?.hasReflection == true || day?.hasWeeklyProgress == true
    Surface(
        modifier = modifier
            .height(if (compact) 38.dp else 44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = when {
            selected -> PcosinaPink
            status == ProgressCalendarStatus.FutureLocked -> Color(0xFFEDE8EA)
            status == ProgressCalendarStatus.NoRecord -> Color.White.copy(alpha = 0.62f)
            else -> status.legendColor().copy(alpha = 0.15f)
        },
        border = BorderStroke(
            1.dp,
            when {
                selected -> PcosinaPink
                isToday -> PcosinaDeepRose.copy(alpha = 0.5f)
                status != ProgressCalendarStatus.NoRecord -> status.legendColor().copy(alpha = 0.45f)
                else -> PcosinaMuted.copy(alpha = 0.12f)
            }
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = when {
                    selected -> Color.White
                    status == ProgressCalendarStatus.FutureLocked -> PcosinaMuted.copy(alpha = 0.72f)
                    else -> PcosinaDeepRose
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressCalendarMarker(color = if (selected) Color.White else status.legendColor())
                if (hasActivityMarker) {
                    ProgressCalendarMarker(color = if (selected) Color.White else PcosinaPink)
                }
            }
        }
    }
}

@Composable
private fun ProgressCalendarMarker(color: Color) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun ProgressCalendarLegendDot(
    color: Color,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressCalendarMarker(color = color)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = PcosinaMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressHeadlineCard(
    bmiLabel: String,
    bmiCategory: String,
    mealsDoneLabel: String,
    weeklyMealSummary: WeeklyMealSummary,
    compact: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressBmiCard(
                bmiLabel = bmiLabel,
                bmiCategory = bmiCategory,
                compact = true,
                modifier = Modifier.weight(1f)
            )
            ProgressMealSummaryCard(
                mealsDoneLabel = mealsDoneLabel,
                weeklyMealSummary = weeklyMealSummary,
                compact = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ProgressHeroMetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 9.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = PcosinaDeepRose.copy(alpha = 0.84f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgressWeeklyHighlightsLauncher(
    weekStart: LocalDate,
    weeklyHighlights: List<ProgressWeeklyHighlight>,
    onClick: () -> Unit,
    compact: Boolean,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH) }
    val leadHighlight = weeklyHighlights.firstOrNull()
    RefinedOverviewCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        containerColor = Color(0xFFFFF8FB),
        borderColor = PcosinaPink.copy(alpha = 0.24f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = PcosinaPink.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.24f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.pcosina_weekly_highlight),
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Weekly Highlights",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Text(
                    text = leadHighlight?.let { "${it.title}: ${it.body}" }
                        ?: "${weekStart.format(formatter)} to ${weekStart.plusDays(6).format(formatter)} • ${weeklyHighlights.size} insights",
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RefinedStatusPill(
                text = "Open",
                containerColor = PcosinaPink,
                contentColor = Color.White
            )
        }
    }
}

@Composable
private fun ProgressWeeklyHighlightsCard(
    weekStart: LocalDate,
    weekNodes: List<ProgressWeekNode>,
    weeklyHighlights: List<ProgressWeeklyHighlight>,
    avatarId: String,
    compact: Boolean,
    onDismiss: (() -> Unit)? = null,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH) }
    val completedDays = weekNodes.count { it.state == ProgressNodeState.Complete }
    val leadHighlight = weeklyHighlights.firstOrNull()
    RefinedOverviewCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 405.dp),
        containerColor = Color.White,
        borderColor = PcosinaSoftPink.copy(alpha = 0.55f),
        contentPadding = PaddingValues(if (compact) 14.dp else 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = PcosinaSoftPink.copy(alpha = 0.42f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "How Your Week Went",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black,
                        ),
                    )
                    Text(
                        text = "From ${weekStart.format(formatter)} to ${weekStart.plusDays(6).format(formatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PcosinaMuted,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        weekNodes.take(7).forEach { node ->
                            val complete = node.state == ProgressNodeState.Complete
                            Surface(
                                modifier = Modifier.size(if (compact) 30.dp else 34.dp),
                                shape = CircleShape,
                                color = if (complete) PcosinaPink else Color(0xFFFFD9E1),
                                border = BorderStroke(1.dp, PcosinaDeepRose.copy(alpha = 0.45f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (complete) "OK" else node.valueText.ifBlank { "--" },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = if (complete) Color.White else PcosinaDeepRose,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = if (completedDays > 0) {
                            "You completed $completedDays day(s). Keep the same energy into next week."
                        } else {
                            "Start with one logged meal or check-in so next week has a clearer story."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = PcosinaDeepRose,
                    )
                }
            }

            Text(
                text = "Weekly Highlights",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PcosinaAvatarBadge(
                    avatarId = avatarId,
                    size = if (compact) 64.dp else 74.dp,
                    shadowElevation = if (compact) 3.dp else 6.dp,
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(22.dp),
                    color = PcosinaSoftPink.copy(alpha = 0.86f),
                ) {
                    Text(
                        text = leadHighlight?.let {
                            "This recap uses your meals, budget, and check-ins. ${it.body}"
                        } ?: "This recap uses your meals, budget, and check-ins once you start logging.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PcosinaDeepRose,
                    )
                }
            }
            weeklyHighlights.forEach { highlight ->
                ProgressWeeklyHighlightRow(highlight)
            }
            onDismiss?.let {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PcosinaPink,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = it)
                ) {
                    Text(
                        text = "Got it!",
                        modifier = Modifier.padding(vertical = 13.dp),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressFourWeekTrendCard(summary: ProgressTrendSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF8FFF9),
        border = BorderStroke(1.dp, Color(0xFF009A57).copy(alpha = 0.22f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "4-week local trends",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF009A57),
            )
            Text(
                text = summary.headline,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
            )
            Text(
                text = summary.detail,
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressTrendMetric("Days", summary.checkInDays.toString(), Modifier.weight(1f))
                ProgressTrendMetric("Energy", summary.averageEnergy.formatTrendAverage(), Modifier.weight(1f))
                ProgressTrendMetric("Mood", summary.averageMood.formatTrendAverage(), Modifier.weight(1f))
                ProgressTrendMetric("Cravings", summary.averageCravings.formatTrendAverage(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProgressTrendMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaSoftPink.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = PcosinaMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressWeeklyHighlightRow(highlight: ProgressWeeklyHighlight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = PcosinaSoftPink,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = highlight.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = highlight.color,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = highlight.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = highlight.color,
                ),
            )
            Text(
                text = highlight.body,
                style = MaterialTheme.typography.bodySmall,
                color = highlight.color,
            )
        }
    }
}

@Composable
private fun ProgressDropdownCard(
    title: String,
    value: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    RefinedOverviewCard(
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (value.isNotBlank()) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose,
                        textAlign = TextAlign.End
                    )
                }
                RefinedStatusPill(
                    text = if (expanded) "Hide details" else "Show details",
                    containerColor = Color(0xFFFFF2F5),
                    contentColor = PcosinaDeepRose
                )
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ProgressWeeklyCard(
    weekNodes: List<ProgressWeekNode>,
    weeklyMealSummary: WeeklyMealSummary,
    compact: Boolean,
) {
    val plannedMeals = weeklyMealSummary.plannedMeals.coerceAtLeast(1)
    val adherencePercent = ((weeklyMealSummary.completedMeals.toFloat() / plannedMeals.toFloat()) * 100f)
        .toInt()
        .coerceIn(0, 100)
    val adherenceSummary = when {
        adherencePercent >= 80 -> "Strong logging week. Your completed meals give the next plan better context."
        weeklyMealSummary.completedMeals > 0 -> "Your meal logs are building a usable week-by-week pattern."
        else -> "Log meals or check in so this weekly pattern can reflect your actual routine."
    }
    RefinedOverviewCard(
        containerColor = Color(0xFFFFE2E8),
        borderColor = PcosinaPink.copy(alpha = 0.28f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.56f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.pcosina_weekly_adherence),
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Weekly adherence",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Text(
                    text = adherenceSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaDeepRose.copy(alpha = 0.76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        weekNodes.forEachIndexed { index, node ->
            val point = weeklyMealSummary.chartPoints.getOrNull(index)
            val progress = point?.value?.coerceIn(0f, 1f) ?: 0f
            val valueText = point?.valueText ?: node.valueText.ifBlank { "--" }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.label.take(3).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() },
                    modifier = Modifier.width(34.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(Color.White.copy(alpha = 0.68f), RoundedCornerShape(999.dp))
                ) {
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(PcosinaPink, RoundedCornerShape(999.dp))
                        )
                    }
                }
                Text(
                    text = valueText,
                    modifier = Modifier.width(44.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                    textAlign = TextAlign.End
                )
            }
        }
        RefinedStatusPill(
            text = "${weeklyMealSummary.completedMeals}/${weeklyMealSummary.plannedMeals} meals done",
            containerColor = Color.White.copy(alpha = 0.82f),
            contentColor = PcosinaDeepRose
        )
    }
}

@Composable
private fun ProgressBmiCard(
    bmiLabel: String,
    bmiCategory: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val bmiValue = bmiLabel.toFloatOrNull()
    val scalePosition = bmiValue
        ?.let { ((it - 14f) / 26f).coerceIn(0.04f, 0.96f) }
        ?: 0f
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFDFE6),
        border = BorderStroke(1.dp, Color(0xFF30181E).copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(if (compact) 34.dp else 38.dp),
                    shape = CircleShape,
                    color = Color(0xFFFF9AAE).copy(alpha = 0.34f),
                    contentColor = PcosinaPink
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PcosinaDesignIcon(
                            resId = R.drawable.pcosina_svg_39_gauge,
                            contentDescription = null,
                            modifier = Modifier.size(if (compact) 24.dp else 28.dp),
                            tint = Color.Unspecified
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Target Your BMI",
                        style = if (compact) {
                            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                        } else {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                        },
                        color = Color(0xFF5E2531),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Personal Summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2B1B20)
                    )
                }
            }
            Text(
                text = bmiLabel,
                style = if (compact) {
                    MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                } else {
                    MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold)
                },
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            ProgressBmiScaleBar(position = scalePosition)
            Text(
                text = "Your BMI Indicates You Are ${bmiCategory.ifBlank { "--" }}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF2B1B20),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProgressBmiScaleBar(
    position: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        ) {
            val y = size.height * 0.48f
            val stroke = 5.dp.toPx()
            val segmentWidth = size.width / 4f
            val colors = listOf(
                Color(0xFF5B8CFF),
                Color(0xFF18B76A),
                Color(0xFFFFBE45),
                Color(0xFFE2556C)
            )
            colors.forEachIndexed { index, color ->
                drawLine(
                    color = color,
                    start = Offset(segmentWidth * index, y),
                    end = Offset(segmentWidth * (index + 1), y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            val indicatorX = size.width * position.coerceIn(0f, 1f)
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(indicatorX, y)
            )
            drawCircle(
                color = Color(0xFF18B76A),
                radius = 4.dp.toPx(),
                center = Offset(indicatorX, y)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Under", "Normal", "Over", "Obese").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4A3A40),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ProgressMealSummaryCard(
    mealsDoneLabel: String,
    weeklyMealSummary: WeeklyMealSummary,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val plannedMeals = weeklyMealSummary.plannedMeals.coerceAtLeast(1)
    val completeDays = weeklyMealSummary.chartPoints.count { it.value >= 1f }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFF30181E).copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(if (compact) 34.dp else 38.dp),
                    shape = CircleShape,
                    color = Color(0xFFFFE1E7),
                    contentColor = PcosinaPink
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PcosinaDesignIcon(
                            resId = R.drawable.pcosina_svg_37_meal,
                            contentDescription = null,
                            modifier = Modifier.size(if (compact) 24.dp else 28.dp),
                            tint = Color.Unspecified
                        )
                    }
                }
                Text(
                    text = "Weekly Meal Summary",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF5E2531),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProgressMiniArcMetric(
                    label = "Logged",
                    value = mealsDoneLabel,
                    progress = weeklyMealSummary.completedMeals.toFloat() / plannedMeals.toFloat(),
                    color = Color(0xFFF69A61),
                    modifier = Modifier.weight(1f)
                )
                ProgressMiniArcMetric(
                    label = "Planned",
                    value = weeklyMealSummary.plannedMeals.toString(),
                    progress = 1f,
                    color = Color(0xFF6E6BFF),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProgressMiniArcMetric(
                    label = "Adherence",
                    value = "${weeklyMealSummary.adherencePercent}%",
                    progress = weeklyMealSummary.adherencePercent.toFloat() / 100f,
                    color = PcosinaPink,
                    modifier = Modifier.weight(1f)
                )
                ProgressMiniArcMetric(
                    label = "Days",
                    value = "$completeDays/7",
                    progress = completeDays.toFloat() / 7f,
                    color = Color(0xFFEAC04B),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProgressMiniArcMetric(
    label: String,
    value: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                drawArc(
                    color = color.copy(alpha = 0.22f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF2B1B20),
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF4A3A40),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressWeeklyCardLegacy(
    weekNodes: List<ProgressWeekNode>,
    weeklyMealSummary: WeeklyMealSummary,
    compact: Boolean,
) {
    weekNodes.forEachIndexed { index, node ->
        val point = weeklyMealSummary.chartPoints.getOrNull(index)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = node.label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PcosinaDeepRose
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .background(Color(0xFFFFEFF3), RoundedCornerShape(999.dp))
            ) {
                val progress = point?.value?.coerceIn(0f, 1f) ?: 0f
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(point?.color ?: PcosinaPink, RoundedCornerShape(999.dp))
                    )
                }
            }
            Text(
                text = point?.valueText ?: node.valueText.ifBlank { "--" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose,
                textAlign = TextAlign.End
            )
        }
    }
    RefinedStatusPill(
        text = "${weeklyMealSummary.completedMeals}/${weeklyMealSummary.plannedMeals} meals completed this week",
        containerColor = PcosinaSoftPink.copy(alpha = 0.26f),
        contentColor = PcosinaDeepRose
    )
}

@Composable
private fun ProgressCheckInHistoryCard(
    checkInHistory: List<ProgressCheckInHistoryDay>,
) {
    if (checkInHistory.isEmpty()) {
        Text(
            text = "No saved check-ins in the last 4 weeks. Your daily and meal check-ins stay private on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Stored locally on this device. These entries are visible here but are not sent to backend ML.",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = PcosinaMuted
        )
        checkInHistory.take(10).forEach { day ->
            ProgressCheckInHistoryDayCard(day)
        }
        if (checkInHistory.size > 10) {
            Text(
                text = "${checkInHistory.size - 10} older local check-in day(s) hidden from this quick view.",
                style = MaterialTheme.typography.labelSmall,
                color = PcosinaMuted
            )
        }
    }
}

@Composable
private fun ProgressCheckInHistoryDayCard(day: ProgressCheckInHistoryDay) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaSoftPink.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = day.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProgressLevelPill("Energy", day.energyLevel?.toString() ?: "--")
                ProgressLevelPill("Mood", day.moodLevel?.toString() ?: "--")
                ProgressLevelPill("Cravings", day.cravingsLevel?.toString() ?: "--")
                if (day.mealCheckIns.isNotEmpty()) {
                    ProgressLevelPill("Meals", day.mealCheckIns.size.toString())
                }
            }
            if (!day.symptomsNote.isNullOrBlank()) {
                Text(
                    text = day.symptomsNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted
                )
            }
            day.mealCheckIns.take(3).forEach { checkIn ->
                ProgressMealCheckInRow(
                    label = checkIn.mealLabel,
                    energy = checkIn.energyLevel,
                    fullness = checkIn.fullnessLevel,
                    cravings = checkIn.cravingsLevel,
                    satisfaction = checkIn.satisfactionLevel,
                    note = checkIn.note
                )
            }
            if (day.mealCheckIns.size > 3) {
                Text(
                    text = "${day.mealCheckIns.size - 3} more meal check-in(s) saved for this day.",
                    style = MaterialTheme.typography.labelSmall,
                    color = PcosinaMuted
                )
            }
        }
    }
}

@Composable
private fun ProgressMealCheckInRow(
    label: String,
    energy: Int?,
    fullness: Int?,
    cravings: Int?,
    satisfaction: Int?,
    note: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label.ifBlank { "Meal" },
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = PcosinaDeepRose
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProgressLevelPill("Energy", energy?.toString() ?: "--")
            ProgressLevelPill("Fullness", fullness?.toString() ?: "--")
            ProgressLevelPill("Cravings", cravings?.toString() ?: "--")
            ProgressLevelPill("Satisfaction", satisfaction?.toString() ?: "--")
        }
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted
            )
        }
    }
}

@Composable
private fun ProgressSavingsCard(
    value: String,
    subtitle: String,
    summary: String,
    chartPoints: List<ProgressChartPoint>,
    spendStats: List<ProgressSpendStat>,
    compact: Boolean,
) {
    val monthLabel = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH) }
    RefinedOverviewCard(
        containerColor = Color.White,
        borderColor = PcosinaDeepRose.copy(alpha = 0.34f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color(0xFFFFE2E8)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PcosinaDesignIcon(
                        resId = R.drawable.pcosina_svg_42_activity,
                        contentDescription = null,
                        tint = PcosinaPink,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Weekly savings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Text(
                    text = "Track your savings progress to keep your PCOS nutrition plan sustainable.",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = PcosinaMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.Black
            )
            RefinedStatusPill(
                text = "$value ${subtitle.take(20)}",
                containerColor = Color(0xFFFFF2F5),
                contentColor = PcosinaDeepRose
            )
        }
        ProgressSavingsLineChart(chartPoints = chartPoints, compact = compact)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
        if (spendStats.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                spendStats.forEach { stat ->
                    RefinedStatusPill(
                        text = "${stat.label}: ${stat.value}",
                        containerColor = Color(0xFFFFF2F5),
                        contentColor = PcosinaDeepRose
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressSavingsLineChart(
    chartPoints: List<ProgressChartPoint>,
    compact: Boolean,
) {
    if (chartPoints.isEmpty()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 104.dp else 118.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFFFF5F7),
            border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.16f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Add a weekly budget or spend value to draw your savings chart.",
                    modifier = Modifier.padding(horizontal = 18.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted
                )
            }
        }
        return
    }
    val safePoints = chartPoints
    val maxValue = safePoints.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(48.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(maxValue, maxValue * 0.66f, maxValue * 0.33f, 0f).forEach { value ->
                    Text(
                        text = formatCompactPhp(value),
                        style = MaterialTheme.typography.labelSmall,
                        color = PcosinaMuted,
                        maxLines = 1
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 104.dp else 118.dp)
            ) {
                val left = 4.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val top = 8.dp.toPx()
                val bottom = size.height - 16.dp.toPx()
                repeat(4) { index ->
                    val y = top + (bottom - top) * index / 3f
                    drawLine(
                        color = PcosinaMuted.copy(alpha = 0.34f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
                val points = safePoints.mapIndexed { index, point ->
                    val x = if (safePoints.size == 1) {
                        (left + right) / 2f
                    } else {
                        left + (right - left) * index / (safePoints.lastIndex).coerceAtLeast(1)
                    }
                    val y = bottom - (bottom - top) * (point.value / maxValue).coerceIn(0f, 1f)
                    Offset(x, y)
                }
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = PcosinaPink,
                        start = start,
                        end = end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                points.forEach { point ->
                    drawCircle(color = PcosinaPink, radius = 5.dp.toPx(), center = point)
                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = point)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.width(48.dp))
            safePoints.forEach { point ->
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (point.color == PcosinaPink) PcosinaPink else PcosinaMuted,
                    fontWeight = if (point.color == PcosinaPink) FontWeight.ExtraBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ProgressCheckInCard(
    todayLog: DailyLog?,
    profileWeightLb: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    RefinedOverviewCard(
        modifier = modifier,
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Text(
            text = "Today snapshot",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose
        )
        Text(
            text = "Today's reflection is still here when you need to update it.",
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProgressLevelPill("Energy", todayLog?.energyLevel?.toString() ?: "--")
            ProgressLevelPill("Mood", todayLog?.moodLevel?.toString() ?: "--")
            ProgressLevelPill("Cravings", todayLog?.cravingsLevel?.toString() ?: "--")
            ProgressLevelPill(
                "Weight",
                todayLog?.weightKg?.let { kg ->
                    if (profileWeightLb) {
                        "${String.format(Locale.ENGLISH, "%.1f", UnitConverter.kgToLb(kg))} lb"
                    } else {
                        "${String.format(Locale.ENGLISH, "%.1f", kg)} kg"
                    }
                } ?: "--"
            )
        }
        if (!todayLog?.symptomsNote.isNullOrBlank()) {
            Text(
                text = todayLog?.symptomsNote.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted
            )
        } else {
            Text(
                text = "No note saved yet.",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted
            )
        }
    }
}

@Composable
private fun ProgressInsightsCard(
    planMetrics: com.pcosina.app.ui.PlanMetrics,
    currentPlan: PlannerPlanResponse?,
    weeklyMealSummary: WeeklyMealSummary,
    planFeedbackTags: List<String>,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    val avgCalories = remember(currentPlan) {
        currentPlan?.days?.takeIf { it.isNotEmpty() }?.map { it.totalCalories }?.average()?.toInt() ?: 0
    }
    val calorieTarget = currentPlan?.explanation?.targetCalories?.coerceAtLeast(1) ?: avgCalories.coerceAtLeast(1)
    RefinedOverviewCard(
        modifier = modifier,
        containerColor = Color.White,
        borderColor = PcosinaDeepRose.copy(alpha = 0.34f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color(0xFFFFE2E8)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.pcosina_average_daily_macros),
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Average daily macros",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Text(
                    text = "Track your weekly nutrition balance using real meal-plan averages.",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = PcosinaMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProgressMacroMiniRing(
                label = "Carbs",
                valueText = "${planMetrics.avgCarbs}g",
                targetText = "${currentPlan?.explanation?.targetCarbs ?: 0}g",
                progress = planMetrics.avgCarbs.toFloat() / (currentPlan?.explanation?.targetCarbs?.coerceAtLeast(1) ?: 1).toFloat(),
                color = Color(0xFFFFB171),
                modifier = Modifier.width(72.dp)
            )
            ProgressMacroMiniRing(
                label = "Fiber",
                valueText = "${planMetrics.avgFiber}g",
                targetText = "${currentPlan?.explanation?.fiberMinTarget ?: 0}g",
                progress = planMetrics.avgFiber.toFloat() / (currentPlan?.explanation?.fiberMinTarget?.coerceAtLeast(1) ?: 1).toFloat(),
                color = Color(0xFF8E93FF),
                modifier = Modifier.width(72.dp)
            )
            ProgressMacroMiniRing(
                label = "Protein",
                valueText = "${planMetrics.avgProtein}g",
                targetText = "${currentPlan?.explanation?.targetProtein ?: 0}g",
                progress = planMetrics.avgProtein.toFloat() / (currentPlan?.explanation?.targetProtein?.coerceAtLeast(1) ?: 1).toFloat(),
                color = Color(0xFF00A863),
                modifier = Modifier.width(72.dp)
            )
            ProgressMacroMiniRing(
                label = "Calories",
                valueText = avgCalories.toString(),
                targetText = calorieTarget.toString(),
                progress = avgCalories.toFloat() / calorieTarget.toFloat(),
                color = PcosinaPink,
                modifier = Modifier.width(72.dp)
            )
        }
        RefinedStatusPill(
            text = "${weeklyMealSummary.completedMeals}/${weeklyMealSummary.plannedMeals} meals done",
            containerColor = Color(0xFFFFF2F5),
            contentColor = PcosinaDeepRose
        )
        Text(
            text = if (currentPlan != null) "See your weekly nutrition balance." else "Generate a plan to see macro balance.",
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
        if (planFeedbackTags.isNotEmpty()) {
            Text(
                text = "Next plan tuning: ${planFeedbackTags.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted
            )
        }
    }
}

@Composable
private fun ProgressSymptomManagementCard(
    metrics: List<ProgressSymptomMetric>,
    summary: ProgressTrendSummary,
    compact: Boolean,
) {
    RefinedOverviewCard(
        containerColor = Color(0xFFFFE2E8),
        borderColor = PcosinaDeepRose.copy(alpha = 0.34f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.72f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.pcosina_pcos_system_management),
                        contentDescription = null,
                        modifier = Modifier.padding(7.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "PCOS Symptom Management",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Text(
                    text = summary.headline,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = PcosinaMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        metrics.forEach { metric ->
            ProgressSymptomMetricRow(metric)
        }
        Text(
            text = summary.detail,
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
    }
}

@Composable
private fun ProgressSymptomMetricRow(metric: ProgressSymptomMetric) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(metric.color, CircleShape)
        )
        Text(
            text = metric.label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = PcosinaDeepRose,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(metric.progress.coerceIn(0f, 1f))
                    .background(metric.color, RoundedCornerShape(999.dp))
            )
        }
        Text(
            text = metric.valueText,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose
        )
    }
}

@Composable
private fun ProgressMacroMiniRing(
    label: String,
    valueText: String,
    targetText: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = color.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    size = Size(size.width, size.height),
                    style = stroke
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    size = Size(size.width, size.height),
                    style = stroke
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
                textAlign = TextAlign.Center
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose,
            textAlign = TextAlign.Center
        )
        Text(
            text = "out of $targetText",
            style = MaterialTheme.typography.labelSmall,
            color = PcosinaMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressComparisonRow(
    label: String,
    valueText: String,
    fillRatio: Float,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PcosinaDeepRose
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(Color(0xFFFFEFF3), RoundedCornerShape(999.dp))
        ) {
            if (fillRatio > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fillRatio)
                        .background(color, RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
private fun ProgressMacroDonutChart(
    protein: Int,
    carbs: Int,
    fiber: Int,
    compact: Boolean,
) {
    val slices = listOf(
        "Protein" to (protein.coerceAtLeast(0).toFloat() to Color(0xFFFF9BAA)),
        "Carbs" to (carbs.coerceAtLeast(0).toFloat() to Color(0xFFD9AF77)),
        "Fiber" to (fiber.coerceAtLeast(0).toFloat() to Color(0xFFB7E8A8))
    )
    val total = slices.sumOf { it.second.first.toDouble() }.toFloat().coerceAtLeast(1f)

    Box(
        modifier = Modifier.size(if (compact) 118.dp else 126.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = if (compact) 18.dp.toPx() else 20.dp.toPx(), cap = StrokeCap.Round)
            var startAngle = -90f
            slices.forEach { (_, data) ->
                val sweep = (data.first / total) * 360f
                if (sweep > 0f) {
                    drawArc(
                        color = data.second,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        size = Size(size.width, size.height),
                        style = stroke
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${protein + carbs + fiber}g",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose
            )
            Text(
                text = "avg total",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted
            )
        }
    }
}

@Composable
private fun ProgressMacroLegendRow(
    label: String,
    valueText: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = PcosinaDeepRose
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
    }
}

@Composable
private fun ProgressBottomCtaCard(
    completedMealsToday: Int,
    plannedMealsToday: Int,
    weeklyJournal: String,
    onCheckIn: () -> Unit,
    onReviewWeek: () -> Unit,
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 22.dp else 24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, PcosinaDeepRose.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF8BA2), Color(0xFFFFC3CE))
                    )
                )
                .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 12.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (completedMealsToday >= plannedMealsToday && plannedMealsToday > 0) {
                        "You're keeping the week on track."
                    } else {
                        "Save today's check-in before the week gets away from you."
                    },
                    style = if (compact) {
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PcosinaDeepRose
                        )
                    } else {
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PcosinaDeepRose
                        )
                    }
                )
                Text(
                    text = if (weeklyJournal.isNotBlank()) {
                        weeklyJournal
                    } else {
                        "Use Check in for today's reflection, then Review week for spending notes and plan feedback."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RefinedPrimaryButton(
                        text = "Check in",
                        onClick = onCheckIn,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable(onClick = onReviewWeek)
                    ) {
                        Text(
                            text = "Review week",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = "✍️",
                    modifier = Modifier.padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 12.dp else 14.dp),
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    color = PcosinaDeepRose
                )
            }
        }
    }
}

@Composable
private fun ProgressSupportCtaCard(
    onSupport: () -> Unit,
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF7890), Color(0xFFFFC2CE))
                    ),
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 14.dp else 16.dp)
        ) {
            PcosinaDesignIcon(
                resId = R.drawable.pcosina_svg_v3_10_handshake,
                contentDescription = null,
                tint = PcosinaDeepRose.copy(alpha = 0.52f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(if (compact) 88.dp else 104.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (compact) 88.dp else 106.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Need a hand?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = "Access Guides & Feedback Hub now to get the support you need.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF3E1F28),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PcosinaPink,
                    shadowElevation = 8.dp,
                    modifier = Modifier.clickable(onClick = onSupport)
                ) {
                    Text(
                        text = "Go to Support  →",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressSummaryTile(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    RefinedOverviewCard(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = PcosinaMuted
        )
        Text(
            text = value,
            style = if (value.length > 16) {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
            } else {
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)
            },
            color = PcosinaDeepRose
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
    }
}

@Composable
private fun ProgressLevelPill(
    label: String,
    value: String,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFFFF2F5),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f))
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = PcosinaDeepRose
        )
    }
}

@Composable
private fun ProgressLevelPicker(
    label: String,
    value: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = PcosinaDeepRose
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { level ->
                Surface(
                    shape = CircleShape,
                    color = if (level == value) PcosinaPink else Color.White,
                    border = BorderStroke(1.dp, if (level == value) PcosinaPink else PcosinaMuted.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable { onSelect(level) }
                ) {
                    Text(
                        text = level.toString(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (level == value) Color.White else PcosinaDeepRose
                    )
                }
            }
        }
    }
}

private fun buildProgressWeeklyHighlights(
    weeklySavings: Int?,
    weeklySpendValue: Int?,
    estimatedWeeklyCost: Int?,
    budgetTarget: Int?,
    planMetrics: com.pcosina.app.ui.PlanMetrics,
    weeklyMealSummary: WeeklyMealSummary,
    weekNodes: List<ProgressWeekNode>,
    logs: Map<String, DailyLog>,
): List<ProgressWeeklyHighlight> {
    val savingsBody = when {
        weeklySpendValue != null && weeklySavings != null && weeklySavings >= 0 ->
            "You stayed ₱$weeklySavings under budget based on your actual spend."
        weeklySpendValue != null && weeklySavings != null ->
            "You went ₱${kotlin.math.abs(weeklySavings)} over budget based on your actual spend."
        estimatedWeeklyCost != null && budgetTarget != null ->
            "Your current plan is estimated against your ₱$budgetTarget weekly budget."
        else ->
            "Add actual weekly spend to see a clearer savings story."
    }
    val balanceBody = when {
        planMetrics.avgFiber >= 20 && planMetrics.avgProtein >= 45 ->
            "Your plan has a solid mix of fiber and protein for a steadier week."
        planMetrics.avgFiber > 0 || planMetrics.avgProtein > 0 ->
            "Your nutrition balance is visible now. Use swaps if meals feel too heavy or too light."
        else ->
            "Generate a plan to see your nutrition balance here."
    }
    val averageEnergy = logs.values.mapNotNull { it.energyLevel }.takeIf { it.isNotEmpty() }?.average()
    val energyBody = when {
        averageEnergy == null ->
            "Add check-ins so PCOSina can summarize how your energy felt."
        averageEnergy >= 4.0 ->
            "Your check-ins show strong energy this week. Keep the meals that helped."
        averageEnergy >= 3.0 ->
            "Your energy looked steady. Watch which meals made you feel best."
        else ->
            "Your energy looked low. Next week should lean easier and more supportive."
    }
    val completedDays = weekNodes.count { it.state == ProgressNodeState.Complete }
    val feelingBody = when {
        completedDays >= 5 ->
            "You showed up on most days. That consistency matters more than perfect logging."
        weeklyMealSummary.completedMeals > 0 ->
            "You have meal logs to learn from. Build the next week from what felt doable."
        else ->
            "Start with one logged meal today so progress has something real to learn from."
    }
    return listOf(
        ProgressWeeklyHighlight(
            label = "PHP",
            title = "Your Weekly Savings",
            body = savingsBody,
            color = Color(0xFFE2526E),
        ),
        ProgressWeeklyHighlight(
            label = "BAL",
            title = "Your Body's Balance",
            body = balanceBody,
            color = Color(0xFF009A57),
        ),
        ProgressWeeklyHighlight(
            label = "ENE",
            title = "Your Energy Levels",
            body = energyBody,
            color = Color(0xFFE2526E),
        ),
        ProgressWeeklyHighlight(
            label = "YOU",
            title = "How You're Feeling",
            body = feelingBody,
            color = Color(0xFFE2526E),
        ),
    )
}

private fun buildProgressCalendarDays(
    visibleMonth: YearMonth,
    logs: Map<String, DailyLog>,
    weekStart: LocalDate,
    weeklyJournal: String,
    weeklySpend: Int?,
    currentPlan: PlannerPlanResponse?,
    today: LocalDate,
): List<ProgressCalendarDay> {
    val weeklyRecordExists = weeklyJournal.isNotBlank() || weeklySpend != null
    val plannedMealsByDate = buildCalendarPlannedMealsByDate(currentPlan, weekStart)
    return (1..visibleMonth.lengthOfMonth()).map { dayOfMonth ->
        val date = visibleMonth.atDay(dayOfMonth)
        val log = logs[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]
        val plannedMeals = plannedMealsByDate[date].orEmpty()
        val completedKeys = log?.completedMealIds.orEmpty().toSet()
        val mealCheckIns = log?.mealCheckIns.orEmpty()
        val completedPlannedMeals = plannedMeals.filter { meal ->
            isCalendarMealCompleted(meal, completedKeys, mealCheckIns)
        }
        val completedMealCount = if (plannedMeals.isNotEmpty()) {
            completedPlannedMeals.size
        } else {
            distinctLoggedMealLabels(log).size
        }
        val completedMeals = if (plannedMeals.isNotEmpty()) {
            completedPlannedMeals.map { it.calendarDisplayName() }
        } else {
            distinctLoggedMealLabels(log)
        }
        val remainingPlannedMeals = plannedMeals
            .filterNot { meal -> isCalendarMealCompleted(meal, completedKeys, mealCheckIns) }
            .map { it.calendarDisplayName() }
        val missedMeals = if (date.isBefore(today)) remainingPlannedMeals else emptyList()
        val pendingMeals = if (date == today) remainingPlannedMeals else emptyList()
        val hasMealCheckIn = mealCheckIns.isNotEmpty()
        val hasReflection = hasCalendarReflection(log)
        val hasWeeklyProgress = weeklyRecordExists && date == weekStart
        val hasLog = completedMealCount > 0 || hasMealCheckIn || hasReflection || hasWeeklyProgress
        val isFuture = date.isAfter(today)
        val status = when {
            isFuture -> ProgressCalendarStatus.FutureLocked
            plannedMeals.isNotEmpty() && completedMealCount >= plannedMeals.size -> ProgressCalendarStatus.Completed
            plannedMeals.isNotEmpty() && completedMealCount > 0 -> ProgressCalendarStatus.Partial
            plannedMeals.isNotEmpty() && date == today -> ProgressCalendarStatus.Today
            plannedMeals.isNotEmpty() && date.isBefore(today) -> ProgressCalendarStatus.Missed
            hasLog -> ProgressCalendarStatus.Partial
            date == today -> ProgressCalendarStatus.Today
            else -> ProgressCalendarStatus.NoRecord
        }
        ProgressCalendarDay(
            date = date,
            isToday = date == today,
            isFuture = isFuture,
            hasLog = hasLog,
            hasMealCheckIn = hasMealCheckIn,
            hasReflection = hasReflection,
            hasWeeklyProgress = hasWeeklyProgress,
            completedMealCount = completedMealCount,
            plannedMealCount = plannedMeals.size,
            completedMeals = completedMeals,
            missedMeals = missedMeals,
            pendingMeals = pendingMeals,
            checkInSummaries = buildCalendarCheckInSummaries(mealCheckIns),
            reflectionSummaries = buildCalendarReflectionSummaries(log),
            status = status,
        )
    }
}

private fun buildCalendarPlannedMealsByDate(
    currentPlan: PlannerPlanResponse?,
    weekStart: LocalDate,
): Map<LocalDate, List<PlannerPlannedMeal>> {
    return currentPlan?.days.orEmpty().mapIndexed { index, day ->
        weekStart.plusDays(index.toLong()) to day.meals
    }.toMap()
}

private fun isCalendarMealCompleted(
    meal: PlannerPlannedMeal,
    completedKeys: Set<String>,
    checkIns: List<MealCheckIn>,
): Boolean {
    val mealKey = ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)
    return mealKey in completedKeys ||
        meal.recipeId in completedKeys ||
        checkIns.any { checkIn ->
            checkIn.mealKey == mealKey ||
                checkIn.recipeId == meal.recipeId ||
                (checkIn.mealLabel.equals(meal.mealLabel, ignoreCase = true) &&
                    checkIn.recipeId == meal.recipeId)
        }
}

private fun distinctLoggedMealLabels(log: DailyLog?): List<String> {
    if (log == null) return emptyList()
    val completedLabels = log.completedMealIds.mapNotNull { key ->
        ProgressViewModel.extractMealLabel(key)?.toCalendarTitle()
            ?: key.takeIf { it.isNotBlank() }?.let { "Saved meal record" }
    }
    val checkInLabels = log.mealCheckIns.map { checkIn ->
        checkIn.mealLabel.ifBlank { "Meal" }.toCalendarTitle()
    }
    return (completedLabels + checkInLabels).distinct()
}

private fun hasCalendarReflection(log: DailyLog?): Boolean {
    if (log == null) return false
    return log.energyLevel != null ||
        log.moodLevel != null ||
        log.cravingsLevel != null ||
        !log.symptomsNote.isNullOrBlank() ||
        !log.journalText.isNullOrBlank() ||
        log.symptomTags.isNotEmpty() ||
        log.weightKg != null
}

private fun buildCalendarCheckInSummaries(checkIns: List<MealCheckIn>): List<String> {
    return checkIns.map { checkIn ->
        buildString {
            append(checkIn.mealLabel.ifBlank { "Meal" }.toCalendarTitle())
            append(" check-in")
            val ratings = listOfNotNull(
                checkIn.energyLevel?.let { "energy $it/5" },
                checkIn.fullnessLevel?.let { "fullness $it/5" },
                checkIn.cravingsLevel?.let { "cravings $it/5" },
                checkIn.satisfactionLevel?.let { "satisfaction $it/5" },
            )
            if (ratings.isNotEmpty()) append(": ${ratings.joinToString(", ")}")
            if (!checkIn.note.isNullOrBlank()) append(". Note saved.")
        }
    }
}

private fun buildCalendarReflectionSummaries(log: DailyLog?): List<String> {
    if (log == null) return emptyList()
    val lines = mutableListOf<String>()
    log.energyLevel?.let { lines += "Energy: $it/5" }
    log.moodLevel?.let { lines += "Mood: $it/5" }
    log.cravingsLevel?.let { lines += "Cravings: $it/5" }
    log.weightKg?.let { lines += "Weight record: ${it}kg" }
    if (log.symptomTags.isNotEmpty()) lines += "Self-reported symptoms: ${log.symptomTags.joinToString(", ")}"
    if (!log.symptomsNote.isNullOrBlank()) lines += "Symptom note saved."
    if (!log.journalText.isNullOrBlank()) lines += "Journal note saved."
    return lines
}

private fun PlannerPlannedMeal.calendarDisplayName(): String {
    val mealLabel = this.mealLabel.toCalendarTitle()
    return if (this.title.isBlank()) mealLabel else "$mealLabel - ${this.title}"
}

private fun String.toCalendarTitle(): String {
    return trim()
        .replace("_", " ")
        .replace("-", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.lowercase(Locale.ENGLISH).replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase(Locale.ENGLISH) else first.toString()
            }
        }
        .ifBlank { this.ifBlank { "Meal" } }
}

private fun ProgressCalendarDay.insightMessage(): String {
    return when (status) {
        ProgressCalendarStatus.Completed -> "All planned meals were marked as eaten for this day."
        ProgressCalendarStatus.Partial -> if (plannedMealCount > 0) {
            "Some planned meals were marked as eaten; other meals were not recorded."
        } else {
            "Self-reported progress was recorded for this day."
        }
        ProgressCalendarStatus.Missed -> "No planned meals were marked as eaten for this day."
        ProgressCalendarStatus.NoRecord -> "No progress recorded for this day yet."
        ProgressCalendarStatus.FutureLocked -> "Future days are locked until they arrive."
        ProgressCalendarStatus.Today -> "Today is still in progress. Continue logging meals through the existing check-in flow."
    }
}

private fun ProgressCalendarStatus.displayLabel(): String {
    return when (this) {
        ProgressCalendarStatus.Completed -> "All meals marked"
        ProgressCalendarStatus.Partial -> "Partly recorded"
        ProgressCalendarStatus.Missed -> "No meals marked"
        ProgressCalendarStatus.NoRecord -> "No record"
        ProgressCalendarStatus.FutureLocked -> "Future locked"
        ProgressCalendarStatus.Today -> "Today"
    }
}

private fun ProgressCalendarStatus.legendColor(): Color {
    return when (this) {
        ProgressCalendarStatus.Completed -> Color(0xFF247A45)
        ProgressCalendarStatus.Partial -> Color(0xFF7FBF68)
        ProgressCalendarStatus.Missed -> Color(0xFFE2526E)
        ProgressCalendarStatus.NoRecord -> Color(0xFFB9AEB2)
        ProgressCalendarStatus.FutureLocked -> Color(0xFF8A8185)
        ProgressCalendarStatus.Today -> PcosinaDeepRose
    }
}

private fun ProgressCalendarStatus.contentColor(): Color {
    return when (this) {
        ProgressCalendarStatus.NoRecord,
        ProgressCalendarStatus.FutureLocked -> PcosinaMuted
        else -> PcosinaDeepRose
    }
}

private fun buildMonthCalendarCells(visibleMonth: YearMonth): List<LocalDate?> {
    val firstDay = visibleMonth.atDay(1)
    val leadingBlankCount = firstDay.dayOfWeek.value % 7
    val datedCells = (1..visibleMonth.lengthOfMonth()).map { visibleMonth.atDay(it) }
    val cells = List(leadingBlankCount) { null } + datedCells
    val trailingBlankCount = (7 - (cells.size % 7)).takeIf { it < 7 } ?: 0
    return cells + List(trailingBlankCount) { null }
}

private fun buildProgressSymptomMetrics(summary: ProgressTrendSummary): List<ProgressSymptomMetric> {
    val energyProgress = (summary.averageEnergy?.toFloat()?.div(5f) ?: 0f).coerceIn(0f, 1f)
    val moodProgress = (summary.averageMood?.toFloat()?.div(5f) ?: 0f).coerceIn(0f, 1f)
    val cravingControl = summary.averageCravings
        ?.let { ((6f - it.toFloat()) / 5f).coerceIn(0f, 1f) }
        ?: 0f
    val checkInCoverage = (summary.checkInDays.toFloat() / 28f).coerceIn(0f, 1f)
    return listOf(
        ProgressSymptomMetric(
            label = "Energy support",
            valueText = "${(energyProgress * 100).toInt()}%",
            progress = energyProgress,
            color = Color(0xFF22B35A)
        ),
        ProgressSymptomMetric(
            label = "Mood stability",
            valueText = "${(moodProgress * 100).toInt()}%",
            progress = moodProgress,
            color = Color(0xFFFFA000)
        ),
        ProgressSymptomMetric(
            label = "Craving control",
            valueText = "${(cravingControl * 100).toInt()}%",
            progress = cravingControl,
            color = Color(0xFFE2526E)
        ),
        ProgressSymptomMetric(
            label = "Check-in coverage",
            valueText = "${summary.checkInDays}/28",
            progress = checkInCoverage,
            color = PcosinaPink
        )
    )
}

private fun formatCompactPhp(value: Float): String {
    val rounded = value.toInt()
    return if (rounded >= 1000) {
        "₱${rounded / 1000}k"
    } else {
        "₱$rounded"
    }
}

private fun ProgressWeekNodeSummary.toUiWeekNode(): ProgressWeekNode = ProgressWeekNode(
    label = label,
    valueText = valueText,
    state = status.toUiNodeState()
)

private fun WeeklyMealSummaryResult.toUiWeeklyMealSummary(): WeeklyMealSummary = WeeklyMealSummary(
    plannedMeals = plannedMeals,
    completedMeals = completedMeals,
    adherencePercent = adherencePercent,
    chartPoints = chartPoints.map { it.toUiChartPoint() }
)

private fun ProgressAdherencePoint.toUiChartPoint(): ProgressChartPoint = ProgressChartPoint(
    label = label,
    value = ratio,
    valueText = valueText,
    color = when (status) {
        ProgressDayStatus.COMPLETE -> PcosinaPink
        ProgressDayStatus.PARTIAL -> Color(0xFFFFB171)
        ProgressDayStatus.PENDING, ProgressDayStatus.FUTURE -> PcosinaSoftPink
    }
)

private fun ProgressDayStatus.toUiNodeState(): ProgressNodeState = when (this) {
    ProgressDayStatus.COMPLETE -> ProgressNodeState.Complete
    ProgressDayStatus.PARTIAL -> ProgressNodeState.Partial
    ProgressDayStatus.PENDING -> ProgressNodeState.Pending
    ProgressDayStatus.FUTURE -> ProgressNodeState.Future
}

private fun Double?.formatTrendAverage(): String =
    this?.let { String.format(Locale.ENGLISH, "%.1f", it) } ?: "--"
