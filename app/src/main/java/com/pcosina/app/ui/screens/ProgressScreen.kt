package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.core.content.FileProvider
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.MacroProgressBar
import com.pcosina.app.ui.components.StatCard
import com.pcosina.app.ui.util.buildMealReasons
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.UnitConverter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek
import java.util.Locale

@Composable
fun ProgressScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    progressViewModel: ProgressViewModel,
    userId: String,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val planState by mealPlanViewModel.uiState.collectAsState()
    val planMetrics by mealPlanViewModel.planMetrics.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val weeklyJournal by progressViewModel.weeklyJournal.collectAsState()
    val feedbackQueue by progressViewModel.feedbackQueue.collectAsState()
    val profile by userViewModel.userProfile.collectAsState()
    var showConfidenceInfo by rememberSaveable { mutableStateOf(false) }

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

    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }
    val selectedDate = weekStart.plusDays(selectedDayIndex.toLong())
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val dayLabelFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    val todayLabel = LocalDate.now().format(dayLabelFmt)

    LaunchedEffect(userId, weekStartKey, fallbackWeekStartKey) {
        if (userId.isNotBlank()) {
            progressViewModel.loadForUser(userId, weekStartKey, fallbackWeekStartKey)
        }
    }

    val planDays = (planState as? MealPlanUiState.Success)?.response?.days.orEmpty()
    val planExplanation = (planState as? MealPlanUiState.Success)?.response?.explanation
    val recipeCounts = remember(planDays) {
        planDays.flatMap { it.meals }.groupingBy { it.recipeId }.eachCount()
    }
    val planByLabel = planDays.associateBy { it.dayLabel.lowercase(Locale.ENGLISH) }
    val selectedDayLabel = selectedDate.format(dayLabelFmt).lowercase(Locale.ENGLISH)
    val selectedPlanDay = planByLabel[selectedDayLabel]
    val plannedMealsForDay = selectedPlanDay?.meals.orEmpty()
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
    var energyLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var cravingsLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var moodLevel by rememberSaveable { mutableStateOf<Int?>(null) }
    var symptomTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var symptomNote by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(logs, selectedDate) {
        val key = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val log = logs[key]
        weightInput = log?.weightKg?.let { kg ->
            if (profile.weightUnit == UnitConverter.WEIGHT_LB) {
                String.format(Locale.ENGLISH, "%.1f", UnitConverter.kgToLb(kg))
            } else {
                String.format(Locale.ENGLISH, "%.1f", kg)
            }
        } ?: ""
        energyLevel = log?.energyLevel
        cravingsLevel = log?.cravingsLevel
        moodLevel = log?.moodLevel
        symptomTags = log?.symptomTags ?: emptyList()
        symptomNote = log?.symptomsNote ?: ""
    }

    var journalText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(weeklyJournal) {
        journalText = weeklyJournal
    }

    var feedbackText by rememberSaveable { mutableStateOf("") }
    val isOnline = remember { mutableStateOf(isNetworkAvailable(context)) }
    LaunchedEffect(Unit) { isOnline.value = isNetworkAvailable(context) }
    val showWeightEntryAtTop = goalType == com.pcosina.app.domain.GoalType.WEIGHT_LOSS
    val weightUnitLabel = if (profile.weightUnit == UnitConverter.WEIGHT_LB) "lb" else "kg"
    val displayWeight: (Float) -> String = { kg ->
        val value = if (profile.weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.kgToLb(kg) else kg
        String.format(Locale.ENGLISH, "%.1f", value)
    }
    val symptomOptions = remember {
        listOf("Bloating", "Cramps", "Acne", "Headache", "Fatigue", "Mood swings")
    }

    // Macro aggregation
    var macroLabel by remember { mutableStateOf("Planned macros (avg/day, weekly plan)") }
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
        macroLabel = if (completedMacroAvailable) "Completed meals (avg/day, weekly)" else "Planned macros (avg/day, weekly)"
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

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "Weekly Insights",
                    subtitle = "Monitoring your metabolic markers",
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

        if (showStaleBanner) {
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
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

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

        if (planExplanation != null) {
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
                            text = "Plan Explanation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Signals used by the optimizer to balance nutrition, variety, and pantry use.",
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
                                    modifier = Modifier.size(20.dp)
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

        if (showWeightEntryAtTop) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Weight Entry (Selected Day)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
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
                        Button(
                            onClick = {
                                val value = weightInput.toFloatOrNull()
                                val kgValue = value?.let {
                                    if (profile.weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
                                }
                                progressViewModel.setWeight(selectedDate, kgValue)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("Save Weight")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            Text("Guidance: choose low‑GI carbs and balanced meals.", color = colorScheme.onSurfaceVariant)
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

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Daily Compliance",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.secondary
                        )
                    }
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
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Aggregated Macros",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface
                    )
                    Text(text = macroLabel, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                    if (!isOnline.value) {
                        Text(
                            text = "Macro details require internet to fetch recipe nutrition.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
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

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Weekly Journal",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.secondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
                Button(
                    onClick = { progressViewModel.saveWeeklyJournal(weekStartKey, journalText) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                ) {
                    Text("Save Weekly Reflection", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        val file = progressViewModel.exportReflections()
                        if (file == null) {
                            Toast.makeText(context, "No reflections to export.", Toast.LENGTH_SHORT).show()
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
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Export Reflections (Local)")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Daily Meal Check-off", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(weekDays) { idx, date ->
                            val label = date.format(dayLabelFmt)
                            val isToday = label == todayLabel
                            FilterChip(
                                selected = selectedDayIndex == idx,
                                onClick = { selectedDayIndex = idx },
                                label = { Text(if (isToday) "$label • Today" else label) }
                            )
                        }
                    }
                    if (plannedMealsForDay.isEmpty()) {
                        Text("No planned meals yet. Generate a plan first.", color = colorScheme.onSurfaceVariant)
                    } else {
                        plannedMealsForDay.forEach { meal ->
                            val dateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val mealKey = ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)
                            val completedIds = logs[dateKey]?.completedMealIds.orEmpty()
                            val checked = completedIds.contains(mealKey) || completedIds.contains(meal.recipeId)
                            val reasons = remember(meal.recipeId, planExplanation, recipeCounts, profile.weeklyBudgetPhp) {
                                buildMealReasons(
                                    recipeId = meal.recipeId,
                                    recipeCounts = recipeCounts,
                                    explanation = planExplanation,
                                    budgetPhp = profile.weeklyBudgetPhp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        progressViewModel.toggleMeal(selectedDate, meal.recipeId, meal.mealLabel)
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
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Daily Reflection", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "Quick check-in to observe patterns. This is not medical advice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(weekDays) { idx, date ->
                            val label = date.format(dayLabelFmt)
                            val isToday = label == todayLabel
                            FilterChip(
                                selected = selectedDayIndex == idx,
                                onClick = { selectedDayIndex = idx },
                                label = { Text(if (isToday) "$label • Today" else label) }
                            )
                        }
                    }
                    Text("Energy", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { value ->
                            FilterChip(
                                selected = energyLevel == value,
                                onClick = { energyLevel = value },
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
                                label = { Text(value.toString()) }
                            )
                        }
                    }
                    Text("Symptoms", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(symptomOptions) { symptom ->
                            val selected = symptomTags.contains(symptom)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    symptomTags = if (selected) {
                                        symptomTags.filterNot { it == symptom }
                                    } else {
                                        symptomTags + symptom
                                    }
                                },
                                label = { Text(symptom) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = symptomNote,
                        onValueChange = { symptomNote = it },
                        label = { Text("Symptoms / notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            progressViewModel.saveReflection(
                                selectedDate,
                                energyLevel,
                                cravingsLevel,
                                moodLevel,
                                symptomTags,
                                symptomNote
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                    ) {
                        Text("Save Reflection")
                    }
                }
            }
        }

        if (!showWeightEntryAtTop) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Weight Entry (Selected Day)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text("Weight ($weightUnitLabel)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val value = weightInput.toFloatOrNull()
                                val kgValue = value?.let {
                                    if (profile.weightUnit == UnitConverter.WEIGHT_LB) UnitConverter.lbToKg(it) else it
                                }
                                progressViewModel.setWeight(selectedDate, kgValue)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("Save Weight")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Send Feedback", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text("Feedback") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (feedbackText.isNotBlank()) {
                                progressViewModel.queueFeedback(feedbackText)
                                progressViewModel.trySendQueuedFeedback(isOnline.value)
                                feedbackText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                    ) {
                        Text("Send (Queued if offline)")
                    }
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
                                    TextButton(onClick = { progressViewModel.retryAllFeedback(isOnline.value) }) {
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
                                        TextButton(onClick = { progressViewModel.retryFeedback(entry.id, isOnline.value) }) {
                                            Text("Retry")
                                        }
                                    }
                                }
                                if (!entry.lastError.isNullOrBlank()) {
                                    Text(
                                        text = entry.lastError!!,
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

    if (showConfidenceInfo) {
        AlertDialog(
            onDismissRequest = { showConfidenceInfo = false },
            confirmButton = {
                TextButton(onClick = { showConfidenceInfo = false }) { Text("Got it") }
            },
            title = { Text("Confidence score") },
            text = {
                Text(
                    "Heuristic score based on how tightly the plan matches calorie targets, " +
                    "tolerance level used, repeat limits, and restriction complexity. " +
                    "Higher is better."
                )
            }
        )
    }
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

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
