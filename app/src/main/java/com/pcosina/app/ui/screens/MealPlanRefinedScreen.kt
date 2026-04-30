package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.pcosina.app.ui.components.RefinedActionCard
import com.pcosina.app.ui.components.RefinedHeroBanner
import com.pcosina.app.ui.components.RefinedMetricBar
import com.pcosina.app.ui.components.RefinedOverviewCard
import com.pcosina.app.ui.components.RefinedPrimaryButton
import com.pcosina.app.ui.components.RefinedRingMeter
import com.pcosina.app.ui.components.RefinedStatusPill
import com.pcosina.app.ui.components.RefinedTabBrandHeader
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.theme.PcosinaSuccess
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt
import com.pcosina.app.ui.util.rememberIsOnline
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private data class MealSwapTarget(
    val dayIndex: Int,
    val mealIndex: Int,
    val meal: PlannedMealDto,
)

@Composable
fun MealPlanRefinedScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    onRecipeClick: (String, String?) -> Unit,
    onViewProgress: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    nextActionAnalytics: MealPlanNextActionAnalytics? = null,
    onlineStateOverride: Boolean? = null,
    swapOptionsLoader: (suspend (mealLabel: String, limit: Int) -> Result<List<RecipeSummaryDto>>)? = null,
    swapGrocerySourceLoader: (suspend (recipeId: String) -> Result<List<GroceryItemSource>>)? = null,
    swapApplyOverride: (suspend (dayIndex: Int, mealIndex: Int, recipeId: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val uiState by mealPlanViewModel.uiState.collectAsState()
    val generationNotice by mealPlanViewModel.generationNotice.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val lastReviewedWeek by mealPlanViewModel.lastReviewedWeek.collectAsState()
    val planMetrics by mealPlanViewModel.planMetrics.collectAsState()
    val profile by userViewModel.userProfile.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val mealSources by groceryViewModel.mealSources.collectAsState()
    val sortedHistory = remember(planHistory) { planHistory.sortedBy { it.weekStart } }
    val currentPlan = remember(uiState, sortedHistory, activePlanId) {
        (uiState as? MealPlanUiState.Success)?.response
            ?: sortedHistory.firstOrNull { it.id == activePlanId }?.response
            ?: sortedHistory.maxByOrNull { it.generatedAt }?.response
    }
    val today = LocalDate.now()
    val weekStart = remember(activeWeekStart, currentPlan?.weekLabel) {
        activeWeekStart?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        } ?: today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
    }
    var selectedDayIndex by rememberSaveable(activePlanId) {
        mutableStateOf(
            (0..6).firstOrNull { weekStart.plusDays(it.toLong()) == today } ?: 0
        )
    }
    val weekDates = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    val selectedDate = remember(weekStart, selectedDayIndex) { weekStart.plusDays(selectedDayIndex.toLong()) }
    val selectedDay = remember(currentPlan, selectedDate) {
        val token = selectedDate.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).lowercase(Locale.ENGLISH)
        currentPlan?.days?.firstOrNull { it.dayLabel.lowercase(Locale.ENGLISH) == token }
    }
    val selectedMeals = selectedDay?.meals.orEmpty()
    val logKey = remember(selectedDate) { selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE) }
    val completedMealIds = logs[logKey]?.completedMealIds.orEmpty()
    val recipeDetails = remember { mutableStateMapOf<String, RecipeDetailDto?>() }
    val scope = rememberCoroutineScope()
    val activeIndex = remember(activePlanId, sortedHistory) {
        sortedHistory.indexOfFirst { it.id == activePlanId }.takeIf { it >= 0 } ?: (sortedHistory.size - 1)
    }
    val previousPlan = sortedHistory.getOrNull(activeIndex - 1)
    val nextPlan = sortedHistory.getOrNull(activeIndex + 1)
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var swapTarget by remember { mutableStateOf<MealSwapTarget?>(null) }
    var swapOptions by remember { mutableStateOf<List<RecipeSummaryDto>>(emptyList()) }
    var swapLoading by remember { mutableStateOf(false) }
    var swapApplying by remember { mutableStateOf(false) }
    var swapError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedMeals.map { it.recipeId }.joinToString(",")) {
        selectedMeals.forEach { meal ->
            if (recipeDetails.containsKey(meal.recipeId)) return@forEach
            mealPlanViewModel.getRecipeDetails(meal.recipeId)
                .onSuccess { detail -> recipeDetails[meal.recipeId] = detail }
                .onFailure { recipeDetails[meal.recipeId] = null }
        }
    }

    LaunchedEffect(currentPlan?.weekLabel, mealSources.isEmpty()) {
        if (currentPlan != null && mealSources.isEmpty()) {
            mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
                groceryViewModel.setPlanSources(sources)
            }
        }
    }

    LaunchedEffect(swapTarget?.meal?.recipeId, currentPlan?.weekLabel) {
        val target = swapTarget ?: return@LaunchedEffect
        if (!isOnline) {
            swapError = "Internet required for meal swaps."
            swapOptions = emptyList()
            swapLoading = false
            return@LaunchedEffect
        }
        val activeIds = currentPlan?.days?.flatMap { day -> day.meals.map { it.recipeId } }.orEmpty()
        swapLoading = true
        swapError = null
        val loadResult = swapOptionsLoader?.invoke(target.meal.mealLabel, 8)
            ?: mealPlanViewModel.getSwapOptions(
                profile = profile,
                mealLabel = target.meal.mealLabel,
                currentRecipeId = target.meal.recipeId,
                activeRecipeIds = activeIds,
                limit = 8
            )
        loadResult.onSuccess { options ->
            swapOptions = options.filter { it.id != target.meal.recipeId }
        }.onFailure { error ->
            swapError = error.message ?: "Unable to load swap options."
            swapOptions = emptyList()
        }
        swapLoading = false
    }

    if (swapTarget != null) {
        AlertDialog(
            onDismissRequest = { if (!swapApplying) swapTarget = null },
            confirmButton = {
                TextButton(onClick = { if (!swapApplying) swapTarget = null }) {
                    Text("Close")
                }
            },
            title = {
                Text(
                    text = "Swap ${swapTarget?.meal?.mealLabel.orEmpty()}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = swapTarget?.meal?.title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PcosinaMuted
                    )
                    when {
                        swapLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PcosinaPink)
                            }
                        }
                        !swapError.isNullOrBlank() -> {
                            Text(text = swapError.orEmpty(), color = MaterialTheme.colorScheme.error)
                        }
                        swapOptions.isEmpty() -> {
                            Text("No alternative meals are available right now.")
                        }
                        else -> {
                            swapOptions.take(5).forEach { option ->
                                RefinedOverviewCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !swapApplying) {
                                            val target = swapTarget ?: return@clickable
                                            scope.launch {
                                                swapApplying = true
                                                runCatching {
                                                    swapApplyOverride?.invoke(
                                                        target.dayIndex,
                                                        target.mealIndex,
                                                        option.id,
                                                        option.title
                                                    ) ?: mealPlanViewModel.swapMeal(
                                                        target.dayIndex,
                                                        target.mealIndex,
                                                        option.id,
                                                        option.title
                                                    )
                                                }.onSuccess {
                                                    val sources = swapGrocerySourceLoader?.invoke(option.id)
                                                        ?: mealPlanViewModel.getGrocerySourcesForRecipe(option.id)
                                                    sources.onSuccess { newSources ->
                                                        val planId = activePlanId ?: currentPlan?.weekLabel ?: "plan"
                                                        val mealId = mealPlanViewModel.buildMealInstanceId(
                                                            weekLabel = planId,
                                                            dayIndex = target.dayIndex,
                                                            mealIndex = target.mealIndex,
                                                            mealLabel = target.meal.mealLabel
                                                        )
                                                        groceryViewModel.replaceMealItems(mealId, newSources)
                                                    }
                                                    feedbackMessage = "${target.meal.mealLabel} swapped to ${option.title}."
                                                    swapTarget = null
                                                }.onFailure { error ->
                                                    swapError = error.message ?: "Unable to apply swap."
                                                }
                                                swapApplying = false
                                            }
                                        },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = PcosinaDeepRose
                                    )
                                    Text(
                                        text = option.mealType ?: swapTarget?.meal?.mealLabel.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PcosinaMuted
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
        val noSafePlanNotice = generationNotice as? MealPlanGenerationNotice.NoSafePlan
        val errorState = uiState as? MealPlanUiState.Error
        val isGenerating = uiState is MealPlanUiState.Loading
        val waitingOnSameRequest = errorState?.message?.let { message ->
            message.contains("still running", ignoreCase = true) ||
                message.contains("keep waiting", ignoreCase = true)
        } == true
        val compact = maxHeight < 760.dp
        val heroDate = selectedDate.format(DateTimeFormatter.ofPattern("MMM\ndd", Locale.ENGLISH))
        val explanation = currentPlan?.explanation
        val targetCalories = explanation?.targetCalories ?: userViewModel.dailyCalorieTarget
        val targetProtein = explanation?.targetProtein?.takeIf { it > 0 } ?: planMetrics.avgProtein.coerceAtLeast(1)
        val targetCarbs = explanation?.targetCarbs?.takeIf { it > 0 } ?: planMetrics.avgCarbs.coerceAtLeast(1)
        val targetFiber = explanation?.fiberMinTarget?.takeIf { it > 0 } ?: planMetrics.avgFiber.coerceAtLeast(1)
        val hasReviewedActiveWeek = activePlanId != null && activePlanId == lastReviewedWeek
        val totalProtein = selectedMeals.sumOf { recipeDetails[it.recipeId]?.proteinGrams ?: 0 }
        val totalCarbs = selectedMeals.sumOf { recipeDetails[it.recipeId]?.carbsGrams ?: 0 }
        val totalFiber = selectedMeals.sumOf { recipeDetails[it.recipeId]?.fiberGrams ?: 0 }
        val dayCalories = selectedDay?.totalCalories ?: 0
        val loggedMeals = selectedMeals.count { meal ->
            completedMealIds.contains(ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)) ||
                completedMealIds.contains(meal.recipeId)
        }
        val canLogSelectedDay = progressViewModel.isDateLoggable(selectedDate)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            RefinedTabBrandHeader(
                online = isOnline,
                onSettings = { onNavigateToRoute(Routes.Settings) },
                onSupport = { onNavigateToRoute(Routes.Ipo) },
                compact = compact
            )

            RefinedHeroBanner(
                title = "Plan Your Meals",
                subtitle = "Meals picked for your goal, budget, and pantry.",
                icon = Icons.Filled.RestaurantMenu,
                compact = compact,
                trailing = {
                    RefinedStatusPill(
                        text = heroDate,
                        containerColor = Color.White.copy(alpha = 0.86f),
                        contentColor = PcosinaDeepRose
                    )
                }
            )

            if (isGenerating || errorState != null) {
                RefinedOverviewCard(
                    containerColor = if (errorState != null) Color(0xFFFFF4F6) else Color(0xFFFFF8FB),
                    borderColor = if (errorState != null) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
                    } else {
                        PcosinaPink.copy(alpha = 0.22f)
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
                ) {
                    if (isGenerating) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = PcosinaPink,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Creating your weekly plan...",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PcosinaDeepRose
                                )
                                Text(
                                    text = if (currentPlan != null) {
                                        "Your current saved week stays available below while the next request is running."
                                    } else {
                                        "This can take a while if the backend queue is busy."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PcosinaMuted
                                )
                            }
                        }
                    } else if (errorState != null) {
                        Text(
                            text = if (waitingOnSameRequest) {
                                "Planner is still running"
                            } else {
                                "Planner needs attention"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (waitingOnSameRequest) PcosinaDeepRose else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = errorState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = PcosinaDeepRose
                        )
                        if (currentPlan != null) {
                            Text(
                                text = "Your latest saved week is still visible below while you retry.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PcosinaMuted
                            )
                        }
                        if (profile.isProfileCompleted) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RefinedPrimaryButton(
                                    text = if (waitingOnSameRequest) "Keep waiting" else "Retry",
                                    onClick = {
                                        mealPlanViewModel.generateMealPlan(profile)
                                        feedbackMessage = if (waitingOnSameRequest) {
                                            "Checking the same plan request again."
                                        } else {
                                            "Trying to generate your weekly plan again."
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = PcosinaSurfaceAlt,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (waitingOnSameRequest) {
                                                mealPlanViewModel.generateMealPlanFresh(profile)
                                                feedbackMessage = "Started a fresh weekly plan request."
                                            } else {
                                                onNavigateToRoute(Routes.Ipo)
                                            }
                                        }
                                ) {
                                    Text(
                                        text = if (waitingOnSameRequest) "Start fresh" else "Open Support",
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PcosinaDeepRose,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (noSafePlanNotice != null) {
                RefinedOverviewCard(
                    containerColor = Color(0xFFFFF4F6),
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
                ) {
                    Text(
                        text = noSafePlanNotice.message,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error
                    )
                    noSafePlanNotice.guidance.take(2).forEach { line ->
                        Text(text = "• $line", style = MaterialTheme.typography.bodySmall, color = PcosinaDeepRose)
                    }
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                RefinedOverviewCard(
                    containerColor = Color(0xFFF3FFF7),
                    borderColor = PcosinaSuccess.copy(alpha = 0.26f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    Text(
                        text = feedbackMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PcosinaSuccess
                    )
                }
            }

            MealPlanWeekStrip(
                dates = weekDates,
                selectedDayIndex = selectedDayIndex,
                onSelectDay = { selectedDayIndex = it },
                onPreviousWeek = previousPlan?.let { { mealPlanViewModel.selectPlan(it.id) } },
                onNextWeek = nextPlan?.let { { mealPlanViewModel.selectPlan(it.id) } },
                compact = compact
            )

            if (currentPlan == null) {
                RefinedOverviewCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        text = "No meal plan yet",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose
                    )
                    Text(
                        text = "Generate a weekly plan to see meals, nutrition targets, and grocery syncing in this screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PcosinaMuted
                    )
                }
                RefinedActionCard(
                    title = if (profile.isProfileCompleted) {
                        "Ready to start your first weekly plan?"
                    } else {
                        "Finish your profile first"
                    },
                    subtitle = if (profile.isProfileCompleted) {
                        "Generate a plan using your saved goal, budget, pantry, and cooking preferences."
                    } else {
                        "Complete your basic profile and goal setup so PCOSina can generate a valid weekly plan."
                    },
                    buttonLabel = if (uiState is MealPlanUiState.Loading) "Generating..." else "Generate plan",
                    buttonEnabled = profile.isProfileCompleted && uiState !is MealPlanUiState.Loading,
                    onClick = {
                        if (profile.isProfileCompleted && uiState !is MealPlanUiState.Loading) {
                            mealPlanViewModel.generateMealPlan(profile)
                            feedbackMessage = "Generating a weekly plan..."
                        }
                    },
                    secondaryLabel = if (!profile.isProfileCompleted) "Finish profile" else null,
                    onSecondaryClick = if (!profile.isProfileCompleted) {
                        { onNavigateToRoute(Routes.UserProfile) }
                    } else {
                        null
                    },
                    compact = compact
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
                ) {
                    RefinedOverviewCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(if (compact) 14.dp else 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = currentPlan.weekLabel.ifBlank { "Current week" },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = PcosinaDeepRose
                                )
                                Text(
                                    text = currentPlan.message.ifBlank {
                                        "This week was built around your nutrition targets, budget, and pantry."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PcosinaMuted
                                )
                            }
                            RefinedStatusPill(
                                text = if (hasReviewedActiveWeek) "Reviewed" else "Needs review",
                                containerColor = if (hasReviewedActiveWeek) Color(0xFFF3FFF7) else PcosinaSoftPink.copy(alpha = 0.26f),
                                contentColor = if (hasReviewedActiveWeek) PcosinaSuccess else PcosinaDeepRose
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            explanation?.confidenceScore?.let {
                                RefinedStatusPill(
                                    text = "Fit $it%",
                                    modifier = Modifier.weight(1f),
                                    containerColor = PcosinaSurfaceAlt
                                )
                            }
                            explanation?.estimatedWeeklyCost?.let {
                                RefinedStatusPill(
                                    text = "₱$it est.",
                                    modifier = Modifier.weight(1f),
                                    containerColor = PcosinaSurfaceAlt
                                )
                            }
                            explanation?.pantryMatches?.let {
                                RefinedStatusPill(
                                    text = "$it pantry",
                                    modifier = Modifier.weight(1f),
                                    containerColor = PcosinaSurfaceAlt
                                )
                            }
                        }
                        if (!hasReviewedActiveWeek && activePlanId != null) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = PcosinaSurfaceAlt,
                                modifier = Modifier.clickable {
                                    mealPlanViewModel.markWeekReviewed(activePlanId)
                                    feedbackMessage = "This week is marked as reviewed."
                                }
                            ) {
                                Text(
                                    text = "Mark this week as reviewed",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = PcosinaDeepRose
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedMeals.take(3).forEachIndexed { mealIndex, meal ->
                            val isLogged = completedMealIds.contains(ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)) ||
                                completedMealIds.contains(meal.recipeId)
                            MealPlanOutlineMealCard(
                                meal = meal,
                                logged = isLogged,
                                canLog = canLogSelectedDay,
                                onOpen = { onRecipeClick(meal.recipeId, meal.mealLabel) },
                                onLog = {
                                    if (progressViewModel.markMealAsEaten(selectedDate, meal.recipeId, meal.mealLabel)) {
                                        feedbackMessage = "${meal.mealLabel} logged for ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH))}."
                                    } else {
                                        feedbackMessage = "Meal logging is available for today only."
                                    }
                                },
                                onSwap = {
                                    if (!isOnline) {
                                        feedbackMessage = "Internet required for meal swaps."
                                    } else {
                                        swapTarget = MealSwapTarget(
                                            dayIndex = selectedDayIndex,
                                            mealIndex = mealIndex,
                                            meal = meal
                                        )
                                    }
                                },
                                compact = compact
                            )
                        }
                    }

                    RefinedOverviewCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = Color(0xFFFFF4F7),
                        borderColor = PcosinaPink.copy(alpha = 0.2f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(if (compact) 14.dp else 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RefinedRingMeter(
                                valueText = "$dayCalories",
                                subtitle = "planned kcal",
                                progress = if (targetCalories > 0) dayCalories.toFloat() / targetCalories.toFloat() else 0f,
                                color = PcosinaPink,
                                compact = compact
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RefinedStatusPill(
                                    text = "${loggedMeals}/${selectedMeals.size.coerceAtLeast(1)} meals logged • ${profile.householdSize.coerceAtLeast(1)} serving",
                                    containerColor = Color.White.copy(alpha = 0.8f)
                                )
                                RefinedMetricBar(
                                    label = "Protein",
                                    valueText = "${totalProtein}g/$targetProtein g",
                                    progress = totalProtein.toFloat() / targetProtein.toFloat(),
                                    color = Color(0xFFFF9BAA)
                                )
                                RefinedMetricBar(
                                    label = "Carbs",
                                    valueText = "${totalCarbs}g/$targetCarbs g",
                                    progress = totalCarbs.toFloat() / targetCarbs.toFloat(),
                                    color = Color(0xFFD9AF77)
                                )
                                RefinedMetricBar(
                                    label = "Fiber",
                                    valueText = "${totalFiber}g/$targetFiber g",
                                    progress = totalFiber.toFloat() / targetFiber.toFloat(),
                                    color = Color(0xFFB7E8A8)
                                )
                            }
                        }
                    }

                    RefinedActionCard(
                        title = "Ready to shop?",
                        subtitle = "Sync this week's ingredients to Grocery and keep logging meals from the same plan.",
                        buttonLabel = "Go to Grocery",
                        buttonEnabled = currentPlan.days.isNotEmpty(),
                        onClick = {
                            mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
                                groceryViewModel.setPlanSources(sources)
                                onNavigateToRoute(Routes.GroceryList)
                            }
                        },
                        secondaryLabel = if (uiState is MealPlanUiState.Loading) "Generating..." else "New week",
                        onSecondaryClick = {
                            if (uiState !is MealPlanUiState.Loading) {
                                mealPlanViewModel.generateMealPlan(profile)
                            }
                        },
                        compact = compact
                    )
                }
            }
        }
    }
}

@Composable
private fun MealPlanWeekStrip(
    dates: List<LocalDate>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    onPreviousWeek: (() -> Unit)?,
    onNextWeek: (() -> Unit)?,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        WeekArrowButton(
            icon = Icons.Filled.ChevronLeft,
            enabled = onPreviousWeek != null,
            onClick = { onPreviousWeek?.invoke() }
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dates.forEachIndexed { index, date ->
                val selected = index == selectedDayIndex
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (selected) PcosinaDeepRose else Color.White,
                    border = BorderStroke(1.dp, if (selected) PcosinaDeepRose else PcosinaMuted.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clickable { onSelectDay(index) }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = if (compact) 8.dp else 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) Color.White else PcosinaMuted
                        )
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = if (compact) {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            } else {
                                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            },
                            color = if (selected) Color.White else PcosinaDeepRose
                        )
                    }
                }
            }
        }
        WeekArrowButton(
            icon = Icons.Filled.ChevronRight,
            enabled = onNextWeek != null,
            onClick = { onNextWeek?.invoke() }
        )
    }
}

@Composable
private fun WeekArrowButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) Color.White else PcosinaSurfaceAlt,
        border = BorderStroke(1.dp, PcosinaMuted.copy(alpha = 0.3f)),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) PcosinaDeepRose else PcosinaMuted,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun MealPlanOutlineMealCard(
    meal: PlannedMealDto,
    logged: Boolean,
    canLog: Boolean,
    onOpen: () -> Unit,
    onLog: () -> Unit,
    onSwap: () -> Unit,
    compact: Boolean,
) {
    val (containerColor, accentColor, icon) = when {
        meal.mealLabel.equals("Breakfast", ignoreCase = true) -> Triple(
            if (logged) Color(0xFF2D181D) else Color.White,
            Color(0xFFFFB171),
            Icons.Filled.RestaurantMenu
        )
        meal.mealLabel.equals("Lunch", ignoreCase = true) -> Triple(
            if (logged) Color(0xFF2D181D) else Color.White,
            Color(0xFFFF7EA0),
            Icons.Filled.RestaurantMenu
        )
        else -> Triple(
            if (logged) Color(0xFF2D181D) else Color.White,
            Color(0xFF8E93FF),
            Icons.Filled.RestaurantMenu
        )
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, if (logged) Color.Transparent else PcosinaMuted.copy(alpha = 0.26f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 12.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.22f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${meal.mealLabel} • Tap for details",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (logged) Color.White.copy(alpha = 0.78f) else PcosinaMuted
                )
                Text(
                    text = meal.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (logged) Color.White else PcosinaDeepRose,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (logged) Color.Transparent else PcosinaSoftPink.copy(alpha = if (canLog) 0.28f else 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (logged) Color.White.copy(alpha = 0.3f) else PcosinaPink.copy(alpha = 0.24f)
                ),
                modifier = Modifier.clickable(enabled = !logged && canLog, onClick = onLog)
            ) {
                Text(
                    text = when {
                        logged -> "LOGGED"
                        canLog -> "LOG MEAL"
                        else -> "TODAY ONLY"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (logged) Color.White else if (canLog) PcosinaDeepRose else PcosinaMuted
                )
            }
            Surface(
                shape = CircleShape,
                color = if (logged) Color.White.copy(alpha = 0.12f) else PcosinaSurfaceAlt,
                modifier = Modifier.clickable(onClick = onSwap)
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = "Swap meal",
                    tint = if (logged) Color.White else PcosinaPink,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}
