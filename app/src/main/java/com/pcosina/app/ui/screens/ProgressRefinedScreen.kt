package com.pcosina.app.ui.screens

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.PlannerPlanResponse
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.PlannedDayCount
import com.pcosina.app.domain.ProgressAdherencePoint
import com.pcosina.app.domain.ProgressDayStatus
import com.pcosina.app.domain.ProgressSummaryUseCase
import com.pcosina.app.domain.ProgressWeekNodeSummary
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.domain.WeeklyMealSummaryResult
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.RefinedMetricBar
import com.pcosina.app.ui.components.RefinedOverviewCard
import com.pcosina.app.ui.components.RefinedPrimaryButton
import com.pcosina.app.ui.components.RefinedStatusPill
import com.pcosina.app.ui.components.RefinedTabBrandHeader
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.theme.PcosinaBlush
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt
import com.pcosina.app.ui.util.rememberIsOnline
import java.time.LocalDate
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
    var showReflectionDialog by remember { mutableStateOf(false) }
    var showWeeklyReviewDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
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
        var adherenceExpanded by remember { mutableStateOf(true) }
        var savingsExpanded by remember { mutableStateOf(false) }
        var macrosExpanded by remember { mutableStateOf(feedbackSectionExpandedByDefault) }

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
                onSupport = { onNavigateToRoute(Routes.Ipo) },
                compact = compact
            )

            ProgressHeadlineCard(
                dateLabel = today.format(DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH)),
                bmiLabel = bmiLabel,
                bmiCategory = bmiCategory,
                mealsDoneLabel = mealsDoneLabel,
                compact = compact
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
                subtitle = "$mealsDoneLabel meals done",
                expanded = adherenceExpanded,
                onToggle = { adherenceExpanded = !adherenceExpanded },
                compact = compact
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
                expanded = savingsExpanded,
                onToggle = { savingsExpanded = !savingsExpanded },
                compact = compact
            ) {
                ProgressSavingsCard(
                    summary = savingsSummaryLabel,
                    chartPoints = savingsChartPoints,
                    spendStats = spendStats,
                    compact = compact
                )
            }

            ProgressDropdownCard(
                title = "Average daily macros",
                value = "",
                subtitle = if (currentPlan != null) "See your weekly nutrition balance." else "Generate a plan to see macro balance.",
                expanded = macrosExpanded,
                onToggle = { macrosExpanded = !macrosExpanded },
                compact = compact
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

            ProgressBottomCtaCard(
                completedMealsToday = completedMealsToday,
                plannedMealsToday = plannedMealsToday,
                weeklyJournal = weeklyJournal,
                onCheckIn = { showReflectionDialog = true },
                onReviewWeek = { showWeeklyReviewDialog = true },
                compact = compact
            )
        }
    }
}

@Composable
private fun ProgressHeadlineCard(
    dateLabel: String,
    bmiLabel: String,
    bmiCategory: String,
    mealsDoneLabel: String,
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 20.dp else 22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.25.dp, PcosinaDeepRose.copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(PcosinaBlush, Color(0xFFFF91A7))
                    )
                )
                .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Weekly Progress",
                        style = if (compact) {
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = PcosinaDeepRose
                            )
                        } else {
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = PcosinaDeepRose
                            )
                        }
                    )
                    Text(
                        text = "Track how your week is going in one clean view.",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = PcosinaDeepRose
                    )
                }
                RefinedStatusPill(
                    text = dateLabel,
                    containerColor = Color.White.copy(alpha = 0.82f),
                    contentColor = PcosinaDeepRose
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProgressHeroMetricCard(
                    title = "BMI",
                    value = bmiLabel,
                    subtitle = bmiCategory,
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
                ProgressHeroMetricCard(
                    title = "Meal summary",
                    value = mealsDoneLabel,
                    subtitle = "Meals completed this week",
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
            }
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
    Text(
        text = "See how your daily small actions are adding up this week.",
        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
        color = PcosinaMuted
    )
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
private fun ProgressSavingsCard(
    summary: String,
    chartPoints: List<ProgressChartPoint>,
    spendStats: List<ProgressSpendStat>,
    compact: Boolean,
) {
    Text(
        text = summary,
        style = MaterialTheme.typography.bodyMedium,
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
    if (chartPoints.isEmpty()) {
        Text(
            text = "Add a weekly budget or spend value to compare savings here.",
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
    } else {
        val maxValue = chartPoints.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            chartPoints.forEach { point ->
                ProgressComparisonRow(
                    label = point.label,
                    valueText = point.valueText,
                    fillRatio = (point.value / maxValue).coerceIn(0f, 1f),
                    color = point.color
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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Use these averages as a quick weekly nutrition snapshot.",
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressMacroDonutChart(
                protein = planMetrics.avgProtein,
                carbs = planMetrics.avgCarbs,
                fiber = planMetrics.avgFiber,
                compact = compact
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProgressMacroLegendRow("Protein", "${planMetrics.avgProtein}g avg", Color(0xFFFF9BAA))
                ProgressMacroLegendRow("Carbs", "${planMetrics.avgCarbs}g avg", Color(0xFFD9AF77))
                ProgressMacroLegendRow("Fiber", "${planMetrics.avgFiber}g avg", Color(0xFFB7E8A8))
                RefinedStatusPill(
                    text = "${weeklyMealSummary.completedMeals}/${weeklyMealSummary.plannedMeals} meals done",
                    containerColor = Color(0xFFFFF2F5),
                    contentColor = PcosinaDeepRose
                )
            }
        }
        RefinedMetricBar(
            label = "Protein",
            valueText = "${planMetrics.avgProtein}g avg",
            progress = planMetrics.avgProtein.toFloat() / (currentPlan?.explanation?.targetProtein?.coerceAtLeast(1) ?: 1).toFloat(),
            color = Color(0xFFFF9BAA)
        )
        RefinedMetricBar(
            label = "Carbs",
            valueText = "${planMetrics.avgCarbs}g avg",
            progress = planMetrics.avgCarbs.toFloat() / (currentPlan?.explanation?.targetCarbs?.coerceAtLeast(1) ?: 1).toFloat(),
            color = Color(0xFFD9AF77)
        )
        RefinedMetricBar(
            label = "Fiber",
            valueText = "${planMetrics.avgFiber}g avg",
            progress = planMetrics.avgFiber.toFloat() / (currentPlan?.explanation?.fiberMinTarget?.coerceAtLeast(1) ?: 1).toFloat(),
            color = Color(0xFFB7E8A8)
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
