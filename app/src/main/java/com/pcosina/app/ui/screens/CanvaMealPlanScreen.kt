package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.PlannedMealDto
import com.pcosina.app.data.api.RecipeDetailDto
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanGenerationNotice
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.components.CanvaBrandBar
import com.pcosina.app.ui.components.CanvaCard
import com.pcosina.app.ui.components.CanvaCircularMetric
import com.pcosina.app.ui.components.CanvaGradientPanel
import com.pcosina.app.ui.components.CanvaHeroCard
import com.pcosina.app.ui.components.CanvaMetricBar
import com.pcosina.app.ui.components.CanvaPrimaryButton
import com.pcosina.app.ui.components.CanvaStatusChip
import com.pcosina.app.ui.components.canvaMealPalette
import com.pcosina.app.ui.theme.CanvaTokens
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api

private data class CanvaSwapTarget(
    val dayIndex: Int,
    val mealIndex: Int,
    val meal: PlannedMealDto,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvaMealPlanScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    onRecipeClick: (String, String?) -> Unit,
    onViewProgress: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by mealPlanViewModel.uiState.collectAsState()
    val generationNotice by mealPlanViewModel.generationNotice.collectAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val planMetrics by mealPlanViewModel.planMetrics.collectAsState()
    val profile by userViewModel.userProfile.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()

    val currentPlan = (uiState as? MealPlanUiState.Success)?.response
    val today = LocalDate.now()
    val weekStart = remember(activeWeekStart, currentPlan?.weekLabel) {
        activeWeekStart?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        } ?: today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
    }
    val dayLabels = remember(currentPlan, weekStart) {
        currentPlan?.days?.indices?.map { index -> weekStart.plusDays(index.toLong()) }.orEmpty()
    }

    var selectedDayIndex by rememberSaveable(currentPlan?.weekLabel) { mutableStateOf(0) }
    LaunchedEffect(currentPlan?.weekLabel, weekStart) {
        val todayIndex = (0..6).firstOrNull { weekStart.plusDays(it.toLong()) == today } ?: 0
        selectedDayIndex = todayIndex
    }

    val selectedDay = currentPlan?.days?.getOrNull(selectedDayIndex)
    val selectedDate = remember(weekStart, selectedDayIndex) { weekStart.plusDays(selectedDayIndex.toLong()) }
    val logKey = remember(selectedDate) { selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val completedMealIds = logs[logKey]?.completedMealIds.orEmpty()
    val selectedMeals = selectedDay?.meals.orEmpty()
    val recipeDetails = remember { mutableStateMapOf<String, RecipeDetailDto?>() }

    LaunchedEffect(selectedMeals.map { it.recipeId }.joinToString(",")) {
        selectedMeals.forEach { meal ->
            if (recipeDetails.containsKey(meal.recipeId)) return@forEach
            mealPlanViewModel.getRecipeDetails(meal.recipeId)
                .onSuccess { detail -> recipeDetails[meal.recipeId] = detail }
                .onFailure { recipeDetails[meal.recipeId] = null }
        }
    }

    val totalProtein = selectedMeals.sumOf { recipeDetails[it.recipeId]?.proteinGrams ?: 0 }
    val totalCarbs = selectedMeals.sumOf { recipeDetails[it.recipeId]?.carbsGrams ?: 0 }
    val totalFiber = selectedMeals.sumOf { recipeDetails[it.recipeId]?.fiberGrams ?: 0 }
    val targetCalories = currentPlan?.explanation?.targetCalories ?: userViewModel.dailyCalorieTarget
    val targetProtein = currentPlan?.explanation?.targetProtein ?: planMetrics.avgProtein
    val targetCarbs = currentPlan?.explanation?.targetCarbs ?: planMetrics.avgCarbs
    val targetFiber = currentPlan?.explanation?.fiberMinTarget ?: planMetrics.avgFiber

    var swapTarget by remember { mutableStateOf<CanvaSwapTarget?>(null) }
    val swapOptions = remember { mutableStateListOf<RecipeSummaryDto>() }
    var swapLoading by remember { mutableStateOf(false) }
    var swapError by remember { mutableStateOf<String?>(null) }
    var swapApplying by remember { mutableStateOf(false) }
    val swapSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(swapTarget?.meal?.recipeId, currentPlan?.weekLabel) {
        val target = swapTarget ?: return@LaunchedEffect
        val activeIds = currentPlan?.days?.flatMap { day -> day.meals.map { it.recipeId } }.orEmpty()
        swapLoading = true
        swapError = null
        swapOptions.clear()
        mealPlanViewModel.getSwapOptions(
            profile = profile,
            mealLabel = target.meal.mealLabel,
            currentRecipeId = target.meal.recipeId,
            activeRecipeIds = activeIds,
            limit = 20,
        ).onSuccess { options ->
            swapOptions += options.filter { option -> option.id != target.meal.recipeId }
        }.onFailure { error ->
            swapError = error.message ?: "Unable to load meal swap options."
        }
        swapLoading = false
    }

    if (swapTarget != null) {
        ModalBottomSheet(
            onDismissRequest = { if (!swapApplying) swapTarget = null },
            sheetState = swapSheetState,
            containerColor = Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Swap ${swapTarget?.meal?.mealLabel}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = CanvaTokens.HeadlineMaroon,
                )
                Text(
                    text = swapTarget?.meal?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CanvaTokens.Ink,
                )

                when {
                    swapLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = CanvaTokens.AccentPinkStrong)
                        }
                    }
                    !swapError.isNullOrBlank() -> {
                        CanvaCard(borderColor = CanvaTokens.RedStrong, containerColor = Color(0xFFFFF3F4)) {
                            Text(
                                text = swapError.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = CanvaTokens.RedStrong,
                            )
                        }
                    }
                    swapOptions.isEmpty() -> {
                        CanvaCard {
                            Text(
                                text = "No alternate meals are available for this slot right now.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CanvaTokens.Ink,
                            )
                        }
                    }
                    else -> {
                        swapOptions.forEach { option ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = !swapApplying,
                                        onClick = clickable@{
                                        val target = swapTarget ?: return@clickable
                                        if (swapApplying) return@clickable
                                        swapApplying = true
                                        scope.launch {
                                            val itemsResult = mealPlanViewModel.getGrocerySourcesForRecipe(option.id)
                                            mealPlanViewModel.swapMeal(
                                                dayIndex = target.dayIndex,
                                                mealIndex = target.mealIndex,
                                                newRecipeId = option.id,
                                                newTitle = option.title,
                                            )
                                            val planKey = activePlanId ?: currentPlan?.planId ?: currentPlan?.weekLabel.orEmpty()
                                            if (planKey.isNotBlank()) {
                                                val mealId = mealPlanViewModel.buildMealInstanceId(
                                                    weekLabel = planKey,
                                                    dayIndex = target.dayIndex,
                                                    mealIndex = target.mealIndex,
                                                    mealLabel = target.meal.mealLabel,
                                                )
                                                groceryViewModel.replaceMealItems(
                                                    mealId = mealId,
                                                    items = itemsResult.getOrElse { emptyList<GroceryItemSource>() },
                                                )
                                            }
                                            recipeDetails.remove(target.meal.recipeId)
                                            recipeDetails.remove(option.id)
                                            swapApplying = false
                                            swapTarget = null
                                        }
                                        },
                                    ),
                                shape = RoundedCornerShape(22.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, CanvaTokens.SoftOutline),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = option.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = CanvaTokens.Ink,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = option.minutes?.let { "$it min" } ?: "Recipe available",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CanvaTokens.SupportGray,
                                        )
                                    }
                                    CanvaStatusChip(text = "Choose")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CanvaTokens.CanvasBackground)
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            CanvaBrandBar(
                onSettings = { onNavigateToRoute(Routes.Settings) },
                onNotifications = { onNavigateToRoute(Routes.MoreTools) },
            )
        }

        item {
            CanvaHeroCard(
                title = "Plan Your Meals",
                subtitle = "Meals picked for your goal, budget, and pantry.",
            )
        }

        val noSafePlan = generationNotice as? MealPlanGenerationNotice.NoSafePlan
        if (noSafePlan != null) {
            item {
                CanvaCard(
                    containerColor = Color(0xFFFFF5F5),
                    borderColor = CanvaTokens.RedStrong,
                ) {
                    Text(
                        text = noSafePlan.message,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CanvaTokens.RedStrong,
                    )
                    noSafePlan.guidance.take(3).forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CanvaTokens.Ink,
                        )
                    }
                }
            }
        }

        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, CanvaTokens.SoftOutline),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ChevronLeft,
                                contentDescription = null,
                                tint = CanvaTokens.Ink,
                            )
                        }
                    }
                }
                itemsIndexed(dayLabels) { index, date ->
                    CanvaDaySelector(
                        date = date,
                        selected = index == selectedDayIndex,
                        onClick = { selectedDayIndex = index },
                    )
                }
                item {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = Color.White,
                        border = BorderStroke(1.dp, CanvaTokens.SoftOutline),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = CanvaTokens.Ink,
                            )
                        }
                    }
                }
            }
        }

        when (uiState) {
            MealPlanUiState.Loading -> item {
                CanvaCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(color = CanvaTokens.AccentPinkStrong)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Generating your week...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = CanvaTokens.Ink,
                            )
                            Text(
                                text = "This may take a moment while the planner builds a safe weekly set of meals.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CanvaTokens.SupportGray,
                            )
                        }
                    }
                }
            }
            is MealPlanUiState.Error -> item {
                CanvaCard(
                    containerColor = Color(0xFFFFF5F5),
                    borderColor = CanvaTokens.RedStrong,
                ) {
                    Text(
                        text = (uiState as MealPlanUiState.Error).message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CanvaTokens.RedStrong,
                    )
                    CanvaPrimaryButton(
                        text = "Generate Weekly Plan",
                        onClick = { mealPlanViewModel.generateMealPlan(profile) },
                    )
                }
            }
            else -> Unit
        }

        if (currentPlan == null && uiState !is MealPlanUiState.Loading && uiState !is MealPlanUiState.Error) {
            item {
                CanvaCard {
                    Text(
                        text = "Generate a weekly plan to unlock the daily schedule, summary card, and grocery sync.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CanvaTokens.Ink,
                    )
                    CanvaPrimaryButton(
                        text = "Generate Weekly Plan",
                        onClick = { mealPlanViewModel.generateMealPlan(profile) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (currentPlan != null) {
            itemsIndexed(selectedMeals) { mealIndex, meal ->
                val logged = isMealLogged(completedMealIds, meal)
                val dateLoggable = progressViewModel.isDateLoggable(selectedDate)
                val detail = recipeDetails[meal.recipeId]
                CanvaMealRowCard(
                    meal = meal,
                    detail = detail,
                    logged = logged,
                    logEnabled = dateLoggable,
                    onOpen = { onRecipeClick(meal.recipeId, meal.mealLabel) },
                    onToggleLog = {
                        if (dateLoggable) {
                            progressViewModel.toggleMeal(selectedDate, meal.recipeId, meal.mealLabel)
                        }
                    },
                    onSwap = {
                        swapTarget = CanvaSwapTarget(
                            dayIndex = selectedDayIndex,
                            mealIndex = mealIndex,
                            meal = meal,
                        )
                    },
                )
            }

            item {
                CanvaCard(
                    containerColor = Color(0xFFFFE7EB),
                    borderColor = CanvaTokens.Outline,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CanvaCircularMetric(
                            primaryText = "${selectedDay?.totalCalories ?: 0}",
                            secondaryText = "planned out of $targetCalories kcal target",
                            progress = if (targetCalories > 0) {
                                (selectedDay?.totalCalories ?: 0).toFloat() / targetCalories.toFloat()
                            } else {
                                0f
                            },
                            modifier = Modifier.weight(0.9f),
                        )
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = CanvaTokens.PanelPink,
                            ) {
                                Text(
                                    text = "Macronutrients",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                    color = CanvaTokens.HeadlineMaroon,
                                )
                            }
                            CanvaMetricBar(
                                label = "Protein",
                                valueText = "${totalProtein}g/${targetProtein}g",
                                progress = safeProgress(totalProtein, targetProtein),
                                barColor = Color(0xFFF9A4A5),
                            )
                            CanvaMetricBar(
                                label = "Carbs",
                                valueText = "${totalCarbs}g/${targetCarbs}g",
                                progress = safeProgress(totalCarbs, targetCarbs),
                                barColor = Color(0xFFC79B64),
                            )
                            CanvaMetricBar(
                                label = "Fibers",
                                valueText = "${totalFiber}g/${targetFiber}g",
                                progress = safeProgress(totalFiber, targetFiber),
                                barColor = Color(0xFFC2EDB5),
                            )
                        }
                    }
                }
            }

            item {
                CanvaGradientPanel(brush = CanvaTokens.MainCtaGradient) {
                    Text(
                        text = "Ready to shop?",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = CanvaTokens.HeadlineMaroon,
                    )
                    Text(
                        text = "Consolidate all ${currentPlan.days.sumOf { it.meals.size }} meals and sync to your grocery list.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CanvaTokens.HeadlineMaroon,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CanvaPrimaryButton(
                            text = "Go to Grocery",
                            onClick = { onNavigateToRoute(Routes.GroceryList) },
                        )
                        Surface(
                            modifier = Modifier.size(74.dp),
                            shape = CircleShape,
                            color = CanvaTokens.AccentPinkStrong,
                            shadowElevation = 10.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            CanvaPrimaryButton(
                text = "GENERATE A NEW WEEKLY PLAN",
                onClick = { mealPlanViewModel.generateMealPlan(profile) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CanvaDaySelector(
    date: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val outline = if (selected) CanvaTokens.AccentPinkStrong else CanvaTokens.SoftOutline
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = if (selected) Color.White else Color(0xFFFAFAFA),
        border = BorderStroke(1.dp, outline),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = date.dayOfWeek.name.take(3).lowercase(Locale.ENGLISH)
                    .replaceFirstChar { it.titlecase(Locale.ENGLISH) },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                color = if (selected) CanvaTokens.AccentPinkStrong else CanvaTokens.SupportGray,
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = if (selected) CanvaTokens.AccentPinkStrong else CanvaTokens.SupportGray,
            )
        }
    }
}

@Composable
private fun CanvaMealRowCard(
    meal: PlannedMealDto,
    detail: RecipeDetailDto?,
    logged: Boolean,
    logEnabled: Boolean,
    onOpen: () -> Unit,
    onToggleLog: () -> Unit,
    onSwap: () -> Unit,
) {
    val (iconBackground, labelColor) = canvaMealPalette(meal.mealLabel)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = if (logged) Color(0xFF2E181D) else Color.White,
        border = BorderStroke(1.dp, if (logged) Color(0xFF2E181D) else CanvaTokens.SoftOutline),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = iconBackground,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.RestaurantMenu,
                        contentDescription = null,
                        tint = labelColor,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${meal.mealLabel} • Tap for more details",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (logged) Color.White.copy(alpha = 0.9f) else CanvaTokens.Ink,
                )
                Text(
                    text = meal.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (logged) Color.White else Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                detail?.let {
                    Text(
                        text = listOfNotNull(
                            detail.calories?.let { kcal -> "$kcal kcal" },
                            detail.minutes?.let { mins -> "$mins min" },
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (logged) Color.White.copy(alpha = 0.72f) else CanvaTokens.SupportGray,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.clickable(enabled = logEnabled, onClick = onToggleLog),
                    shape = RoundedCornerShape(999.dp),
                    color = if (logged) Color(0xFF4E2530) else Color.White,
                    border = BorderStroke(1.dp, if (logged) Color(0xFF633341) else CanvaTokens.SoftOutline),
                ) {
                    Text(
                        text = when {
                            logged -> "LOGGED"
                            logEnabled -> "LOG MEAL?"
                            else -> "LOCKED"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = if (logged) CanvaTokens.PanelPinkLight else CanvaTokens.Ink,
                    )
                }
                Column(
                    modifier = Modifier.clickable(onClick = onSwap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = "Swap meal",
                        tint = CanvaTokens.AccentPinkStrong,
                    )
                    Text(
                        text = "Swap",
                        style = MaterialTheme.typography.bodySmall,
                        color = CanvaTokens.AccentPinkStrong,
                    )
                }
            }
        }
    }
}

private fun isMealLogged(completedMealIds: List<String>, meal: PlannedMealDto): Boolean {
    val mealKey = ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)
    return completedMealIds.contains(mealKey) || completedMealIds.contains(meal.recipeId)
}

private fun safeProgress(value: Int, target: Int): Float {
    if (target <= 0) return 0f
    return (value.toFloat() / target.toFloat()).coerceIn(0f, 1f)
}
