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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.MealCheckIn
import com.pcosina.app.data.model.PlanInstance
import com.pcosina.app.data.model.PlannerPlannedMeal
import com.pcosina.app.data.model.PlannerPlanResponse
import com.pcosina.app.data.model.PlannerRecipeDetail
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.PlannedMealSlot
import com.pcosina.app.domain.ProgressAdherencePoint
import com.pcosina.app.domain.ProgressCheckInHistoryDay
import com.pcosina.app.domain.ProgressDayStatus
import com.pcosina.app.domain.ProgressMealSlotStatus
import com.pcosina.app.domain.ProgressSummaryUseCase
import com.pcosina.app.domain.ProgressTrendSummary
import com.pcosina.app.domain.ProgressWeekNodeSummary
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.domain.WeightGoalGuardrailSummary
import com.pcosina.app.domain.WeightGoalGuardrailUseCase
import com.pcosina.app.domain.WeeklyMealSummaryResult
import com.pcosina.app.R
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.ArtworkAlignmentKeys
import com.pcosina.app.ui.components.PcosinaDesignIcon
import com.pcosina.app.ui.components.RefinedOverviewCard
import com.pcosina.app.ui.components.RefinedPrimaryButton
import com.pcosina.app.ui.components.RefinedStatusPill
import com.pcosina.app.ui.components.RefinedTabBrandHeader
import com.pcosina.app.ui.components.ScreenArtworkAlignment
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
    val duePlannedMeals: Int,
    val skippedMeals: Int,
    val missedMeals: Int,
    val pendingMeals: Int,
    val weeklyCompletionPercent: Int,
    val chartPoints: List<ProgressChartPoint>,
    val mealSlots: List<ProgressMealSlotCell>,
)

private data class ProgressMealSlotCell(
    val dayLabel: String,
    val mealLabel: String,
    val recipeId: String,
    val status: ProgressMealSlotStatus,
)

private data class ProgressWeeklyHighlight(
    val label: String,
    val title: String,
    val body: String,
    val action: String,
    val color: Color,
)

private data class ProgressSymptomMetric(
    val label: String,
    val valueText: String,
    val progress: Float,
    val color: Color,
)

private data class LoggedNutritionSummary(
    val loggedMeals: Int,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fiberGrams: Int,
    val missingDetails: Int,
)

private data class MealResponseSummary(
    val checkInCount: Int,
    val dayCount: Int,
    val averageEnergy: Double?,
    val averageFullness: Double?,
    val averageCravings: Double?,
    val averageSatisfaction: Double?,
)

private data class ProgressDraftInputs(
    val selectedFeedbackTags: Set<String> = emptySet(),
)

private const val WeeklySpendValidationMessage = "Weekly spend must be a whole number in pesos."

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
    val lastReviewedWeek by mealPlanViewModel.lastReviewedWeek.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val weeklySpend by progressViewModel.weeklySpend.collectAsState()
    val planFeedbackTags by progressViewModel.planFeedbackTags.collectAsState()
    val today = LocalDate.now()
    val currentPlan = remember(planState, planHistory, activePlanId) {
        (planState as? MealPlanUiState.Success)?.response
            ?: planHistory.firstOrNull { it.id == activePlanId }?.response
    }
    val planRecipeIds = remember(currentPlan) {
        currentPlan?.days.orEmpty()
            .flatMap { day -> day.meals.map { meal -> meal.recipeId } }
            .filter { it.isNotBlank() }
            .distinct()
    }
    val recipeDetails = remember { mutableStateMapOf<String, PlannerRecipeDetail?>() }
    val progressSummaryUseCase = remember { ProgressSummaryUseCase() }
    val weightGoalGuardrailUseCase = remember { WeightGoalGuardrailUseCase() }
    val weekStart = remember(activeWeekStart, currentPlan?.weekLabel) {
        activeWeekStart?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        } ?: today
    }
    val todayToken = today.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).lowercase(Locale.ENGLISH)
    val viewingCurrentWeek = remember(weekStart, today) { isDateWithinPlanWeek(today, weekStart) }
    val todayPlan = remember(currentPlan, todayToken, viewingCurrentWeek) {
        if (viewingCurrentWeek) {
            currentPlan?.days?.firstOrNull { it.dayLabel.lowercase(Locale.ENGLISH) == todayToken }
        } else {
            null
        }
    }
    val todayLog = logs[today.format(DateTimeFormatter.ISO_LOCAL_DATE)]
    val completedMealsToday = todayPlan?.meals?.count { meal ->
        todayLog?.completedMealIds?.contains(ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)) == true ||
            todayLog?.completedMealIds?.contains(meal.recipeId) == true
    } ?: 0
    val plannedMealsToday = todayPlan?.meals?.size ?: 0
    val plannedMealSlots = remember(currentPlan) {
        currentPlan?.days.orEmpty().flatMap { day ->
            day.meals.map { meal ->
                PlannedMealSlot(
                    dayLabel = day.dayLabel,
                    mealLabel = meal.mealLabel,
                    recipeId = meal.recipeId,
                )
            }
        }
    }
    val weeklyMealSummary = remember(plannedMealSlots, logs, weekStart, today) {
        runCatching {
            progressSummaryUseCase.buildWeeklyMealSlotSummary(plannedMealSlots, logs, weekStart, today)
                .toUiWeeklyMealSummary()
        }.onFailure { error ->
            Log.e("ProgressRefinedScreen", "Failed to compute weekly meal summary safely.", error)
        }.getOrElse {
            WeeklyMealSummary(
                plannedMeals = 0,
                completedMeals = 0,
                adherencePercent = 0,
                duePlannedMeals = 0,
                skippedMeals = 0,
                missedMeals = 0,
                pendingMeals = 0,
                weeklyCompletionPercent = 0,
                chartPoints = emptyList(),
                mealSlots = emptyList(),
            )
        }
    }
    val checkInHistory = remember(logs, today) {
        progressSummaryUseCase.buildCheckInHistory(logs, today)
    }
    val weightGoalSummary = remember(profile, today) {
        weightGoalGuardrailUseCase.build(profile, today)
    }
    var showWeeklyReviewDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var visibleCalendarMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedCalendarDate by remember { mutableStateOf(today) }
    val weekStartKey = remember(weekStart) { weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    var weeklySpendInput by remember(weekStartKey, weeklySpend) {
        mutableStateOf(weeklySpend?.toString().orEmpty())
    }
    var weeklySpendError by remember(weekStartKey) { mutableStateOf<String?>(null) }
    val weightUnitLabel = if (profile.weightUnit == UnitConverter.WEIGHT_LB) "lb" else "kg"
    val draftInputs = remember(planFeedbackTags, weekStartKey) {
        ProgressDraftInputs(
            selectedFeedbackTags = planFeedbackTags.toSet()
        )
    }
    var selectedFeedbackTags by remember(weekStartKey) { mutableStateOf(draftInputs.selectedFeedbackTags) }
    val planFeedbackOptions = remember { listOf("Too repetitive", "Too expensive", "Too hard to cook") }

    LaunchedEffect(weekStart) {
        visibleCalendarMonth = YearMonth.from(weekStart)
        selectedCalendarDate = if (isDateWithinPlanWeek(today, weekStart)) today else weekStart
    }

    LaunchedEffect(planFeedbackTags, weekStartKey) {
        selectedFeedbackTags = planFeedbackTags.toSet()
    }

    LaunchedEffect(planRecipeIds.joinToString("|")) {
        val activeIds = planRecipeIds.toSet()
        recipeDetails.keys.toList()
            .filterNot { it in activeIds }
            .forEach { recipeDetails.remove(it) }
        planRecipeIds.forEach { recipeId ->
            if (recipeDetails.containsKey(recipeId)) return@forEach
            mealPlanViewModel.getRecipeDetails(recipeId)
                .onSuccess { detail -> recipeDetails[recipeId] = detail }
                .onFailure { recipeDetails[recipeId] = null }
        }
    }

    if (showWeeklyReviewDialog) {
        Dialog(onDismissRequest = { showWeeklyReviewDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 430.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Tune next plan",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = PcosinaDeepRose,
                            )
                            Text(
                                text = "Choose what should change before the next weekly plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PcosinaMuted,
                            )
                        }
                    }
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) Color.White else PcosinaDeepRose
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showWeeklyReviewDialog = false }) { Text("Cancel") }
                        TextButton(
                            onClick = {
                                val existing = planFeedbackTags.toSet()
                                val toToggle = (existing - selectedFeedbackTags) + (selectedFeedbackTags - existing)
                                toToggle.forEach { tag -> progressViewModel.togglePlanFeedbackTag(tag) }
                                mealPlanViewModel.markWeekReviewed(weekStartKey)
                                feedbackMessage = "Next-plan preferences updated."
                                showWeeklyReviewDialog = false
                            }
                        ) { Text("Save") }
                    }
                }
            }
        }
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
        val weekRangeLabel = remember(weekStart) {
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
            val end = weekStart.plusDays(6)
            "${weekStart.format(formatter)} - ${end.format(formatter)}"
        }
        val estimatedWeeklyCost = currentPlan
            ?.groceryOutput
            ?.estimatedTotalPhp
            ?.takeIf { it >= 0 }
            ?: currentPlan?.groceryOutput?.finalGroceryEstimatePhp?.takeIf { it >= 0 }
            ?: currentPlan?.explanation?.estimatedWeeklyCost
        val estimatedBudgetDifference = if (budgetTarget != null && estimatedWeeklyCost != null) {
            budgetTarget - estimatedWeeklyCost
        } else {
            null
        }
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
        val recipeDetailSnapshot = recipeDetails.toMap()
        val loggedNutritionSummary = remember(weeklyMealSummary, recipeDetailSnapshot) {
            buildLoggedNutritionSummary(weeklyMealSummary, recipeDetailSnapshot)
        }
        val mealResponseSummary = remember(logs, weekStart, today) {
            buildMealResponseSummary(logs, weekStart, today)
        }
        val weekReviewed = lastReviewedWeek == weekStartKey
        val progressCalendarDays = remember(
            visibleCalendarMonth,
            logs,
            weekStart,
            currentPlan,
            today,
            profile.weightUnit,
        ) {
            buildProgressCalendarDays(
                visibleMonth = visibleCalendarMonth,
                logs = logs,
                weekStart = weekStart,
                currentPlan = currentPlan,
                today = today,
                profileWeightLb = profile.weightUnit == UnitConverter.WEIGHT_LB,
            )
        }
        val savedWeeks = remember(planHistory) {
            planHistory.sortedByDescending { it.weekStart }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .testTag("progress_content_list")
                .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            RefinedTabBrandHeader(
                online = isOnline,
                onSettings = { onNavigateToRoute(Routes.Settings) },
                compact = compact,
                avatarId = profile.avatarId
            )

            SharedAvatarHeader(
                title = "Progress",
                subtitle = "Weekly meal, budget, and check-in summary.",
                avatarId = profile.avatarId,
                modifier = Modifier
                    .testTag("progress_header"),
                dateLabel = today.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)),
                compact = compact,
                avatarAlignment = ScreenArtworkAlignment.ProgressHeaderAvatar,
                avatarArtworkKey = ArtworkAlignmentKeys.ProgressHeaderAvatar,
                onHeaderClick = { onNavigateToRoute(Routes.settingsRoute(Routes.SettingsSectionProfile)) },
            )

            ProgressThisWeekDashboard(
                weeklyMealSummary = weeklyMealSummary,
                weekRangeLabel = weekRangeLabel,
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_week_dashboard")
                    .semantics { traversalIndex = 4f },
            )

            ProgressSavedWeekSelector(
                plans = savedWeeks,
                activePlanId = activePlanId,
                onSelectPlan = { planId ->
                    mealPlanViewModel.selectPlan(planId)
                    feedbackMessage = "Opened saved week for review."
                },
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_saved_week_selector")
                    .semantics { traversalIndex = 4.5f },
            )

            ProgressCalendarCard(
                visibleMonth = visibleCalendarMonth,
                today = today,
                selectedDate = selectedCalendarDate,
                days = progressCalendarDays,
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

            ProgressBudgetSummaryCard(
                periodLabel = weekRangeLabel,
                budgetTarget = budgetTarget,
                estimatedWeeklyCost = estimatedWeeklyCost,
                estimatedBudgetDifference = estimatedBudgetDifference,
                actualSpend = weeklySpend,
                weeklySpendInput = weeklySpendInput,
                weeklySpendError = weeklySpendError,
                onWeeklySpendInputChange = {
                    weeklySpendInput = it
                    if (weeklySpendError != null) weeklySpendError = null
                },
                onSaveWeeklySpend = {
                    val rawValue = weeklySpendInput.trim()
                    val parsedValue = if (rawValue.isBlank()) {
                        null
                    } else if (rawValue.all { char -> char.isDigit() }) {
                        rawValue.toIntOrNull()
                    } else {
                        null
                    }
                    if (rawValue.isNotBlank() && parsedValue == null) {
                        weeklySpendError = WeeklySpendValidationMessage
                    } else {
                        progressViewModel.saveWeeklySpend(weekStartKey, parsedValue)
                        weeklySpendError = null
                        feedbackMessage = if (parsedValue == null) {
                            "Weekly spend cleared."
                        } else {
                            "Weekly spend saved."
                        }
                    }
                },
                editable = viewingCurrentWeek,
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_week_budget_card")
                    .semantics { traversalIndex = 6f },
            )

            ProgressLoggedNutritionCard(
                summary = loggedNutritionSummary,
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_week_macro_card")
                    .semantics { traversalIndex = 7f },
            )

            ProgressMealResponseCard(
                summary = mealResponseSummary,
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_meal_response_card")
                    .semantics { traversalIndex = 8f },
            )

            ProgressBmiCard(
                bmiValue = bmiValue,
                bmiLabel = bmiLabel,
                bmiCategory = bmiCategory,
                modifier = Modifier.testTag("progress_today_hub_card"),
                compact = compact
            )

            if (profile.goal.contains("Weight Loss", ignoreCase = true) || weightGoalSummary.hasTarget) {
                ProgressWeightSupportCard(
                    summary = weightGoalSummary,
                    weightUnitLabel = weightUnitLabel,
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("progress_weight_support_card")
                )
            }

            ProgressBottomCtaCard(
                completedMealsToday = completedMealsToday,
                plannedMealsToday = plannedMealsToday,
                weekReviewed = weekReviewed,
                reviewEnabled = viewingCurrentWeek,
                onReviewWeek = { showWeeklyReviewDialog = true },
                compact = compact
            )

            Spacer(modifier = Modifier.height(if (compact) 18.dp else 24.dp))
        }

    }
}

internal fun isDateWithinPlanWeek(date: LocalDate, weekStart: LocalDate): Boolean =
    !date.isBefore(weekStart) && !date.isAfter(weekStart.plusDays(6))

@Composable
private fun ProgressThisWeekDashboard(
    weeklyMealSummary: WeeklyMealSummary,
    weekRangeLabel: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val dueMeals = weeklyMealSummary.duePlannedMeals
    val adherenceLabel = if (dueMeals > 0) {
        "${weeklyMealSummary.adherencePercent}%"
    } else {
        "--"
    }
    val dueMealLabel = if (dueMeals > 0) {
        "${weeklyMealSummary.completedMeals}/$dueMeals due so far (${weeklyMealSummary.adherencePercent}%)"
    } else {
        "No due meals yet"
    }
    RefinedOverviewCard(
        modifier = modifier,
        containerColor = Color(0xFFFFF8FB),
        borderColor = PcosinaPink.copy(alpha = 0.28f),
        contentPadding = PaddingValues(if (compact) 13.dp else 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "This week",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose,
                    )
                    Text(
                        text = weekRangeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = PcosinaMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = adherenceLabel,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = "adherence due so far",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PcosinaMuted,
                        textAlign = TextAlign.End,
                    )
                }
            }

            ProgressMealMatrix(
                slots = weeklyMealSummary.mealSlots,
                compact = compact,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressSummaryPill(dueMealLabel)
                ProgressSummaryPill(
                    "${weeklyMealSummary.weeklyCompletionPercent}% full-week completion " +
                        "(${weeklyMealSummary.completedMeals}/${weeklyMealSummary.plannedMeals})"
                )
                if (weeklyMealSummary.skippedMeals > 0) ProgressSummaryPill("${weeklyMealSummary.skippedMeals} skipped")
                if (weeklyMealSummary.missedMeals > 0) ProgressSummaryPill("${weeklyMealSummary.missedMeals} missed")
                if (weeklyMealSummary.pendingMeals > 0) ProgressSummaryPill("${weeklyMealSummary.pendingMeals} pending today")
            }
        }
    }
}

@Composable
private fun ProgressSavedWeekSelector(
    plans: List<PlanInstance>,
    activePlanId: String?,
    onSelectPlan: (String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (plans.isEmpty()) return
    RefinedOverviewCard(
        modifier = modifier,
        containerColor = Color.White,
        borderColor = PcosinaSoftPink.copy(alpha = 0.5f),
        contentPadding = PaddingValues(if (compact) 11.dp else 13.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ProgressSectionHeader(
                title = "Saved weeks",
                subtitle = "Open a saved plan week to review its meals and records.",
                trailing = "${plans.size} saved",
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                plans.take(8).forEach { plan ->
                    val selected = plan.id == activePlanId
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) PcosinaPink else Color(0xFFFFF3F6),
                        border = BorderStroke(1.dp, if (selected) PcosinaPink else PcosinaPink.copy(alpha = 0.18f)),
                        modifier = Modifier.clickable(enabled = !selected) { onSelectPlan(plan.id) },
                    ) {
                        Text(
                            text = buildString {
                                append(plan.weekRangeLabel())
                                if (selected) append(" - Open")
                            },
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (selected) Color.White else PcosinaDeepRose,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressMealMatrix(
    slots: List<ProgressMealSlotCell>,
    compact: Boolean,
) {
    val dayLabels = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val slotsByDay = remember(slots) { slots.groupBy { it.dayLabel.uppercase(Locale.ENGLISH).take(3) } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Weekly meal matrix showing completed, skipped, missed, pending, and future meals."
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            dayLabels.forEach { day ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = day.take(3),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaMuted,
                        maxLines = 1,
                    )
                    val daySlots = slotsByDay[day].orEmpty()
                    listOf("Breakfast", "Lunch", "Dinner").forEach { mealLabel ->
                        val slot = daySlots.firstOrNull {
                            it.mealLabel.equals(mealLabel, ignoreCase = true)
                        } ?: daySlots.getOrNull(
                            when (mealLabel) {
                                "Breakfast" -> 0
                                "Lunch" -> 1
                                else -> 2
                            }
                        )
                        ProgressMealMatrixCell(
                            label = mealLabel.firstOrNull()?.toString().orEmpty(),
                            status = slot?.status,
                            compact = compact,
                            accessibilityLabel = "$day $mealLabel: ${slot?.status.accessibilityLabel()}",
                        )
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ProgressMatrixLegend("Done", ProgressMealSlotStatus.COMPLETED.matrixColor())
            ProgressMatrixLegend("Skip", ProgressMealSlotStatus.SKIPPED.matrixColor())
            ProgressMatrixLegend("Missed", ProgressMealSlotStatus.MISSED.matrixColor())
            ProgressMatrixLegend("Open", ProgressMealSlotStatus.PENDING.matrixColor())
            ProgressMatrixLegend("Future", ProgressMealSlotStatus.FUTURE.matrixColor())
        }
    }
}

@Composable
private fun ProgressMealMatrixCell(
    label: String,
    status: ProgressMealSlotStatus?,
    compact: Boolean,
    accessibilityLabel: String,
) {
    val size = if (compact) 22.dp else 24.dp
    val color = status?.matrixColor() ?: Color(0xFFE8E1E4)
    val contentColor = when (status) {
        ProgressMealSlotStatus.COMPLETED,
        ProgressMealSlotStatus.SKIPPED,
        ProgressMealSlotStatus.MISSED -> Color.White
        else -> PcosinaMuted
    }
    Box(
        modifier = Modifier
            .size(size)
            .background(color, RoundedCornerShape(6.dp))
            .semantics { contentDescription = accessibilityLabel },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (status) {
                ProgressMealSlotStatus.COMPLETED -> "✓"
                ProgressMealSlotStatus.SKIPPED -> "-"
                ProgressMealSlotStatus.MISSED -> "!"
                ProgressMealSlotStatus.PENDING -> label
                ProgressMealSlotStatus.FUTURE -> ""
                null -> ""
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProgressMatrixLegend(
    label: String,
    color: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = PcosinaMuted,
        )
    }
}

@Composable
private fun ProgressSummaryPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.82f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
        color = PcosinaDeepRose,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun ProgressMealSlotStatus?.accessibilityLabel(): String = when (this) {
    ProgressMealSlotStatus.COMPLETED -> "completed"
    ProgressMealSlotStatus.SKIPPED -> "skipped"
    ProgressMealSlotStatus.MISSED -> "missed"
    ProgressMealSlotStatus.PENDING -> "open"
    ProgressMealSlotStatus.FUTURE -> "future"
    null -> "not planned"
}

@Composable
private fun ProgressBudgetSummaryCard(
    periodLabel: String,
    budgetTarget: Int?,
    estimatedWeeklyCost: Int?,
    estimatedBudgetDifference: Int?,
    actualSpend: Int?,
    weeklySpendInput: String,
    weeklySpendError: String?,
    onWeeklySpendInputChange: (String) -> Unit,
    onSaveWeeklySpend: () -> Unit,
    editable: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    RefinedOverviewCard(
        modifier = modifier,
        containerColor = Color.White,
        borderColor = PcosinaSoftPink.copy(alpha = 0.55f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgressSectionHeader(
                title = "Weekly budget",
                subtitle = periodLabel,
                trailing = "estimate only",
            )
            ProgressBudgetStrip(
                budgetTarget = budgetTarget,
                estimatedWeeklyCost = estimatedWeeklyCost,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressMetricChip("Budget", budgetTarget?.pesoLabel() ?: "--")
                ProgressMetricChip("Plan estimate", estimatedWeeklyCost?.pesoLabel() ?: "--")
                ProgressMetricChip(
                    if ((estimatedBudgetDifference ?: 0) >= 0) "Estimated remaining" else "Estimate over",
                    estimatedBudgetDifference?.signedPesoLabel() ?: "--",
                )
                if (actualSpend != null) {
                    ProgressMetricChip("Actual spend", actualSpend.pesoLabel())
                }
            }
            OutlinedTextField(
                value = weeklySpendInput,
                onValueChange = onWeeklySpendInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Actual weekly spend") },
                placeholder = { Text("Optional pesos") },
                singleLine = true,
                enabled = editable,
                isError = !weeklySpendError.isNullOrBlank(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        text = weeklySpendError
                            ?: if (editable) {
                                "Save what you actually spent this week for budget review."
                            } else {
                                "Historical weeks are read-only. Open the current week to update actual spend."
                            },
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onSaveWeeklySpend, enabled = editable) {
                    Text("Save spend")
                }
            }
        }
    }
}

@Composable
private fun ProgressBudgetStrip(
    budgetTarget: Int?,
    estimatedWeeklyCost: Int?,
) {
    val values = listOfNotNull(budgetTarget, estimatedWeeklyCost)
    if (values.isEmpty()) return
    val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics {
                contentDescription = "Budget comparison with weekly budget and estimated plan cost."
            }
    ) {
        val y = size.height * 0.55f
        val stroke = 8.dp.toPx()
        drawLine(
            color = Color(0xFFF1E6EA),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        fun xFor(value: Int): Float = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f) * size.width
        estimatedWeeklyCost?.let {
            drawLine(
                color = Color(0xFFFFB171),
                start = Offset(0f, y),
                end = Offset(xFor(it), y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        budgetTarget?.let {
            val x = xFor(it)
            drawLine(
                color = PcosinaDeepRose,
                start = Offset(x, y - 11.dp.toPx()),
                end = Offset(x, y + 11.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ProgressLoggedNutritionCard(
    summary: LoggedNutritionSummary,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    RefinedOverviewCard(
        modifier = modifier,
        containerColor = Color.White,
        borderColor = PcosinaSoftPink.copy(alpha = 0.55f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgressSectionHeader(
                title = "Logged nutrition",
                subtitle = if (summary.loggedMeals > 0) {
                    "Estimated from ${summary.loggedMeals} logged planned meal${if (summary.loggedMeals == 1) "" else "s"}."
                } else {
                    "Log planned meals to build this summary."
                },
                trailing = if (summary.missingDetails > 0) "partial data" else null,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressNutritionTile("Calories", "${summary.calories} kcal", PcosinaPink, Modifier.weight(1f))
                ProgressNutritionTile("Protein", "${summary.proteinGrams} g", Color(0xFF00A863), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProgressNutritionTile("Carbs", "${summary.carbsGrams} g", Color(0xFFFF9F43), Modifier.weight(1f))
                ProgressNutritionTile("Fiber", "${summary.fiberGrams} g", Color(0xFF6E6BFF), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProgressMealResponseCard(
    summary: MealResponseSummary,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val enoughData = summary.checkInCount >= 3
    RefinedOverviewCard(
        modifier = modifier,
        containerColor = Color.White,
        borderColor = PcosinaSoftPink.copy(alpha = 0.55f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgressSectionHeader(
                title = "Meal response",
                subtitle = if (enoughData) {
                    "${summary.checkInCount} meal check-ins across ${summary.dayCount} day${if (summary.dayCount == 1) "" else "s"}."
                } else {
                    "Complete at least 3 meal check-ins to show a pattern."
                },
                trailing = if (enoughData) "self-reported" else "${summary.checkInCount}/3",
            )
            if (enoughData) {
                ProgressResponseRow("Energy", summary.averageEnergy, Color(0xFF22B35A))
                ProgressResponseRow("Fullness", summary.averageFullness, Color(0xFF00A863))
                ProgressResponseRow("Cravings", summary.averageCravings, Color(0xFFE2526E))
                ProgressResponseRow("Satisfaction", summary.averageSatisfaction, Color(0xFF6E6BFF))
            } else {
                Text(
                    text = "No symptom trend is shown until the app has enough real check-in data.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PcosinaMuted,
                )
            }
        }
    }
}

@Composable
private fun ProgressSectionHeader(
    title: String,
    subtitle: String,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
            )
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .background(Color(0xFFFFF2F5), RoundedCornerShape(999.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressMetricChip(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .background(Color(0xFFFFF7FA), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

@Composable
private fun ProgressNutritionTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun ProgressResponseRow(
    label: String,
    average: Double?,
    color: Color,
) {
    val progress = ((average ?: 0.0) / 5.0).toFloat().coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(92.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(Color(0xFFF1E6EA), RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(color, RoundedCornerShape(999.dp))
            )
        }
        Text(
            text = average?.let { String.format(Locale.ENGLISH, "%.1f/5", it) } ?: "--",
            modifier = Modifier.width(50.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ProgressCalendarCard(
    visibleMonth: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    days: List<ProgressCalendarDay>,
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
        selectedDay.reflectionSummaries.isNotEmpty()
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Calendar history",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaPink,
                )
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
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous month",
                    tint = PcosinaPink,
                )
            }
            Text(
                text = visibleMonth.format(monthFormatter),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
            )
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next month",
                    tint = PcosinaPink,
                )
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
                            title = "Meal check-in notes",
                            items = selectedDay.checkInSummaries,
                        )
                    }
                    if (selectedDay.reflectionSummaries.isNotEmpty()) {
                        ProgressCalendarTextGroup(
                            title = "Daily reflection notes",
                            items = selectedDay.reflectionSummaries,
                        )
                    }
                }
                Text(
                    text = when {
                        selectedDay.isFuture -> "Future days are locked and cannot be edited."
                        selectedDay.isToday && selectedDay.pendingMeals.isEmpty() ->
                            "Today's planned meals are already recorded."
                        selectedDay.isToday -> "Today can still be updated through meal logging or skipped planned meals."
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
                text = "+${items.size - 4} more records for this date.",
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
private fun ProgressWeeklyHighlightsCard(
    weekStart: LocalDate,
    weekNodes: List<ProgressWeekNode>,
    weeklyHighlights: List<ProgressWeeklyHighlight>,
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
            Text(
                text = leadHighlight?.let {
                    "This recap uses saved meals, budget, and check-ins. ${it.action}"
                } ?: "This recap updates once meals or check-ins are saved.",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = PcosinaDeepRose,
            )
            weeklyHighlights.take(3).forEach { highlight ->
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
                        text = "Got it",
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
private fun ProgressWeightSupportCard(
    summary: WeightGoalGuardrailSummary,
    weightUnitLabel: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val paceValue = summary.weeklyChangeKg?.let { weeklyKg ->
        val display = if (weightUnitLabel == "lb") weeklyKg * 2.20462 else weeklyKg
        String.format(Locale.ENGLISH, "%.2f %s/week", kotlin.math.abs(display), weightUnitLabel)
    }
    RefinedOverviewCard(
        modifier = modifier,
        containerColor = Color(0xFFF8FFF9),
        borderColor = Color(0xFF009A57).copy(alpha = 0.22f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Weight support",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF009A57),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RefinedStatusPill(
                text = when (summary.status) {
                    "within_guardrail" -> "Ready"
                    "review_pace" -> "Review"
                    "direction_only" -> "Date needed"
                    "maintenance" -> "Maintain"
                    else -> "Optional"
                },
                containerColor = Color.White,
                contentColor = PcosinaDeepRose
            )
        }
        Text(
            text = summary.detail,
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
        if (paceValue != null) {
            ProgressLevelPill(label = "Weekly pace", value = paceValue)
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
            Text(
                text = highlight.action,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
            )
        }
    }
}

@Composable
private fun ProgressDropdownCard(
    title: String,
    value: String,
    subtitle: String,
    sectionLabel: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
    content: @Composable () -> Unit,
) {
    RefinedOverviewCard(
        modifier = modifier.fillMaxWidth(),
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
                if (!sectionLabel.isNullOrBlank()) {
                    Text(
                        text = sectionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaPink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
    }
}

@Composable
private fun ProgressBmiCard(
    bmiValue: Double,
    bmiLabel: String,
    bmiCategory: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val scalePosition = if (bmiValue > 0.0) {
        ((bmiValue.toFloat() - BmiScaleMin) / (BmiScaleMax - BmiScaleMin)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val indicatorColor = when {
        bmiValue <= 0.0 -> PcosinaMuted
        bmiValue < 18.5 -> Color(0xFF5B8CFF)
        bmiValue < 25.0 -> Color(0xFF18B76A)
        bmiValue < 30.0 -> Color(0xFFFFA91F)
        else -> Color(0xFFE2556C)
    }
    val categoryMessage = remember(bmiCategory) { bmiCategory.toFriendlyBmiMessage() }
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
                        text = "Your BMI",
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
                        text = "Update height and weight in Settings to keep this current.",
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
            ProgressBmiScaleBar(
                position = scalePosition,
                indicatorColor = indicatorColor
            )
            Text(
                text = categoryMessage,
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
    indicatorColor: Color,
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
            val segments = listOf(
                Triple(BmiScaleMin, 18.5f, Color(0xFF5B8CFF)),
                Triple(18.5f, 25f, Color(0xFF18B76A)),
                Triple(25f, 30f, Color(0xFFFFBE45)),
                Triple(30f, BmiScaleMax, Color(0xFFE2556C))
            )
            fun xFor(value: Float): Float =
                (((value - BmiScaleMin) / (BmiScaleMax - BmiScaleMin)).coerceIn(0f, 1f)) * size.width
            segments.forEach { (startValue, endValue, color) ->
                drawLine(
                    color = color,
                    start = Offset(xFor(startValue), y),
                    end = Offset(xFor(endValue), y),
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
                color = indicatorColor,
                radius = 4.dp.toPx(),
                center = Offset(indicatorX, y)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(
                "Under" to (18.5f - BmiScaleMin),
                "Normal" to (25f - 18.5f),
                "Over" to (30f - 25f),
                "Obese" to (BmiScaleMax - 30f)
            ).forEach { (label, rangeWidth) ->
                Text(
                    text = label,
                    modifier = Modifier.weight(rangeWidth),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4A3A40),
                    maxLines = 1,
                    textAlign = TextAlign.Center
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
            text = "Stored locally on this device. These entries stay private here and only support your progress review.",
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
                    satisfaction = checkIn.satisfactionLevel
                )
            }
            if (day.mealCheckIns.size > 3) {
                Text(
                    text = "+${day.mealCheckIns.size - 3} more meal check-ins saved for this day.",
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
    }
}

@Composable
private fun ProgressSavingsCard(
    periodLabel: String,
    summary: String,
    spendStats: List<ProgressSpendStat>,
    hasActualSpend: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = periodLabel,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.Black
            )
            RefinedStatusPill(
                text = "Estimate only",
                containerColor = Color(0xFFFFF2F5),
                contentColor = PcosinaDeepRose
            )
        }
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

private const val BmiScaleMin = 15f
private const val BmiScaleMax = 40f

private fun String.toFriendlyBmiMessage(): String = when {
    equals("Normal", ignoreCase = true) -> "Your BMI is within the normal range."
    equals("Underweight", ignoreCase = true) -> "Your BMI is below the normal range."
    equals("Overweight", ignoreCase = true) -> "Your BMI is above the normal range."
    equals("Obese", ignoreCase = true) -> "Your BMI is in the obesity range."
    isBlank() || this == "--" -> "Add height and weight in Settings to calculate BMI."
    else -> "BMI category: $this"
}

@Composable
private fun ProgressInsightsCard(
    planMetrics: com.pcosina.app.ui.PlanMetrics,
    currentPlan: PlannerPlanResponse?,
    modifier: Modifier = Modifier,
) {
    val avgCalories = currentPlan.averagePlanCalories()
    val calorieTarget = currentPlan?.explanation?.targetCalories?.takeIf { it > 0 }
    val proteinTarget = currentPlan?.explanation?.targetProtein?.takeIf { it > 0 }
    val carbsTarget = currentPlan?.explanation?.targetCarbs?.takeIf { it > 0 }
    val fiberTarget = currentPlan?.explanation?.fiberMinTarget?.takeIf { it > 0 }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProgressMacroValueRow(
            label = "Calories",
            valueText = "$avgCalories kcal",
            targetText = calorieTarget?.let { "Target: $it kcal" } ?: "Target not set",
            color = PcosinaPink,
        )
        ProgressMacroValueRow(
            label = "Protein",
            valueText = "${planMetrics.avgProtein} g",
            targetText = proteinTarget?.let { "Target: $it g" } ?: "Target not set",
            color = Color(0xFF00A863),
        )
        ProgressMacroValueRow(
            label = "Carbs",
            valueText = "${planMetrics.avgCarbs} g",
            targetText = carbsTarget?.let { "Target: $it g" } ?: "Target not set",
            color = Color(0xFFFFB171),
        )
        ProgressMacroValueRow(
            label = "Fiber",
            valueText = "${planMetrics.avgFiber} g",
            targetText = fiberTarget?.let { "Target: at least $it g" } ?: "Target not set",
            color = Color(0xFF8E93FF),
        )
    }
}

@Composable
private fun ProgressSymptomManagementCard(
    metrics: List<ProgressSymptomMetric>,
    summary: ProgressTrendSummary,
) {
    val hasEnoughData = summary.checkInDays >= 5
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!hasEnoughData) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF2F5),
                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f)),
            ) {
                Text(
                    text = "Complete at least 3 meal check-ins to show meal response patterns.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PcosinaDeepRose,
                )
            }
        } else {
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
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = PcosinaDeepRose,
            maxLines = 2,
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
            modifier = Modifier.widthIn(min = 44.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose
        )
    }
}

@Composable
private fun ProgressBottomCtaCard(
    completedMealsToday: Int,
    plannedMealsToday: Int,
    weekReviewed: Boolean,
    reviewEnabled: Boolean,
    onReviewWeek: () -> Unit,
    compact: Boolean,
) {
    RefinedOverviewCard(
        containerColor = Color(0xFFFFF8FB),
        borderColor = PcosinaPink.copy(alpha = 0.22f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (!reviewEnabled) {
                    "Historical week"
                } else if (weekReviewed) {
                    "Week reviewed"
                } else if (completedMealsToday >= plannedMealsToday && plannedMealsToday > 0) {
                    "Today's planned meals are recorded"
                } else {
                    "Review this week"
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = PcosinaDeepRose
                )
            )
            Text(
                text = if (!reviewEnabled) {
                    "This saved week is available for review. Plan tuning stays attached to the current week."
                } else if (weekReviewed) {
                    "Plan feedback is saved for the next weekly plan."
                } else {
                    "Choose what should change before starting the next week."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = PcosinaMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            RefinedPrimaryButton(
                text = if (weekReviewed) "Edit plan tuning" else "Review week",
                onClick = onReviewWeek,
                enabled = reviewEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_open_weekly_review_cta")
            )
        }
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

private fun buildProgressWeeklyHighlights(
    estimatedWeeklyCost: Int?,
    budgetTarget: Int?,
    planMetrics: com.pcosina.app.ui.PlanMetrics,
    weeklyMealSummary: WeeklyMealSummary,
    weekNodes: List<ProgressWeekNode>,
    weekStart: LocalDate,
    logs: Map<String, DailyLog>,
): List<ProgressWeeklyHighlight> {
    val savingsBody = when {
        estimatedWeeklyCost != null && budgetTarget != null ->
            "Estimated Remaining Budget: your current plan is estimated against your ₱$budgetTarget weekly budget."
        else ->
            "Estimated Remaining Budget appears after you set a weekly budget and generate a plan."
    }
    val savingsAction = when {
        estimatedWeeklyCost != null && budgetTarget != null ->
            "Next: save Too expensive if this plan still feels costly."
        else ->
            "Next: set a weekly budget in Settings."
    }
    val balanceBody = when {
        planMetrics.avgFiber >= 20 && planMetrics.avgProtein >= 45 ->
            "Your plan has a solid mix of fiber and protein for a steadier week."
        planMetrics.avgFiber > 0 || planMetrics.avgProtein > 0 ->
            "Your nutrition balance is visible now. Use swaps if meals feel too heavy or too light."
        else ->
            "Generate a plan to see your nutrition balance here."
    }
    val balanceAction = when {
        planMetrics.avgFiber >= 20 && planMetrics.avgProtein >= 45 ->
            "Next: keep the high-fiber, protein-rich meals you actually ate."
        planMetrics.avgFiber > 0 || planMetrics.avgProtein > 0 ->
            "Next: swap meals that felt too heavy or too light."
        else ->
            "Next: generate a plan to get macro guide ranges."
    }
    val followedMealType = mostFollowedMealType(logs, weekStart)
    val followedBody = followedMealType?.let { (label, count) ->
        "You followed your ${label.lowercase(Locale.ENGLISH)} plans most consistently this week."
    } ?: "Log planned meals to see which meal type you followed most this week."
    val followedAction = followedMealType?.let { (label, count) ->
        "Saved $count ${label.lowercase(Locale.ENGLISH)} check-in${if (count == 1) "" else "s"} this week."
    } ?: "Next: log a planned meal from the Plan page."
    return listOf(
        ProgressWeeklyHighlight(
            label = "PHP",
            title = "Weekly Savings",
            body = savingsBody,
            action = savingsAction,
            color = Color(0xFFE2526E),
        ),
        ProgressWeeklyHighlight(
            label = "BAL",
            title = "Your Body's Balance",
            body = balanceBody,
            action = balanceAction,
            color = Color(0xFF009A57),
        ),
        ProgressWeeklyHighlight(
            label = "MEAL",
            title = "Most Followed Meal Type",
            body = followedBody,
            action = followedAction,
            color = Color(0xFFE2526E),
        ),
    )
}

private fun mostFollowedMealType(
    logs: Map<String, DailyLog>,
    weekStart: LocalDate,
): Pair<String, Int>? {
    val counts = mutableMapOf<String, Int>()
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    (0..6).forEach { offset ->
        val dateKey = weekStart.plusDays(offset.toLong()).format(dateFormatter)
        val log = logs[dateKey] ?: return@forEach
        val labels = (log.completedMealIds.mapNotNull { key ->
            ProgressViewModel.extractMealLabel(key)
        } + log.mealCheckIns.map { it.mealLabel })
            .map { it.toCalendarTitle() }
            .filter { it.isNotBlank() && it != "Saved Meal Record" }
            .distinct()
        labels.forEach { label ->
            counts[label] = (counts[label] ?: 0) + 1
        }
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()
        ?.let { it.key to it.value }
}

private fun buildProgressCalendarDays(
    visibleMonth: YearMonth,
    logs: Map<String, DailyLog>,
    weekStart: LocalDate,
    currentPlan: PlannerPlanResponse?,
    today: LocalDate,
    profileWeightLb: Boolean,
): List<ProgressCalendarDay> {
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
        val hasWeeklyProgress = false
        val hasLog = completedMealCount > 0 || hasMealCheckIn || hasReflection
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
            reflectionSummaries = buildCalendarReflectionSummaries(log, profileWeightLb),
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
        log.symptomSeverityByTag.isNotEmpty() ||
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
        }
    }
}

private fun PlannerPlanResponse?.averagePlanCalories(): Int {
    this?.explanation?.avgCalories?.takeIf { it > 0 }?.let { return it }
    val plannedCalories = this?.days.orEmpty()
        .map { it.totalCalories }
        .filter { it > 0 }
    return plannedCalories.takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0
}

@Composable
private fun ProgressMacroValueRow(
    label: String,
    valueText: String,
    targetText: String,
    color: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF7FA),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(color, CircleShape)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                )
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.labelSmall,
                    color = PcosinaMuted,
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun buildCalendarReflectionSummaries(log: DailyLog?, profileWeightLb: Boolean): List<String> {
    if (log == null) return emptyList()
    val lines = mutableListOf<String>()
    log.energyLevel?.let { lines += "Energy: $it/5" }
    log.moodLevel?.let { lines += "Mood: $it/5" }
    log.cravingsLevel?.let { lines += "Cravings: $it/5" }
    log.weightKg?.let { lines += "Weight record: ${formatCalendarWeight(it, profileWeightLb)}" }
    if (log.symptomSeverityByTag.isNotEmpty()) {
        lines += "Symptom severity: ${
            log.symptomSeverityByTag.entries.joinToString(", ") { (label, level) -> "$label $level/5" }
        }"
    }
    if (log.symptomTags.isNotEmpty()) lines += "Self-reported symptoms: ${log.symptomTags.joinToString(", ")}"
    if (!log.symptomsNote.isNullOrBlank()) lines += "Symptom note: ${log.symptomsNote.compactProgressNote()}"
    if (!log.journalText.isNullOrBlank()) lines += "Journal note: ${log.journalText.compactProgressNote()}"
    return lines
}

private fun formatCalendarWeight(weightKg: Float, profileWeightLb: Boolean): String {
    val displayValue = if (profileWeightLb) UnitConverter.kgToLb(weightKg) else weightKg
    val unit = if (profileWeightLb) "lb" else "kg"
    return "${String.format(Locale.ENGLISH, "%.2f", displayValue)} $unit"
}

private fun String.compactProgressNote(maxLength: Int = 96): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength - 1).trimEnd() + "..."
    }
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
    val symptomStability = summary.averageSymptomSeverity
        ?.let { ((6f - it.toFloat()) / 5f).coerceIn(0f, 1f) }
        ?: 0f
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
            label = "Symptom stability",
            valueText = if (summary.symptomSeverityDays > 0) "${(symptomStability * 100).toInt()}%" else "--",
            progress = symptomStability,
            color = Color(0xFF7B61FF)
        )
    )
}

private fun buildLoggedNutritionSummary(
    weeklyMealSummary: WeeklyMealSummary,
    recipeDetails: Map<String, PlannerRecipeDetail?>,
): LoggedNutritionSummary {
    val loggedSlots = weeklyMealSummary.mealSlots.filter { it.status == ProgressMealSlotStatus.COMPLETED }
    var missingDetails = 0
    var calories = 0
    var protein = 0
    var carbs = 0
    var fiber = 0
    loggedSlots.forEach { slot ->
        val detail = recipeDetails[slot.recipeId]
        if (detail == null) {
            missingDetails += 1
        } else {
            calories += detail.calories ?: 0
            protein += detail.proteinGrams ?: 0
            carbs += detail.carbsGrams ?: 0
            fiber += detail.fiberGrams ?: 0
        }
    }
    return LoggedNutritionSummary(
        loggedMeals = loggedSlots.size,
        calories = calories,
        proteinGrams = protein,
        carbsGrams = carbs,
        fiberGrams = fiber,
        missingDetails = missingDetails,
    )
}

private fun buildMealResponseSummary(
    logs: Map<String, DailyLog>,
    weekStart: LocalDate,
    today: LocalDate,
): MealResponseSummary {
    val weekEnd = weekStart.plusDays(6)
    val cappedEnd = if (today.isBefore(weekEnd)) today else weekEnd
    val scoped = logs.values.mapNotNull { log ->
        val date = runCatching { LocalDate.parse(log.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
            ?: return@mapNotNull null
        if (date.isBefore(weekStart) || date.isAfter(cappedEnd)) null else date to log
    }
    val checkIns = scoped.flatMap { it.second.mealCheckIns }
    fun averageOf(selector: (MealCheckIn) -> Int?): Double? =
        checkIns.mapNotNull(selector).takeIf { it.isNotEmpty() }?.average()
    return MealResponseSummary(
        checkInCount = checkIns.size,
        dayCount = scoped.count { it.second.mealCheckIns.isNotEmpty() },
        averageEnergy = averageOf { it.energyLevel },
        averageFullness = averageOf { it.fullnessLevel },
        averageCravings = averageOf { it.cravingsLevel },
        averageSatisfaction = averageOf { it.satisfactionLevel },
    )
}

private fun ProgressMealSlotStatus.matrixColor(): Color = when (this) {
    ProgressMealSlotStatus.COMPLETED -> Color(0xFF247A45)
    ProgressMealSlotStatus.SKIPPED -> Color(0xFFFFA91F)
    ProgressMealSlotStatus.MISSED -> Color(0xFFE2526E)
    ProgressMealSlotStatus.PENDING -> Color(0xFFFFEEF3)
    ProgressMealSlotStatus.FUTURE -> Color(0xFFE8E1E4)
}

private fun Int.pesoLabel(): String = "₱$this"

private fun Int.signedPesoLabel(): String =
    if (this >= 0) "₱$this" else "-₱${kotlin.math.abs(this)}"

private fun PlanInstance.weekRangeLabel(): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    val start = runCatching { LocalDate.parse(weekStart, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    val end = runCatching { LocalDate.parse(weekEnd, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    return if (start != null && end != null) {
        "${start.format(formatter)} - ${end.format(formatter)}"
    } else {
        response.weekLabel.ifBlank { weekStart.ifBlank { id } }
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
    duePlannedMeals = duePlannedMeals,
    skippedMeals = skippedMeals,
    missedMeals = missedMeals,
    pendingMeals = pendingMeals,
    weeklyCompletionPercent = weeklyCompletionPercent,
    chartPoints = chartPoints.map { it.toUiChartPoint() },
    mealSlots = mealSlots.map { slot ->
        ProgressMealSlotCell(
            dayLabel = slot.dayLabel,
            mealLabel = slot.mealLabel,
            recipeId = slot.recipeId,
            status = slot.status,
        )
    },
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
