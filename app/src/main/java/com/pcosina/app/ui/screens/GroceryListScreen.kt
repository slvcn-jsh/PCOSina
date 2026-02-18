package com.pcosina.app.ui.screens

import android.content.Intent
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
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
import com.pcosina.app.data.model.DummyData.GroceryItem
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
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.TokenizedFilterChip
import com.pcosina.app.ui.theme.PcosinaSuccess
import com.pcosina.app.ui.theme.UiChipTokens
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.domain.PriceCatalog
import com.pcosina.app.ui.util.GuidedJourneyInput
import com.pcosina.app.ui.util.ActionFeedbackCopy
import com.pcosina.app.ui.util.LockedFlowCopy
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val groceryChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 108.dp, medium = 168.dp)
    val pantryChipLabelWidth = UiChipTokens.widthByClass(screenWidthDp, compact = 132.dp, medium = 196.dp)
    val totalItems = allItems.size
    val weeklyBudget = userProfile.weeklyBudgetPhp.takeIf { it > 0 }
    var checkedNames by remember { mutableStateOf(setOf<String>()) }
    var pantryOptOut by remember { mutableStateOf(setOf<String>()) }
    val hasPlan = planHistory.isNotEmpty() || planState is MealPlanUiState.Success
    val hasReviewedWeek = activePlanId != null && activePlanId == lastReviewedWeek
    val hasGrocery = addedItems.isNotEmpty()
    val hasTracked = logs.isNotEmpty()
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
        pantryItems.map { it.lowercase(Locale.getDefault()) }.toSet()
    }
    val pantryMatches = remember(allItems, pantryTokens) {
        allItems.filter { item ->
            val name = item.name.lowercase(Locale.getDefault())
            pantryTokens.any { token ->
                name.contains(token) || token.contains(name)
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
    var showLockedInfo by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var groceryFeedbackBanner by remember { mutableStateOf<FeedbackBannerData?>(null) }
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val searchFilteredItems = if (searchQuery.text.isBlank()) allItems else allItems.filter {
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
    val emptyFilterResults = allItems.isNotEmpty() && filteredItems.isEmpty() && !emptySearchResults

    val groups: Map<String, List<GroceryItem>> = filteredItems.groupBy { inferCategory(it) }
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
            "Reset checklist/search for user=$activeUserId plan=${activePlanId ?: "none"}"
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

    LaunchedEffect(statusFilter, selectedCategoryFilter, searchQuery.text, activeUserId) {
        Log.i(
            "GroceryUX",
            "Filters user=$activeUserId status=${statusFilter.label} category=$selectedCategoryFilter query='${searchQuery.text.trim()}' results=${filteredItems.size}"
        )
    }

    LaunchedEffect(emptySearchResults, searchQuery.text, activeUserId) {
        if (emptySearchResults) {
            Log.i(
                "GroceryUX",
                "No grocery search matches for user=$activeUserId query='${searchQuery.text.trim()}'"
            )
        }
    }

    LaunchedEffect(allItems.size, hasPlan, activeUserId) {
        if (allItems.isEmpty()) {
            Log.i(
                "GroceryUX",
                "Grocery list empty for user=$activeUserId hasPlan=$hasPlan"
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

    fun effectivePrice(item: GroceryItem): Int {
        return if (item.price > 0) item.price else PriceCatalog.estimatePrice(item.name)
    }
    val totalCost = allItems
        .filter { it.name !in effectiveCheckedNames }
        .sumOf { effectivePrice(it) }
    val savings = displayBudget?.toInt()?.minus(totalCost) ?: 0
    val costProgress = if (displayBudget != null && displayBudget > 0) {
        (totalCost.toFloat() / displayBudget.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = UiSpacingTokens.SectionGap),
        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
    ) {
        item {
            GradientHeader(
                title = "Grocery List",
                subtitle = weekLabel,
            )
        }

        item {
            GuidedJourneyCard(
                step = guidedStep,
                onContinue = { step -> onNavigateToRoute(step.route) }
            )
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

        if (!hasPlan) {
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
                                        message = "Opening plan generator…"
                                    )
                                    onNavigateToRoute(Routes.MealPlan)
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Go to Plan")
                        }
                    }
                }
            }
        }
        if (hasPlan) {
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
                            text = if (allItems.isEmpty()) {
                                "Open Meal Plan and tap Sync to pull this week's ingredients."
                            } else {
                                "Filter to Need to buy, check items as you shop, then open Progress to log meals."
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
                                        message = "Opening meal plan for grocery sync…"
                                    )
                                    onNavigateToRoute(Routes.MealPlan)
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("grocery_open_mealplan_cta")
                            ) {
                                Text(if (allItems.isEmpty()) "Open Meal Plan to Sync" else "Open Meal Plan")
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

        item {
            ExpandableSection(
                title = "Pantry Inventory",
                subtitle = "Optional. Use-first items for planning",
                defaultExpanded = false
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Items here are treated as “use-first” during planning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val compactPantryAddFlow = screenWidthDp <= 360
                    var pantryInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
                    var pantryQty by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
                    var pantryExpiry by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
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
                            postGroceryFeedback(
                                tone = FeedbackBannerTone.Loading,
                                message = "Saving pantry item…"
                            )
                            val newEntry = PantryEntry(
                                name = name,
                                quantity = pantryQty.text.trim().takeIf { it.isNotBlank() },
                                expiryDate = pantryExpiry.text.trim().takeIf { it.isNotBlank() }
                            )
                            val updated = (pantryEntries + newEntry)
                                .distinctBy { it.name.lowercase(Locale.getDefault()) }
                            userViewModel.updatePantryEntries(updated)
                            pantryInput = TextFieldValue("")
                            pantryQty = TextFieldValue("")
                            pantryExpiry = TextFieldValue("")
                            if (compactPantryAddFlow) showPantryAddForm = false
                            postGroceryFeedback(
                                tone = FeedbackBannerTone.Success,
                                message = "Pantry updated: added $name."
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
                                    Text("Add to Pantry")
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
                                        Text("Add")
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
                        if (pantryEntries.size > 3) {
                            Text(
                                text = "Swipe left or right to review pantry chips.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(pantryEntries.size) { idx ->
                                val entry = pantryEntries[idx]
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
                                        val updated = pantryEntries.filterNot { it.name.equals(entry.name, true) }
                                        userViewModel.updatePantryEntries(updated)
                                        postGroceryFeedback(
                                            tone = FeedbackBannerTone.Success,
                                            message = "Pantry updated: removed ${entry.name}."
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
                        Text(
                            text = "Tap the X to remove an item.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search ingredients") },
                placeholder = { Text("Try: egg, spinach, fish") },
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )
        }

        item {
            Text(
                text = "Showing ${filteredItems.size} of ${allItems.size} items • ${statusFilter.label}" +
                    if (selectedCategoryFilter == AllCategoriesFilterKey) "" else " • $selectedCategoryFilter",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (allItems.isNotEmpty()) {
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

        if (allItems.isEmpty()) {
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
                                "Sync from Meal Plan to build your list for this week."
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
                                        message = "Opening meal plan for grocery sync…"
                                    )
                                    onNavigateToRoute(Routes.MealPlan)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(if (hasPlan) "Open Meal Plan to Sync" else "Generate Plan First")
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SummaryTile(
                        title = "Total ingredients",
                        value = "${effectiveCheckedNames.size}/$totalItems",
                        modifier = Modifier.weight(1f),
                    )
                    SummaryTile(
                        title = "Estimated Cost",
                        value = "₱$totalCost",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

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
                        if (displayBudget == null) {
                            Text(
                                text = "Budget not set",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Estimates are shown without a budget limit. Set a weekly budget in Profile if you want alerts.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = budgetMode == "Weekly",
                                    onClick = { budgetMode = "Weekly" },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = {
                                        Text(
                                            text = "Weekly",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = groceryChipLabelWidth)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                                FilterChip(
                                    selected = budgetMode == "Monthly",
                                    onClick = { budgetMode = "Monthly" },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = {
                                        Text(
                                            text = "Monthly (≈ weekly × 4.33)",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = groceryChipLabelWidth)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                            Text(
                                text = if (totalCost <= displayBudget) "Within Budget! 🎉" else "Over Budget! ⚠️",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (budgetMode == "Weekly") {
                                    "Estimated ₱$totalCost of ₱${displayBudget.toInt()} weekly budget. ${if (savings >= 0) "Saving ₱$savings!" else "₱${-savings} over budget!"}"
                                } else {
                                    "Estimated ₱$totalCost of ₱${displayBudget.toInt()} monthly budget. Weekly equivalent ₱${derivedWeekly?.toInt() ?: 0}."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Budget is projected, not actual. Log actual spending in Progress.",
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
                                val listText = allItems.joinToString("\n") {
                                    val pantryTag = if (it.name in pantryMatches) " [PANTRY]" else ""
                                    "- ${it.name} (${it.quantity})" + pantryTag + (if (it.name in effectiveCheckedNames) " [CHECKED]" else "")
                                }
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Loading,
                                    message = "Preparing grocery list to share…"
                                )
                                runCatching {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "My PCOSINA Grocery List:\n\n$listText")
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
                            Text("Share/Copy List")
                        }
                    }
                }
            }
        }

        if (emptySearchResults) {
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
                            Text("Clear Search")
                        }
                    }
                }
            }
        }
        if (emptyFilterResults) {
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
                            Text("Reset Filters")
                        }
                    }
                }
            }
        }

        if (filteredItems.isNotEmpty()) {
            item {
                Text(
                    text = "Quick categories",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Categories", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sortAlpha,
                            onClick = { sortAlpha = !sortAlpha },
                            modifier = Modifier.heightIn(min = 48.dp),
                            label = {
                                Text(
                                    text = "A–Z",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
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
            if (displayCategories.size > 4) {
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
                            expandedMap = expandedMap.toMutableMap().apply {
                                this[category] = !(this[category] ?: true)
                            }
                        },
                        checkedNames = checkedNames,
                        onCheckedChange = { name, checked ->
                            if (name in pantryMatches) {
                                pantryOptOut = if (checked) pantryOptOut - name else pantryOptOut + name
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = if (checked) {
                                        "Using pantry-first for $name."
                                    } else {
                                        "Marked $name as needed from store."
                                    }
                                )
                            } else {
                                checkedNames = if (checked) checkedNames + name else checkedNames - name
                                postGroceryFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = if (checked) {
                                        "Marked $name as bought."
                                    } else {
                                        "Moved $name back to buy list."
                                    }
                                )
                            }
                        },
                        pantryMatches = pantryMatches,
                        pantryOptOut = pantryOptOut,
                        priceResolver = { effectivePrice(it) }
                    )
                }
            }

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
                            text = "Shopping Tips",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "• Buy fresh produce at local palengke for better prices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "• Check pantry items before shopping",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "• Buy proteins in bulk and freeze portions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                androidx.compose.material3.TextButton(onClick = { showLockedInfo = false }) {
                    Text("Got it")
                }
            },
            title = { Text(groceryLockedCopy.dialogTitle) },
            text = {
                Text(groceryLockedCopy.dialogBody)
            }
        )
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
private fun CategoryCard(
    icon: String,
    title: String,
    items: List<GroceryItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    checkedNames: Set<String>,
    onCheckedChange: (String, Boolean) -> Unit,
    pantryMatches: Set<String>,
    pantryOptOut: Set<String>,
    priceResolver: (GroceryItem) -> Int,
) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = icon, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        val pantryMatch = item.name in pantryMatches
                        val checked = if (pantryMatch) item.name !in pantryOptOut else item.name in checkedNames
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
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
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    if (pantryMatch) {
                                        Text(
                                            text = "Use first • Pantry",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = item.quantity,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = "₱${if (checked) 0 else priceResolver(item)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun defaultCategoryExpanded(category: String): Boolean {
    return !category.equals("Others", ignoreCase = true)
}

private fun inferCategory(item: GroceryItem): String {
    val raw = item.category.trim()
    if (raw.isNotBlank()) {
        // "Needed" is a placeholder for uncategorized items; infer from name instead.
        if (!raw.equals("Needed", ignoreCase = true)) {
            val mapped = when (raw) {
                "Produce / Vegetables",
                "Fruits" -> "Produce"
                "Proteins (Meat/Seafood)" -> "Meat/Seafood"
                "Eggs & Dairy" -> "Eggs & Dairy"
                "Dry Goods / Grains" -> "Dry Goods"
                "Spices & Condiments" -> "Spices & Condiments"
                "Canned/Packaged",
                "Beverages",
                "Produce",
                "Meat/Seafood",
                "Dry Goods",
                "Others" -> raw
                else -> raw // preserve custom categories
            }
            return mapped
        }
    }
    return PriceCatalog.inferCategory(item.name)
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

