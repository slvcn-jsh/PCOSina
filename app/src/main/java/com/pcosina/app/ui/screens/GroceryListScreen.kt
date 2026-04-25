package com.pcosina.app.ui.screens

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GuidedJourneyCard
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.ExpandableSection
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.CompactWidgetGrid
import com.pcosina.app.ui.components.CompactWidgetSpec
import com.pcosina.app.ui.components.FeedbackActionState
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.FocusModePanel
import com.pcosina.app.ui.components.FriendlyEmptyStateCard
import com.pcosina.app.ui.components.LoadingActionButton
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip
import com.pcosina.app.ui.components.StatusCenterCard
import com.pcosina.app.ui.components.TokenizedFilterChip
import com.pcosina.app.ui.theme.PcosinaSuccess
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.util.safeUserLogScope
import com.pcosina.app.domain.GroceryListEntry
import com.pcosina.app.domain.buildGroceryListEntries
import com.pcosina.app.domain.householdSizeLabel
import com.pcosina.app.ui.util.GuidedJourneyInput
import com.pcosina.app.ui.util.ActionFeedbackCopy
import com.pcosina.app.ui.util.LockedFlowCopy
import com.pcosina.app.ui.util.goalShoppingTips
import com.pcosina.app.ui.util.rememberIsOnline
import com.pcosina.app.ui.util.resolveGuidedJourneyStep
import com.pcosina.app.ui.navigation.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class GroceryItemStatusFilter(val label: String) {
    All("All items"),
    NeedToBuy("Need to buy"),
    Completed("Bought / pantry")
}

private enum class GroceryScreenFocus {
    List,
    Pantry,
    Budget,
    Tips,
}

private const val AllCategoriesFilterKey = "All categories"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroceryListScreen(
    groceryViewModel: GroceryViewModel,
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    progressViewModel: ProgressViewModel,
    onNavigateToRoute: (String) -> Unit = {},
    onlineStateOverride: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val addedItems by groceryViewModel.groceryItems.collectAsState()
    val lastPlanTimestamp by groceryViewModel.lastPlanTimestamp.collectAsState()
    val activePlanId by groceryViewModel.activePlanId.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val planState by mealPlanViewModel.uiState.collectAsState()
    val lastReviewedWeek by mealPlanViewModel.lastReviewedWeek.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val focusManager = LocalFocusManager.current
    val activeUserId = userViewModel.activeUserId
    val allItems = remember(addedItems) {
        addedItems
            .filter { it.name.isNotBlank() }
            .distinctBy { item ->
                "${item.name.trim().lowercase(Locale.getDefault())}|${item.quantity.trim().lowercase(Locale.getDefault())}"
            }
    }
    val householdSize = userProfile.householdSize.coerceIn(1, 6)
    val householdLabel = remember(householdSize) { householdSizeLabel(householdSize) }
    val groupedEntries = remember(allItems, householdSize) {
        buildGroceryListEntries(allItems, householdSize)
    }
    val allItemNames = remember(groupedEntries) { groupedEntries.map { it.name }.toSet() }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val groceryChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 108.dp, medium = 168.dp)
    val pantryChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 132.dp, medium = 196.dp)
    var groceryFocusKey by rememberSaveable { mutableStateOf(GroceryScreenFocus.List.name) }
    val weeklyBudget = userProfile.weeklyBudgetPhp.takeIf { it > 0 }
    var checkedNames by remember { mutableStateOf(setOf<String>()) }
    var pantryOptOut by remember { mutableStateOf(setOf<String>()) }
    val hasPlan = planHistory.isNotEmpty() || planState is MealPlanUiState.Success
    val hasReviewedWeek = activePlanId != null && activePlanId == lastReviewedWeek
    val hasGrocery = addedItems.isNotEmpty()
    val hasTracked = logs.isNotEmpty()
    val showJourneyCard = !hasPlan || !hasGrocery
    val groceryLockedCopy = remember { LockedFlowCopy.groceryLocked() }
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

    var budgetMode by rememberSaveable { mutableStateOf("Weekly") }
    val displayBudget = weeklyBudget?.let { if (budgetMode == "Weekly") it.toDouble() else it * 4.33 }
    val derivedWeekly = displayBudget?.let { if (budgetMode == "Monthly") (it / 4.33) else it }

    val pantryEntries by userViewModel.pantryEntries.collectAsState()
    val pantryItems = remember(pantryEntries) {
        pantryEntries.map { it.name.trim() }.filter { it.isNotBlank() }
    }
    val pantryTokens = remember(pantryItems) {
        pantryItems.map(::normalizedPantryEntryKey).filter { it.isNotBlank() }.toSet()
    }
    val pantryMatches = remember(groupedEntries, pantryTokens) {
        groupedEntries.filter { item ->
            pantryTokens.any { pantryName ->
                pantryEntryMatchesGroceryItem(pantryName, item.name)
            }
        }.map { it.name }.toSet()
    }
    val effectiveCheckedNames = remember(checkedNames, pantryMatches, pantryOptOut) {
        checkedNames + pantryMatches.filter { it !in pantryOptOut }
    }

    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var sortAlpha by rememberSaveable { mutableStateOf(false) }
    var statusFilter by rememberSaveable { mutableStateOf(GroceryItemStatusFilter.All) }
    var selectedCategoryFilter by rememberSaveable { mutableStateOf(AllCategoriesFilterKey) }
    var showListFilters by rememberSaveable { mutableStateOf(false) }
    var showLockedInfo by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var groceryFeedbackBanner by remember { mutableStateOf<FeedbackBannerData?>(null) }
    var groceryActionNoteTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var groceryActionNoteDetail by rememberSaveable { mutableStateOf<String?>(null) }
    val groceryPrimaryHeroActionState = remember { mutableStateOf(FeedbackActionState.Idle) }
    val grocerySecondaryHeroActionState = remember { mutableStateOf(FeedbackActionState.Idle) }
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val searchFilteredItems = if (searchQuery.text.isBlank()) groupedEntries else groupedEntries.filter {
        it.name.contains(searchQuery.text, ignoreCase = true)
    }
    val filteredItems = when (statusFilter) {
        GroceryItemStatusFilter.All -> searchFilteredItems
        GroceryItemStatusFilter.NeedToBuy -> searchFilteredItems.filter { item ->
            item.name !in effectiveCheckedNames
        }
        GroceryItemStatusFilter.Completed -> searchFilteredItems.filter { item ->
            item.name in effectiveCheckedNames
        }
    }
    val emptySearchResults = searchQuery.text.isNotBlank() && searchFilteredItems.isEmpty()
    val emptyFilterResults = groupedEntries.isNotEmpty() && filteredItems.isEmpty() && !emptySearchResults

    val groups: Map<String, List<GroceryListEntry>> = filteredItems.groupBy { it.category }
    val categoryOrder = listOf(
        "Produce",
        "Meat/Seafood",
        "Eggs & Dairy",
        "Dry Goods",
        "Spices & Condiments",
        "Canned/Packaged",
        "Beverages",
        "Others"
    )
    val categoryCounts = remember(groups) {
        groups.mapValues { (_, items) -> items.size }
    }
    val orderedCategoryEntries = remember(categoryCounts) {
        val preferred = categoryOrder.mapNotNull { category ->
            categoryCounts[category]?.let { count -> category to count }
        }
        val custom = categoryCounts.keys
            .filter { it !in categoryOrder }
            .sorted()
            .mapNotNull { category ->
                categoryCounts[category]?.let { count -> category to count }
            }
        preferred + custom
    }
    val displayCategories = remember(selectedCategoryFilter, orderedCategoryEntries) {
        if (selectedCategoryFilter == AllCategoriesFilterKey) {
            orderedCategoryEntries.map { it.first }
        } else {
            listOf(selectedCategoryFilter)
        }
    }

    var expandedMap by rememberSaveable(displayCategories) {
        mutableStateOf(displayCategories.associateWith { defaultCategoryExpanded(it) })
    }
    val allExpanded = displayCategories.isNotEmpty() && expandedMap.values.all { it }
    val anyExpanded = displayCategories.any { expandedMap[it] == true }
    val weekLabel = remember(activePlanId, lastPlanTimestamp) {
        formatWeekRange(activePlanId, lastPlanTimestamp)
    }

    LaunchedEffect(activeUserId, activePlanId) {
        checkedNames = emptySet()
        pantryOptOut = emptySet()
        searchQuery = TextFieldValue("")
        statusFilter = GroceryItemStatusFilter.All
        selectedCategoryFilter = AllCategoriesFilterKey
        Log.i(
            "GroceryUX",
            "Reset checklist/search for ${safeUserLogScope(activeUserId)} plan=${activePlanId ?: "none"}"
        )
    }

    LaunchedEffect(selectedCategoryFilter, orderedCategoryEntries) {
        if (selectedCategoryFilter == AllCategoriesFilterKey) return@LaunchedEffect
        val stillAvailable = orderedCategoryEntries.any { (category, _) ->
            category == selectedCategoryFilter
        }
        if (!stillAvailable) {
            selectedCategoryFilter = AllCategoriesFilterKey
        }
    }
    LaunchedEffect(displayCategories, selectedCategoryFilter) {
        if (selectedCategoryFilter != AllCategoriesFilterKey && displayCategories.size == 1) {
            expandedMap = displayCategories.associateWith { it == selectedCategoryFilter }
        }
    }

    LaunchedEffect(statusFilter, selectedCategoryFilter, searchQuery.text, activeUserId) {
        Log.i(
            "GroceryUX",
            "Filters ${safeUserLogScope(activeUserId)} status=${statusFilter.label} category=$selectedCategoryFilter query='${searchQuery.text.trim()}' results=${filteredItems.size}"
        )
    }

    LaunchedEffect(emptySearchResults, searchQuery.text, activeUserId) {
        if (emptySearchResults) {
            Log.i(
                "GroceryUX",
                "No grocery search matches for ${safeUserLogScope(activeUserId)} query='${searchQuery.text.trim()}'"
            )
        }
    }

    LaunchedEffect(groupedEntries.size, hasPlan, activeUserId) {
        if (groupedEntries.isEmpty()) {
            Log.i(
                "GroceryUX",
                "Grocery list empty for ${safeUserLogScope(activeUserId)} hasPlan=$hasPlan"
            )
        }
    }
    fun postGroceryFeedback(
        tone: FeedbackBannerTone,
        message: String
    ) {
        groceryFeedbackBanner = FeedbackBannerData(
            tone = tone,
            message = message
        )
        Log.i("GroceryUX", message)
        if (tone != FeedbackBannerTone.Loading) {
            coroutineScope.launch {
                delay(2200)
                if (groceryFeedbackBanner?.message == message) {
                    groceryFeedbackBanner = null
                }
            }
        }
    }
    fun noteGroceryAction(title: String, detail: String) {
        groceryActionNoteTitle = title
        groceryActionNoteDetail = detail
    }
    fun runGroceryHeroAction(
        state: androidx.compose.runtime.MutableState<FeedbackActionState>,
        loadingMessage: String,
        successMessage: String,
        action: () -> Unit
    ) {
        if (state.value == FeedbackActionState.Loading) return
        coroutineScope.launch {
            state.value = FeedbackActionState.Loading
            postGroceryFeedback(
                tone = FeedbackBannerTone.Loading,
                message = loadingMessage
            )
            delay(140)
            state.value = FeedbackActionState.Success
            postGroceryFeedback(
                tone = FeedbackBannerTone.Success,
                message = successMessage
            )
            delay(110)
            action()
            delay(500)
            state.value = FeedbackActionState.Idle
        }
    }
    LaunchedEffect(groceryActionNoteTitle, groceryActionNoteDetail) {
        val currentTitle = groceryActionNoteTitle ?: return@LaunchedEffect
        val currentDetail = groceryActionNoteDetail
        delay(2800)
        if (groceryActionNoteTitle == currentTitle && groceryActionNoteDetail == currentDetail) {
            groceryActionNoteTitle = null
            groceryActionNoteDetail = null
        }
    }
    LaunchedEffect(groceryFocusKey) {
        groceryPrimaryHeroActionState.value = FeedbackActionState.Idle
        grocerySecondaryHeroActionState.value = FeedbackActionState.Idle
    }

    fun effectivePrice(item: GroceryListEntry): Int {
        return item.estimatedCostPhp.coerceAtLeast(0)
    }
    val remainingCount = groupedEntries.count { it.name !in effectiveCheckedNames }
    val completedCount = (groupedEntries.size - remainingCount).coerceAtLeast(0)
    val pantryCoveredCount = pantryMatches.count { it !in pantryOptOut }
    val totalCost = groupedEntries
        .filter { it.name !in effectiveCheckedNames }
        .sumOf { effectivePrice(it) }
    val savings = displayBudget?.toInt()?.minus(totalCost) ?: 0
    val costProgress = if (displayBudget != null && displayBudget > 0) {
        (totalCost.toFloat() / displayBudget.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val shoppingProgress = if (groupedEntries.isNotEmpty()) {
        completedCount.toFloat() / groupedEntries.size.toFloat()
    } else 0f
    val groceryStatusSummary = when {
        !hasPlan -> "Generate a weekly plan to unlock shopping totals and auto-filled items."
        groupedEntries.isEmpty() -> "Plan exists, but grocery items have not populated yet."
        pantryMatches.isNotEmpty() -> "Pantry-first matches are already marked so you can shop the gap."
        else -> "Shopping list is ready for review and check-off."
    }
    val grocerySyncSummary = if (isOnline) {
        "Online: grocery changes can sync when needed."
    } else {
        "Offline-safe: using your saved grocery list."
    }
    val hasActiveListControls = searchQuery.text.isNotBlank() ||
        sortAlpha ||
        statusFilter != GroceryItemStatusFilter.All ||
        selectedCategoryFilter != AllCategoriesFilterKey
    val groceryNextFocus = when {
        !hasPlan -> "Next focus: open meal planning"
        groupedEntries.isEmpty() -> "Next focus: review your meal plan"
        emptySearchResults -> "Next focus: clear search and browse all categories"
        weeklyBudget == null -> "Next focus: add a budget in Profile for spending alerts"
        else -> "Next focus: filter to need-to-buy items and check them off"
    }
    val groceryFocus = remember(groceryFocusKey) {
        GroceryScreenFocus.valueOf(groceryFocusKey)
    }
    val groceryFocusOptions = remember(weeklyBudget, groupedEntries.isNotEmpty()) {
        listOf(
            ScreenFocusOption(
                key = GroceryScreenFocus.List.name,
                label = "List",
                summary = "See your shopping list and filters."
            ),
            ScreenFocusOption(
                key = GroceryScreenFocus.Pantry.name,
                label = "Pantry",
                summary = "Manage what you already have."
            ),
            ScreenFocusOption(
                key = GroceryScreenFocus.Budget.name,
                label = "Spend",
                summary = if (weeklyBudget == null) {
                    "See estimated cost and decide if you want a budget."
                } else {
                    "See estimated cost and budget pressure."
                }
            ),
            ScreenFocusOption(
                key = GroceryScreenFocus.Tips.name,
                label = "Help",
                summary = "Open shopping help and next steps."
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("grocery_content_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = UiSpacingTokens.SectionGap),
        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
    ) {
        item {
            GradientHeader(
                title = "Shopping",
                subtitle = if (hasPlan) {
                    "$weekLabel • What to buy, what you have, and what is done."
                } else {
                    "Your shopping list appears after you create a week."
                },
                containerHeight = 116,
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
        groceryFeedbackBanner?.let { banner ->
            item {
                AppFeedbackBanner(
                    data = banner,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (!isOnline) {
            item {
                AppFeedbackBanner(
                    data = FeedbackBannerData(
                        tone = FeedbackBannerTone.Loading,
                        message = ActionFeedbackCopy.OfflineSync
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            ScreenFocusStrip(
                title = "Show",
                options = groceryFocusOptions,
                selectedKey = groceryFocusKey,
                onSelect = { groceryFocusKey = it },
                labelMaxWidth = groceryChipLabelWidth
            )
        }
        item {
            FocusModePanel(
                targetKey = groceryFocusKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grocery_focus_panel")
            ) { focusKey ->
                when (GroceryScreenFocus.valueOf(focusKey)) {
                    GroceryScreenFocus.List -> GroceryListHero(
                        hasPlan = hasPlan,
                        weekLabel = weekLabel,
                        totalItems = groupedEntries.size,
                        remainingCount = remainingCount,
                        completedCount = completedCount,
                        totalCost = totalCost,
                        selectedCategoryLabel = if (selectedCategoryFilter == AllCategoriesFilterKey) "All categories" else selectedCategoryFilter,
                        statusFilterLabel = statusFilter.label,
                        onOpenPlan = {
                            if (!isOnline) {
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Error,
                                    message = "${ActionFeedbackCopy.InternetRequired} Connect to open plan generation."
                                )
                            } else {
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Loading,
                                    message = "Opening plan generator…"
                                )
                                onNavigateToRoute(Routes.MealPlan)
                            }
                        },
                        lockedMessage = groceryLockedCopy.cardText
                    )

                    GroceryScreenFocus.Pantry -> GroceryPantryHero(
                        pantryCount = pantryEntries.size,
                        pantryCoveredCount = pantryCoveredCount,
                        remainingCount = remainingCount,
                        householdLabel = householdLabel
                    )

                    GroceryScreenFocus.Budget -> GroceryBudgetHero(
                        totalCost = totalCost,
                        displayBudget = displayBudget?.toInt(),
                        savings = savings,
                        remainingCount = remainingCount,
                        completedCount = completedCount,
                        totalItems = groupedEntries.size,
                        actionState = groceryPrimaryHeroActionState.value,
                        onOpenProgress = {
                            runGroceryHeroAction(
                                state = groceryPrimaryHeroActionState,
                                loadingMessage = "Opening progress…",
                                successMessage = "Progress opened."
                            ) {
                                onNavigateToRoute(Routes.Progress)
                            }
                        }
                    )

                    GroceryScreenFocus.Tips -> GroceryTipsHero(
                        tipCount = goalShoppingTips(userProfile.goal, householdSize).size,
                        hasItems = groupedEntries.isNotEmpty(),
                        planActionState = grocerySecondaryHeroActionState.value,
                        progressActionState = groceryPrimaryHeroActionState.value,
                        onOpenPlan = {
                            runGroceryHeroAction(
                                state = grocerySecondaryHeroActionState,
                                loadingMessage = "Opening meal plan…",
                                successMessage = "Meal plan opened."
                            ) {
                                onNavigateToRoute(Routes.MealPlan)
                            }
                        },
                        onOpenProgress = {
                            runGroceryHeroAction(
                                state = groceryPrimaryHeroActionState,
                                loadingMessage = "Opening progress…",
                                successMessage = "Progress opened."
                            ) {
                                onNavigateToRoute(Routes.Progress)
                            }
                        }
                    )
                }
            }
        }
        if (!groceryActionNoteTitle.isNullOrBlank() && !groceryActionNoteDetail.isNullOrBlank()) {
            item {
                AnimatedVisibility(visible = true) {
                    GroceryActionNoteCard(
                        title = groceryActionNoteTitle.orEmpty(),
                        detail = groceryActionNoteDetail.orEmpty()
                    )
                }
            }
        }
        if (false && groceryFocus != GroceryScreenFocus.Pantry) {
            item {
                StatusCenterCard(
                    queuedActionsLabel = groceryStatusSummary,
                    syncLabel = grocerySyncSummary,
                    planRangeLabel = if (hasPlan) "Shopping week: $weekLabel" else "No shopping week active yet",
                    nextReminderLabel = groceryNextFocus,
                    modifier = Modifier.testTag("grocery_status_center_card")
                )
            }
        }

        if (false && !hasPlan && groceryFocus == GroceryScreenFocus.List) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = groceryLockedCopy.cardText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        InputChip(
                            selected = false,
                            onClick = { showLockedInfo = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = { Text(LockedFlowCopy.LearnMoreLabel) },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        FilledTonalButton(
                            onClick = {
                                if (!isOnline) {
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Error,
                                        message = "${ActionFeedbackCopy.InternetRequired} Connect to open plan generation."
                                    )
                                } else {
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Loading,
                                        message = "Opening plan generator…"
                                    )
                                    onNavigateToRoute(Routes.MealPlan)
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text("Open meal plan builder")
                        }
                    }
                }
            }
        }
        if (false && hasPlan && groceryFocus == GroceryScreenFocus.Tips) {
            item {
                Card(
                    modifier = Modifier.testTag("grocery_next_steps_card"),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "What to do next",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = if (groupedEntries.isEmpty()) {
                                "Your shopping list fills in from your week. If you just made a plan, wait a moment."
                            } else {
                                "Check items as you shop, then open Progress after meals."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Loading,
                                        message = "Opening your meal plan…"
                                    )
                                    onNavigateToRoute(Routes.MealPlan)
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("grocery_open_mealplan_cta")
                            ) {
                                Text("Open Plan")
                            }
                            OutlinedButton(
                                onClick = {
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Loading,
                                        message = "Opening Progress tracking…"
                                    )
                                    onNavigateToRoute(Routes.Progress)
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("grocery_open_progress_cta")
                            ) {
                                Text("Open Progress")
                            }
                        }
                    }
                }
            }
        }

        if (groceryFocus == GroceryScreenFocus.Pantry) {
            item {
                ExpandableSection(
                    title = "Manage pantry",
                    subtitle = if (pantryEntries.isEmpty()) {
                        "Save what you already have"
                    } else {
                        "${pantryEntries.size} item(s) saved"
                    },
                    defaultExpanded = false
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    val compactPantryAddFlow = screenWidthDp <= 360
                    var pantryInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
                    var pantryQty by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
                    var pantryExpiry by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
                    var showAllPantryItems by rememberSaveable(pantryEntries.size) { mutableStateOf(false) }
                    var showPantryAddForm by rememberSaveable(compactPantryAddFlow) {
                        mutableStateOf(!compactPantryAddFlow)
                    }
                    val addPantryEntry = {
                        val name = pantryInput.text.trim()
                        if (name.isBlank()) {
                            postGroceryFeedback(
                                tone = FeedbackBannerTone.Error,
                                message = "Enter a pantry item name before saving."
                            )
                        } else {
                            val newEntry = PantryEntry(
                                name = name,
                                quantity = pantryQty.text.trim().takeIf { it.isNotBlank() },
                                expiryDate = pantryExpiry.text.trim().takeIf { it.isNotBlank() }
                            )
                            val updated = (pantryEntries + newEntry)
                                .distinctBy { normalizedPantryEntryKey(it.name) }
                            userViewModel.updatePantryEntries(updated)
                            mealPlanViewModel.trackMlEvent(
                                eventName = "pantry_item_added",
                                payload = mapOf(
                                    "item_name" to name,
                                    "quantity" to (newEntry.quantity ?: ""),
                                    "expiry_date" to (newEntry.expiryDate ?: "")
                                )
                            )
                            pantryInput = TextFieldValue("")
                            pantryQty = TextFieldValue("")
                            pantryExpiry = TextFieldValue("")
                            if (compactPantryAddFlow) showPantryAddForm = false
                            noteGroceryAction(
                                title = "Pantry saved",
                                detail = "$name was added. The planner can use it first next time."
                            )
                        }
                    }
                    if (compactPantryAddFlow) {
                        InputChip(
                            selected = showPantryAddForm,
                            onClick = { showPantryAddForm = !showPantryAddForm },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics {
                                    stateDescription = if (showPantryAddForm) "Expanded" else "Collapsed"
                                },
                            label = {
                                Text(if (showPantryAddForm) "Hide add form" else "Add pantry item")
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        )
                    }
                    AnimatedVisibility(visible = showPantryAddForm) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pantryInput,
                                onValueChange = { pantryInput = it },
                                label = { Text("Pantry item") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = pantryQty,
                                    onValueChange = { pantryQty = it },
                                    label = { Text("Qty") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = pantryExpiry,
                                    onValueChange = { pantryExpiry = it },
                                    label = { Text("Expiry YYYY-MM-DD") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (compactPantryAddFlow) {
                                OutlinedButton(
                                    onClick = addPantryEntry,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text("Add pantry item")
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    OutlinedButton(
                                        onClick = addPantryEntry,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Save pantry item")
                                    }
                                }
                            }
                        }
                    }
                    if (pantryItems.isEmpty()) {
                        Text(
                            text = "No pantry items yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val visiblePantryEntries = if (showAllPantryItems) pantryEntries else pantryEntries.take(6)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            visiblePantryEntries.forEach { entry ->
                                val expiryText = entry.expiryDate?.trim()
                                val expiryDate = expiryText?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                val isExpired = expiryDate?.isBefore(LocalDate.now()) == true
                                val labelText = buildString {
                                    append(entry.name)
                                    entry.quantity?.let { append(" • $it") }
                                    expiryText?.let {
                                        append(" • exp $it")
                                        if (isExpired) append(" (expired)")
                                    }
                                }
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        val updated = pantryEntries.filterNot {
                                            normalizedPantryEntryKey(it.name) == normalizedPantryEntryKey(entry.name)
                                        }
                                        userViewModel.updatePantryEntries(updated)
                                        mealPlanViewModel.trackMlEvent(
                                            eventName = "pantry_item_removed",
                                            payload = mapOf(
                                                "item_name" to entry.name,
                                                "quantity" to (entry.quantity ?: ""),
                                                "expiry_date" to (entry.expiryDate ?: ""),
                                                "expired" to isExpired
                                            )
                                        )
                                        if (isExpired) {
                                            mealPlanViewModel.trackMlEvent(
                                                eventName = "pantry_item_expired",
                                                payload = mapOf(
                                                    "item_name" to entry.name,
                                                    "expiry_date" to (entry.expiryDate ?: "")
                                                )
                                            )
                                        }
                                        noteGroceryAction(
                                            title = "Pantry updated",
                                            detail = "${entry.name} was removed from your saved pantry items."
                                        )
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = {
                                        Text(
                                            text = labelText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = pantryChipLabelWidth)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove pantry item ${entry.name}",
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                        if (pantryEntries.size > visiblePantryEntries.size) {
                            OutlinedButton(
                                onClick = { showAllPantryItems = true },
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("Show ${pantryEntries.size - visiblePantryEntries.size} more")
                            }
                        } else if (showAllPantryItems && pantryEntries.size > 6) {
                            OutlinedButton(
                                onClick = { showAllPantryItems = false },
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text("Show less")
                            }
                        }
                    }
                }
            }
            }
        }

        if (groupedEntries.isNotEmpty() && groceryFocus == GroceryScreenFocus.List) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search items") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.text.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = TextFieldValue("")
                                            postGroceryFeedback(
                                                tone = FeedbackBannerTone.Success,
                                                message = "Search cleared. Showing all categories."
                                            )
                                        },
                                        modifier = Modifier.testTag("grocery_clear_search_icon")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )
                        Text(
                            text = buildString {
                                append("Showing ${filteredItems.size} of ${groupedEntries.size}")
                                append(" • ${statusFilter.label}")
                                if (selectedCategoryFilter != AllCategoriesFilterKey) {
                                    append(" • $selectedCategoryFilter")
                                }
                                if (sortAlpha) append(" • A-Z")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showListFilters = !showListFilters },
                                modifier = Modifier.heightIn(min = 44.dp)
                            ) {
                                Text(if (showListFilters || hasActiveListControls) "Hide filters" else "Show filters")
                            }
                            TokenizedFilterChip(
                                selected = sortAlpha,
                                onClick = { sortAlpha = !sortAlpha },
                                modifier = Modifier.heightIn(min = 44.dp),
                                text = "A-Z",
                                labelMaxWidth = 44.dp
                            )
                            OutlinedButton(
                                onClick = {
                                    expandedMap = if (anyExpanded) {
                                        displayCategories.associateWith { false }
                                    } else {
                                        displayCategories.associateWith { category ->
                                            category == displayCategories.firstOrNull()
                                        }
                                    }
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Success,
                                        message = if (anyExpanded) {
                                            "Collapsed all categories."
                                        } else {
                                            "Opened the first category."
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("grocery_expand_toggle_all")
                            ) {
                                Text(if (anyExpanded) "Collapse all" else "Open first")
                            }
                        }
                        AnimatedVisibility(visible = showListFilters || hasActiveListControls) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GroceryItemStatusFilter.values().forEach { option ->
                                        TokenizedFilterChip(
                                            selected = statusFilter == option,
                                            onClick = {
                                                if (statusFilter != option) {
                                                    statusFilter = option
                                                    postGroceryFeedback(
                                                        tone = FeedbackBannerTone.Success,
                                                        message = when (option) {
                                                            GroceryItemStatusFilter.All -> "Showing all grocery items."
                                                            GroceryItemStatusFilter.NeedToBuy -> "Showing items you still need to buy."
                                                            GroceryItemStatusFilter.Completed -> "Showing bought and pantry-first items."
                                                        }
                                                    )
                                                }
                                            },
                                            text = option.label,
                                            labelMaxWidth = groceryChipLabelWidth
                                        )
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TokenizedFilterChip(
                                        selected = selectedCategoryFilter == AllCategoriesFilterKey,
                                        onClick = {
                                            selectedCategoryFilter = AllCategoriesFilterKey
                                            postGroceryFeedback(
                                                tone = FeedbackBannerTone.Success,
                                                message = "Showing all categories."
                                            )
                                        },
                                        text = "$AllCategoriesFilterKey (${filteredItems.size})",
                                        labelMaxWidth = groceryChipLabelWidth
                                    )
                                    orderedCategoryEntries.forEach { (category, count) ->
                                        TokenizedFilterChip(
                                            selected = selectedCategoryFilter == category,
                                            onClick = {
                                                selectedCategoryFilter = category
                                                postGroceryFeedback(
                                                    tone = FeedbackBannerTone.Success,
                                                    message = "Filtered to $category ($count items)."
                                                )
                                            },
                                            text = "$category ($count)",
                                            labelMaxWidth = groceryChipLabelWidth
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (false && groceryFocus == GroceryScreenFocus.List) {
            item {
            Text(
                text = "Showing ${filteredItems.size} of ${groupedEntries.size} items • ${statusFilter.label}" +
                    if (selectedCategoryFilter == AllCategoriesFilterKey) "" else " • $selectedCategoryFilter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }

        if (false && groupedEntries.isNotEmpty() && groceryFocus == GroceryScreenFocus.List) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GroceryItemStatusFilter.values().toList()) { option ->
                        TokenizedFilterChip(
                            selected = statusFilter == option,
                            onClick = {
                                if (statusFilter != option) {
                                    statusFilter = option
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Success,
                                        message = when (option) {
                                            GroceryItemStatusFilter.All -> "Showing all grocery items."
                                            GroceryItemStatusFilter.NeedToBuy -> "Showing items you still need to buy."
                                            GroceryItemStatusFilter.Completed -> "Showing bought and pantry-first items."
                                        }
                                    )
                                }
                            },
                            text = option.label,
                            labelMaxWidth = groceryChipLabelWidth
                        )
                    }
                }
            }
        }

        if (groupedEntries.isEmpty() && groceryFocus == GroceryScreenFocus.List) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No grocery items yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasPlan) {
                                "Your list updates from your meal plan automatically. Open your plan if you want to review meals or refresh after a change."
                            } else {
                                "Generate your first weekly plan to unlock your grocery list."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                if (!isOnline) {
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Error,
                                        message = "${ActionFeedbackCopy.InternetRequired} Connect to open plan generation."
                                    )
                                } else {
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Loading,
                                        message = "Opening your meal plan…"
                                    )
                                    onNavigateToRoute(Routes.MealPlan)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(if (hasPlan) "Open Meal Plan" else "Generate Plan First")
                        }
                    }
                }
            }
        }

        if (groceryFocus == GroceryScreenFocus.Budget) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Cost view",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Shopping for $householdLabel. These prices are only a guide and can change week to week.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SummaryTile(
                                title = "Need to buy",
                                value = "$remainingCount items",
                                modifier = Modifier.weight(1f),
                            )
                            SummaryTile(
                                title = "Estimated total",
                                value = "₱$totalCost",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Shopping progress",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    GroceryStatusPill(
                                        text = "$completedCount/${groupedEntries.size} covered",
                                        emphasized = completedCount > 0
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { shoppingProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                                Text(
                                    text = when {
                                        groupedEntries.isEmpty() -> "No shopping items yet."
                                        remainingCount == 0 -> "Everything is either bought or already covered by pantry items."
                                        pantryCoveredCount > 0 -> "$remainingCount item(s) still need buying. $pantryCoveredCount saved pantry match(es) already cover part of the list."
                                        else -> "$remainingCount item(s) still need buying for this week."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (displayBudget == null) {
                            Text(
                                text = "No budget yet",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "You can still use the estimate. Add a weekly budget in Profile if you want faster warnings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TokenizedFilterChip(
                                    selected = budgetMode == "Weekly",
                                    onClick = { budgetMode = "Weekly" },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = "Weekly",
                                    labelMaxWidth = groceryChipLabelWidth
                                )
                                TokenizedFilterChip(
                                    selected = budgetMode == "Monthly",
                                    onClick = { budgetMode = "Monthly" },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = "Monthly (≈ weekly × 4.33)",
                                    labelMaxWidth = groceryChipLabelWidth
                                )
                            }
                            Text(
                                text = if (totalCost <= displayBudget) "Within budget" else "Over budget",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (budgetMode == "Weekly") {
                                    "This week's shopping is about ₱$totalCost for $householdLabel against a ₱${displayBudget.toInt()} weekly budget. ${if (savings >= 0) "About ₱$savings left." else "About ₱${-savings} over."}"
                                } else {
                                    "This month's budget is ₱${displayBudget.toInt()}. Your current week guide is about ₱${derivedWeekly?.toInt() ?: 0}, and this list is about ₱$totalCost."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "This is still only an estimate. Save your real spending in Progress.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { costProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (totalCost <= displayBudget) PcosinaSuccess else MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val listText = groupedEntries.joinToString("\n") {
                                    val pantryTag = if (it.name in pantryMatches) " [PANTRY]" else ""
                                    "- ${it.name} (${it.quantityDisplay})" + pantryTag + (if (it.name in effectiveCheckedNames) " [CHECKED]" else "")
                                }
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Loading,
                                    message = "Preparing grocery list to share…"
                                )
                                runCatching {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "My PCOSINA Grocery List for $householdLabel:\n\n$listText")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Grocery List"))
                                }.onSuccess {
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Success,
                                        message = "Share options opened for your grocery list."
                                    )
                                }.onFailure {
                                    postGroceryFeedback(
                                        tone = FeedbackBannerTone.Error,
                                        message = "Couldn’t open share options on this device."
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            Text("Share grocery list")
                        }
                    }
                }
            }
        }

        if (emptySearchResults && groceryFocus == GroceryScreenFocus.List) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No results for \"${searchQuery.text.trim()}\"",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Try a broader keyword or clear search to browse all categories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                searchQuery = TextFieldValue("")
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = "Search cleared. Showing all categories."
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text("Clear search and show all")
                        }
                    }
                }
            }
        }
        if (emptyFilterResults && groceryFocus == GroceryScreenFocus.List) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No items match this filter yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Try All items or clear category to continue shopping.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                statusFilter = GroceryItemStatusFilter.All
                                selectedCategoryFilter = AllCategoriesFilterKey
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = "Filters reset. Showing all items."
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text("Reset filters and show all items")
                        }
                    }
                }
            }
        }

        if (filteredItems.isNotEmpty() && groceryFocus == GroceryScreenFocus.List) {
            if (false) item {
                Text(
                    text = "Quick categories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (false) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        TokenizedFilterChip(
                            selected = selectedCategoryFilter == AllCategoriesFilterKey,
                            onClick = {
                                selectedCategoryFilter = AllCategoriesFilterKey
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = "Showing all categories."
                                )
                            },
                            text = "$AllCategoriesFilterKey (${filteredItems.size})",
                            labelMaxWidth = groceryChipLabelWidth
                        )
                    }
                    items(orderedCategoryEntries) { (category, count) ->
                        TokenizedFilterChip(
                            selected = selectedCategoryFilter == category,
                            onClick = {
                                selectedCategoryFilter = category
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = "Filtered to $category ($count items)."
                                )
                            },
                            text = "$category ($count)",
                            labelMaxWidth = groceryChipLabelWidth
                        )
                    }
                }
            }
            if (false) item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Categories", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TokenizedFilterChip(
                            selected = sortAlpha,
                            onClick = { sortAlpha = !sortAlpha },
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = "A–Z",
                            labelMaxWidth = 44.dp
                        )
                        OutlinedButton(
                            onClick = {
                                expandedMap = displayCategories.associateWith { !allExpanded }
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = if (allExpanded) {
                                        "Collapsed all categories."
                                    } else {
                                        "Expanded all categories."
                                    }
                                )
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("grocery_expand_toggle_all")
                        ) {
                            Text(if (allExpanded) "Collapse all" else "Expand all")
                        }
                    }
                }
            }
            if (false && displayCategories.size > 4) {
                item {
                    Text(
                        text = if (selectedCategoryFilter == AllCategoriesFilterKey) {
                            "Swipe vertically to browse more categories."
                        } else {
                            "Showing one category. Select All categories to browse everything."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            displayCategories.forEach { category ->
                val items = groups[category].orEmpty().let { list ->
                    if (sortAlpha) list.sortedBy { it.name.lowercase(Locale.getDefault()) } else list
                }
                if (items.isEmpty()) return@forEach
                item {
                    CategoryCard(
                        icon = when (category) {
                            "Produce" -> "🥬"
                            "Meat/Seafood" -> "🐟"
                            "Eggs & Dairy" -> "🥚"
                            "Dry Goods" -> "🌾"
                            "Spices & Condiments" -> "🧂"
                            "Canned/Packaged" -> "🥫"
                            "Beverages" -> "🥤"
                            else -> "📦"
                        },
                        title = category,
                        items = items,
                        expanded = expandedMap[category] ?: true,
                        onToggle = {
                            val willExpand = !(expandedMap[category] ?: false)
                            expandedMap = displayCategories.associateWith { current ->
                                willExpand && current == category
                            }
                        },
                        checkedNames = checkedNames,
                        onCheckedChange = { name, checked ->
                            var nextPantryOptOut = pantryOptOut
                            var nextCheckedNames = checkedNames
                            if (name in pantryMatches) {
                                nextPantryOptOut = if (checked) pantryOptOut - name else pantryOptOut + name
                                pantryOptOut = nextPantryOptOut
                                noteGroceryAction(
                                    title = if (checked) "Pantry-first enabled" else "Back on the buy list",
                                    detail = if (checked) {
                                        "$name is now treated as already at home."
                                    } else {
                                        "$name will show up as something to buy again."
                                    }
                                )
                            } else {
                                nextCheckedNames = if (checked) checkedNames + name else checkedNames - name
                                checkedNames = nextCheckedNames
                                noteGroceryAction(
                                    title = if (checked) "Marked bought" else "Back on the buy list",
                                    detail = if (checked) {
                                        "$name was moved out of your shopping list."
                                    } else {
                                        "$name still needs to be bought."
                                    }
                                )
                            }
                            val nextEffectiveChecked = nextCheckedNames + pantryMatches.filter { it !in nextPantryOptOut }
                            if (checked && allItemNames.isNotEmpty() && nextEffectiveChecked.containsAll(allItemNames)) {
                                mealPlanViewModel.trackMlEvent(
                                    eventName = "grocery_completed",
                                    payload = mapOf(
                                        "completed_items" to nextEffectiveChecked.size,
                                        "total_items" to allItemNames.size,
                                        "completion_ratio" to 1.0
                                    )
                                )
                            }
                        },
                        pantryMatches = pantryMatches,
                        pantryOptOut = pantryOptOut,
                        priceResolver = { effectivePrice(it) }
                    )
                }
            }
        }

        if (groceryFocus == GroceryScreenFocus.Tips) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Shopping tips",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        goalShoppingTips(userProfile.goal, householdSize).forEach { tip ->
                            Text(
                                text = "• $tip",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    if (showLockedInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLockedInfo = false },
            confirmButton = {
                GroceryDialogGotItButton(onClick = { showLockedInfo = false })
            },
            title = { Text(groceryLockedCopy.dialogTitle) },
            text = {
                Text(groceryLockedCopy.dialogBody)
            }
        )
    }
}

@Composable
private fun GroceryListHero(
    hasPlan: Boolean,
    weekLabel: String,
    totalItems: Int,
    remainingCount: Int,
    completedCount: Int,
    totalCost: Int,
    selectedCategoryLabel: String,
    statusFilterLabel: String,
    onOpenPlan: () -> Unit,
    lockedMessage: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    if (!hasPlan) {
        FriendlyEmptyStateCard(
            title = "Shopping starts after your first week",
            message = lockedMessage,
            actionLabel = "Open meal plan",
            onAction = onOpenPlan,
            accentColor = colorScheme.primary,
            modifier = modifier
        )
        return
    }

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
                text = "Shopping for $weekLabel",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.primary
            )
            Text(
                text = "See what still needs buying, what is already covered, and which view you are using before you scroll.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Need to buy",
                        value = "$remainingCount",
                        hint = "Items still missing this week.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Covered",
                        value = "$completedCount/$totalItems",
                        hint = "Already bought or already covered at home.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Showing",
                        value = statusFilterLabel,
                        hint = selectedCategoryLabel,
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Estimate",
                        value = "₱$totalCost",
                        hint = "Guide total for what is still missing.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
        }
    }
}

@Composable
private fun GroceryPantryHero(
    pantryCount: Int,
    pantryCoveredCount: Int,
    remainingCount: Int,
    householdLabel: String,
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
                text = "Pantry first",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.secondary
            )
            Text(
                text = "These items help the plan use what you already have at home for $householdLabel.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Pantry items",
                        value = "$pantryCount",
                        hint = "Saved ingredients in your pantry list.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Auto-covered",
                        value = "$pantryCoveredCount",
                        hint = "Shopping items already covered at home.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Still to buy",
                        value = "$remainingCount",
                        hint = "Items not covered yet.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Planner use",
                        value = if (pantryCount > 0) "Active" else "Add items",
                        hint = "Saved pantry items get used first when possible.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
        }
    }
}

@Composable
private fun GroceryBudgetHero(
    totalCost: Int,
    displayBudget: Int?,
    savings: Int,
    remainingCount: Int,
    completedCount: Int,
    totalItems: Int,
    actionState: FeedbackActionState,
    onOpenProgress: () -> Unit,
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
                text = "Cost view",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.tertiary
            )
            Text(
                text = if (displayBudget == null) {
                    "You can still see a price guide even without a budget."
                } else if (savings >= 0) {
                    "Your current shopping guide is still within the budget you set."
                } else {
                    "Your current shopping guide is above the budget you set."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Need to buy",
                        value = "$remainingCount",
                        hint = "Items still on your shopping run.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Covered",
                        value = "$completedCount/$totalItems",
                        hint = "Bought or pantry-covered items.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Estimate",
                        value = "₱$totalCost",
                        hint = "Guide total for this list.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Budget",
                        value = displayBudget?.let { "₱$it" } ?: "Not set",
                        hint = displayBudget?.let {
                            if (savings >= 0) "About ₱$savings left." else "About ₱${-savings} over."
                        } ?: "Set one in Profile for quicker warnings.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
            LoadingActionButton(
                state = actionState,
                idleLabel = "Open progress to save real spending",
                loadingLabel = "Opening progress…",
                successLabel = "Progress opened",
                errorLabel = "Try again",
                onClick = onOpenProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
private fun GroceryTipsHero(
    tipCount: Int,
    hasItems: Boolean,
    planActionState: FeedbackActionState,
    progressActionState: FeedbackActionState,
    onOpenPlan: () -> Unit,
    onOpenProgress: () -> Unit,
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
                text = "Shopping help",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.primary
            )
            Text(
                text = if (hasItems) {
                    "Use the checklist first, then open Progress after meals and spending."
                } else {
                    "Create a week first so your list and tips feel specific instead of empty."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            CompactWidgetGrid(
                widgets = listOf(
                    CompactWidgetSpec(
                        title = "Tips ready",
                        value = "$tipCount",
                        hint = "Quick reminders for your goal.",
                        accentColor = colorScheme.primary
                    ),
                    CompactWidgetSpec(
                        title = "Plan link",
                        value = "Meal plan",
                        hint = "Open the current week anytime.",
                        accentColor = colorScheme.secondary
                    ),
                    CompactWidgetSpec(
                        title = "Progress link",
                        value = "Tracking",
                        hint = "Save meals and spending there.",
                        accentColor = colorScheme.tertiary
                    ),
                    CompactWidgetSpec(
                        title = "Mode",
                        value = if (hasItems) "Active week" else "Needs plan",
                        hint = "Shopping works best with a current week.",
                        accentColor = colorScheme.primary.copy(alpha = 0.9f)
                    )
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LoadingActionButton(
                    state = planActionState,
                    idleLabel = "Open plan",
                    loadingLabel = "Opening meal plan…",
                    successLabel = "Plan opened",
                    errorLabel = "Try again",
                    onClick = onOpenPlan,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurface
                    )
                )
                LoadingActionButton(
                    state = progressActionState,
                    idleLabel = "Open progress",
                    loadingLabel = "Opening progress…",
                    successLabel = "Progress opened",
                    errorLabel = "Try again",
                    onClick = onOpenProgress,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.secondaryContainer,
                        contentColor = colorScheme.onSecondaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun GroceryDialogGotItButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
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
private fun GroceryActionNoteCard(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.primaryContainer.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colorScheme.primary.copy(alpha = 0.12f),
                contentColor = colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.primary
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroceryStatusPill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
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
private fun CategoryCard(
    icon: String,
    title: String,
    items: List<GroceryListEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    checkedNames: Set<String>,
    onCheckedChange: (String, Boolean) -> Unit,
    pantryMatches: Set<String>,
    pantryOptOut: Set<String>,
    priceResolver: (GroceryListEntry) -> Int,
) {
    val coveredCount = items.count { item ->
        if (item.name in pantryMatches) item.name !in pantryOptOut else item.name in checkedNames
    }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleLarge)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = "${items.size} items • $coveredCount covered",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .semantics {
                            contentDescription = if (expanded) {
                                "Collapse $title category"
                            } else {
                                "Expand $title category"
                            }
                        }
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { item ->
                        val pantryMatch = item.name in pantryMatches
                        val checked = if (pantryMatch) item.name !in pantryOptOut else item.name in checkedNames
                        val statusText = if (pantryMatch) {
                            if (checked) "Use what you have" else "Buy instead"
                        } else if (checked) {
                            "Bought"
                        } else {
                            "Still need"
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = if (checked) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (checked) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onCheckedChange(item.name, it) },
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(item.quantityDisplay)
                                            if (pantryMatch) {
                                                append(" • ")
                                                append(if (checked) "Use what you have" else "Buy instead")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pantryMatch) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "₱${if (checked) 0 else priceResolver(item)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (checked || pantryMatch) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun defaultCategoryExpanded(category: String): Boolean {
    return false
}

private fun normalizedPantryEntryKey(raw: String): String =
    raw.trim()
        .lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun normalizedPantryEntryTokens(raw: String): Set<String> =
    normalizedPantryEntryKey(raw)
        .split(" ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun pantryEntryMatchesGroceryItem(pantryName: String, groceryName: String): Boolean {
    val pantryKey = normalizedPantryEntryKey(pantryName)
    val groceryKey = normalizedPantryEntryKey(groceryName)
    if (pantryKey.isBlank() || groceryKey.isBlank()) return false
    if (pantryKey == groceryKey) return true

    val pantryTokens = normalizedPantryEntryTokens(pantryName)
    val groceryTokens = normalizedPantryEntryTokens(groceryName)
    if (pantryTokens.size <= 1 || groceryTokens.size <= 1) return false

    return pantryTokens.containsAll(groceryTokens) || groceryTokens.containsAll(pantryTokens)
}

private fun formatWeekRange(weekStartId: String?, timestamp: Long?): String {
    val zone = ZoneId.systemDefault()
    val baseDate = if (!weekStartId.isNullOrBlank()) {
        runCatching { LocalDate.parse(weekStartId, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    } else null
    val resolved = baseDate ?: if (timestamp != null && timestamp > 0) {
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    } else {
        LocalDate.now(zone)
    }
    val weekFields = WeekFields.of(Locale.getDefault())
    val start = resolved.with(TemporalAdjusters.previousOrSame(weekFields.firstDayOfWeek))
    val end = start.plusDays(6)
    val sameYear = start.year == end.year
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val fmtYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    return if (sameYear) {
        "${start.format(fmt)} – ${end.format(fmtYear)}"
    } else {
        "${start.format(fmtYear)} – ${end.format(fmtYear)}"
    }
}

