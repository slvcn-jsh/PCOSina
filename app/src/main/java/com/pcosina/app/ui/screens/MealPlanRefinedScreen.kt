package com.pcosina.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pcosina.app.R
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.model.PlannerPlannedMeal
import com.pcosina.app.data.model.PlannerRecipeDetail
import com.pcosina.app.data.model.PlannerRecipeSummary
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanGenerationNotice
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.MealCheckInDialog
import com.pcosina.app.ui.components.MealCheckInDraft
import com.pcosina.app.ui.components.PcosinaAvatarBadge
import com.pcosina.app.ui.components.RefinedMetricBar
import com.pcosina.app.ui.components.RefinedOverviewCard
import com.pcosina.app.ui.components.RefinedPrimaryButton
import com.pcosina.app.ui.components.RefinedRingMeter
import com.pcosina.app.ui.components.RefinedStatusPill
import com.pcosina.app.ui.components.RefinedTabBrandHeader
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.theme.PcosinaBlush
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaRoseShadow
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
    val meal: PlannerPlannedMeal,
)

private data class PlanMealCheckInPrompt(
    val recipeId: String,
    val mealLabel: String,
    val mealTitle: String,
)

@Composable
private fun MealSwapDialog(
    target: MealSwapTarget,
    options: List<PlannerRecipeSummary>,
    loading: Boolean,
    applying: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSelect: (PlannerRecipeSummary) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 390.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFFFF8FB),
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, PcosinaSoftPink.copy(alpha = 0.58f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Swap ${target.meal.mealLabel}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.Black
                )
                Text(
                    text = target.meal.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PcosinaMuted
                )
                when {
                    loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 92.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PcosinaPink)
                    }
                    !error.isNullOrBlank() -> Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    options.isEmpty() -> Text(
                        text = "No alternative meals are available right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.take(5).forEach { option ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !applying) { onSelect(option) },
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, PcosinaSoftPink.copy(alpha = 0.64f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = PcosinaDeepRose,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = option.mealType ?: target.meal.mealLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PcosinaMuted
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = PcosinaPink,
                        modifier = Modifier.clickable(enabled = !applying, onClick = onDismiss)
                    ) {
                        Text(
                            text = "Close",
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MealPlanRefinedScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    onRecipeClick: (String, String?) -> Unit,
    onViewProgress: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    onlineStateOverride: Boolean? = null,
    swapOptionsLoader: (suspend (mealLabel: String, limit: Int) -> Result<List<PlannerRecipeSummary>>)? = null,
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
    val recipeDetails = remember { mutableStateMapOf<String, PlannerRecipeDetail?>() }
    val scope = rememberCoroutineScope()
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var swapTarget by remember { mutableStateOf<MealSwapTarget?>(null) }
    var swapOptions by remember { mutableStateOf<List<PlannerRecipeSummary>>(emptyList()) }
    var swapLoading by remember { mutableStateOf(false) }
    var swapApplying by remember { mutableStateOf(false) }
    var swapError by remember { mutableStateOf<String?>(null) }
    var mealCheckInPrompt by remember(logKey) { mutableStateOf<PlanMealCheckInPrompt?>(null) }
    var showReplacePlanDialog by remember(activePlanId) { mutableStateOf(false) }

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

    swapTarget?.let { target ->
        MealSwapDialog(
            target = target,
            options = swapOptions,
            loading = swapLoading,
            applying = swapApplying,
            error = swapError,
            onDismiss = { if (!swapApplying) swapTarget = null },
            onSelect = { option ->
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
        val noSafePlanNotice = generationNotice as? MealPlanGenerationNotice.NoSafePlan
        val errorState = uiState as? MealPlanUiState.Error
        val isGenerating = uiState is MealPlanUiState.Loading
        val waitingOnSameRequest = errorState?.message?.let { message ->
            message.contains("still running", ignoreCase = true) ||
                message.contains("keep waiting", ignoreCase = true)
        } == true
        val compact = maxHeight < 760.dp || maxWidth < 390.dp
        val heroDate = selectedDate.format(DateTimeFormatter.ofPattern("MMM\ndd", Locale.ENGLISH))
        val explanation = currentPlan?.explanation
        val targetCalories = explanation?.targetCalories ?: userViewModel.dailyCalorieTarget
        val targetProtein = explanation?.targetProtein?.takeIf { it > 0 } ?: planMetrics.avgProtein.coerceAtLeast(1)
        val targetCarbs = explanation?.targetCarbs?.takeIf { it > 0 } ?: planMetrics.avgCarbs.coerceAtLeast(1)
        val targetFiber = explanation?.fiberMinTarget?.takeIf { it > 0 } ?: planMetrics.avgFiber.coerceAtLeast(1)
        val totalProtein = selectedMeals.sumOf { recipeDetails[it.recipeId]?.proteinGrams ?: 0 }
        val totalCarbs = selectedMeals.sumOf { recipeDetails[it.recipeId]?.carbsGrams ?: 0 }
        val totalFiber = selectedMeals.sumOf { recipeDetails[it.recipeId]?.fiberGrams ?: 0 }
        val dayCalories = selectedDay?.totalCalories ?: 0
        val loggedMeals = selectedMeals.count { meal ->
            completedMealIds.contains(ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)) ||
                completedMealIds.contains(meal.recipeId)
        }
        val canLogSelectedDay = progressViewModel.isDateLoggable(selectedDate)
        val servingLabel = profile.householdSize.coerceAtLeast(1)
        val planEndDate = weekStart.plusDays(6)
        val lastPlanDayIndex = (currentPlan?.days?.lastIndex ?: 6).coerceAtLeast(0)
        val lastPlanDayDate = weekStart.plusDays(lastPlanDayIndex.toLong())
        val lastPlanDayMeals = currentPlan?.days?.lastOrNull()?.meals.orEmpty()
        val lastPlanDayCompletedIds = logs[lastPlanDayDate.format(DateTimeFormatter.ISO_LOCAL_DATE)]
            ?.completedMealIds
            .orEmpty()
        val lastPlanDayComplete = currentPlan != null &&
            lastPlanDayMeals.isNotEmpty() &&
            lastPlanDayMeals.all { meal ->
                lastPlanDayCompletedIds.contains(ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)) ||
                    lastPlanDayCompletedIds.contains(meal.recipeId)
            }
        val planRenewalEligible = currentPlan == null ||
            today.isAfter(planEndDate) ||
            (today == lastPlanDayDate && lastPlanDayComplete)
        val planEndLabel = planEndDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
        val replaceWeekSubtitle = if (planRenewalEligible && currentPlan != null) {
            "Your 7-day plan is complete. Start the next week when you are ready; Grocery will resync automatically."
        } else {
            "Your grocery list updates automatically from this plan. A new weekly plan unlocks after $planEndLabel or when the final day's meals are logged."
        }
        val requestFreshWeek: () -> Unit = {
            if (currentPlan == null) {
                mealPlanViewModel.generateMealPlanFresh(profile)
                feedbackMessage = "Generating a new weekly plan..."
            } else if (planRenewalEligible) {
                showReplacePlanDialog = true
            } else {
                feedbackMessage = "This weekly plan is still active. Finish the 7-day plan before starting the next one."
            }
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
                onSupport = { onNavigateToRoute(Routes.Ipo) },
                compact = compact,
                avatarId = profile.avatarId
            )

            MealPlanHeadlineCard(
                heroDate = heroDate,
                avatarId = profile.avatarId,
                compact = compact
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
                                            if (waitingOnSameRequest && planRenewalEligible) {
                                                requestFreshWeek()
                                            } else {
                                                onNavigateToRoute(Routes.Ipo)
                                            }
                                        }
                                ) {
                                    Text(
                                        text = if (waitingOnSameRequest && planRenewalEligible) {
                                            "Start next week"
                                        } else {
                                            "Open Support"
                                        },
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
                today = today,
                onSelectDay = { selectedDayIndex = it },
                compact = compact
            )

            if (currentPlan == null) {
                RefinedOverviewCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No meal plan yet",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose
                    )
                    Text(
                        text = "Generate a weekly plan to see your meals here. Your grocery list will update automatically from it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PcosinaMuted
                    )
                }
                MealPlanShoppingCard(
                    title = if (profile.isProfileCompleted) "Ready to start your first weekly plan?" else "Finish your profile first",
                    subtitle = if (profile.isProfileCompleted) {
                        "Generate a plan using your saved goal, budget, pantry, and cooking preferences."
                    } else {
                        "Complete your basic profile and goal setup so PCOSina can generate a valid weekly plan."
                    },
                    primaryLabel = "Generate plan",
                    secondaryLabel = if (!profile.isProfileCompleted) "Finish profile" else null,
                    onPrimaryClick = {
                        if (profile.isProfileCompleted && uiState !is MealPlanUiState.Loading) {
                            mealPlanViewModel.generateMealPlan(profile)
                            feedbackMessage = "Generating a weekly plan..."
                        }
                    },
                    onSecondaryClick = if (!profile.isProfileCompleted) {
                        { onNavigateToRoute(Routes.UserProfile) }
                    } else null,
                    primaryEnabled = profile.isProfileCompleted && uiState !is MealPlanUiState.Loading,
                    compact = compact
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedMeals.isEmpty()) {
                            RefinedOverviewCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "No meals assigned for this day yet.",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PcosinaDeepRose
                                )
                                Text(
                                    text = "Choose another day or generate a fresh weekly plan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PcosinaMuted
                                )
                            }
                        } else {
                            selectedMeals.take(3).forEachIndexed { mealIndex, meal ->
                                val isLogged = completedMealIds.contains(ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)) ||
                                    completedMealIds.contains(meal.recipeId)
                                MealPlanOutlineMealCard(
                                    meal = meal,
                                    logged = isLogged,
                                    canLog = canLogSelectedDay,
                                    onOpen = { onRecipeClick(meal.recipeId, meal.mealLabel) },
                                    onLog = {
                                        if (progressViewModel.markMealAsEaten(
                                                date = selectedDate,
                                                recipeId = meal.recipeId,
                                                mealLabel = meal.mealLabel,
                                                plannedMealLabels = selectedMeals.map { it.mealLabel }
                                            )
                                        ) {
                                            mealCheckInPrompt = PlanMealCheckInPrompt(
                                                recipeId = meal.recipeId,
                                                mealLabel = meal.mealLabel,
                                                mealTitle = meal.title
                                            )
                                        } else {
                                            feedbackMessage = progressViewModel.mealLoggingLockReason(
                                                date = selectedDate,
                                                mealLabel = meal.mealLabel,
                                                plannedMealLabels = selectedMeals.map { it.mealLabel }
                                            ).ifBlank {
                                                "Meal logging is available for today only."
                                            }
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
                    }

                    MealPlanDailySummaryCard(
                        dayCalories = dayCalories,
                        targetCalories = targetCalories,
                        loggedMeals = loggedMeals,
                        mealCount = selectedMeals.size,
                        servingLabel = servingLabel,
                        totalProtein = totalProtein,
                        totalCarbs = totalCarbs,
                        totalFiber = totalFiber,
                        targetProtein = targetProtein,
                        targetCarbs = targetCarbs,
                        targetFiber = targetFiber,
                        compact = compact
                    )

                    MealPlanShoppingCard(
                        title = "Ready to shop?",
                        subtitle = replaceWeekSubtitle,
                        primaryLabel = "Go to Grocery",
                        secondaryLabel = when {
                            uiState is MealPlanUiState.Loading -> null
                            planRenewalEligible -> "Start next week"
                            else -> null
                        },
                        onPrimaryClick = {
                            mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
                                groceryViewModel.setPlanSources(sources)
                                onNavigateToRoute(Routes.GroceryList)
                            }
                        },
                        onSecondaryClick = if (planRenewalEligible) {
                            {
                                if (uiState !is MealPlanUiState.Loading) {
                                    requestFreshWeek()
                                }
                            }
                        } else {
                            null
                        },
                        primaryEnabled = currentPlan.days.isNotEmpty(),
                        compact = compact
                    )
                }
            }
        }

        if (showReplacePlanDialog) {
            AlertDialog(
                onDismissRequest = { showReplacePlanDialog = false },
                title = {
                    Text(
                        text = "Replace this week's plan?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Text(
                        text = "Review this week in Progress before starting a new one. Grocery will update to match the replacement week after you confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PcosinaDeepRose
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showReplacePlanDialog = false
                            mealPlanViewModel.generateMealPlanFresh(profile)
                            feedbackMessage = "Generating the next weekly plan..."
                        }
                    ) {
                        Text("Replace week")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplacePlanDialog = false }) {
                        Text("Keep this week")
                    }
                }
            )
        }

        mealCheckInPrompt?.let { prompt ->
            val existing = logs[logKey]?.mealCheckIns.orEmpty().firstOrNull { checkIn ->
                checkIn.mealKey == ProgressViewModel.buildMealKey(prompt.mealLabel, prompt.recipeId) ||
                    (checkIn.recipeId == prompt.recipeId &&
                        checkIn.mealLabel.equals(prompt.mealLabel, ignoreCase = true))
            }
            MealCheckInDialog(
                goal = profile.goal,
                mealTitle = prompt.mealTitle,
                mealLabel = prompt.mealLabel,
                initial = existing,
                onDismiss = { mealCheckInPrompt = null },
                onSave = { draft: MealCheckInDraft ->
                    val saved = progressViewModel.saveMealCheckIn(
                        date = selectedDate,
                        recipeId = prompt.recipeId,
                        mealLabel = prompt.mealLabel,
                        energyLevel = draft.energyLevel,
                        fullnessLevel = draft.fullnessLevel,
                        cravingsLevel = draft.cravingsLevel,
                        satisfactionLevel = draft.satisfactionLevel,
                        note = draft.note
                    )
                    feedbackMessage = if (saved) {
                        "${prompt.mealLabel} check-in saved."
                    } else {
                        "Couldn’t save this meal check-in right now."
                    }
                    if (saved) {
                        mealCheckInPrompt = null
                    }
                }
            )
        }
    }
}

@Composable
private fun MealPlanHeadlineCard(
    heroDate: String,
    avatarId: String,
    compact: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PcosinaBlush, RoundedCornerShape(26.dp))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = PcosinaBlush,
            border = BorderStroke(2.dp, Color(0xFF30181E).copy(alpha = 0.76f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 12.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PcosinaAvatarBadge(
                    avatarId = avatarId,
                    size = if (compact) 68.dp else 78.dp,
                    shadowElevation = if (compact) 3.dp else 6.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Plan Your Meals",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF682937),
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = PcosinaRoseShadow.copy(alpha = 0.22f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 3f),
                                blurRadius = 4f
                            )
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF682937).copy(alpha = 0.35f))
                            .padding(vertical = 0.5.dp)
                    )
                    Text(
                        text = "Meals picked for your goal, budget, and pantry.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF682937),
                            fontStyle = FontStyle.Italic
                        )
                    )
                }
                Text(
                    text = heroDate,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2B1B20)
                    )
                )
            }
        }
    }
}

@Composable
private fun MealPlanDailySummaryCard(
    dayCalories: Int,
    targetCalories: Int,
    loggedMeals: Int,
    mealCount: Int,
    servingLabel: Int,
    totalProtein: Int,
    totalCarbs: Int,
    totalFiber: Int,
    targetProtein: Int,
    targetCarbs: Int,
    targetFiber: Int,
    compact: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFFFFE2E8),
        border = BorderStroke(2.dp, Color(0xFF30181E).copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Plan",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF682937)
                    )
                )
                RefinedStatusPill(
                    text = "Serving size is set for $servingLabel person${if (servingLabel == 1) "" else "s"}.",
                    containerColor = Color.White.copy(alpha = 0.76f),
                    contentColor = Color(0xFF682937)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RefinedRingMeter(
                    valueText = "$dayCalories",
                    subtitle = "planned of ${targetCalories.coerceAtLeast(1)} kcal",
                    progress = if (targetCalories > 0) dayCalories.toFloat() / targetCalories.toFloat() else 0f,
                    color = PcosinaPink,
                    compact = compact
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFF58C9F)
                    ) {
                        Text(
                            text = "Macronutrients",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF682937)
                            )
                        )
                    }
                    RefinedMetricBar(
                        label = "Protein",
                        valueText = "${totalProtein}g/${targetProtein}g",
                        progress = totalProtein.toFloat() / targetProtein.coerceAtLeast(1).toFloat(),
                        color = Color(0xFFFF9BAA)
                    )
                    RefinedMetricBar(
                        label = "Carbs",
                        valueText = "${totalCarbs}g/${targetCarbs}g",
                        progress = totalCarbs.toFloat() / targetCarbs.coerceAtLeast(1).toFloat(),
                        color = Color(0xFFD0A069)
                    )
                    RefinedMetricBar(
                        label = "Fibers",
                        valueText = "${totalFiber}g/${targetFiber}g",
                        progress = totalFiber.toFloat() / targetFiber.coerceAtLeast(1).toFloat(),
                        color = Color(0xFFB9E7A6)
                    )
                    RefinedStatusPill(
                        text = "$loggedMeals/${mealCount.coerceAtLeast(1)} meals logged",
                        containerColor = Color.White.copy(alpha = 0.82f),
                        contentColor = PcosinaDeepRose
                    )
                }
            }
        }
    }
}

@Composable
private fun MealPlanShoppingCard(
    title: String,
    subtitle: String,
    primaryLabel: String,
    secondaryLabel: String? = null,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    compact: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFFF7F2F4),
        border = BorderStroke(2.dp, Color(0xFF30181E).copy(alpha = 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF682937)
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = PcosinaMuted
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RefinedPrimaryButton(
                        text = primaryLabel,
                        onClick = onPrimaryClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = primaryEnabled
                    )
                    if (!secondaryLabel.isNullOrBlank() && onSecondaryClick != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onSecondaryClick),
                            shape = RoundedCornerShape(18.dp),
                            color = PcosinaBlush.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.22f))
                        ) {
                            Text(
                                text = secondaryLabel,
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PcosinaDeepRose
                                )
                            )
                        }
                    }
                }
                Text(
                    text = "🛒",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MealPlanWeekStrip(
    dates: List<LocalDate>,
    selectedDayIndex: Int,
    today: LocalDate,
    onSelectDay: (Int) -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dates.forEachIndexed { index, date ->
                val selected = index == selectedDayIndex
                val isToday = date == today
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, if (selected) PcosinaPink else PcosinaMuted.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clickable { onSelectDay(index) }
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = if (compact) 7.dp else 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selected) PcosinaPink else PcosinaMuted
                        )
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = if (compact) {
                                MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold)
                            } else {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                            },
                            color = if (selected) PcosinaPink else PcosinaDeepRose
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .size(width = if (selected) 18.dp else 6.dp, height = 3.dp)
                                .background(
                                    color = when {
                                        selected -> PcosinaPink
                                        isToday -> PcosinaSoftPink
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(999.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MealPlanOutlineMealCard(
    meal: PlannerPlannedMeal,
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
            "🍳"
        )
        meal.mealLabel.equals("Lunch", ignoreCase = true) -> Triple(
            if (logged) Color(0xFF2D181D) else Color.White,
            Color(0xFFFF7EA0),
            "🍱"
        )
        else -> Triple(
            if (logged) Color(0xFF2D181D) else Color.White,
            Color(0xFF8E93FF),
            "🥘"
        )
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (logged) Color.Transparent else PcosinaMuted.copy(alpha = 0.26f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 10.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.22f)
            ) {
                Text(
                    text = icon,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "${meal.mealLabel} • Tap for more details",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (logged) Color.White.copy(alpha = 0.78f) else PcosinaMuted
                )
                Text(
                    text = meal.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (logged) Color.White else if (canLog) PcosinaDeepRose else PcosinaMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (logged) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier.clickable(onClick = onSwap)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = "Swap meal",
                        tint = if (logged) Color.White else PcosinaPink
                    )
                    Text(
                        text = "Swap",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (logged) Color.White else PcosinaPink
                    )
                }
            }
        }
    }
}
