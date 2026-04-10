package com.pcosina.app.ui.screens

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
import android.os.Bundle
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
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.LoadingActionButton
import com.pcosina.app.ui.components.StatusCenterCard
import com.pcosina.app.ui.components.SyncStatusChip
import com.pcosina.app.ui.components.TokenizedFilterChip
import com.pcosina.app.notifications.NotificationScheduler
import com.pcosina.app.util.safeUserLogScope
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.util.buildMealReasons
import com.pcosina.app.ui.util.GuidedJourneyInput
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
    val notificationPrefs by userViewModel.notificationPreferences.collectAsState()
    val groceryItems by groceryViewModel.groceryItems.collectAsState()
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
    val weekStartDate = remember(activeWeekStart) {
        val base = activeWeekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        base.with(TemporalAdjusters.previousOrSame(firstDay))
    }
    val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var selectedDayAnchor by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    var showConfidenceInfo by rememberSaveable { mutableStateOf(false) }
    var showLowGiInfo by rememberSaveable { mutableStateOf(false) }
    
    // Track if we are currently extracting ingredients
    var syncState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var generateActionState by remember { mutableStateOf(FeedbackActionState.Idle) }
    var lastSyncStatus by remember { mutableStateOf("No sync yet") }
    var pendingPlanReadyNotification by rememberSaveable { mutableStateOf(false) }
    var feedbackBanner by remember { mutableStateOf<MealPlanBannerState?>(null) }
    var swapTarget by remember { mutableStateOf<SwapTarget?>(null) }
    var swapOptions by remember { mutableStateOf<List<RecipeSummaryDto>>(emptyList()) }
    var swapQuery by remember { mutableStateOf("") }
    var swapLoading by remember { mutableStateOf(false) }
    var swapApplying by remember { mutableStateOf(false) }
    var swapError by remember { mutableStateOf<String?>(null) }
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
    val guidedStep = resolveGuidedJourneyStep(
        GuidedJourneyInput(
            profileComplete = userProfile.isProfileCompleted,
            goal = userProfile.goal,
            hasPlan = hasPlan,
            hasReviewedWeek = hasReviewedWeek,
            hasGrocery = hasGrocery,
            hasTracked = hasTracked
        )
    )

    fun triggerPlanGeneration() {
        if (!isOnline) {
            Log.w("MealPlanUX", "Plan generation blocked: offline ${safeUserLogScope(userViewModel.activeUserId)}")
            generateActionState = FeedbackActionState.Error
            feedbackBanner = MealPlanBannerState(
                data = FeedbackBannerData(
                    tone = FeedbackBannerTone.Error,
                    message = "Failed—tap retry after reconnecting.",
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
                message = "Generating your weekly plan…"
            )
        )
        mealPlanViewModel.generateMealPlan(userProfile)
    }

    fun triggerGrocerySync() {
        if (!isOnline) {
            Log.w("MealPlanUX", "Grocery sync blocked: offline ${safeUserLogScope(userViewModel.activeUserId)}")
            syncState = FeedbackActionState.Error
            lastSyncStatus = "Failed (offline)"
            feedbackBanner = MealPlanBannerState(
                data = FeedbackBannerData(
                    tone = FeedbackBannerTone.Error,
                    message = "Sync failed—tap retry when you're online.",
                    actionLabel = "Retry"
                ),
                action = MealPlanBannerAction.RetrySync
            )
            scope.launch {
                val userId = userViewModel.activeUserId
                if (userId.isNotBlank()) {
                    NotificationScheduler.notifyGrocerySyncResult(context, userId, success = false)
                }
            }
            return
        }
        analytics.logEvent("sync_groceries", null)
        syncState = FeedbackActionState.Loading
        lastSyncStatus = "Syncing…"
        feedbackBanner = MealPlanBannerState(
            data = FeedbackBannerData(
                tone = FeedbackBannerTone.Loading,
                message = "Syncing groceries…"
            )
        )
        mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
            if (sources.isEmpty()) {
                Log.w("MealPlanUX", "Grocery sync failed: no extracted sources ${safeUserLogScope(userViewModel.activeUserId)}")
                syncState = FeedbackActionState.Error
                lastSyncStatus = "Failed (no items)"
                feedbackBanner = MealPlanBannerState(
                    data = FeedbackBannerData(
                        tone = FeedbackBannerTone.Error,
                        message = "Failed—tap retry after regenerating your plan.",
                        actionLabel = "Retry"
                    ),
                    action = MealPlanBannerAction.RetrySync
                )
                scope.launch {
                    val userId = userViewModel.activeUserId
                    if (userId.isNotBlank()) {
                        NotificationScheduler.notifyGrocerySyncResult(context, userId, success = false)
                    }
                }
                return@extractGrocerySourcesForPlan
            }
            groceryViewModel.setPlanSources(sources)
            Log.i(
                "MealPlanUX",
                "Grocery sync success: ${sources.values.sumOf { it.size }} items mapped ${safeUserLogScope(userViewModel.activeUserId)}"
            )
            syncState = FeedbackActionState.Success
            lastSyncStatus = "Synced successfully"
            feedbackBanner = MealPlanBannerState(
                data = FeedbackBannerData(
                    tone = FeedbackBannerTone.Success,
                    message = "Synced to Grocery List."
                )
            )
            scope.launch {
                val userId = userViewModel.activeUserId
                if (userId.isNotBlank()) {
                    NotificationScheduler.notifyGrocerySyncResult(context, userId, success = true)
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
    LaunchedEffect(uiState, generationNotice) {
        when (uiState) {
            is MealPlanUiState.Loading -> {
                generateActionState = FeedbackActionState.Loading
                pendingPlanReadyNotification = true
                feedbackBanner = MealPlanBannerState(
                    data = FeedbackBannerData(
                        tone = FeedbackBannerTone.Loading,
                        message = "Generating your weekly plan…"
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
                                message = "No safe new plan was generated. Review the guidance below or retry.",
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
                                message = "Plan ready. Review your week and continue."
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
                            message = "Plan generation failed—tap retry.",
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
    LaunchedEffect(activePlanId, currentPlan?.weekLabel) {
        val anchor = activePlanId ?: currentPlan?.weekLabel
        if (anchor != null && anchor != selectedDayAnchor) {
            selectedDayIndex = when (LocalDate.now().dayOfWeek) {
                DayOfWeek.MONDAY -> 0
                DayOfWeek.TUESDAY -> 1
                DayOfWeek.WEDNESDAY -> 2
                DayOfWeek.THURSDAY -> 3
                DayOfWeek.FRIDAY -> 4
                DayOfWeek.SATURDAY -> 5
                DayOfWeek.SUNDAY -> 6
            }
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
    Scaffold(
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
                            text = "Step 3 of 4: Generate Plan",
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
                                    text = "Generate your first optimized week",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "This creates your plan in about 30 seconds and unlocks the next steps.",
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
                        LoadingActionButton(
                            state = generateActionState,
                            idleLabel = "Generate My Optimized Plan",
                            loadingLabel = "Generating plan…",
                            successLabel = "Plan Ready",
                            errorLabel = "Retry Generation",
                            onClick = { triggerPlanGeneration() },
                            enabled = isOnline,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        )
                        Text(
                            text = if (isOnline) "Status: Online" else "Status: Offline",
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
                            "MILP Engine is optimizing…",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.secondary
                        )
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
                            text = "Step 3 of 4: Generate Plan",
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
                                    text = if (noSafePlanNotice != null) {
                                        "No safe plan is available yet"
                                    } else {
                                        "We couldn’t generate your plan"
                                    },
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
                        LoadingActionButton(
                            state = generateActionState,
                            idleLabel = "Retry",
                            loadingLabel = "Retrying…",
                            successLabel = "Recovered",
                            errorLabel = "Retry failed",
                            onClick = { triggerPlanGeneration() },
                            enabled = isOnline,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (isOnline) "Status: Online" else "Status: Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
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
                val nextBestAction = remember(planExpired, hasGrocery, hasTracked, isOnline, userProfile.goal) {
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
                        !hasGrocery -> MealPlanNextAction(
                            label = if (isOnline) "Sync Grocery List" else "Sync Grocery List (Internet required)",
                            reason = "Why this helps $goalLabel: synced groceries remove friction between plan and shopping.",
                            enabled = isOnline,
                            onClick = {
                                logNextBestActionTap("sync_grocery_list")
                                triggerGrocerySync()
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
                    Text(
                        text = "Step 3 of 4: Generate Plan",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().testTag("mealplan_step3_label")
                    )
                }
                item {
                    GuidedJourneyCard(
                        step = guidedStep,
                        onContinue = { step -> onNavigateToRoute(step.route) }
                    )
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
                    val queuedCount = feedbackQueue.count { it.status != "Sent" }
                    val remindersEnabled = notificationPrefs.masterEnabled && notificationPrefs.mealRemindersEnabled
                    val nextReminder = "Next reminders: B ${formatClock(notificationPrefs.breakfastHour, notificationPrefs.breakfastMinute)} • " +
                        "L ${formatClock(notificationPrefs.lunchHour, notificationPrefs.lunchMinute)} • " +
                        "D ${formatClock(notificationPrefs.dinnerHour, notificationPrefs.dinnerMinute)}"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mealplan_top_section_capture"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mealplan_next_best_action_card"),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Next best action",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = nextBestAction.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("mealplan_next_best_action_reason")
                                )
                                Button(
                                    onClick = nextBestAction.onClick,
                                    enabled = nextBestAction.enabled,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("mealplan_next_best_action_cta"),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(nextBestAction.label)
                                }
                            }
                        }
                        StatusCenterCard(
                            queuedActionsLabel = if (queuedCount > 0) "Queued actions: $queuedCount" else null,
                            syncLabel = null,
                            planRangeLabel = "Plan range: ${plan.weekLabel}",
                            nextReminderLabel = if (remindersEnabled) nextReminder else null
                        )
                    }
                }
                item {
                    if (!isOnline) {
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
                        Spacer(Modifier.height(10.dp))
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
                                    text = "Aligned to your goal: ${primaryGoalLabel(userProfile.goal)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Why this matters: this week is optimized to support your selected goal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Use the day chips below to review each meal. Primary next action is pinned above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
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
                        LoadingActionButton(
                            state = generateActionState,
                            idleLabel = "Generate New Week",
                            loadingLabel = "Generating…",
                            successLabel = "Week ready",
                            errorLabel = "Try again",
                            onClick = { triggerPlanGeneration() },
                            enabled = isOnline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("mealplan_generate_new_week_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        )
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
                                    TokenizedFilterChip(
                                        selected = selected,
                                        onClick = { mealPlanViewModel.selectPlan(instance.id) },
                                        text = instance.response.weekLabel,
                                        labelMaxWidth = weekChipLabelWidth,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = colorScheme.primary,
                                            selectedLabelColor = colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { previousPlan?.let { mealPlanViewModel.selectPlan(it.id) } },
                                    enabled = previousPlan != null
                                ) {
                                    Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Prev")
                                }
                                TextButton(onClick = onViewProgress) { Text("View Insights") }
                                OutlinedButton(
                                    onClick = { nextPlan?.let { mealPlanViewModel.selectPlan(it.id) } },
                                    enabled = nextPlan != null
                                ) {
                                    Text("Next")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                        if (supportsLowGiGuidance(userProfile.goal)) {
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
                                    Spacer(Modifier.height(6.dp))
                                    SyncStatusChip(state = syncState)
                                }
                                LoadingActionButton(
                                    state = syncState,
                                    idleLabel = if (isOnline) "Sync" else "Sync (Internet required)",
                                    loadingLabel = "Syncing…",
                                    successLabel = "Synced",
                                    errorLabel = "Retry Sync",
                                    onClick = { triggerGrocerySync() },
                                    enabled = isOnline
                                )
                            }
                        }
                    }

                    // 2. Day selector chips + navigation hint
                    item {
                        val dayCount = dayLabels.size
                        val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
                        val todayIndex = dayLabels.indexOfFirst { it.equals(todayLabel, ignoreCase = true) }
                        val weekendStartIndex = dayLabels.indexOfFirst { it.equals("Sat", ignoreCase = true) }
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
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Day ${selectedDayIndex + 1} of $dayCount • $selectedDayLabel selected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.primary,
                                    modifier = Modifier.testTag("mealplan_day_position_label")
                                )
                                Text(
                                    text = "Swipe left/right or use arrows to view all days (Sat-Sun included).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (todayIndex >= 0 && selectedDayIndex != todayIndex) {
                                        TextButton(onClick = {
                                            selectedDayIndex = todayIndex
                                            Log.i("MealPlanUX", "Jump to Today tapped index=$todayIndex")
                                        }) {
                                            Text("Jump to Today ($todayLabel)")
                                        }
                                    }
                                    if (weekendStartIndex >= 0 && selectedDayIndex < weekendStartIndex) {
                                        TextButton(onClick = {
                                            selectedDayIndex = weekendStartIndex
                                            Log.i(
                                                "MealPlanUX",
                                                "Jump to weekend tapped index=$weekendStartIndex"
                                            )
                                        }) {
                                            Text("Jump to Weekend (Sat)")
                                        }
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
                        Spacer(Modifier.height(8.dp))
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
                            onClick = { onRecipeClick(plannedMeal.recipeId, plannedMeal.mealLabel) },
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
                                    modifier = Modifier.testTag("mealplan_swap_meal_button_$mealIndex"),
                                    onClick = {
                                        mealPlanViewModel.trackMlEvent(
                                            eventName = "manual_override_attempted",
                                            requestId = currentPlan?.requestId,
                                            payload = mapOf(
                                                "week_label" to currentPlan?.weekLabel.orEmpty(),
                                                "day_index" to selectedDayIndex,
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
                                            val result = (swapOptionsLoader ?: mealPlanViewModel::getSwapOptions)
                                                .invoke(plannedMeal.mealLabel, 40)
                                            result.onSuccess { list ->
                                                val filtered = list.filter { it.id != plannedMeal.recipeId }
                                                swapOptions = filtered
                                                if (filtered.isEmpty()) {
                                                    swapError = "No swaps available for ${plannedMeal.mealLabel}."
                                                    postMealPlanFeedback(
                                                        tone = FeedbackBannerTone.Error,
                                                        message = swapError.orEmpty()
                                                    )
                                                }
                                            }.onFailure { e ->
                                                swapError = e.message ?: "Unable to load swap options."
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
                MealPlanDialogGotItButton(onClick = { showConfidenceInfo = false })
            },
            title = { Text("Confidence score", modifier = Modifier.semantics { heading() }) },
            text = {
                Text(
                    "Heuristic score based on how tightly the plan matches calorie targets, " +
                    "tolerance level used, repeat limits, and restriction complexity. " +
                    "Higher is better."
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
                    "We favor higher‑fiber, balanced meals to support steadier energy. " +
                    "This is guidance only and not medical treatment."
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
                                            postMealPlanFeedback(
                                                tone = FeedbackBannerTone.Loading,
                                                message = "Applying meal swap…",
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
                                                    if (items != null) {
                                                        if (groceryViewModel.hasSourcesForMeal(mealId)) {
                                                            groceryViewModel.replaceMealItems(mealId, items)
                                                            if (items.isEmpty()) {
                                                                postMealPlanFeedback(
                                                                    tone = FeedbackBannerTone.Success,
                                                                    message = "Swapped ${target.mealLabel}: ${target.mealTitle} -> ${option.title}. Grocery items cleared for this meal."
                                                                )
                                                            } else {
                                                                postMealPlanFeedback(
                                                                    tone = FeedbackBannerTone.Success,
                                                                    message = "Swapped ${target.mealLabel}: ${target.mealTitle} -> ${option.title}. Grocery updated with ${items.size} ingredient changes."
                                                                )
                                                            }
                                                        } else {
                                                            postMealPlanFeedback(
                                                                tone = FeedbackBannerTone.Success,
                                                                message = "Swapped ${target.mealLabel}: ${target.mealTitle} -> ${option.title}. Sync groceries to update the list."
                                                            )
                                                        }
                                                    } else {
                                                        postMealPlanFeedback(
                                                            tone = FeedbackBannerTone.Success,
                                                            message = "Swapped ${target.mealLabel}: ${target.mealTitle} -> ${option.title}. Grocery unchanged because ingredient data was unavailable."
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    postMealPlanFeedback(
                                                        tone = FeedbackBannerTone.Error,
                                                        message = "Swap failed. Please try again."
                                                    )
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

@Composable
private fun MealPlanDialogGotItButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { traversalIndex = 1f }
    ) {
        Text("Got it")
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
