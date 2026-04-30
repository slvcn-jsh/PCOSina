package com.pcosina.app.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.domain.UnitConverter
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
    val weekNodes = remember(currentPlan, logs, weekStart, today) {
        buildProgressWeekNodes(currentPlan?.days.orEmpty(), logs, weekStart, today)
    }
    var showReflectionDialog by remember { mutableStateOf(false) }
    var showWeeklyReviewDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val weekStartKey = remember(weekStart) { weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val weightUnitLabel = if (profile.weightUnit == UnitConverter.WEIGHT_LB) "lb" else "kg"
    val displayedWeight = remember(todayLog?.weightKg, profile.weightUnit) {
        todayLog?.weightKg?.let { value ->
            val displayValue = if (profile.weightUnit == UnitConverter.WEIGHT_LB) {
                UnitConverter.kgToLb(value)
            } else {
                value
            }
            String.format(Locale.ENGLISH, "%.1f", displayValue)
        }.orEmpty()
    }
    var energyLevel by rememberSaveable(today.toString()) { mutableStateOf(todayLog?.energyLevel ?: 3) }
    var moodLevel by rememberSaveable(today.toString()) { mutableStateOf(todayLog?.moodLevel ?: 3) }
    var cravingsLevel by rememberSaveable(today.toString()) { mutableStateOf(todayLog?.cravingsLevel ?: 3) }
    var noteText by rememberSaveable(today.toString()) { mutableStateOf(todayLog?.symptomsNote.orEmpty()) }
    var weightInput by rememberSaveable(today.toString(), profile.weightUnit) { mutableStateOf(displayedWeight) }
    var weeklyJournalDraft by rememberSaveable(weekStartKey) { mutableStateOf(weeklyJournal) }
    var weeklySpendInput by rememberSaveable(weekStartKey) { mutableStateOf(weeklySpend?.toString().orEmpty()) }
    var selectedFeedbackTags by rememberSaveable(weekStartKey) { mutableStateOf(planFeedbackTags.toSet()) }
    val planFeedbackOptions = remember { listOf("Too repetitive", "Too expensive", "Too hard to cook") }

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
        val narrow = maxWidth < 420.dp
        val calorieTarget = userViewModel.dailyCalorieTarget
        val budgetTarget = profile.weeklyBudgetPhp.takeIf { it > 0 }

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

            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressSummaryTile(
                        title = "Today",
                        value = "$completedMealsToday/$plannedMealsToday",
                        subtitle = "meals logged",
                        modifier = Modifier.fillMaxWidth()
                    )
                    ProgressSummaryTile(
                        title = "Target",
                        value = "$calorieTarget",
                        subtitle = "kcal/day",
                        modifier = Modifier.fillMaxWidth()
                    )
                    ProgressSummaryTile(
                        title = "Spend",
                        value = weeklySpend?.let { "₱$it" } ?: "--",
                        subtitle = budgetTarget?.let { "of ₱$it" } ?: "weekly spend",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProgressSummaryTile(
                        title = "Today",
                        value = "$completedMealsToday/$plannedMealsToday",
                        subtitle = "meals logged",
                        modifier = Modifier.weight(1f)
                    )
                    ProgressSummaryTile(
                        title = "Target",
                        value = "$calorieTarget",
                        subtitle = "kcal/day",
                        modifier = Modifier.weight(1f)
                    )
                    ProgressSummaryTile(
                        title = "Spend",
                        value = weeklySpend?.let { "₱$it" } ?: "--",
                        subtitle = budgetTarget?.let { "of ₱$it" } ?: "weekly spend",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ProgressWeeklyCard(
                weekNodes = weekNodes,
                compact = compact
            )

            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressCheckInCard(
                        todayLog = todayLog,
                        profileWeightLb = profile.weightUnit == UnitConverter.WEIGHT_LB,
                        modifier = Modifier.fillMaxWidth(),
                        compact = compact
                    )

                    ProgressInsightsCard(
                        planMetrics = planMetrics,
                        currentPlan = currentPlan,
                        planFeedbackTags = planFeedbackTags,
                        modifier = Modifier.fillMaxWidth(),
                        compact = compact
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProgressCheckInCard(
                        todayLog = todayLog,
                        profileWeightLb = profile.weightUnit == UnitConverter.WEIGHT_LB,
                        modifier = Modifier.weight(1f),
                        compact = compact
                    )

                    ProgressInsightsCard(
                        planMetrics = planMetrics,
                        currentPlan = currentPlan,
                        planFeedbackTags = planFeedbackTags,
                        modifier = Modifier.weight(1f),
                        compact = compact
                    )
                }
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
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, PcosinaDeepRose.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(PcosinaBlush, Color(0xFFFF91A7))
                    )
                )
                .padding(horizontal = if (compact) 16.dp else 18.dp, vertical = if (compact) 14.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.24f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.36f))
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 12.dp else 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📈",
                        style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Track Your Progress",
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = PcosinaDeepRose.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(text = " ", modifier = Modifier.padding(vertical = 1.dp))
                }
                Text(
                    text = "See today's momentum and your week at a glance.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = PcosinaDeepRose
                )
            }
            RefinedStatusPill(
                text = dateLabel,
                containerColor = Color.White.copy(alpha = 0.82f),
                contentColor = PcosinaDeepRose
            )
        }
    }
}

@Composable
private fun ProgressWeeklyCard(
    weekNodes: List<ProgressWeekNode>,
    compact: Boolean,
) {
    RefinedOverviewCard(
        contentPadding = PaddingValues(if (compact) 14.dp else 16.dp)
    ) {
        Text(
            text = "Your Weekly Progress",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose
        )
        Text(
            text = "Check each day at a glance and spot where the week is slipping or staying steady.",
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = PcosinaMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            weekNodes.forEachIndexed { index, node ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when (node.state) {
                            ProgressNodeState.Complete -> PcosinaPink
                            ProgressNodeState.Partial -> PcosinaSoftPink
                            ProgressNodeState.Pending -> Color.White
                            ProgressNodeState.Future -> PcosinaSurfaceAlt
                        },
                        border = BorderStroke(
                            2.dp,
                            when (node.state) {
                                ProgressNodeState.Complete -> PcosinaDeepRose
                                ProgressNodeState.Partial -> PcosinaPink
                                ProgressNodeState.Pending -> PcosinaMuted.copy(alpha = 0.35f)
                                ProgressNodeState.Future -> PcosinaMuted.copy(alpha = 0.26f)
                            }
                        ),
                        modifier = Modifier.size(if (compact) 38.dp else 44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = node.valueText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (node.state == ProgressNodeState.Complete) Color.White else PcosinaDeepRose,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = PcosinaDeepRose
                    )
                }
                if (index != weekNodes.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .padding(bottom = if (compact) 26.dp else 28.dp)
                            .background(PcosinaMuted.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {}
                    }
                }
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
        contentPadding = PaddingValues(if (compact) 14.dp else 16.dp)
    ) {
        Text(
            text = "Today's Check-in",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose
        )
        ProgressLevelDisplay("Energy", todayLog?.energyLevel)
        ProgressLevelDisplay("Mood", todayLog?.moodLevel)
        ProgressLevelDisplay("Cravings", todayLog?.cravingsLevel)
        ProgressLevelDisplay("Weight", todayLog?.weightKg?.let { kg ->
            if (profileWeightLb) {
                "${String.format(Locale.ENGLISH, "%.1f", UnitConverter.kgToLb(kg))} lb"
            } else {
                "${String.format(Locale.ENGLISH, "%.1f", kg)} kg"
            }
        })
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
    currentPlan: GeneratePlanResponse?,
    planFeedbackTags: List<String>,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    RefinedOverviewCard(
        modifier = modifier,
        contentPadding = PaddingValues(if (compact) 14.dp else 16.dp)
    ) {
        Text(
            text = "Plan Insights",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose
        )
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
private fun ProgressBottomCtaCard(
    completedMealsToday: Int,
    plannedMealsToday: Int,
    weeklyJournal: String,
    onCheckIn: () -> Unit,
    onReviewWeek: () -> Unit,
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, PcosinaDeepRose.copy(alpha = 0.72f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF8BA2), Color(0xFFFFC3CE))
                    )
                )
                .padding(horizontal = if (compact) 16.dp else 18.dp, vertical = if (compact) 14.dp else 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (completedMealsToday >= plannedMealsToday && plannedMealsToday > 0) {
                        "Today's plan is on track."
                    } else {
                        "Keep today's plan moving."
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
                        "Update today's check-in here, then review the week to save spending and tune the next plan."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
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
                        modifier = Modifier.padding(horizontal = if (compact) 16.dp else 18.dp, vertical = if (compact) 14.dp else 16.dp),
                        style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall,
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
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = PcosinaMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
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
private fun ProgressLevelDisplay(
    label: String,
    value: Any?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PcosinaDeepRose
        )
        RefinedStatusPill(
            text = value?.toString() ?: "--",
            containerColor = PcosinaSoftPink.copy(alpha = 0.3f)
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

private fun buildProgressWeekNodes(
    days: List<com.pcosina.app.data.api.DayPlanDto>,
    logs: Map<String, DailyLog>,
    weekStart: LocalDate,
    today: LocalDate,
): List<ProgressWeekNode> {
    val dayCounts = days.associate { it.dayLabel.lowercase(Locale.ENGLISH) to it.meals.size }
    val formatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    return (0..6).map { offset ->
        val date = weekStart.plusDays(offset.toLong())
        val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val label = date.format(formatter).uppercase(Locale.ENGLISH).take(3)
        val planned = dayCounts[date.format(formatter).lowercase(Locale.ENGLISH)] ?: 0
        val completed = logs[dateKey]?.completedMealIds?.size ?: 0
        when {
            date.isAfter(today) -> ProgressWeekNode(label, "", ProgressNodeState.Future)
            planned == 0 -> ProgressWeekNode(label, "--", ProgressNodeState.Pending)
            completed >= planned && planned > 0 -> ProgressWeekNode(label, "✓", ProgressNodeState.Complete)
            completed > 0 -> ProgressWeekNode(label, "${((completed.toFloat() / planned.toFloat()) * 100f).toInt()}%", ProgressNodeState.Partial)
            else -> ProgressWeekNode(label, "0%", ProgressNodeState.Pending)
        }
    }
}
