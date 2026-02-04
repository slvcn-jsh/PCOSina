package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.MacroProgressBar
import com.pcosina.app.ui.components.StatCard
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
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
    val logs by progressViewModel.dailyLogs.collectAsState()
    val weeklyJournal by progressViewModel.weeklyJournal.collectAsState()
    val feedbackQueue by progressViewModel.feedbackQueue.collectAsState()

    val planTimestamp = (planState as? MealPlanUiState.Success)?.timestamp
    val weekStart = remember(planTimestamp) { weekStartDate(planTimestamp) }
    val weekLabel = remember(weekStart) { weekLabelFor(weekStart) }
    val weekStartKey = weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE)

    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }
    val selectedDate = weekStart.plusDays(selectedDayIndex.toLong())

    LaunchedEffect(userId, weekStartKey) {
        if (userId.isNotBlank()) {
            progressViewModel.loadForUser(userId, weekStartKey)
        }
    }

    val planDays = (planState as? MealPlanUiState.Success)?.response?.days.orEmpty()
    val selectedPlanDay = planDays.getOrNull(selectedDayIndex)
    val plannedMealsForDay = selectedPlanDay?.meals.orEmpty()
    val plannedMealsCount = planDays.sumOf { it.meals.size }
    val completedMealsCount = logs.filterKeys { isInWeek(it, weekStart) }
        .values.sumOf { it.completedMealIds.size }
    val adherence = if (plannedMealsCount > 0) completedMealsCount.toFloat() / plannedMealsCount else 0f

    val weightEntries = logs.filterKeys { isInWeek(it, weekStart) }.toSortedMap()
        .values.mapNotNull { it.weightKg }
    val weightDelta = if (weightEntries.size >= 2) {
        (weightEntries.last() - weightEntries.first())
    } else null

    var weightInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(logs, selectedDate) {
        val key = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val w = logs[key]?.weightKg
        weightInput = w?.toString() ?: ""
    }

    var journalText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(weeklyJournal) {
        journalText = weeklyJournal
    }

    var feedbackText by rememberSaveable { mutableStateOf("") }
    val isOnline = remember { mutableStateOf(isNetworkAvailable(context)) }
    LaunchedEffect(Unit) { isOnline.value = isNetworkAvailable(context) }

    // Macro aggregation
    var macroLabel by remember { mutableStateOf("Planned macros (avg/day)") }
    var avgProtein by remember { mutableStateOf(0) }
    var avgCarbs by remember { mutableStateOf(0) }
    var avgFats by remember { mutableStateOf(0) }

    LaunchedEffect(logs, planState) {
        val completedIds = logs.filterKeys { isInWeek(it, weekStart) }
            .values.flatMap { it.completedMealIds }.distinct()
        val sourceIds = if (completedIds.isNotEmpty()) completedIds else {
            planDays.flatMap { it.meals }.map { it.recipeId }.distinct()
        }
        macroLabel = if (completedIds.isNotEmpty()) "Completed meals (avg/day)" else "Planned macros (avg/day)"
        val details = sourceIds.mapNotNull { id ->
            mealPlanViewModel.getRecipeDetails(id).getOrNull()
        }
        val dayDivisor = if (planDays.isNotEmpty()) planDays.size else 7
        if (details.isNotEmpty()) {
            avgProtein = details.sumOf { it.proteinGrams ?: 0 } / dayDivisor
            avgCarbs = details.sumOf { it.carbsGrams ?: 0 } / dayDivisor
            avgFats = details.sumOf { it.fatsGrams ?: 0 } / dayDivisor
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
                    value = weightDelta?.let { String.format("%.1fkg", it) } ?: "—",
                    subtitle = "This Week",
                    modifier = Modifier.weight(1f),
                )
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
                    days.forEachIndexed { index, (label, _) ->
                        val date = weekStart.plusDays(index.toLong())
                        val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val planned = planDays.getOrNull(index)?.meals?.size ?: 0
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun").forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedDayIndex == idx,
                                onClick = { selectedDayIndex = idx },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (plannedMealsForDay.isEmpty()) {
                        Text("No planned meals yet. Generate a plan first.", color = colorScheme.onSurfaceVariant)
                    } else {
                        plannedMealsForDay.forEach { meal ->
                            val dateKey = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val checked = logs[dateKey]?.completedMealIds?.contains(meal.recipeId) == true
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = { progressViewModel.toggleMeal(selectedDate, meal.recipeId) })
                                Text(meal.title)
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Weight Entry (Selected Day)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight (kg)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val value = weightInput.toFloatOrNull()
                            progressViewModel.setWeight(selectedDate, value)
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
                        Text("Queue: " + feedbackQueue.joinToString { it.status }, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun weekStartDate(timestamp: Long?): LocalDate {
    val zone = ZoneId.systemDefault()
    val base = if (timestamp != null) Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate() else LocalDate.now()
    val weekFields = WeekFields.of(Locale.getDefault())
    return base.with(TemporalAdjusters.previousOrSame(weekFields.firstDayOfWeek))
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
