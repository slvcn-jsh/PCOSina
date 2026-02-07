package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.util.buildMealReasons
import com.google.firebase.analytics.FirebaseAnalytics
import com.pcosina.app.data.api.RecipeSummaryDto
import kotlinx.coroutines.launch
import java.util.Locale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

private data class SwapTarget(
    val dayIndex: Int,
    val mealIndex: Int,
    val mealLabel: String,
    val recipeId: String,
    val mealTitle: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by mealPlanViewModel.uiState.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()
    val planExpired by mealPlanViewModel.planExpired.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val currentPlan = (uiState as? MealPlanUiState.Success)?.response
    val userProfile by userViewModel.userProfile.collectAsState()
    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val analytics = FirebaseAnalytics.getInstance(context)
    val isOnline = remember { mutableStateOf(isNetworkAvailable(context)) }
    LaunchedEffect(Unit) {
        isOnline.value = isNetworkAvailable(context)
    }
    val colorScheme = MaterialTheme.colorScheme
    val weekStartDate = remember(activeWeekStart) {
        val base = activeWeekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        base.with(TemporalAdjusters.previousOrSame(firstDay))
    }
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showConfidenceInfo by rememberSaveable { mutableStateOf(false) }
    
    // Track if we are currently extracting ingredients
    var isSyncingGroceries by remember { mutableStateOf(false) }
    var syncSuccess by remember { mutableStateOf(false) }
    var swapTarget by remember { mutableStateOf<SwapTarget?>(null) }
    var swapOptions by remember { mutableStateOf<List<RecipeSummaryDto>>(emptyList()) }
    var swapQuery by remember { mutableStateOf("") }
    var swapLoading by remember { mutableStateOf(false) }
    var swapApplying by remember { mutableStateOf(false) }
    var swapError by remember { mutableStateOf<String?>(null) }
    val swapSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(syncSuccess) {
        if (syncSuccess) {
            kotlinx.coroutines.delay(2000)
            syncSuccess = false
        }
    }
    LaunchedEffect(activePlanId) {
        selectedDayIndex = 0
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { padding ->
        when (val state = uiState) {
            is MealPlanUiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().background(colorScheme.background).padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!isOnline.value) {
                            Text(
                                text = "Offline. Connect to the internet to generate your first plan.",
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        Button(
                            onClick = {
                                isOnline.value = isNetworkAvailable(context)
                                if (!isOnline.value) {
                                    mealPlanViewModel.showError("Offline. Connect to the internet to generate a new plan.")
                                    return@Button
                                }
                                analytics.logEvent("generate_plan", null)
                                mealPlanViewModel.generateMealPlan(userProfile)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.height(56.dp).padding(horizontal = 32.dp)
                        ) {
                            Text(
                                if (isOnline.value) "Generate My Optimized Plan" else "Generate (Internet required)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (planHistory.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "View past weeks",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(planHistory.sortedByDescending { it.weekStart }) { instance ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { mealPlanViewModel.selectPlan(instance.id) },
                                        label = { Text(instance.response.weekLabel) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is MealPlanUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().background(colorScheme.background).padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("MILP Engine is optimizing...", color = colorScheme.secondary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "First run can take up to ~30s. Please keep the app open.",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            is MealPlanUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        if (!isOnline.value) {
                            Text(
                                text = "You are offline. Saved plans will still be available.",
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Button(onClick = {
                            isOnline.value = isNetworkAvailable(context)
                            if (!isOnline.value) {
                                mealPlanViewModel.showError("Offline. Connect to the internet to generate a new plan.")
                                return@Button
                            }
                            mealPlanViewModel.generateMealPlan(userProfile)
                        }) { Text("Retry") }
                    }
                }
            }
            is MealPlanUiState.Success -> {
                val plan = state.response
                val planByLabel = plan.days.associateBy { it.dayLabel.lowercase(Locale.ENGLISH) }
                val selectedLabel = dayLabels.getOrNull(selectedDayIndex) ?: "Mon"
                val selectedDay = planByLabel[selectedLabel.lowercase(Locale.ENGLISH)]
                val explanation = plan.explanation
                val selectedMeals = selectedDay?.meals.orEmpty()
                val selectedDayLabel = selectedDay?.dayLabel ?: selectedLabel
                val selectedDayCalories = selectedDay?.totalCalories ?: 0
                val recipeCounts = remember(plan) {
                    plan.days.flatMap { it.meals }.groupingBy { it.recipeId }.eachCount()
                }
                LaunchedEffect(plan.weekLabel) {
                    swapTarget = null
                    swapOptions = emptyList()
                    swapQuery = ""
                    swapLoading = false
                    swapApplying = false
                    swapError = null
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(colorScheme.background).padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                item {
                    if (!isOnline.value) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Text(
                                    text = "Offline mode: showing your last saved plan.",
                                    modifier = Modifier.padding(14.dp),
                                    color = colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        GradientHeader(
                            title = "PCOS-Optimized Plan",
                            subtitle = "Target: ${userViewModel.dailyCalorieTarget} kcal/day",
                            containerHeight = 180
                        )
                        if (planExpired) {
                            Spacer(Modifier.height(10.dp))
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "This week has ended.",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Generate a new week to keep tracking current meals.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                isOnline.value = isNetworkAvailable(context)
                                if (!isOnline.value) {
                                    mealPlanViewModel.showError("Offline. Connect to the internet to generate a new plan.")
                                    return@Button
                                }
                                mealPlanViewModel.generateMealPlan(userProfile)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Generate New Week", fontWeight = FontWeight.Bold)
                        }
                        if (planHistory.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Week History",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(planHistory.sortedByDescending { it.weekStart }) { instance ->
                                    val selected = instance.id == activePlanId
                                    FilterChip(
                                        selected = selected,
                                        onClick = { mealPlanViewModel.selectPlan(instance.id) },
                                        label = { Text(instance.response.weekLabel) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colorScheme.primary,
                                            selectedLabelColor = colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                        }
                        if (userProfile.goal.contains("Symptom", true)) {
                            Text(
                                text = "Low‑GI guidance: favor high‑fiber carbs and balanced meals.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                            )
                        }
                        val weeklyBudget = if (userProfile.weeklyBudgetPhp > 0) userProfile.weeklyBudgetPhp else 2000
                        Spacer(Modifier.height(12.dp))
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Weekly Budget", style = MaterialTheme.typography.bodyMedium)
                                Text("₱$weeklyBudget", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }

                        if (explanation != null) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Optimization Notes",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "These are solver signals used to balance nutrition, variety, and pantry use.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    explanation.confidenceScore?.let { score ->
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
                                    val avgDev = explanation.avgCaloriesDeviation
                                    if (explanation.targetCalories != null) {
                                        items.add("Target calories: ${explanation.targetCalories} kcal/day")
                                    }
                                    if (explanation.avgCalories != null) {
                                        val devText = if (avgDev != null) " (±$avgDev)" else ""
                                        items.add("Avg calories: ${explanation.avgCalories} kcal/day$devText")
                                    }
                                    val targetMacros = listOf(
                                        explanation.targetProtein?.let { "P ${it}g" },
                                        explanation.targetCarbs?.let { "C ${it}g" },
                                        explanation.targetFats?.let { "F ${it}g" }
                                    ).filterNotNull()
                                    if (targetMacros.isNotEmpty()) {
                                        items.add("Macro targets: ${targetMacros.joinToString(" • ")}")
                                    }
                                    val avgMacros = listOf(
                                        explanation.avgProtein?.let { "P ${it}g" },
                                        explanation.avgCarbs?.let { "C ${it}g" },
                                        explanation.avgFats?.let { "F ${it}g" }
                                    ).filterNotNull()
                                    if (avgMacros.isNotEmpty()) {
                                        items.add("Avg macros: ${avgMacros.joinToString(" • ")}")
                                    }
                                    val constraintItems = mutableListOf<String>()
                                    explanation.toleranceUsed?.let {
                                        val pct = String.format(Locale.ENGLISH, "%.0f", it * 100)
                                        constraintItems.add("Tolerance used: $pct%")
                                    }
                                    explanation.maxPerWeek?.let {
                                        constraintItems.add("Max repeats per recipe: $it")
                                    }
                                    if (explanation.budgetWeekly != null || explanation.estimatedWeeklyCost != null) {
                                        val budget = explanation.budgetWeekly?.let {
                                            "₱" + String.format(Locale.ENGLISH, "%.0f", it)
                                        }
                                        val est = explanation.estimatedWeeklyCost?.let { "₱$it" }
                                        val text = when {
                                            budget != null && est != null -> "Budget weekly: $budget (est $est)"
                                            budget != null -> "Budget weekly: $budget"
                                            est != null -> "Estimated weekly cost: $est"
                                            else -> null
                                        }
                                        if (text != null) constraintItems.add(text)
                                    }
                                    explanation.restrictionCount?.let {
                                        constraintItems.add("Restriction count: $it")
                                    }
                                    explanation.pantryMatches?.let {
                                        items.add("Pantry matches used: $it")
                                    }
                                    explanation.uniqueVegTokens?.let {
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

                    // 1. Generate Grocery List Action (feedback + snackbar)
                    item {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ready to shop?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text("Consolidate all 21 meals", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = { 
                                        isOnline.value = isNetworkAvailable(context)
                                        if (!isOnline.value) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Sync requires internet for recipe details.")
                                            }
                                            return@Button
                                        }
                                        analytics.logEvent("sync_groceries", null)
                                        isSyncingGroceries = true
                                        mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
                                            if (sources.isEmpty()) {
                                                isSyncingGroceries = false
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("No items to sync yet")
                                                }
                                                return@extractGrocerySourcesForPlan
                                            }
                                            groceryViewModel.setPlanSources(sources)
                                            isSyncingGroceries = false
                                            syncSuccess = true
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Synced to Grocery List")
                                            }
                                        }
                                    },
                                    enabled = !isSyncingGroceries && isOnline.value,
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                                ) {
                                    when {
                                        isSyncingGroceries -> {
                                            CircularProgressIndicator(color = colorScheme.onPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        }
                                        syncSuccess -> {
                                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Synced")
                                        }
                                        else -> {
                                            Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (isOnline.value) "Sync" else "Sync (Internet required)")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Day selector chips + navigation hint
                    item {
                        val dayCount = dayLabels.size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { if (selectedDayIndex > 0) selectedDayIndex-- },
                                enabled = selectedDayIndex > 0
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
                            }
                            Text(
                                text = "Swipe → for more days",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = { if (selectedDayIndex < dayCount - 1) selectedDayIndex++ },
                                enabled = selectedDayIndex < dayCount - 1
                            ) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
                            dayLabels.forEachIndexed { index, label ->
                                val selected = index == selectedDayIndex
                                val isToday = label.equals(todayLabel, true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedDayIndex = index },
                                    label = { Text(if (isToday) "$label • Today" else label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorScheme.primary,
                                        selectedLabelColor = colorScheme.onPrimary,
                                        labelColor = colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(dayCount) { i ->
                                Box(
                                    modifier = Modifier
                                        .size(if (i == selectedDayIndex) 8.dp else 6.dp)
                                        .background(
                                            color = if (i == selectedDayIndex) colorScheme.primary else colorScheme.surfaceVariant,
                                            shape = CircleShape
                                        )
                                )
                                if (i != dayCount - 1) Spacer(Modifier.width(6.dp))
                            }
                        }
                    }

                    // 3. Daily summary card
                    item {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = null, tint = colorScheme.primary)
                                    Column {
                                        Text(text = "${selectedDayLabel}'s Total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                        Text(text = "MILP Validated", style = MaterialTheme.typography.bodySmall, color = colorScheme.primary)
                                    }
                                }
                                Text(text = "${selectedDayCalories} kcal", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = colorScheme.secondary))
                            }
                        }
                    }

                    // 4. Meals list
                    if (selectedMeals.isEmpty()) {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Text(
                                    text = "No meals planned for $selectedDayLabel.",
                                    modifier = Modifier.padding(16.dp),
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    itemsIndexed(selectedMeals) { mealIndex, plannedMeal ->
                        Card(
                            onClick = { onRecipeClick(plannedMeal.recipeId) },
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(colorScheme.surfaceVariant, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = if(plannedMeal.mealLabel == "Breakfast") "🍳" else if(plannedMeal.mealLabel == "Lunch") "🍱" else "🥘", fontSize = 24.sp)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = plannedMeal.mealLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                                    Text(text = plannedMeal.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val reasons = remember(plannedMeal.recipeId, explanation, recipeCounts) {
                                        buildMealReasons(
                                            recipeId = plannedMeal.recipeId,
                                            recipeCounts = recipeCounts,
                                            explanation = explanation,
                                            budgetPhp = userProfile.weeklyBudgetPhp
                                        )
                                    }
                                    if (reasons.isNotEmpty()) {
                                        Text(
                                            text = "Why: " + reasons.joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        isOnline.value = isNetworkAvailable(context)
                                        if (!isOnline.value) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Swap requires internet for recipe options.")
                                            }
                                            return@IconButton
                                        }
                                        val target = SwapTarget(
                                            dayIndex = selectedDayIndex,
                                            mealIndex = mealIndex,
                                            mealLabel = plannedMeal.mealLabel,
                                            recipeId = plannedMeal.recipeId,
                                            mealTitle = plannedMeal.title
                                        )
                                        swapTarget = target
                                        swapQuery = ""
                                        swapOptions = emptyList()
                                        swapError = null
                                        swapLoading = true
                                        scope.launch {
                                            val result = mealPlanViewModel.getSwapOptions(plannedMeal.mealLabel, 40)
                                            result.onSuccess { list ->
                                                val filtered = list.filter { it.id != plannedMeal.recipeId }
                                                swapOptions = filtered
                                                if (filtered.isEmpty()) {
                                                    swapError = "No swaps available for ${plannedMeal.mealLabel}."
                                                }
                                            }.onFailure { e ->
                                                swapError = e.message ?: "Unable to load swap options."
                                            }
                                            swapLoading = false
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapHoriz,
                                        contentDescription = "Swap meal",
                                        tint = colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
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

    if (swapTarget != null && currentPlan != null) {
        val target = swapTarget!!
        val filteredOptions = remember(swapOptions, swapQuery) {
            if (swapQuery.isBlank()) swapOptions
            else swapOptions.filter { it.title.contains(swapQuery, ignoreCase = true) }
        }
        ModalBottomSheet(
            onDismissRequest = { if (!swapApplying) swapTarget = null },
            sheetState = swapSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Swap ${target.mealLabel}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Replace: ${target.mealTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = swapQuery,
                    onValueChange = { swapQuery = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Search recipes") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (swapLoading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                swapError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                if (!swapLoading && filteredOptions.isEmpty()) {
                    Text(
                        "No options found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredOptions) { option ->
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val meta = listOfNotNull(
                                            option.mealType?.takeIf { it.isNotBlank() },
                                            option.minutes?.let { "${it} min" }
                                        )
                                        if (meta.isNotEmpty()) {
                                            Text(
                                                text = meta.joinToString(" • "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(
                                        enabled = !swapApplying,
                                        onClick = {
                                            if (swapApplying) return@TextButton
                                            swapApplying = true
                                            scope.launch {
                                                try {
                                                    val mealId = mealPlanViewModel.buildMealInstanceId(
                                                        activePlanId ?: currentPlan.weekLabel,
                                                        target.dayIndex,
                                                        target.mealIndex,
                                                        target.mealLabel
                                                    )
                                                    val itemsResult = mealPlanViewModel.getGrocerySourcesForRecipe(option.id)
                                                    mealPlanViewModel.swapMeal(
                                                        target.dayIndex,
                                                        target.mealIndex,
                                                        option.id,
                                                        option.title
                                                    )
                                                    val items = itemsResult.getOrNull()
                                                    if (items != null) {
                                                        if (groceryViewModel.hasSourcesForMeal(mealId)) {
                                                            groceryViewModel.replaceMealItems(mealId, items)
                                                            if (items.isEmpty()) {
                                                                snackbarHostState.showSnackbar("Meal swapped. Grocery items cleared for this meal.")
                                                            } else {
                                                                snackbarHostState.showSnackbar("Meal swapped and grocery list updated.")
                                                            }
                                                        } else {
                                                            snackbarHostState.showSnackbar("Meal swapped. Sync groceries to update list.")
                                                        }
                                                    } else {
                                                        snackbarHostState.showSnackbar("Meal swapped. Grocery update skipped (ingredients unavailable).")
                                                    }
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar("Swap failed. Please try again.")
                                                } finally {
                                                    swapApplying = false
                                                    swapTarget = null
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Swap")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
