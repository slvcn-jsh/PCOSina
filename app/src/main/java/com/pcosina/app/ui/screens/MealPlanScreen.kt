package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanGenerationNotice
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.GuidedJourneyCard
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.CompactWidgetGrid
import com.pcosina.app.ui.components.CompactWidgetSpec
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.FocusModePanel
import com.pcosina.app.ui.components.FriendlyEmptyStateCard
import com.pcosina.app.ui.components.LoadingActionButton
import com.pcosina.app.ui.components.ExpandableSection
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip
import com.pcosina.app.ui.components.StatusCenterCard
import com.pcosina.app.ui.components.SyncStatusChip
import com.pcosina.app.ui.components.TokenizedFilterChip
import com.pcosina.app.notifications.NotificationScheduler
import com.pcosina.app.util.safeUserLogScope
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.util.GuidedJourneyInput
import com.pcosina.app.ui.util.goalPlanFocusCopy
import com.pcosina.app.domain.householdSizeLabel
import com.pcosina.app.ui.util.primaryGoalLabel
import com.pcosina.app.ui.util.MealPlanNextActionDebugLog
import com.pcosina.app.ui.util.rememberIsOnline
import com.pcosina.app.ui.util.resolveGuidedJourneyStep
import com.pcosina.app.ui.util.supportsLowGiGuidance
import com.google.firebase.analytics.FirebaseAnalytics
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.model.GroceryItemSource
import kotlinx.coroutines.launch
import java.util.Locale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class SwapTarget(
    val dayIndex: Int,
    val mealIndex: Int,
    val mealLabel: String,
    val recipeId: String,
    val mealTitle: String
)

private enum class MealPlanBannerAction {
    None,
    RetryGenerate,
    RetrySync
}

private data class MealPlanBannerState(
    val data: FeedbackBannerData,
    val action: MealPlanBannerAction = MealPlanBannerAction.None
)

private data class MealPlanNextAction(
    val label: String,
    val reason: String,
    val enabled: Boolean,
    val onClick: () -> Unit
)

private data class MealPlanStickyAction(
    val label: String,
    val hint: String,
    val enabled: Boolean,
    val usesLoadingState: Boolean,
    val loadingLabel: String = label,
    val successLabel: String = label,
    val errorLabel: String = label,
    val onClick: () -> Unit
)

private enum class MealPlanScreenFocus {
    ManageWeek,
    Today,
    Insights,
}

interface MealPlanNextActionAnalytics {
    fun trackTap(actionType: String, networkState: String, userId: String)
}

private class FirebaseMealPlanNextActionAnalytics(
    private val analytics: FirebaseAnalytics
) : MealPlanNextActionAnalytics {
    override fun trackTap(actionType: String, networkState: String, userId: String) {
        analytics.logEvent(
            "mealplan_next_best_action_tap",
            Bundle().apply {
                putString("action_type", actionType)
                putString("network_state", networkState)
            }
        )
        MealPlanNextActionDebugLog.record(
            actionType = actionType,
            networkState = networkState,
            userId = userId
        )
        Log.i(
            "MealPlanUX",
            "Next best action tapped action=$actionType network=$networkState ${safeUserLogScope(userId)}"
        )
    }
}

@Composable
private fun rememberMealPlanNextActionAnalytics(): MealPlanNextActionAnalytics {
    val context = LocalContext.current
    return remember(context) {
        FirebaseMealPlanNextActionAnalytics(FirebaseAnalytics.getInstance(context))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
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
    val uiState by mealPlanViewModel.uiState.collectAsState()
    val generationNotice by mealPlanViewModel.generationNotice.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val activePlanId by mealPlanViewModel.activePlanId.collectAsState()
    val planExpired by mealPlanViewModel.planExpired.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val lastReviewedWeek by mealPlanViewModel.lastReviewedWeek.collectAsState()
    val currentPlan = (uiState as? MealPlanUiState.Success)?.response
    val userProfile by userViewModel.userProfile.collectAsState()
    val adminMode by userViewModel.adminMode.collectAsState()
    val notificationPrefs by userViewModel.notificationPreferences.collectAsState()
    val groceryItems by groceryViewModel.groceryItems.collectAsState()
    val mealSources by groceryViewModel.mealSources.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val feedbackQueue by progressViewModel.feedbackQueue.collectAsState()
    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val analytics = FirebaseAnalytics.getInstance(context)
    val resolvedNextActionAnalytics = nextActionAnalytics ?: rememberMealPlanNextActionAnalytics()
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val colorScheme = MaterialTheme.colorScheme
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val weekChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 108.dp, medium = 164.dp)
    val dayChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 102.dp, medium = 152.dp)
    var mealPlanFocusKey by rememberSaveable { mutableStateOf(MealPlanScreenFocus.ManageWeek.name) }
    var weekSwitcherExpanded by rememberSaveable { mutableStateOf(false) }
    var planningDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    val weekStartDate = remember(activeWeekStart) {
        activeWeekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
    }
    val dayLabels = remember(currentPlan?.days, weekStartDate) {
        currentPlan?.days
            ?.map { it.dayLabel.trim().takeIf(String::isNotBlank) ?: "Day" }
            ?.takeIf { it.isNotEmpty() }
            ?: (0..6).map { offset ->
                weekStartDate.plusDays(offset.toLong())
                    .format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
            }
    }
    var selectedDayAnchor by rememberSaveable { mutableStateOf<String?>(null) }
    val householdLabel = remember(userProfile.householdSize) {
        householdSizeLabel(userProfile.householdSize)
    }

    val scope = rememberCoroutineScope()
    var showConfidenceInfo by rememberSaveable { mutableStateOf(false) }
    var showLowGiInfo by rememberSaveable { mutableStateOf(false) }
    
    // Track if we are currently extracting ingredients
    var syncState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var generateActionState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var pendingPlanReadyNotification by rememberSaveable { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<MealPlanBannerState?>(null) }
    var stickyTapState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var swapTarget by remember { mutableStateOf<SwapTarget?>(null) }
    var swapOptions by remember { mutableStateOf<List<RecipeSummaryDto>>(emptyList()) }
    var swapQuery by remember { mutableStateOf("") }
    var swapLoading by remember { mutableStateOf(false) }
    var swapApplying by remember { mutableStateOf(false) }
    var swapError by remember { mutableStateOf<String?>(null) }
    var recentSwapSummary by rememberSaveable { mutableStateOf<String?>(null) }
    var recentSwapDetail by rememberSaveable { mutableStateOf<String?>(null) }
    var recentSwapDateKey by rememberSaveable { mutableStateOf<String?>(null) }
    var recentSwapHasGroceryUpdate by rememberSaveable { mutableStateOf(false) }
    val swapSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortedHistory = remember(planHistory) { planHistory.sortedBy { it.weekStart } }
    val noSafePlanNotice = generationNotice as? MealPlanGenerationNotice.NoSafePlan
    val activeIndex = remember(activePlanId, sortedHistory) {
        sortedHistory.indexOfFirst { it.id == activePlanId }.takeIf { it >= 0 }
            ?: (sortedHistory.size - 1)
    }
    val previousPlan = sortedHistory.getOrNull(activeIndex - 1)
    val nextPlan = sortedHistory.getOrNull(activeIndex + 1)
    val hasPlan = planHistory.isNotEmpty() || uiState is MealPlanUiState.Success
    val hasReviewedWeek = activePlanId != null && activePlanId == lastReviewedWeek
    val hasGrocery = groceryItems.isNotEmpty()
    val hasTracked = logs.isNotEmpty()
    val showJourneyCard = !hasPlan
    val expectedMealSourceIds = remember(currentPlan, activePlanId) {
        val plan = currentPlan ?: return@remember emptyList()
        val planKey = activePlanId ?: plan.planId ?: plan.weekLabel
        plan.days.flatMapIndexed { dayIndex, day ->
            day.meals.mapIndexed { mealIndex, meal ->
                mealPlanViewModel.buildMealInstanceId(planKey, dayIndex, mealIndex, meal.mealLabel)
            }
        }
    }
    val groceryReady = remember(expectedMealSourceIds, mealSources, groceryItems) {
        expectedMealSourceIds.isNotEmpty() &&
            groceryItems.isNotEmpty() &&
            expectedMealSourceIds.all { mealSources.containsKey(it) }
    }
    val guidedStep = resolveGuidedJourneyStep(
        GuidedJourneyInput(
            profileComplete = userProfile.isProfileCompleted,
            goal = userProfile.goal,
            hasPlan = hasPlan,
            hasReviewedWeek = hasReviewedWeek,
            hasGrocery = groceryReady || hasGrocery,
            hasTracked = hasTracked
        )
    )
    LaunchedEffect(hasPlan) {
        if (hasPlan && mealPlanFocusKey == MealPlanScreenFocus.ManageWeek.name) {
            mealPlanFocusKey = MealPlanScreenFocus.Today.name
        } else if (!hasPlan && mealPlanFocusKey == MealPlanScreenFocus.Today.name) {
            mealPlanFocusKey = MealPlanScreenFocus.ManageWeek.name
        }
    }
    val mealPlanFocus = remember(mealPlanFocusKey) {
        MealPlanScreenFocus.valueOf(mealPlanFocusKey)
    }
    val mealPlanFocusOptions = remember(hasPlan) {
        listOf(
            ScreenFocusOption(
                key = MealPlanScreenFocus.ManageWeek.name,
                label = "Week",
                summary = "Create or switch weeks."
            ),
            ScreenFocusOption(
                key = MealPlanScreenFocus.Today.name,
                label = "Today",
                summary = "See one day at a time."
            ),
            ScreenFocusOption(
                key = MealPlanScreenFocus.Insights.name,
                label = "Why",
                summary = if (hasPlan) {
                    "See why this week fits you."
                } else {
                    "See planning help before you create a week."
                }
            )
        )
    }

    fun triggerPlanGeneration() {
        if (!isOnline) {
            Log.w("MealPlanUX", "Plan generation blocked: offline ${safeUserLogScope(userViewModel.activeUserId)}")
            generateActionState = FeedbackActionState.Error
            feedbackBanner = MealPlanBannerState(
                data = FeedbackBannerData(
                    tone = FeedbackBannerTone.Error,
                    message = "Reconnect, then tap Retry to create your week.",
                    actionLabel = "Retry"
                ),
                action = MealPlanBannerAction.RetryGenerate
            )
            if (!hasPlan) {
                mealPlanViewModel.showError("Offline. Connect to the internet to generate a new plan.")
            }
            scope.launch {
                kotlinx.coroutines.delay(1200)
                if (generateActionState == FeedbackActionState.Error) {
                    generateActionState = FeedbackActionState.Idle
                }
            }
            return
        }
        Log.i(
            "MealPlanUX",
            "Plan generation started for ${safeUserLogScope(userViewModel.activeUserId)} goal='${userProfile.goal}'"
        )
        analytics.logEvent("generate_plan", null)
        feedbackBanner = MealPlanBannerState(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Loading,
                message = "Creating your week…"
            )
        )
        mealPlanViewModel.generateMealPlan(userProfile)
    }

    fun triggerGrocerySync(manual: Boolean = true) {
        if (!isOnline) {
            if (manual) {
                Log.w("MealPlanUX", "Grocery sync blocked: offline ${safeUserLogScope(userViewModel.activeUserId)}")
                syncState = FeedbackActionState.Error
                feedbackBanner = MealPlanBannerState(
                    data = FeedbackBannerData(
                        tone = FeedbackBannerTone.Error,
                        message = "Grocery updates need internet right now. Try again when you're back online.",
                        actionLabel = "Retry"
                    ),
                    action = MealPlanBannerAction.RetrySync
                )
                scope.launch {
                    val userId = userViewModel.activeUserId
                    if (manual && userId.isNotBlank()) {
                        NotificationScheduler.notifyGrocerySyncResult(context, userId, success = false)
                    }
                }
            }
            return
        }
        analytics.logEvent("sync_groceries", null)
        syncState = FeedbackActionState.Loading
        if (manual) {
            feedbackBanner = MealPlanBannerState(
                    data = FeedbackBannerData(
                        tone = FeedbackBannerTone.Loading,
                        message = "Refreshing your grocery list…"
                    )
                )
            }
        mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
            if (sources.isEmpty()) {
                if (manual) {
                    Log.w("MealPlanUX", "Grocery sync failed: no extracted sources ${safeUserLogScope(userViewModel.activeUserId)}")
                    syncState = FeedbackActionState.Error
                    feedbackBanner = MealPlanBannerState(
                        data = FeedbackBannerData(
                            tone = FeedbackBannerTone.Error,
                            message = "We couldn't refresh your grocery list yet. Try again after reopening this week.",
                            actionLabel = "Retry"
                        ),
                        action = MealPlanBannerAction.RetrySync
                    )
                    scope.launch {
                        val userId = userViewModel.activeUserId
                        if (manual && userId.isNotBlank()) {
                            NotificationScheduler.notifyGrocerySyncResult(context, userId, success = false)
                        }
                    }
                } else {
                    syncState = FeedbackActionState.Idle
                }
                return@extractGrocerySourcesForPlan
            }
            groceryViewModel.setPlanSources(sources)
            Log.i(
                "MealPlanUX",
                "Grocery sync success: ${sources.values.sumOf { it.size }} items mapped ${safeUserLogScope(userViewModel.activeUserId)}"
            )
            syncState = FeedbackActionState.Success
            if (manual) {
                feedbackBanner = MealPlanBannerState(
                    data = FeedbackBannerData(
                        tone = FeedbackBannerTone.Success,
                        message = "Grocery list ready. Review what to buy next."
                    )
                )
            }
            if (manual) {
                scope.launch {
                    val userId = userViewModel.activeUserId
                    if (userId.isNotBlank()) {
                        NotificationScheduler.notifyGrocerySyncResult(context, userId, success = true)
                    }
                }
            }
        }
    }

    val onFeedbackAction: (() -> Unit)? = when (feedbackBanner?.action) {
        MealPlanBannerAction.RetryGenerate -> ({ triggerPlanGeneration() })
        MealPlanBannerAction.RetrySync -> ({ triggerGrocerySync() })
        else -> null
    }

    fun postMealPlanFeedback(
        tone: FeedbackBannerTone,
        message: String,
        autoClearMs: Long = 2200L
    ) {
        feedbackBanner = MealPlanBannerState(
            data = FeedbackBannerData(
                tone = tone,
                message = message
            )
        )
        Log.i("MealPlanUX", message)
        if (tone != FeedbackBannerTone.Loading && autoClearMs > 0L) {
            scope.launch {
                kotlinx.coroutines.delay(autoClearMs)
                if (feedbackBanner?.data?.message == message) {
                    feedbackBanner = null
                }
            }
        }
    }

    fun logNextBestActionTap(actionType: String) {
        val networkState = if (isOnline) "online" else "offline"
        resolvedNextActionAnalytics.trackTap(
            actionType = actionType,
            networkState = networkState,
            userId = userViewModel.activeUserId
        )
    }

    LaunchedEffect(syncState) {
        if (syncState == FeedbackActionState.Success || syncState == FeedbackActionState.Error) {
            kotlinx.coroutines.delay(1800)
            syncState = FeedbackActionState.Idle
        }
    }
    LaunchedEffect(expectedMealSourceIds, isOnline, currentPlan?.requestId, mealSources.size) {
        if (!isOnline || syncState == FeedbackActionState.Loading) return@LaunchedEffect
        if (expectedMealSourceIds.isEmpty()) return@LaunchedEffect
        if (expectedMealSourceIds.all { mealSources.containsKey(it) }) return@LaunchedEffect
        triggerGrocerySync(manual = false)
    }
    LaunchedEffect(uiState, generationNotice) {
        when (uiState) {
            is MealPlanUiState.Loading -> {
                generateActionState = FeedbackActionState.Loading
                pendingPlanReadyNotification = true
                feedbackBanner = MealPlanBannerState(
                    data = FeedbackBannerData(
                        tone = FeedbackBannerTone.Loading,
                        message = "Creating your week…"
                    )
                )
            }
            is MealPlanUiState.Success -> {
                if (noSafePlanNotice != null) {
                    pendingPlanReadyNotification = false
                    if (generateActionState == FeedbackActionState.Loading) {
                        Log.w("MealPlanUX", "No-safe-plan preserved current plan ${safeUserLogScope(userViewModel.activeUserId)}")
                        generateActionState = FeedbackActionState.Error
                        feedbackBanner = MealPlanBannerState(
                            data = FeedbackBannerData(
                                tone = FeedbackBannerTone.Error,
                                message = "No safe new week was created. Review the suggestions below or try again.",
                                actionLabel = "Retry"
                            ),
                            action = MealPlanBannerAction.RetryGenerate
                        )
                        kotlinx.coroutines.delay(1800)
                        generateActionState = FeedbackActionState.Idle
                    }
                } else {
                    if (generateActionState == FeedbackActionState.Loading) {
                        Log.i("MealPlanUX", "Plan generation success ${safeUserLogScope(userViewModel.activeUserId)}")
                        generateActionState = FeedbackActionState.Success
                        feedbackBanner = MealPlanBannerState(
                            data = FeedbackBannerData(
                                tone = FeedbackBannerTone.Success,
                                message = "Week ready. Start with Today or open Grocery next."
                            )
                        )
                        kotlinx.coroutines.delay(1600)
                        generateActionState = FeedbackActionState.Idle
                    }
                    if (pendingPlanReadyNotification) {
                        val userId = userViewModel.activeUserId
                        if (userId.isNotBlank()) {
                            NotificationScheduler.notifyPlanReady(context, userId)
                        }
                        pendingPlanReadyNotification = false
                    }
                }
            }
            is MealPlanUiState.Error -> {
                Log.e("MealPlanUX", "Plan generation failed ${safeUserLogScope(userViewModel.activeUserId)}")
                pendingPlanReadyNotification = false
                if (generateActionState == FeedbackActionState.Loading) {
                    generateActionState = FeedbackActionState.Error
                    feedbackBanner = MealPlanBannerState(
                        data = FeedbackBannerData(
                            tone = FeedbackBannerTone.Error,
                            message = "We couldn't create your week. Tap Retry to try again.",
                            actionLabel = "Retry"
                        ),
                        action = MealPlanBannerAction.RetryGenerate
                    )
                    kotlinx.coroutines.delay(1800)
                    generateActionState = FeedbackActionState.Idle
                }
            }
            else -> Unit
        }
    }
    LaunchedEffect(activePlanId) {
        activePlanId?.let { mealPlanViewModel.markWeekReviewed(it) }
    }
    LaunchedEffect(recentSwapSummary, recentSwapDateKey) {
        if (recentSwapSummary != null) {
            kotlinx.coroutines.delay(3600)
            recentSwapSummary = null
            recentSwapDetail = null
            recentSwapDateKey = null
            recentSwapHasGroceryUpdate = false
        }
    }
    LaunchedEffect(activePlanId, currentPlan?.weekLabel, activeWeekStart, dayLabels) {
        val anchor = activePlanId ?: currentPlan?.weekLabel
        if (anchor != null && anchor != selectedDayAnchor) {
            val todayOffset = java.time.temporal.ChronoUnit.DAYS
                .between(weekStartDate, LocalDate.now())
                .toInt()
            selectedDayIndex = todayOffset.coerceIn(0, (dayLabels.size - 1).coerceAtLeast(0))
            selectedDayAnchor = anchor
        } else if (anchor == null && selectedDayAnchor != null) {
            selectedDayIndex = 0
            selectedDayAnchor = null
        }
    }
    LaunchedEffect(selectedDayIndex, currentPlan?.weekLabel) {
        val weekLabel = currentPlan?.weekLabel ?: return@LaunchedEffect
        val selectedDay = dayLabels.getOrNull(selectedDayIndex) ?: return@LaunchedEffect
        Log.i(
            "MealPlanUX",
            "Day navigation selected ${safeUserLogScope(userViewModel.activeUserId)} week=$weekLabel dayIndex=$selectedDayIndex day=$selectedDay"
        )
    }
    val stickyAction = when (uiState) {
        is MealPlanUiState.Idle -> MealPlanStickyAction(
            label = "Create My Plan",
            hint = if (isOnline) "This button stays visible while you set up your week." else "Connect to the internet to create your first week.",
            enabled = isOnline,
            usesLoadingState = true,
            loadingLabel = "Creating your week…",
            successLabel = "Week ready",
            errorLabel = "Try again",
            onClick = { triggerPlanGeneration() }
        )
        is MealPlanUiState.Error -> MealPlanStickyAction(
            label = "Try Again",
            hint = if (isOnline) "Try building your week again." else "Reconnect, then try again.",
            enabled = isOnline,
            usesLoadingState = true,
            loadingLabel = "Trying again…",
            successLabel = "Week ready",
            errorLabel = "Try again",
            onClick = { triggerPlanGeneration() }
        )
        is MealPlanUiState.Success -> when {
            planExpired -> MealPlanStickyAction(
                label = "Create New Week",
                hint = "This week has ended. Make a fresh one when you're ready.",
                enabled = isOnline,
                usesLoadingState = true,
                loadingLabel = "Creating next week…",
                successLabel = "Next week ready",
                errorLabel = "Try again",
                onClick = { triggerPlanGeneration() }
            )
            !groceryReady -> MealPlanStickyAction(
                label = "Open Grocery",
                hint = "Review what to buy for this week.",
                enabled = true,
                usesLoadingState = false,
                loadingLabel = "Opening grocery…",
                successLabel = "Opening grocery",
                errorLabel = "Try again",
                onClick = { onNavigateToRoute(com.pcosina.app.ui.navigation.Routes.GroceryList) }
            )
            !hasTracked -> MealPlanStickyAction(
                label = "Open Progress",
                hint = "Log meals as you finish them.",
                enabled = true,
                usesLoadingState = false,
                loadingLabel = "Opening progress…",
                successLabel = "Opening progress",
                errorLabel = "Try again",
                onClick = { onViewProgress() }
            )
            else -> MealPlanStickyAction(
                label = "Create New Week",
                hint = "Ready for a fresh week whenever you need it.",
                enabled = isOnline,
                usesLoadingState = true,
                loadingLabel = "Creating next week…",
                successLabel = "Next week ready",
                errorLabel = "Try again",
                onClick = { triggerPlanGeneration() }
            )
        }
        else -> null
    }
    LaunchedEffect(stickyAction?.label, stickyAction?.hint) {
        stickyTapState = FeedbackActionState.Idle
    }
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = stickyAction != null,
                enter = fadeIn(animationSpec = tween(UiMotionTokens.StickyActionRevealMs)) +
                    slideInVertically(animationSpec = tween(UiMotionTokens.StickyActionRevealMs)) { it / 2 },
                exit = fadeOut(animationSpec = tween(UiMotionTokens.StickyActionRevealMs / 2)) +
                    slideOutVertically(animationSpec = tween(UiMotionTokens.StickyActionRevealMs / 2)) { it / 2 }
            ) {
                stickyAction?.let { action ->
                    Surface(
                        tonalElevation = 2.dp,
                        shadowElevation = 8.dp,
                        color = colorScheme.surface,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = action.hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (action.usesLoadingState) {
                                LoadingActionButton(
                                    state = generateActionState,
                                    idleLabel = action.label,
                                    loadingLabel = action.loadingLabel,
                                    successLabel = action.successLabel,
                                    errorLabel = action.errorLabel,
                                    onClick = action.onClick,
                                    enabled = action.enabled,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            } else {
                                LoadingActionButton(
                                    state = stickyTapState,
                                    idleLabel = action.label,
                                    loadingLabel = action.loadingLabel,
                                    successLabel = action.successLabel,
                                    errorLabel = action.errorLabel,
                                    onClick = {
                                        if (!action.enabled || stickyTapState == FeedbackActionState.Loading) return@LoadingActionButton
                                        scope.launch {
                                            stickyTapState = FeedbackActionState.Loading
                                            kotlinx.coroutines.delay(120)
                                            stickyTapState = FeedbackActionState.Success
                                            kotlinx.coroutines.delay(90)
                                            action.onClick()
                                            kotlinx.coroutines.delay(500)
                                            stickyTapState = FeedbackActionState.Idle
                                        }
                                    },
                                    enabled = action.enabled,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        when (val state = uiState) {
            is MealPlanUiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().background(colorScheme.background).statusBarsPadding().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GuidedJourneyCard(
                            step = guidedStep,
                            onContinue = { step -> onNavigateToRoute(step.route) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Step 4 of 6: Build Your Week",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().testTag("mealplan_step3_label")
                        )
                        feedbackBanner?.let { banner ->
                            Spacer(Modifier.height(12.dp))
                            AppFeedbackBanner(
                                data = banner.data,
                                onAction = onFeedbackAction,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Create your first week",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "This builds your week and unlocks shopping and progress.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (!isOnline) {
                            Text(
                                text = "Offline. Connect to the internet to generate your first plan.",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        Text(
                            text = if (isOnline) "You are online" else "You are offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (planHistory.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "View past weeks",
                                style = MaterialTheme.typography.labelLarge,
                                color = colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(planHistory.sortedByDescending { it.weekStart }) { instance ->
                                    TokenizedFilterChip(
                                        selected = false,
                                        onClick = { mealPlanViewModel.selectPlan(instance.id) },
                                        text = instance.response.weekLabel,
                                        labelMaxWidth = weekChipLabelWidth
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is MealPlanUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().background(colorScheme.background).statusBarsPadding().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Building your 7-day plan…",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.secondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "This can take a few minutes on the current server setup. Please keep the app open.",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            is MealPlanUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        GuidedJourneyCard(
                            step = guidedStep,
                            onContinue = { step -> onNavigateToRoute(step.route) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Step 4 of 6: Build Your Week",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().testTag("mealplan_step3_label")
                        )
                        feedbackBanner?.let { banner ->
                            Spacer(Modifier.height(12.dp))
                            AppFeedbackBanner(
                                data = banner.data,
                                onAction = onFeedbackAction,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (noSafePlanNotice != null) "No safe week yet" else "We couldn't create your week",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                if (noSafePlanNotice?.guidance?.isNotEmpty() == true) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Try adjusting:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colorScheme.onSurface
                                    )
                                    noSafePlanNotice.guidance.take(3).forEach { item ->
                                        Text(
                                            text = "• $item",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                noSafePlanNotice?.diagnosticsReference?.let { reference ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Reference: $reference",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (!isOnline) {
                            Text(
                                text = "You are offline. Saved plans will still be available.",
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isOnline) "You are online" else "You are offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is MealPlanUiState.Success -> {
                val plan = state.response
                val selectedLabel = dayLabels.getOrNull(selectedDayIndex) ?: "Mon"
                val selectedDay = plan.days.getOrNull(selectedDayIndex)
                val explanation = plan.explanation
                val selectedMeals = selectedDay?.meals.orEmpty()
                val selectedDayLabel = selectedDay?.dayLabel ?: selectedLabel
                val selectedDayCalories = selectedDay?.totalCalories ?: 0
                val selectedDate = remember(weekStartDate, selectedDayIndex) {
                    weekStartDate.plusDays(selectedDayIndex.toLong())
                }
                val selectedDateKey = remember(selectedDate) {
                    selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                }
                var expandedMealKey by rememberSaveable(selectedDateKey) { mutableStateOf<String?>(null) }
                val selectedCompletedIds = logs[selectedDateKey]?.completedMealIds.orEmpty()
                val selectedCheckInCount = logs[selectedDateKey]?.mealCheckIns?.size ?: 0
                val selectedCompletedMeals = remember(selectedMeals, selectedCompletedIds) {
                    selectedMeals.count { meal ->
                        val mealKey = ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)
                        selectedCompletedIds.contains(mealKey) || selectedCompletedIds.contains(meal.recipeId)
                    }
                }
                val selectedMealCompletionRatio = if (selectedMeals.isNotEmpty()) {
                    selectedCompletedMeals.toFloat() / selectedMeals.size.toFloat()
                } else {
                    0f
                }
                val selectedDayTrackingSummary = when {
                    selectedMeals.isEmpty() -> "No meals planned for this day yet."
                    selectedCompletedMeals >= selectedMeals.size -> "Everything planned for $selectedDayLabel is already logged in Progress."
                    selectedCompletedMeals == 0 -> "Nothing from $selectedDayLabel is logged yet. Open each meal after you complete it."
                    else -> "$selectedCompletedMeals of ${selectedMeals.size} meals from $selectedDayLabel are already logged."
                }
                val nextBestAction = remember(planExpired, groceryReady, hasTracked, userProfile.goal) {
                    val goalLabel = primaryGoalLabel(userProfile.goal)
                    when {
                        planExpired -> MealPlanNextAction(
                            label = if (isOnline) "Generate Next Week" else "Generate Next Week (Internet required)",
                            reason = "Why this helps $goalLabel: keeping a current week keeps daily choices aligned.",
                            enabled = isOnline,
                            onClick = {
                                logNextBestActionTap("generate_next_week")
                                triggerPlanGeneration()
                            }
                        )
                        !groceryReady -> MealPlanNextAction(
                            label = "Open Grocery List",
                            reason = "Why this helps $goalLabel: your grocery list updates automatically from the meals in this week.",
                            enabled = true,
                            onClick = {
                                logNextBestActionTap("open_grocery_list")
                                onNavigateToRoute(com.pcosina.app.ui.navigation.Routes.GroceryList)
                            }
                        )
                        !hasTracked -> MealPlanNextAction(
                            label = "Log Meals in Progress",
                            reason = "Why this helps $goalLabel: meal check-offs improve adherence and insight quality.",
                            enabled = true,
                            onClick = {
                                logNextBestActionTap("log_meals_in_progress")
                                onViewProgress()
                            }
                        )
                        else -> MealPlanNextAction(
                            label = "Review Progress Insights",
                            reason = "Why this helps $goalLabel: reviewing trends helps adjust your next week faster.",
                            enabled = true,
                            onClick = {
                                logNextBestActionTap("review_progress_insights")
                                onViewProgress()
                            }
                        )
                    }
                }
                val planHeaderSyncState = when {
                    generateActionState == FeedbackActionState.Loading || syncState == FeedbackActionState.Loading ->
                        FeedbackActionState.Loading
                    generateActionState == FeedbackActionState.Error || syncState == FeedbackActionState.Error || !isOnline ->
                        FeedbackActionState.Error
                    else -> FeedbackActionState.Success
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background)
                        .statusBarsPadding()
                        .padding(padding)
                        .testTag("mealplan_content_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = UiSpacingTokens.SectionGap),
                    verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
                ) {
                item {
                    GradientHeader(
                        title = "This week",
                        subtitle = "Meals picked for your goal, budget, and what you have.",
                        containerHeight = 118,
                        trailing = {
                            SyncStatusChip(state = planHeaderSyncState)
                        },
                    )
                }
                if (showJourneyCard) {
                    item {
                        GuidedJourneyCard(
                            step = guidedStep,
                            onContinue = { step -> onNavigateToRoute(step.route) }
                        )
                    }
                }
                item {
                    feedbackBanner?.let { banner ->
                        AppFeedbackBanner(
                            data = banner.data,
                            onAction = onFeedbackAction,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    if (noSafePlanNotice != null) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.errorContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mealplan_no_safe_plan_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No safe new plan yet",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colorScheme.onErrorContainer
                                )
                                Text(
                                    text = noSafePlanNotice.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onErrorContainer
                                )
                                if (noSafePlanNotice.guidance.isNotEmpty()) {
                                    Text(
                                        text = "Try adjusting:",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colorScheme.onErrorContainer
                                    )
                                    noSafePlanNotice.guidance.take(3).forEach { item ->
                                        Text(
                                            text = "• $item",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                noSafePlanNotice.diagnosticsReference?.let { reference ->
                                    Text(
                                        text = "Reference: $reference",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onErrorContainer
                                    )
                                }
                                if (noSafePlanNotice.continuityPlanAvailable) {
                                    Text(
                                        text = "Your saved week is still available below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    ScreenFocusStrip(
                        title = "Show",
                        options = mealPlanFocusOptions,
                        selectedKey = mealPlanFocusKey,
                        onSelect = { mealPlanFocusKey = it },
                        labelMaxWidth = weekChipLabelWidth
                    )
                }
                item {
                    FocusModePanel(
                        targetKey = mealPlanFocusKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mealplan_top_section_capture")
                    ) { focusKey ->
                        when (MealPlanScreenFocus.valueOf(focusKey)) {
                            MealPlanScreenFocus.ManageWeek -> MealPlanManageWeekHero(
                                weekLabel = plan.weekLabel,
                                goalLabel = primaryGoalLabel(userProfile.goal),
                                householdLabel = householdLabel,
                                totalMeals = plan.days.sumOf { it.meals.size },
                                estimatedWeeklyCost = explanation?.estimatedWeeklyCost,
                                planExpired = planExpired,
                                groceryReady = groceryReady,
                                hasTracked = hasTracked,
                                ctaLabel = nextBestAction.label,
                                onPrimaryAction = nextBestAction.onClick
                            )

                            MealPlanScreenFocus.Today -> MealPlanTodayHero(
                                dayLabel = selectedDayLabel,
                                mealsPlanned = selectedMeals.size,
                                mealsLogged = selectedCompletedMeals,
                                checkInsCount = selectedCheckInCount,
                                dayCalories = selectedDayCalories,
                                trackingSummary = selectedDayTrackingSummary,
                                ctaLabel = nextBestAction.label,
                                onPrimaryAction = nextBestAction.onClick
                            )

                            MealPlanScreenFocus.Insights -> MealPlanInsightsHero(
                                goalLabel = primaryGoalLabel(userProfile.goal),
                                explanationCopy = goalPlanFocusCopy(userProfile.goal),
                                householdLabel = householdLabel,
                                estimatedWeeklyCost = explanation?.estimatedWeeklyCost,
                                averageCalories = explanation?.avgCalories ?: 0,
                                lowGiGuidance = supportsLowGiGuidance(userProfile.goal),
                                planExpired = planExpired,
                                ctaLabel = nextBestAction.label,
                                onPrimaryAction = nextBestAction.onClick
                            )
                        }
                    }
                }
                item {
                    val queuedCount = feedbackQueue.count { it.status != "Sent" }
                    val remindersEnabled = notificationPrefs.masterEnabled && notificationPrefs.mealRemindersEnabled
                    val nextReminder = "Next reminders: B ${formatClock(notificationPrefs.breakfastHour, notificationPrefs.breakfastMinute)} • " +
                        "L ${formatClock(notificationPrefs.lunchHour, notificationPrefs.lunchMinute)} • " +
                        "D ${formatClock(notificationPrefs.dinnerHour, notificationPrefs.dinnerMinute)}"
                    val statusSummaryLabel = when {
                        planExpired -> "Current week ended. Generate the next week when you're ready."
                        !groceryReady -> "Meals are ready. Grocery details still need a refresh."
                        hasTracked -> "Plan, groceries, and progress are all active this week."
                        queuedCount > 0 -> "Week is ready. $queuedCount feedback item(s) still need delivery."
                        else -> "Week is ready for review and meal check-ins."
                    }
                    val syncSummaryLabel = when {
                        syncState == FeedbackActionState.Loading -> "Updating grocery and plan status…"
                        syncState == FeedbackActionState.Error -> "Sync needs attention. Retry when you're ready."
                        syncState == FeedbackActionState.Success -> "Groceries and plan status are up to date."
                        !isOnline -> "Offline-safe: showing your last saved plan."
                        else -> "Online: local changes can sync when needed."
                    }
                    val nextFocusLabel = when {
                        remindersEnabled -> nextReminder
                        nextBestAction.enabled -> "Next focus: ${nextBestAction.label}"
                        else -> "Next focus: reconnect before generating the next week"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (false && mealPlanFocus != MealPlanScreenFocus.Today && mealPlanFocus != MealPlanScreenFocus.ManageWeek) {
                            StatusCenterCard(
                                queuedActionsLabel = statusSummaryLabel,
                                syncLabel = syncSummaryLabel,
                                planRangeLabel = "Plan range: ${plan.weekLabel}",
                                nextReminderLabel = nextFocusLabel
                            )
                        }
                        if (false && mealPlanFocus == MealPlanScreenFocus.Insights) {
                            Text(
                                text = nextBestAction.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("mealplan_next_best_action_reason")
                            )
                        }
                    }
                }
                item {
                    if (mealPlanFocus == MealPlanScreenFocus.ManageWeek) {
                        if (false && !isOnline) {
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
                            Spacer(Modifier.height(10.dp))
                        }
                        if (false) {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Made for your goal: ${primaryGoalLabel(userProfile.goal)}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "This week follows your goal, budget, and food rules.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Use the day chips below to review one day at a time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        if (false && planExpired) {
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
                        if (planHistory.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            ExpandableSection(
                                title = "Switch week",
                                subtitle = if (weekSwitcherExpanded) {
                                    "Selected: ${plan.weekLabel}"
                                } else {
                                    "Selected: ${plan.weekLabel} • Open to move between saved weeks"
                                },
                                defaultExpanded = false,
                                expanded = weekSwitcherExpanded,
                                onExpandedChange = { weekSwitcherExpanded = it }
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(planHistory.sortedByDescending { it.weekStart }) { instance ->
                                            val selected = instance.id == activePlanId
                                            TokenizedFilterChip(
                                                selected = selected,
                                                onClick = {
                                                    mealPlanViewModel.selectPlan(instance.id)
                                                    weekSwitcherExpanded = false
                                                },
                                                text = instance.response.weekLabel,
                                                labelMaxWidth = weekChipLabelWidth,
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = colorScheme.primary,
                                                    selectedLabelColor = colorScheme.onPrimary
                                                )
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                previousPlan?.let {
                                                    mealPlanViewModel.selectPlan(it.id)
                                                    weekSwitcherExpanded = false
                                                }
                                            },
                                            enabled = previousPlan != null,
                                            modifier = Modifier.heightIn(min = 44.dp),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Prev")
                                        }
                                        FilledTonalButton(
                                            onClick = onViewProgress,
                                            modifier = Modifier.heightIn(min = 44.dp),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text("Progress")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                nextPlan?.let {
                                                    mealPlanViewModel.selectPlan(it.id)
                                                    weekSwitcherExpanded = false
                                                }
                                            },
                                            enabled = nextPlan != null,
                                            modifier = Modifier.heightIn(min = 44.dp),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text("Next")
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Filled.ChevronRight, contentDescription = null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (mealPlanFocus == MealPlanScreenFocus.Insights) {
                        if (false && supportsLowGiGuidance(userProfile.goal)) {
                            Row(
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Low‑GI guidance: favor high‑fiber carbs and balanced meals.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = { showLowGiInfo = true },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Low-GI guidance",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Why: steadier carb choices can support energy consistency. Guidance only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        val weeklyBudget = userProfile.weeklyBudgetPhp.takeIf { it > 0 }
                        if (false) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("Weekly Budget", style = MaterialTheme.typography.bodyMedium)
                                    if (weeklyBudget == null) {
                                        Text(
                                            text = "Not set yet. Add a weekly budget in Profile.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = "₱$weeklyBudget",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Projected only. Log actual spending in Progress.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (explanation != null) {
                            val projectedHouseholdCost = explanation.estimatedWeeklyCost
                            Spacer(Modifier.height(8.dp))
                            ExpandableSection(
                                title = if (adminMode) "How the week was built" else "Why this week fits",
                                subtitle = if (planningDetailsExpanded) {
                                    if (adminMode) "Planner notes, limits, and grocery estimates are open below." else "The short week explanation is open below."
                                } else {
                                    if (adminMode) "Open the planner explanation for this week." else "Open the short explanation for this week."
                                },
                                defaultExpanded = false,
                                expanded = planningDetailsExpanded,
                                onExpandedChange = { planningDetailsExpanded = it }
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (adminMode) "Planner readout" else "Week highlights",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = if (adminMode) {
                                            "These notes show how the planner balanced nutrition, variety, pantry use, and budget for this week."
                                        } else {
                                            goalPlanFocusCopy(userProfile.goal)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    if (adminMode) {
                                        explanation.confidenceScore?.let { score ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Plan fit score: $score%",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                                IconButton(
                                                    onClick = { showConfidenceInfo = true },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Info,
                                                        contentDescription = "Plan fit info",
                                                        tint = colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        val items = mutableListOf<String>()
                                        val avgDev = explanation.avgCaloriesDeviation
                                        if (explanation.targetCalories != null) {
                                            items.add("Daily calorie goal: ${explanation.targetCalories} kcal/day")
                                        }
                                        if (explanation.avgCalories != null) {
                                            val devText = if (avgDev != null) " (±$avgDev)" else ""
                                            items.add("Planned average: ${explanation.avgCalories} kcal/day$devText")
                                        }
                                        val targetMacros = listOf(
                                            explanation.targetProtein?.let { "P ${it}g" },
                                            explanation.targetCarbs?.let { "C ${it}g" },
                                            explanation.targetFats?.let { "F ${it}g" }
                                        ).filterNotNull()
                                        if (targetMacros.isNotEmpty()) {
                                            items.add("Daily macro goals: ${targetMacros.joinToString(" • ")}")
                                        }
                                        val avgMacros = listOf(
                                            explanation.avgProtein?.let { "P ${it}g" },
                                            explanation.avgCarbs?.let { "C ${it}g" },
                                            explanation.avgFats?.let { "F ${it}g" }
                                        ).filterNotNull()
                                        if (avgMacros.isNotEmpty()) {
                                            items.add("Planned macros: ${avgMacros.joinToString(" • ")}")
                                        }
                                        explanation.fiberMinTarget?.let {
                                            items.add("Minimum fiber goal: ${it}g/day")
                                        }
                                        explanation.sugarMaxTarget?.let {
                                            items.add("Sugar limit: ${it}g/day")
                                        }
                                        if (explanation.symptomSelections.isNotEmpty()) {
                                            items.add("Symptoms considered: ${explanation.symptomSelections.joinToString(", ")}")
                                        }
                                        explanation.goalStrategy.forEach { items.add(it) }
                                        explanation.symptomStrategy.forEach { items.add(it) }
                                        val exclusionSummary = explanation.candidateExclusionSummary
                                            ?.entries
                                            ?.filter { it.value > 0 }
                                            ?.joinToString(" • ") { "${it.key} ${it.value}" }
                                        if (!exclusionSummary.isNullOrBlank()) {
                                            items.add("Meals screened out early: $exclusionSummary")
                                        }
                                        val constraintItems = mutableListOf<String>()
                                        explanation.toleranceUsed?.let {
                                            val pct = String.format(Locale.ENGLISH, "%.0f", it * 100)
                                            constraintItems.add("Flex used around the calorie goal: $pct%")
                                        }
                                        explanation.maxPerWeek?.let {
                                            constraintItems.add("Most times one meal can repeat: $it")
                                        }
                                        if (explanation.budgetWeekly != null || explanation.estimatedWeeklyCost != null) {
                                            val budget = explanation.budgetWeekly?.let {
                                                "₱" + String.format(Locale.ENGLISH, "%.0f", it)
                                            }
                                            val est = explanation.estimatedWeeklyCost?.let { "₱$it" }
                                            val text = when {
                                                budget != null && est != null -> "Weekly budget: $budget (planned grocery estimate $est)"
                                                budget != null -> "Weekly budget: $budget"
                                                est != null -> "Planned grocery estimate: $est"
                                                else -> null
                                            }
                                            if (text != null) constraintItems.add(text)
                                        }
                                        if (explanation.budgetHardCapApplied == true) {
                                            constraintItems.add("Weekly budget was treated as a hard limit")
                                        }
                                        explanation.goalValue
                                            ?.takeIf { it.isNotBlank() }
                                            ?.let { constraintItems.add("Goal focus: $it") }
                                        if (!explanation.householdPlanningMode.isNullOrBlank()) {
                                            constraintItems.add("Household shopping was scaled for everyone in the plan")
                                        }
                                        explanation.restrictionCount?.let {
                                            constraintItems.add("Saved food rules used: $it")
                                        }
                                        explanation.pantryMatches?.let {
                                            items.add("Saved ingredients matched: $it")
                                        }
                                        explanation.uniqueVegTokens?.let {
                                            items.add("Produce variety markers: $it")
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
                                                text = "Planner guardrails",
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
                                    } else {
                                        val highlights = mutableListOf<String>()
                                        explanation.avgCalories?.let {
                                            highlights.add("About $it kcal per person each day.")
                                        }
                                        explanation.avgProtein?.let {
                                            highlights.add("Keeps protein near ${it}g per person each day.")
                                        }
                                        projectedHouseholdCost?.let {
                                            highlights.add("Estimated groceries: ₱$it for $householdLabel.")
                                        }
                                        explanation.pantryMatches?.takeIf { it > 0 }?.let {
                                            highlights.add("Uses $it items you already saved in Pantry.")
                                        }
                                        explanation.uniqueVegTokens?.takeIf { it > 0 }?.let {
                                            highlights.add("Adds $it produce picks to keep the week from feeling repetitive.")
                                        }
                                        if (highlights.isEmpty()) {
                                            highlights.add("This week follows the food rules and preferences saved in your profile.")
                                        }
                                        highlights.forEach { line ->
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
                }

                    // 1. Generate Grocery List Action (feedback + snackbar)
                    if (mealPlanFocus == MealPlanScreenFocus.ManageWeek) {
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
                                    Text("Grocery list", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        text = if (groceryReady) {
                                            "Updated automatically from your current plan."
                                        } else {
                                            "We’re organizing ingredients from this week’s meals now."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    SyncStatusChip(state = syncState)
                                }
                                FilledTonalButton(
                                    onClick = { onNavigateToRoute(com.pcosina.app.ui.navigation.Routes.GroceryList) },
                                    modifier = Modifier.height(44.dp),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text("Open grocery list")
                                }
                            }
                        }
                    }
                    }

                    // 2. Day selector chips + navigation hint
                    if (mealPlanFocus == MealPlanScreenFocus.Today) {
                        item {
                        val dayCount = dayLabels.size
                        val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
                        val todayIndex = dayLabels.indexOfFirst { it.equals(todayLabel, ignoreCase = true) }
                        val selectedDayLabel = dayLabels.getOrNull(selectedDayIndex) ?: "Mon"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedDayIndex > 0) {
                                        selectedDayIndex--
                                        Log.i("MealPlanUX", "Day navigation previous tapped index=$selectedDayIndex")
                                    }
                                },
                                enabled = selectedDayIndex > 0
                            ) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$selectedDayLabel • Day ${selectedDayIndex + 1} of $dayCount",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.primary,
                                        modifier = Modifier.testTag("mealplan_day_position_label")
                                    )
                                    if (todayIndex >= 0 && selectedDayIndex != todayIndex) {
                                        AssistChip(
                                            onClick = {
                                                selectedDayIndex = todayIndex
                                                Log.i("MealPlanUX", "Jump to Today tapped index=$todayIndex")
                                            },
                                            modifier = Modifier.heightIn(min = 32.dp),
                                            label = { Text("Today") }
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (selectedDayIndex < dayCount - 1) {
                                        selectedDayIndex++
                                        Log.i("MealPlanUX", "Day navigation next tapped index=$selectedDayIndex")
                                    }
                                },
                                enabled = selectedDayIndex < dayCount - 1
                            ) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            dayLabels.forEachIndexed { index, label ->
                                val selected = index == selectedDayIndex
                                val isToday = label.equals(todayLabel, true)
                                TokenizedFilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedDayIndex = index
                                        Log.i("MealPlanUX", "Day chip selected index=$index day=$label")
                                    },
                                    text = if (isToday) "$label • Today" else label,
                                    labelMaxWidth = dayChipLabelWidth,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorScheme.primary,
                                        selectedLabelColor = colorScheme.onPrimary,
                                        labelColor = colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                    }

                    // 3. Daily summary card
                    if (false && mealPlanFocus == MealPlanScreenFocus.Today) {
                        item {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MealPlanStatusPill(
                                        text = "$selectedCompletedMeals/${selectedMeals.size} logged",
                                        emphasized = selectedCompletedMeals > 0
                                    )
                                    MealPlanStatusPill(
                                        text = "$selectedCheckInCount check-in(s)",
                                        emphasized = selectedCheckInCount > 0
                                    )
                                }
                                if (selectedMeals.isNotEmpty()) {
                                    LinearProgressIndicator(
                                        progress = { selectedMealCompletionRatio },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = colorScheme.primary
                                    )
                                }
                                Text(
                                    text = selectedDayTrackingSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    }

                    // 4. Meals list
                    if (mealPlanFocus == MealPlanScreenFocus.Today) {
                        item {
                            AnimatedContent(
                                targetState = selectedDayIndex,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs)) +
                                        slideInVertically(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs)) { it / 8 })
                                        .togetherWith(
                                            fadeOut(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs)) +
                                                slideOutVertically(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs)) { -it / 8 }
                                        )
                                },
                                label = "mealPlanDayContent"
                            ) { activeDayIndex ->
                                val activeDay = plan.days.getOrNull(activeDayIndex)
                                val activeDayLabel = activeDay?.dayLabel ?: (dayLabels.getOrNull(activeDayIndex) ?: "Mon")
                                val activeDateKey = weekStartDate
                                    .plusDays(activeDayIndex.toLong())
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val activeMeals = activeDay?.meals.orEmpty()
                                val activeCompletedIds = logs[activeDateKey]?.completedMealIds.orEmpty()
                                val activeCompletedMeals = activeMeals.count { meal ->
                                    val mealKey = ProgressViewModel.buildMealKey(meal.mealLabel, meal.recipeId)
                                    activeCompletedIds.contains(mealKey) || activeCompletedIds.contains(meal.recipeId)
                                }
                                val activeTrackingSummary = when {
                                    activeMeals.isEmpty() -> "No meals planned for this day yet."
                                    activeCompletedMeals >= activeMeals.size -> "Everything for $activeDayLabel is already logged."
                                    activeCompletedMeals == 0 -> "Nothing for $activeDayLabel is logged yet. Open each meal after you finish it."
                                    else -> "$activeCompletedMeals of ${activeMeals.size} meals from $activeDayLabel are already logged."
                                }
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)
                                ) {
                                    AnimatedVisibility(
                                        visible = recentSwapSummary != null && recentSwapDateKey == activeDateKey,
                                        enter = fadeIn(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs)) +
                                            slideInVertically(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs)) { it / 3 },
                                        exit = fadeOut(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs / 2)) +
                                            slideOutVertically(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs / 2)) { -it / 4 }
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.large,
                                            color = colorScheme.primaryContainer.copy(alpha = 0.34f),
                                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.20f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary
                                                    )
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = "Meal updated",
                                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                            color = colorScheme.primary
                                                        )
                                                        Text(
                                                            text = recentSwapSummary.orEmpty(),
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                                        )
                                                    }
                                                }
                                                recentSwapDetail?.let { detail ->
                                                    Text(
                                                        text = detail,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    if (recentSwapHasGroceryUpdate) {
                                                        FilledTonalButton(
                                                            onClick = { onNavigateToRoute(com.pcosina.app.ui.navigation.Routes.GroceryList) },
                                                            shape = MaterialTheme.shapes.large
                                                        ) {
                                                            Text("Open grocery")
                                                        }
                                                        Spacer(Modifier.width(8.dp))
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            recentSwapSummary = null
                                                            recentSwapDetail = null
                                                            recentSwapDateKey = null
                                                            recentSwapHasGroceryUpdate = false
                                                        }
                                                    ) {
                                                        Text("Keep planning")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = MaterialTheme.shapes.large,
                                        color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
                                    ) {
                                        Text(
                                            text = activeTrackingSummary,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (activeMeals.isEmpty()) {
                                        FriendlyEmptyStateCard(
                                            title = "No meals planned for $activeDayLabel",
                                            message = "Review another day, or make a new week if this one needs replacing.",
                                            eyebrow = "Open another day",
                                            accentColor = colorScheme.secondary
                                        )
                                    } else {
                                        activeMeals.forEachIndexed { mealIndex, plannedMeal ->
                                            val mealKey = remember(plannedMeal.mealLabel, plannedMeal.recipeId) {
                                                ProgressViewModel.buildMealKey(plannedMeal.mealLabel, plannedMeal.recipeId)
                                            }
                                            val expanded = expandedMealKey == mealKey
                                            val mealLogged = activeCompletedIds.contains(mealKey) || activeCompletedIds.contains(plannedMeal.recipeId)
                                            Card(
                                                onClick = {
                                                    expandedMealKey = if (expanded) null else mealKey
                                                },
                                                shape = MaterialTheme.shapes.extraLarge,
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (mealLogged) {
                                                        colorScheme.primaryContainer.copy(alpha = 0.28f)
                                                    } else {
                                                        colorScheme.surface
                                                    }
                                                ),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (mealLogged) {
                                                        colorScheme.primary.copy(alpha = 0.30f)
                                                    } else {
                                                        colorScheme.outlineVariant.copy(alpha = 0.60f)
                                                    }
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .background(
                                                                if (mealLogged) colorScheme.primary.copy(alpha = 0.12f) else colorScheme.surfaceVariant,
                                                                CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(text = if (plannedMeal.mealLabel == "Breakfast") "🍳" else if (plannedMeal.mealLabel == "Lunch") "🍱" else "🥘", fontSize = 16.sp)
                                                    }
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        Text(
                                                            text = plannedMeal.title,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = buildString {
                                                                append(plannedMeal.mealLabel)
                                                                append(" • ")
                                                                append(if (mealLogged) "Logged" else "Planned")
                                                                append(" • ")
                                                                append(if (expanded) "Tap to hide actions" else "Tap for actions")
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    IconButton(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .testTag("mealplan_swap_meal_button_$mealIndex"),
                                                        onClick = {
                                                            mealPlanViewModel.trackMlEvent(
                                                                eventName = "manual_override_attempted",
                                                                requestId = currentPlan?.requestId,
                                                                payload = mapOf(
                                                                    "week_label" to currentPlan?.weekLabel.orEmpty(),
                                                                    "day_index" to activeDayIndex,
                                                                    "meal_index" to mealIndex,
                                                                    "meal_label" to plannedMeal.mealLabel,
                                                                    "recipe_id" to plannedMeal.recipeId,
                                                                    "blocked_by_offline" to (!isOnline)
                                                                )
                                                            )
                                                            if (!isOnline) {
                                                                postMealPlanFeedback(
                                                                    tone = FeedbackBannerTone.Error,
                                                                    message = "Internet required for swap options. Connect and try again."
                                                                )
                                                                return@IconButton
                                                            }
                                                            val target = SwapTarget(
                                                                dayIndex = activeDayIndex,
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
                                                                val result = swapOptionsLoader?.invoke(plannedMeal.mealLabel, 40)
                                                                    ?: mealPlanViewModel.getSwapOptions(
                                                                        profile = userProfile,
                                                                        mealLabel = plannedMeal.mealLabel,
                                                                        currentRecipeId = plannedMeal.recipeId,
                                                                        activeRecipeIds = plan.days
                                                                            .flatMap { day -> day.meals }
                                                                            .map { meal -> meal.recipeId },
                                                                        limit = 40
                                                                    )
                                                                result.onSuccess { list ->
                                                                    val filtered = list.filter { it.id != plannedMeal.recipeId }
                                                                    swapOptions = filtered
                                                                    if (filtered.isEmpty()) {
                                                                        swapError = "No other ${plannedMeal.mealLabel.lowercase(Locale.ENGLISH)} ideas are ready right now."
                                                                        postMealPlanFeedback(
                                                                            tone = FeedbackBannerTone.Error,
                                                                            message = swapError.orEmpty()
                                                                        )
                                                                    }
                                                                }.onFailure { e ->
                                                                    swapError = e.message ?: "We couldn't load replacement meals right now."
                                                                    postMealPlanFeedback(
                                                                        tone = FeedbackBannerTone.Error,
                                                                        message = swapError.orEmpty()
                                                                    )
                                                                }
                                                                swapLoading = false
                                                            }
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.SwapHoriz,
                                                            contentDescription = "Swap meal",
                                                            tint = colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                AnimatedVisibility(visible = expanded) {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 52.dp, end = 10.dp, bottom = 10.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Text(
                                                            text = if (mealLogged) {
                                                                "This meal is already logged. Open the recipe if you want to check it again."
                                                            } else {
                                                                "Open the recipe for ingredients and steps, or swap it if you want another option."
                                                            },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            FilledTonalButton(
                                                                onClick = { onRecipeClick(plannedMeal.recipeId, plannedMeal.mealLabel) },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = MaterialTheme.shapes.large
                                                            ) {
                                                                Text("View recipe")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
                MealPlanDialogGotItButton(onClick = { showConfidenceInfo = false })
            },
            title = { Text("Plan fit score", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "This is a simple planner fit score. It checks how closely the week stayed near calorie goals, repeat limits, and your saved food rules. Higher means the week stayed closer to the targets."
                )
            }
        )
    }

    if (showLowGiInfo) {
        AlertDialog(
            onDismissRequest = { showLowGiInfo = false },
            confirmButton = {
                MealPlanDialogGotItButton(onClick = { showLowGiInfo = false })
            },
            title = { Text("Low‑GI guidance", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "We lean toward higher-fiber, balanced meals to help the day feel steadier. " +
                    "Use it as general support, not as medical advice."
                )
            }
        )
    }

    val target = swapTarget
    if (target != null && currentPlan != null) {
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
                    text = "Choose another ${target.mealLabel.lowercase(Locale.ENGLISH)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Pick a different meal for this spot. Your grocery list updates too when the ingredient list is ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.70f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MealPlanStatusPill(
                                text = "Current",
                                emphasized = true
                            )
                            MealPlanStatusPill(
                                text = target.mealLabel,
                                emphasized = false
                            )
                        }
                        Text(
                            text = target.mealTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealPlanStatusPill(
                        text = "${filteredOptions.size} ideas",
                        emphasized = filteredOptions.isNotEmpty()
                    )
                    if (swapQuery.isNotBlank()) {
                        MealPlanStatusPill(
                            text = "Filtered",
                            emphasized = false
                        )
                    }
                    }
                    if (swapQuery.isNotBlank()) {
                        TextButton(onClick = { swapQuery = "" }) {
                            Text("Clear search")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = swapQuery,
                    onValueChange = { swapQuery = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text("Search meal ideas") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (swapLoading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Looking for meals that fit this spot…",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                swapError?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.errorContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (!swapLoading && filteredOptions.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No new meal idea yet",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (swapQuery.isBlank()) {
                                    "Try again in a moment, or keep your current meal for now."
                                } else {
                                    "Try a broader search or clear it to see every match."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MealPlanStatusPill(
                                            text = option.mealType?.takeIf { it.isNotBlank() } ?: target.mealLabel,
                                            emphasized = true
                                        )
                                        option.minutes?.let { minutes ->
                                            MealPlanStatusPill(
                                                text = "${minutes} min",
                                                emphasized = false
                                            )
                                        }
                                    }
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = listOfNotNull(
                                        option.mealType?.takeIf { it.isNotBlank() },
                                        option.minutes?.let { "${it} min" }
                                    )
                                    Text(
                                        text = if (meta.isNotEmpty()) {
                                            "Works for this spot as ${meta.joinToString(" • ")}."
                                        } else {
                                            "Works in the same meal spot and can replace your current choice."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "This replaces ${target.mealTitle} and refreshes the grocery list when ingredient details are ready.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        FilledTonalButton(
                                            enabled = !swapApplying,
                                            onClick = {
                                                if (swapApplying) return@FilledTonalButton
                                                swapApplying = true
                                                postMealPlanFeedback(
                                                    tone = FeedbackBannerTone.Loading,
                                                    message = "Updating your meal…",
                                                    autoClearMs = 0L
                                                )
                                                scope.launch {
                                                    try {
                                                        val mealId = mealPlanViewModel.buildMealInstanceId(
                                                            activePlanId ?: currentPlan.weekLabel,
                                                            target.dayIndex,
                                                            target.mealIndex,
                                                            target.mealLabel
                                                        )
                                                        val itemsResult = (swapGrocerySourceLoader
                                                            ?: mealPlanViewModel::getGrocerySourcesForRecipe).invoke(option.id)
                                                        swapApplyOverride?.invoke(
                                                            target.dayIndex,
                                                            target.mealIndex,
                                                            option.id,
                                                            option.title
                                                        ) ?: mealPlanViewModel.swapMeal(
                                                            dayIndex = target.dayIndex,
                                                            mealIndex = target.mealIndex,
                                                            newRecipeId = option.id,
                                                            newTitle = option.title
                                                        )
                                                        mealPlanViewModel.trackMlEvent(
                                                            eventName = "why_replaced_submitted",
                                                            requestId = currentPlan.requestId,
                                                            payload = mapOf(
                                                                "plan_id" to (activePlanId ?: currentPlan.weekLabel),
                                                                "week_label" to currentPlan.weekLabel,
                                                                "day_index" to target.dayIndex,
                                                                "meal_index" to target.mealIndex,
                                                                "slot_index" to ((target.dayIndex * 3) + target.mealIndex),
                                                                "meal_label" to target.mealLabel,
                                                                "old_recipe_id" to target.recipeId,
                                                                "new_recipe_id" to option.id,
                                                                "reason_tag" to "manual_swap"
                                                            )
                                                        )
                                                        val items = itemsResult.getOrNull()
                                                        recentSwapSummary = "${target.mealLabel} now uses ${option.title}."
                                                        if (items != null) {
                                                            groceryViewModel.replaceMealItems(mealId, items)
                                                            recentSwapDateKey = weekStartDate
                                                                .plusDays(target.dayIndex.toLong())
                                                                .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                                            recentSwapHasGroceryUpdate = true
                                                            if (items.isEmpty()) {
                                                                recentSwapDetail = "Items for this meal were removed from your grocery list."
                                                                postMealPlanFeedback(
                                                                    tone = FeedbackBannerTone.Success,
                                                                    message = "${target.mealLabel} updated. Items for this meal were removed from your grocery list."
                                                                )
                                                            } else {
                                                                recentSwapDetail = "Your grocery list was refreshed too."
                                                                postMealPlanFeedback(
                                                                    tone = FeedbackBannerTone.Success,
                                                                    message = "${target.mealLabel} changed. Your grocery list was refreshed too."
                                                                )
                                                            }
                                                        } else {
                                                            groceryViewModel.replaceMealItems(mealId, emptyList())
                                                            recentSwapDateKey = weekStartDate
                                                                .plusDays(target.dayIndex.toLong())
                                                                .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                                            recentSwapHasGroceryUpdate = false
                                                            recentSwapDetail = "Grocery updates will appear when the ingredient list is ready."
                                                            postMealPlanFeedback(
                                                                tone = FeedbackBannerTone.Success,
                                                                message = "${target.mealLabel} updated. Grocery changes will appear when the ingredient list is ready."
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        postMealPlanFeedback(
                                                            tone = FeedbackBannerTone.Error,
                                                            message = "We couldn't change that meal. Please try again."
                                                        )
                                                    } finally {
                                                        swapApplying = false
                                                        swapTarget = null
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.SwapHoriz,
                                                contentDescription = null
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Choose this meal")
                                        }
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

@Composable
private fun MealPlanStatusPill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun MealPlanDialogGotItButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.semantics { traversalIndex = 1f },
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Text(
            "Got it",
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MealPlanManageWeekHero(
    weekLabel: String,
    goalLabel: String,
    householdLabel: String,
    totalMeals: Int,
    estimatedWeeklyCost: Int?,
    planExpired: Boolean,
    groceryReady: Boolean,
    hasTracked: Boolean,
    ctaLabel: String,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                traversalIndex = 2f
            },
        shape = RoundedCornerShape(30.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (planExpired) "Ready for a new week" else "Your saved week",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.primary
                )
                Text(
                    text = "$weekLabel for $goalLabel. Review it or jump to the next step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Meals",
                        value = "$totalMeals",
                        hint = "Planned for the whole week.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Household",
                        value = householdLabel,
                        hint = "Shopping scales to this size.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Grocery",
                        value = if (groceryReady) "Ready" else "Needs refresh",
                        hint = if (groceryReady) "Your shopping list is ready." else "Open Grocery to refresh it.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Tracking",
                        value = if (hasTracked) "Started" else "Not yet",
                        hint = if (hasTracked) "Progress logging has started." else "Start logging meals to get better feedback.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f),
                        badge = estimatedWeeklyCost?.let { "₱$it" }
                    )
                )
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text(ctaLabel)
            }
        }
    }
}

@Composable
private fun MealPlanTodayHero(
    dayLabel: String,
    mealsPlanned: Int,
    mealsLogged: Int,
    checkInsCount: Int,
    dayCalories: Int,
    trackingSummary: String,
    ctaLabel: String,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Today: $dayLabel",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.secondary
            )
            Text(
                text = trackingSummary,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Meals",
                        value = "$mealsPlanned planned",
                        hint = "Meals planned for this day.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Logged",
                        value = "$mealsLogged/$mealsPlanned",
                        hint = "Meals already checked off.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Check-ins",
                        value = "$checkInsCount",
                        hint = "Meal notes saved for this day.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Calories",
                        value = if (dayCalories > 0) "$dayCalories kcal" else "Pending",
                        hint = "Estimated total for this day.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text(ctaLabel)
            }
        }
    }
}

@Composable
private fun MealPlanInsightsHero(
    goalLabel: String,
    explanationCopy: String,
    householdLabel: String,
    estimatedWeeklyCost: Int?,
    averageCalories: Int,
    lowGiGuidance: Boolean,
    planExpired: Boolean,
    ctaLabel: String,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Why this week fits",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.tertiary
            )
            Text(
                text = explanationCopy,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Goal",
                        value = goalLabel,
                        hint = "The main goal for this week.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Avg kcal",
                        value = if (averageCalories > 0) "$averageCalories" else "Pending",
                        hint = "About this much per day.",
                        accentColor = colorScheme.secondary,
                        badge = "daily"
                    ),
                    CompactWidgetSpec(
                        title = "Household",
                        value = householdLabel,
                        hint = "Shopping scales to this size.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Guidance",
                        value = if (lowGiGuidance) "Steady carbs" else if (planExpired) "Week ended" else "Simple fit",
                        hint = estimatedWeeklyCost?.let { "Week estimate: ₱$it" } ?: "Cost estimate appears when ready.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text(ctaLabel)
            }
        }
    }
}

private fun formatClock(hour: Int, minute: Int): String {
    val normalizedHour = hour.coerceIn(0, 23)
    val normalizedMinute = minute.coerceIn(0, 59)
    val amPm = if (normalizedHour >= 12) "PM" else "AM"
    val displayHour = when (val h = normalizedHour % 12) {
        0 -> 12
        else -> h
    }
    return String.format(Locale.ENGLISH, "%d:%02d %s", displayHour, normalizedMinute, amPm)
}
