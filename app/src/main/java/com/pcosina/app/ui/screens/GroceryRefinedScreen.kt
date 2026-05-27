package com.pcosina.app.ui.screens

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pcosina.app.R
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.domain.GroceryListEntry
import com.pcosina.app.domain.PantryCoverage
import com.pcosina.app.domain.PantryCoverageStatus
import com.pcosina.app.domain.buildGroceryListEntries
import com.pcosina.app.domain.buildPantryCoverage
import com.pcosina.app.domain.canonicalGroceryKey
import com.pcosina.app.domain.estimateGroceryCostAfterPantry
import com.pcosina.app.domain.groceryNamesMatch
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.PcosinaDesignIcon
import com.pcosina.app.ui.components.RefinedOverviewCard
import com.pcosina.app.ui.components.RefinedPrimaryButton
import com.pcosina.app.ui.components.RefinedRingMeter
import com.pcosina.app.ui.components.RefinedStatusPill
import com.pcosina.app.ui.components.RefinedTabBrandHeader
import com.pcosina.app.ui.components.ScreenArtworkAlignment
import com.pcosina.app.ui.components.SharedAvatarHeader
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class GroceryFilterScope {
    AllItems,
    NeedToBuy,
    BoughtOrPantry,
}

@Composable
fun GroceryRefinedScreen(
    groceryViewModel: GroceryViewModel,
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    progressViewModel: ProgressViewModel,
    onNavigateToRoute: (String) -> Unit = {},
    onlineStateOverride: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val isOnline = onlineStateOverride ?: observedOnline
    val groceryItems by groceryViewModel.groceryItems.collectAsState()
    val mealSources by groceryViewModel.mealSources.collectAsState()
    val activePlanId by groceryViewModel.activePlanId.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    val pantryEntries by userViewModel.pantryEntries.collectAsState()
    val effectivePantryEntries = remember(pantryEntries, userProfile.pantryItems) {
        if (pantryEntries.isNotEmpty()) {
            pantryEntries
        } else {
            userProfile.pantryItems
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .map { PantryEntry(name = it) }
        }
    }
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val planState by mealPlanViewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    val groupedEntries = remember(groceryItems) {
        buildGroceryListEntries(groceryItems)
    }
    val pantryCoverageByName = remember(groupedEntries, effectivePantryEntries, today) {
        buildPantryCoverage(groupedEntries, effectivePantryEntries, today)
    }
    val pantryMatches = remember(pantryCoverageByName) {
        pantryCoverageByName
            .filterValues { it.autoCovered }
            .keys
            .toSet()
    }
    var checkedNames by rememberSaveable(activePlanId) { mutableStateOf(setOf<String>()) }
    var pantryOptOut by rememberSaveable(activePlanId) { mutableStateOf(setOf<String>()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedCategories by rememberSaveable(activePlanId) { mutableStateOf(setOf<String>()) }
    var initializedCategoryExpansion by rememberSaveable(activePlanId) { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var showPantryDialog by remember { mutableStateOf(false) }
    var showAddPantryDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var pantryName by rememberSaveable { mutableStateOf("") }
    var pantryAmount by rememberSaveable { mutableStateOf("") }
    var pantryUnit by rememberSaveable { mutableStateOf("g") }
    var pantryExpiry by rememberSaveable { mutableStateOf("") }
    var pantryValidationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var groceryFilterScope by rememberSaveable { mutableStateOf(GroceryFilterScope.AllItems.name) }
    var selectedFilterCategories by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedCategoryKey by rememberSaveable(activePlanId) { mutableStateOf("") }
    var expiredPantryEventKeys by rememberSaveable { mutableStateOf(setOf<String>()) }

    val effectiveChecked = remember(checkedNames, pantryMatches, pantryOptOut) {
        checkedNames + pantryMatches.filter { it !in pantryOptOut }
    }
    val hasPlan = planHistory.isNotEmpty() || planState is MealPlanUiState.Success
    val expiredPantryEntries = remember(effectivePantryEntries, today) {
        effectivePantryEntries.filter { entry ->
            val expiry = parsePantryExpiryDate(entry.expiryDate)
            expiry != null && expiry.isBefore(today)
        }
    }

    LaunchedEffect(hasPlan, groupedEntries.isEmpty(), mealSources.isEmpty()) {
        if (hasPlan && groupedEntries.isEmpty() && mealSources.isEmpty()) {
            mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
                groceryViewModel.setPlanSources(sources)
            }
        }
    }

    LaunchedEffect(expiredPantryEntries) {
        val newExpiredKeys = expiredPantryEntries
            .map { refinedPantryKey(it.name) }
            .filter { it.isNotBlank() && it !in expiredPantryEventKeys }
        newExpiredKeys.forEach { token ->
            mealPlanViewModel.trackMlEvent(
                eventName = "pantry_item_expired",
                payload = mapOf("item_token" to token, "source" to "grocery_pantry_review")
            )
        }
        if (newExpiredKeys.isNotEmpty()) {
            expiredPantryEventKeys = expiredPantryEventKeys + newExpiredKeys
        }
    }

    val categoryOrder = listOf(
        "Produce",
        "Meat/Seafood",
        "Eggs & Dairy",
        "Dry Goods",
        "Spices & Condiments",
        "Others"
    )
    val activeFilterScope = remember(groceryFilterScope) {
        runCatching { GroceryFilterScope.valueOf(groceryFilterScope) }.getOrDefault(GroceryFilterScope.AllItems)
    }
    val filteredEntries = remember(
        groupedEntries,
        searchQuery,
        activeFilterScope,
        selectedFilterCategories,
        pantryMatches,
        pantryCoverageByName,
        pantryOptOut,
        checkedNames
    ) {
        groupedEntries.filter { item ->
            val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedFilterCategories.isEmpty() || item.category in selectedFilterCategories
            val isCovered = item.name in checkedNames || (item.name in pantryMatches && item.name !in pantryOptOut)
            val matchesScope = when (activeFilterScope) {
                GroceryFilterScope.AllItems -> true
                GroceryFilterScope.NeedToBuy -> !isCovered
                GroceryFilterScope.BoughtOrPantry -> isCovered
            }
            matchesSearch && matchesCategory && matchesScope
        }
    }
    val categoryEntries = remember(filteredEntries) {
        val grouped = filteredEntries.groupBy { it.category }
        categoryOrder.mapNotNull { category ->
            grouped[category]?.takeIf { it.isNotEmpty() }?.let { category to it }
        } + grouped.filterKeys { key -> key !in categoryOrder }.toList().sortedBy { it.first }
    }
    LaunchedEffect(categoryEntries) {
        if (categoryEntries.isNotEmpty() && categoryEntries.none { it.first == selectedCategoryKey }) {
            selectedCategoryKey = categoryEntries.first().first
        }
        if (categoryEntries.isNotEmpty() && !initializedCategoryExpansion) {
            expandedCategories = categoryEntries.map { it.first }.toSet()
            initializedCategoryExpansion = true
        }
    }
    val selectedCategoryIndex = categoryEntries.indexOfFirst { it.first == selectedCategoryKey }.let { index ->
        if (index >= 0) index else 0
    }
    val todayLabel = remember(today) { today.format(DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH)) }
    val totalCount = groupedEntries.size
    val coveredCount = groupedEntries.count { it.name in effectiveChecked }
    val remainingCount = (totalCount - coveredCount).coerceAtLeast(0)
    val totalEstimated = groupedEntries.sumOf { item ->
        estimateGroceryCostAfterPantry(
            entry = item,
            pantryCoverage = pantryCoverageByName[item.name],
            coveredOrBought = item.name in effectiveChecked
        )
    }
    val weeklyBudget = userProfile.weeklyBudgetPhp.takeIf { it > 0 }
    val remainingBudget = weeklyBudget?.minus(totalEstimated)
    val budgetProgress = weeklyBudget?.let { budget ->
        totalEstimated.toFloat() / budget.toFloat()
    } ?: 0f
    val listProgress = if (totalCount > 0) coveredCount.toFloat() / totalCount.toFloat() else 0f
    val tipLine = remember(userProfile.goal) {
        when {
            userProfile.goal.contains("Symptom", ignoreCase = true) ->
                "Keep pantry staples simple so symptom-friendly meals stay easy to repeat."
            userProfile.goal.contains("Weight", ignoreCase = true) ->
                "Prioritize high-fiber staples and steady-carb swaps before extras."
            else -> "Use pantry matches first so your grocery list stays practical and budget-aware."
        }
    }

    if (showPantryDialog) {
        PantryListDialog(
            entries = effectivePantryEntries,
            onDismiss = { showPantryDialog = false },
            onAdd = {
                showPantryDialog = false
                showAddPantryDialog = true
            },
            onRemove = { entry ->
                userViewModel.updatePantryEntries(
                    effectivePantryEntries.filterNot {
                        refinedPantryKey(it.name) == refinedPantryKey(entry.name)
                    }
                )
                mealPlanViewModel.trackMlEvent(
                    eventName = "pantry_item_removed",
                    payload = mapOf(
                        "item_name" to entry.name,
                        "source" to "grocery_pantry_dialog"
                    )
                )
                feedbackMessage = "${entry.name} removed from pantry."
            }
        )
    }

    if (showAddPantryDialog) {
        AddPantryItemDialog(
            name = pantryName,
            amount = pantryAmount,
            unit = pantryUnit,
            expiry = pantryExpiry,
            validationMessage = pantryValidationMessage,
            onNameChange = {
                pantryName = it
                pantryValidationMessage = null
            },
            onAmountChange = {
                pantryAmount = it
                pantryValidationMessage = null
            },
            onUnitChange = {
                pantryUnit = it
                pantryValidationMessage = null
            },
            onExpiryChange = {
                pantryExpiry = it
                pantryValidationMessage = null
            },
            onDismiss = {
                pantryValidationMessage = null
                showAddPantryDialog = false
            },
            onSave = {
                val trimmed = pantryName.trim()
                val amountText = pantryAmount.trim()
                val amountValue = amountText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                val amountValid = amountText.isBlank() || (amountValue != null && amountValue > 0.0)
                val expiryText = pantryExpiry.trim()
                val expiryValid = expiryText.isBlank() || parsePantryExpiryDate(expiryText) != null
                val normalizedUnit = normalizePantryUnit(pantryUnit)
                when {
                    trimmed.isBlank() -> {
                        pantryValidationMessage = "Add a pantry item name."
                        return@AddPantryItemDialog
                    }
                    !amountValid -> {
                        pantryValidationMessage = "Enter an amount greater than 0, or leave it blank."
                        return@AddPantryItemDialog
                    }
                    amountValue != null && normalizedUnit == null -> {
                        pantryValidationMessage = "Choose a supported pantry unit."
                        return@AddPantryItemDialog
                    }
                    !expiryValid -> {
                        pantryValidationMessage = "Use use-by date format YYYY-MM-DD, or leave it blank."
                        return@AddPantryItemDialog
                    }
                }
                val quantityDisplay = amountValue?.let { value ->
                    "${formatPantryAmountForStorage(value)} $normalizedUnit"
                }
                userViewModel.updatePantryEntries(
                    effectivePantryEntries + PantryEntry(
                        name = trimmed,
                        quantity = quantityDisplay,
                        expiryDate = expiryText.takeIf { it.isNotBlank() },
                        amount = amountValue,
                        unit = normalizedUnit
                    )
                )
                mealPlanViewModel.trackMlEvent(
                    eventName = "pantry_item_added",
                    payload = mapOf(
                        "item_name" to trimmed,
                        "source" to "grocery_pantry_dialog"
                    )
                )
                feedbackMessage = "$trimmed added to pantry."
                pantryName = ""
                pantryAmount = ""
                pantryUnit = "g"
                pantryExpiry = ""
                pantryValidationMessage = null
                showAddPantryDialog = false
            }
        )
    }

    if (showFilterDialog) {
        GroceryFilterDialog(
            activeScope = activeFilterScope,
            categories = categoryOrder,
            selectedCategories = selectedFilterCategories,
            onScopeChange = { groceryFilterScope = it.name },
            onToggleCategory = { category ->
                selectedFilterCategories = when {
                    selectedFilterCategories.isEmpty() -> setOf(category)
                    category in selectedFilterCategories -> {
                        val updated = selectedFilterCategories - category
                        if (updated.isEmpty()) emptySet() else updated
                    }
                    else -> selectedFilterCategories + category
                }
            },
            onClear = {
                groceryFilterScope = GroceryFilterScope.AllItems.name
                selectedFilterCategories = emptySet()
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        val scrollState = rememberScrollState()
        val compact = maxHeight < 760.dp || maxWidth < 390.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .testTag("grocery_content_list")
                .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            RefinedTabBrandHeader(
                online = isOnline,
                onSettings = { onNavigateToRoute(Routes.Settings) },
                onSupport = { onNavigateToRoute(Routes.Notifications) },
                compact = compact,
                avatarId = userProfile.avatarId
            )

            GroceryHeadlineCard(
                avatarId = userProfile.avatarId,
                dateLabel = todayLabel,
                compact = compact
            )

            GroceryNextStepsCard(
                anyExpanded = expandedCategories.isNotEmpty(),
                onToggleAll = {
                    expandedCategories = if (expandedCategories.isNotEmpty()) {
                        feedbackMessage = "Collapsed all categories."
                        emptySet()
                    } else {
                        feedbackMessage = "Opened all categories."
                        categoryEntries.map { it.first }.toSet()
                    }
                },
                onOpenMealPlan = {
                    if (isOnline) {
                        onNavigateToRoute(Routes.MealPlan)
                    } else {
                        feedbackMessage = "Internet required for this action. Connect to open plan generation."
                    }
                },
                onOpenProgress = { onNavigateToRoute(Routes.Progress) },
                compact = compact,
            )

            if (!feedbackMessage.isNullOrBlank()) {
                RefinedOverviewCard(
                    containerColor = Color(0xFFF3FFF7),
                    borderColor = PcosinaSuccess.copy(alpha = 0.28f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    Text(
                        text = feedbackMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = PcosinaSuccess
                    )
                }
            }

            GroceryBudgetCard(
                weeklyBudget = weeklyBudget,
                totalEstimated = totalEstimated,
                remainingBudget = remainingBudget,
                budgetProgress = budgetProgress,
                compact = compact
            )

            GroceryProgressCard(
                remainingCount = remainingCount,
                totalCount = totalCount,
                listProgress = listProgress,
                onShare = {
                    val body = buildString {
                        append("PCOSina Grocery List\n\n")
                        groupedEntries.forEach { item ->
                            val pantryTag = when (pantryCoverageByName[item.name]?.status) {
                                PantryCoverageStatus.Full -> " • pantry covered"
                                PantryCoverageStatus.Partial -> " • pantry partial"
                                PantryCoverageStatus.NameOnly -> " • pantry match"
                                null -> ""
                            }
                            append("• ${item.name} (${item.quantityDisplay})${pantryTag}\n")
                        }
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    feedbackMessage = "Share options opened for your grocery list."
                    context.startActivity(Intent.createChooser(intent, "Share grocery list"))
                },
                onOpenPantry = { showPantryDialog = true },
                compact = compact
            )

            GroceryKitchenHubHeader(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                onOpenFilters = { showFilterDialog = true },
                filtersActive = activeFilterScope != GroceryFilterScope.AllItems || selectedFilterCategories.isNotEmpty(),
                tipLine = tipLine,
                compact = compact
            )

            GroceryCategoryPanel(
                modifier = Modifier.fillMaxWidth(),
                hasPlan = hasPlan,
                groupedEntriesEmpty = groupedEntries.isEmpty(),
                searchFilteredEmpty = filteredEntries.isEmpty(),
                searchQuery = searchQuery,
                pantryMatches = pantryMatches,
                pantryCoverageByName = pantryCoverageByName,
                pantryOptOut = pantryOptOut,
                checkedNames = checkedNames,
                categoryEntries = categoryEntries,
                selectedCategoryIndex = selectedCategoryIndex,
                expandedCategories = expandedCategories,
                onPreviousCategory = {
                    if (categoryEntries.isNotEmpty()) {
                        val nextIndex = (selectedCategoryIndex - 1 + categoryEntries.size) % categoryEntries.size
                        selectedCategoryKey = categoryEntries[nextIndex].first
                    }
                },
                onNextCategory = {
                    if (categoryEntries.isNotEmpty()) {
                        val nextIndex = (selectedCategoryIndex + 1) % categoryEntries.size
                        selectedCategoryKey = categoryEntries[nextIndex].first
                    }
                },
                onToggleCategoryExpanded = { key ->
                    expandedCategories = if (key in expandedCategories) {
                        expandedCategories - key
                    } else {
                        expandedCategories + key
                    }
                },
                onToggleItem = { item ->
                    val wasComplete = totalCount > 0 && coveredCount >= totalCount
                    val nextPantryOptOut: Set<String>
                    val nextCheckedNames: Set<String>
                    if (item.name in pantryMatches) {
                        nextPantryOptOut = if (item.name in pantryOptOut) pantryOptOut - item.name else pantryOptOut + item.name
                        nextCheckedNames = checkedNames
                    } else {
                        nextPantryOptOut = pantryOptOut
                        nextCheckedNames = if (item.name in checkedNames) checkedNames - item.name else checkedNames + item.name
                    }
                    pantryOptOut = nextPantryOptOut
                    checkedNames = nextCheckedNames
                    val nextEffectiveChecked = nextCheckedNames + pantryMatches.filter { it !in nextPantryOptOut }
                    val isComplete = totalCount > 0 && groupedEntries.all { it.name in nextEffectiveChecked }
                    if (!wasComplete && isComplete) {
                        mealPlanViewModel.trackMlEvent(
                            eventName = "grocery_completed",
                            payload = mapOf(
                                "plan_id" to (activePlanId ?: "current"),
                                "item_count" to totalCount
                            )
                        )
                    }
                },
                onSyncIngredients = {
                    mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
                        groceryViewModel.setPlanSources(sources)
                        feedbackMessage = "Ingredients synced from your current meal plan."
                    }
                },
                compact = compact
            )

            GroceryBottomCtaCard(
                onAddPantry = { showAddPantryDialog = true },
                compact = compact
            )
        }
    }
}

@Composable
private fun PantryListDialog(
    entries: List<PantryEntry>,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (PantryEntry) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GroceryDialogSurface(maxWidth = 382.dp, containerColor = Color.White) {
            GroceryDialogHeader(
                icon = "🧺",
                title = "Pantry List",
                subtitle = "Amounts with compatible units can auto-cover or reduce grocery items. Name-only items stay for review."
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (entries.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFEEF2),
                        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f))
                    ) {
                        Text(
                            text = "No pantry items saved yet. Add staples you already have so the list stays realistic.",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PcosinaMuted
                        )
                    }
                } else {
                    entries.forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFDCE4),
                            border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.16f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PcosinaPink,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .semantics { contentDescription = "Edit pantry item" }
                                ) {
                                    PcosinaDesignIcon(
                                        resId = R.drawable.pcosina_svg_18_clipboard,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.padding(7.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = PcosinaDeepRose
                                    )
                                    Text(
                                        text = pantryEntryAmountDisplay(entry).ifBlank { "No amount saved - review matches manually" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PcosinaMuted
                                    )
                                    entry.expiryDate?.takeIf { it.isNotBlank() }?.let { expiry ->
                                        RefinedStatusPill(
                                            text = "Use by: $expiry",
                                            containerColor = Color.White,
                                            contentColor = PcosinaPink
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = PcosinaPink,
                                    border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.24f)),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .semantics { contentDescription = "Remove pantry item" }
                                        .clickable { onRemove(entry) }
                                ) {
                                    Text(
                                        text = "Remove",
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GroceryDialogActionButton(
                    text = "Close",
                    filled = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                GroceryDialogActionButton(
                    text = if (entries.isEmpty()) "Add item" else "Add pantry item",
                    filled = true,
                    onClick = onAdd,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AddPantryItemDialog(
    name: String,
    amount: String,
    unit: String,
    expiry: String,
    validationMessage: String?,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onExpiryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GroceryDialogSurface(maxWidth = 360.dp, containerColor = Color.White) {
            GroceryDialogHeader(
                icon = "🧺",
                title = "Add an item to the pantry",
                subtitle = "Use a clear amount and unit when you want automatic grocery matching."
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pantry item") },
                placeholder = { Text("Pantry item") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Amount") },
                    placeholder = { Text("80") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                PantryUnitDropdown(
                    selectedUnit = unit,
                    onUnitChange = onUnitChange,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = expiry,
                onValueChange = onExpiryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Use-by date") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            if (!validationMessage.isNullOrBlank()) {
                Text(
                    text = validationMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PcosinaPink
                )
            }
            Text(
                text = "Amount, unit, and use-by date are optional. Examples: 80 g, 1 pc, 1 pack. Compatible units auto-cover exact needs, reduce partial needs, and ignore expired items.",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GroceryDialogActionButton(
                    text = "Cancel",
                    filled = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                GroceryDialogActionButton(
                    text = "OK",
                    filled = true,
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class PantryUnitOption(
    val value: String,
    val label: String,
)

private val pantryUnitOptions = listOf(
    PantryUnitOption("g", "g"),
    PantryUnitOption("kg", "kg"),
    PantryUnitOption("ml", "ml"),
    PantryUnitOption("l", "L"),
    PantryUnitOption("piece", "pc"),
    PantryUnitOption("clove", "clove"),
    PantryUnitOption("head", "head"),
    PantryUnitOption("cup", "cup"),
    PantryUnitOption("tbsp", "tbsp"),
    PantryUnitOption("tsp", "tsp"),
    PantryUnitOption("bunch", "bunch"),
    PantryUnitOption("stalk", "stalk"),
    PantryUnitOption("can", "can"),
    PantryUnitOption("pack", "pack"),
)

private fun normalizePantryUnit(raw: String): String? {
    val unit = raw.trim().lowercase(Locale.ENGLISH)
    return pantryUnitOptions.firstOrNull { option ->
        option.value == unit || option.label.lowercase(Locale.ENGLISH) == unit
    }?.value
}

private fun formatPantryAmountForStorage(value: Double): String {
    val rounded = if (value >= 10) {
        (value * 10.0).roundToInt() / 10.0
    } else {
        (value * 100.0).roundToInt() / 100.0
    }
    return if (rounded % 1.0 == 0.0) rounded.roundToInt().toString() else rounded.toString()
}

private fun pantryEntryAmountDisplay(entry: PantryEntry): String {
    val amount = entry.amount?.takeIf { it > 0.0 && !it.isNaN() && !it.isInfinite() }
    val unit = entry.unit?.let(::normalizePantryUnit)
    return if (amount != null && unit != null) {
        val label = pantryUnitOptions.firstOrNull { it.value == unit }?.label ?: unit
        "${formatPantryAmountForStorage(amount)} $label"
    } else {
        entry.quantity.orEmpty()
    }
}

@Composable
private fun PantryUnitDropdown(
    selectedUnit: String,
    onUnitChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = pantryUnitOptions.firstOrNull { it.value == selectedUnit } ?: pantryUnitOptions.first()
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            border = BorderStroke(1.dp, PcosinaMuted.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = "Unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = PcosinaMuted
                    )
                    Text(
                        text = selected.label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = PcosinaDeepRose,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = PcosinaMuted
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            pantryUnitOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onUnitChange(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GroceryFilterDialog(
    activeScope: GroceryFilterScope,
    categories: List<String>,
    selectedCategories: Set<String>,
    onScopeChange: (GroceryFilterScope) -> Unit,
    onToggleCategory: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GroceryDialogSurface(
            maxWidth = 360.dp,
            containerColor = Color(0xFFEFE8EE),
            borderColor = Color(0xFF5C4B53).copy(alpha = 0.24f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = PcosinaPink
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text(
                    text = "Select Filters",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.Black
                )
            }
            GroceryFilterSectionTitle("All Items")
            GroceryFilterChoiceRow(
                title = "All items",
                selected = activeScope == GroceryFilterScope.AllItems,
                onClick = { onScopeChange(GroceryFilterScope.AllItems) }
            )
            GroceryFilterChoiceRow(
                title = "Need to buy",
                selected = activeScope == GroceryFilterScope.NeedToBuy,
                onClick = { onScopeChange(GroceryFilterScope.NeedToBuy) }
            )
            GroceryFilterChoiceRow(
                title = "Bought/Pantry",
                selected = activeScope == GroceryFilterScope.BoughtOrPantry,
                onClick = { onScopeChange(GroceryFilterScope.BoughtOrPantry) }
            )
            GroceryFilterSectionTitle("Categories")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                categories.forEach { category ->
                    GroceryFilterChoiceRow(
                        title = category,
                        selected = selectedCategories.isEmpty() || category in selectedCategories,
                        onClick = { onToggleCategory(category) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GroceryDialogActionButton(
                    text = "Clear All",
                    filled = false,
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                )
                GroceryDialogActionButton(
                    text = "Done",
                    filled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GroceryDialogSurface(
    maxWidth: androidx.compose.ui.unit.Dp = 330.dp,
    containerColor: Color = Color(0xFFFFF8FB),
    borderColor: Color = PcosinaSoftPink.copy(alpha = 0.62f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = maxWidth),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun GroceryDialogHeader(
    icon: String,
    title: String,
    subtitle: String,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFF8FA5),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun GroceryDialogActionButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 46.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (filled) PcosinaPink else Color.White,
        border = BorderStroke(1.dp, PcosinaPink),
        shadowElevation = if (filled) 6.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = if (filled) Color.White else PcosinaPink,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GroceryPreviewRow(
    item: GroceryListEntry,
    pantryCoverage: PantryCoverage?,
    pantryCovered: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val hasPantrySignal = pantryCoverage != null
    val isComplete = checked || pantryCovered
    val remainingCostPhp = estimateGroceryCostAfterPantry(
        entry = item,
        pantryCoverage = pantryCoverage,
        coveredOrBought = isComplete
    )
    Surface(
        color = if (isComplete) Color(0xFFFFF1F4) else Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            when {
                isComplete -> PcosinaPink.copy(alpha = 0.18f)
                hasPantrySignal -> PcosinaSuccess.copy(alpha = 0.28f)
                else -> PcosinaMuted.copy(alpha = 0.24f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(26.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isComplete) PcosinaPink else Color.White,
                border = BorderStroke(1.dp, if (isComplete) PcosinaPink else PcosinaMuted.copy(alpha = 0.4f))
            ) {
                PcosinaDesignIcon(
                    resId = R.drawable.pcosina_svg_12_check,
                    contentDescription = null,
                    tint = if (isComplete) Color.White else Color.Transparent,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(2.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isComplete) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = when {
                        pantryCoverage?.status == PantryCoverageStatus.Partial && !isComplete ->
                            "Need ${item.quantityDisplay}; still buy ${pantryCoverage.remainingQuantityDisplay ?: item.quantityDisplay}"
                        else -> item.quantityDisplay
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted
                )
                if (pantryCoverage != null && !pantryCovered) {
                    Text(
                        text = pantryCoverage.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pantryCoverage.status == PantryCoverageStatus.Partial) PcosinaSuccess else PcosinaMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (remainingCostPhp > 0) "₱$remainingCostPhp" else "₱0",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PcosinaDeepRose
                )
                RefinedStatusPill(
                    text = when {
                        pantryCovered -> "In Pantry"
                        checked -> "Bought"
                        pantryCoverage?.status == PantryCoverageStatus.Partial -> "Buy remaining"
                        pantryCoverage?.status == PantryCoverageStatus.NameOnly -> "Review pantry"
                        else -> "To Buy"
                    },
                    containerColor = if (pantryCovered) Color(0xFFFFE8EE) else PcosinaSurfaceAlt,
                    contentColor = if (pantryCovered) PcosinaPink else PcosinaMuted
                )
            }
        }
    }
}

@Composable
private fun GroceryHeadlineCard(
    avatarId: String,
    dateLabel: String,
    compact: Boolean,
) {
    SharedAvatarHeader(
        title = "Grocery Pantry",
        subtitle = "All your essentials, budgeted and in one place.",
        avatarId = avatarId,
        dateLabel = dateLabel,
        compact = compact,
        avatarAlignment = ScreenArtworkAlignment.GroceryHeaderAvatar,
    )
}

@Composable
private fun GroceryBudgetCard(
    weeklyBudget: Int?,
    totalEstimated: Int,
    remainingBudget: Int?,
    budgetProgress: Float,
    compact: Boolean,
) {
    val hasBudget = weeklyBudget != null
    val hasBudgetComparison = hasBudget && totalEstimated > 0
    val withinBudget = hasBudgetComparison && (remainingBudget ?: 0) >= 0
    val budgetColor = when {
        !hasBudgetComparison -> PcosinaMuted
        withinBudget -> Color(0xFF19B764)
        else -> Color(0xFFE2526E)
    }
    val budgetIcon = when {
        !hasBudgetComparison -> R.drawable.pcosina_grocery_budget
        withinBudget -> R.drawable.pcosina_budgeting_like
        else -> R.drawable.pcosina_budgeting_disliked
    }
    val budgetTitle = when {
        weeklyBudget == null -> "Budget not set"
        !hasBudgetComparison -> "Budget pending"
        withinBudget -> "Within the Budget"
        else -> "Over the Budget"
    }
    val budgetLabel = when {
        weeklyBudget == null -> "Budget not set yet."
        !hasBudgetComparison -> "Add or sync grocery items to compare against your budget."
        withinBudget -> "Within Budget! ${formatPhp(remainingBudget ?: 0)} left."
        else -> "Over Budget by ${formatPhp(kotlin.math.abs(remainingBudget ?: 0))}."
    }

    RefinedOverviewCard(
        containerColor = Color(0xFFF8FFF8),
        borderColor = budgetColor.copy(alpha = 0.24f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = budgetIcon),
                contentDescription = budgetTitle,
                modifier = Modifier.size(if (compact) 72.dp else 86.dp),
                contentScale = ContentScale.Fit,
                alignment = ScreenArtworkAlignment.GroceryBudgetIllustration,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = budgetTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                RefinedStatusPill(
                    text = budgetLabel,
                    containerColor = budgetColor.copy(alpha = 0.16f),
                    contentColor = budgetColor
                )
            }
        }
        Text(
            text = "Total Estimated Spending",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose
        )
        Text(
            text = if (weeklyBudget != null) {
                "${formatPhp(totalEstimated)} / ${formatPhp(weeklyBudget)}"
            } else {
                formatPhp(totalEstimated)
            },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = PcosinaDeepRose
            )
        )
        if (weeklyBudget != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(budgetColor.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(budgetProgress.coerceIn(0f, 1f))
                        .height(10.dp)
                        .background(budgetColor, RoundedCornerShape(999.dp))
                )
            }
        } else {
            Text(
                text = "Set a weekly grocery budget in your profile to compare plan costs against your target.",
                style = MaterialTheme.typography.bodyMedium,
                color = PcosinaMuted
            )
        }
    }
}

@Composable
private fun GroceryProgressCard(
    remainingCount: Int,
    totalCount: Int,
    listProgress: Float,
    onShare: () -> Unit,
    onOpenPantry: () -> Unit,
    compact: Boolean,
) {
    RefinedOverviewCard(
        containerColor = Color(0xFFFFEEF3),
        borderColor = PcosinaPink.copy(alpha = 0.26f),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.pcosina_grocery_progress_background),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alignment = ScreenArtworkAlignment.GroceryProgressBackground,
                alpha = 0.96f,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 12.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Grocery Progress",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = PcosinaDeepRose
                            )
                        )
                        Text(
                            text = "Built for your primary-user plan and synced with your saved pantry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PcosinaMuted
                        )
                    }
                    RefinedStatusPill(
                        text = "${(listProgress * 100).toInt().coerceIn(0, 100)}% checked",
                        containerColor = Color.White.copy(alpha = 0.84f),
                        contentColor = PcosinaDeepRose,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (compact) 12.dp else 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RefinedRingMeter(
                            valueText = "${remainingCount}/${totalCount.coerceAtLeast(1)}",
                            subtitle = "items left",
                            progress = 1f - listProgress,
                            color = PcosinaPink,
                            compact = compact
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                text = if (remainingCount == 0) "All items checked" else "Shop what is still missing",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = PcosinaDeepRose,
                            )
                            Text(
                                text = "Use pantry matches first, then buy only the remaining budgeted ingredients for this week's plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PcosinaMuted,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GroceryActionTile(
                        title = "Share Grocery List?",
                        iconRes = R.drawable.pcosina_svg_30_send,
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        compact = compact
                    )
                    GroceryActionTile(
                        title = "View Pantry List?",
                        iconRes = R.drawable.pcosina_svg_29_cart,
                        onClick = onOpenPantry,
                        modifier = Modifier.weight(1f),
                        compact = compact
                    )
                }
            }
        }
    }
}

@Composable
private fun GroceryActionTile(
    title: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFDCE4),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.14f)),
        modifier = modifier
            .heightIn(min = if (compact) 104.dp else 118.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = if (compact) 12.dp else 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.16f))
            ) {
                PcosinaDesignIcon(
                    resId = iconRes,
                    contentDescription = null,
                    tint = PcosinaPink,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(if (compact) 18.dp else 20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GroceryNextStepsCard(
    anyExpanded: Boolean,
    onToggleAll: () -> Unit,
    onOpenMealPlan: () -> Unit,
    onOpenProgress: () -> Unit,
    compact: Boolean,
) {
    RefinedOverviewCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("grocery_next_steps_card"),
        containerColor = Color(0xFFFFF8FB),
        borderColor = PcosinaPink.copy(alpha = 0.22f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Text(
            text = "Categories",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = PcosinaDeepRose,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroceryPillAction(
                text = "Go to Plan",
                onClick = onOpenMealPlan,
                modifier = Modifier
                    .weight(1f)
                    .testTag("grocery_open_mealplan_cta"),
            )
            GroceryPillAction(
                text = "Progress",
                onClick = onOpenProgress,
                modifier = Modifier
                    .weight(1f)
                    .testTag("grocery_open_progress_cta"),
            )
        }
        GroceryPillAction(
            text = if (anyExpanded) "Collapse all" else "Open all",
            onClick = onToggleAll,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("grocery_expand_toggle_all"),
        )
    }
}

@Composable
private fun GroceryPillAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.24f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GroceryKitchenHubHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    filtersActive: Boolean,
    tipLine: String,
    compact: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.pcosina_grocery_kitchen_hub),
                contentDescription = null,
                modifier = Modifier.size(if (compact) 58.dp else 68.dp),
                contentScale = ContentScale.Fit,
                alignment = ScreenArtworkAlignment.GroceryKitchenHubIllustration,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Your Kitchen Hub",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = tipLine,
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = PcosinaMuted
                )
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(999.dp),
            placeholder = {
                Text(
                    text = "Search ingredients",
                    color = PcosinaMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = PcosinaPink
                )
            },
            trailingIcon = {
                Surface(
                    shape = CircleShape,
                    color = if (filtersActive) PcosinaPink else PcosinaSurfaceAlt,
                    modifier = Modifier
                        .semantics { contentDescription = "Open grocery filters" }
                        .clickable(onClick = onOpenFilters)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = if (filtersActive) Color.White else PcosinaMuted,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PcosinaPink.copy(alpha = 0.4f),
                unfocusedBorderColor = PcosinaMuted.copy(alpha = 0.2f),
                focusedContainerColor = Color(0xFFFFE7EE),
                unfocusedContainerColor = Color(0xFFFFE7EE),
                cursorColor = PcosinaDeepRose,
                focusedTextColor = PcosinaDeepRose,
                unfocusedTextColor = PcosinaDeepRose
            )
        )
    }
}

@Composable
private fun GroceryCategoryPanel(
    modifier: Modifier,
    hasPlan: Boolean,
    groupedEntriesEmpty: Boolean,
    searchFilteredEmpty: Boolean,
    searchQuery: String,
    pantryMatches: Set<String>,
    pantryCoverageByName: Map<String, PantryCoverage>,
    pantryOptOut: Set<String>,
    checkedNames: Set<String>,
    categoryEntries: List<Pair<String, List<GroceryListEntry>>>,
    selectedCategoryIndex: Int,
    expandedCategories: Set<String>,
    onPreviousCategory: () -> Unit,
    onNextCategory: () -> Unit,
    onToggleCategoryExpanded: (String) -> Unit,
    onToggleItem: (GroceryListEntry) -> Unit,
    onSyncIngredients: () -> Unit,
    compact: Boolean,
) {
    RefinedOverviewCard(
        modifier = modifier,
        borderColor = PcosinaDeepRose.copy(alpha = 0.14f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        when {
            !hasPlan && groupedEntriesEmpty -> {
                Text(
                    text = "Your grocery list will appear after you generate a weekly plan.",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = "Use the Plan tab to generate meals first, then come back here to review synced ingredients.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PcosinaMuted
                )
            }

            groupedEntriesEmpty -> {
                Text(
                    text = "No ingredients are synced yet.",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = "Pull your current meal plan into the pantry preview to start checking and sharing the list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PcosinaMuted
                )
                RefinedPrimaryButton(
                    text = "Sync ingredients",
                    onClick = onSyncIngredients
                )
            }

            searchFilteredEmpty -> {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "No ingredients match your current filters."
                    } else {
                        "No ingredients match \"$searchQuery\"."
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = if (searchQuery.isBlank()) {
                        "Clear a filter or switch back to all items to view the full synced list again."
                    } else {
                        "Try a broader search term or clear the search field to view all synced ingredients again."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = PcosinaMuted
                )
            }

            else -> {
                val collapsedCount = 2
                val categoryIndex = selectedCategoryIndex.coerceIn(0, categoryEntries.lastIndex)
                val (category, items) = categoryEntries[categoryIndex]
                GroceryCategoryCarouselHeader(
                    category = category,
                    index = categoryIndex,
                    total = categoryEntries.size,
                    onPrevious = onPreviousCategory,
                    onNext = onNextCategory,
                    compact = compact,
                )
                    val title = category
                    val categoryExpanded = category in expandedCategories
                    val displayedItems = if (categoryExpanded) items else items.take(collapsedCount)
                    val coveredInCategory = items.count { item ->
                        item.name in checkedNames || (item.name in pantryMatches && item.name !in pantryOptOut)
                    }
                    val toggleCategoryLabel = if (categoryExpanded) {
                        "Collapse $title category"
                    } else {
                        "Expand $title category"
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, PcosinaDeepRose.copy(alpha = 0.28f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (category == "Produce") {
                                    PcosinaDesignIcon(
                                        resId = R.drawable.pcosina_svg_25_corn,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (compact) 30.dp else 36.dp),
                                        tint = Color.Unspecified
                                    )
                                } else {
                                    Text(
                                        text = groceryCategoryEmoji(category),
                                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = category,
                                        style = if (compact) {
                                            MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = PcosinaDeepRose
                                            )
                                        } else {
                                            MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                color = PcosinaDeepRose
                                            )
                                        }
                                    )
                                    Text(
                                        text = "$coveredInCategory of ${items.size} items bought/covered",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PcosinaMuted
                                    )
                                }
                                if (items.size > collapsedCount) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFFFEEF2),
                                        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f)),
                                        modifier = Modifier
                                            .size(42.dp)
                                            .semantics { contentDescription = toggleCategoryLabel }
                                            .clickable { onToggleCategoryExpanded(category) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (categoryExpanded) "-" else "+",
                                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                color = PcosinaDeepRose
                                            )
                                        }
                                    }
                                }
                            }

                            displayedItems.forEach { item ->
                                GroceryPreviewRow(
                                    item = item,
                                    pantryCoverage = pantryCoverageByName[item.name],
                                    pantryCovered = item.name in pantryMatches && item.name !in pantryOptOut,
                                    checked = item.name in checkedNames,
                                    onToggle = { onToggleItem(item) }
                                )
                            }

                            if (items.size > displayedItems.size) {
                                GroceryCategoryExpandAction(
                                    text = "View all ${items.size} items",
                                    contentDescription = toggleCategoryLabel,
                                    onClick = { onToggleCategoryExpanded(category) }
                                )
                            } else if (categoryExpanded && items.size > collapsedCount) {
                                GroceryCategoryExpandAction(
                                    text = "Show less",
                                    contentDescription = toggleCategoryLabel,
                                    onClick = { onToggleCategoryExpanded(category) }
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun GroceryCategoryCarouselHeader(
    category: String,
    index: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroceryCategoryArrowButton(
            contentDescription = "Previous grocery category",
            onClick = onPrevious,
            enabled = total > 1,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Browse category",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = PcosinaMuted
            )
            Text(
                text = category,
                style = if (compact) {
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                },
                color = PcosinaDeepRose,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${index + 1} of $total",
                style = MaterialTheme.typography.labelSmall,
                color = PcosinaMuted
            )
        }
        GroceryCategoryArrowButton(
            contentDescription = "Next grocery category",
            onClick = onNext,
            enabled = total > 1,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
        )
    }
}

@Composable
private fun GroceryCategoryArrowButton(
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) Color(0xFFFFEEF2) else PcosinaSurfaceAlt,
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = if (enabled) 0.22f else 0.08f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) PcosinaDeepRose else PcosinaMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun GroceryCategoryExpandAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFFFEEF2),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose
            )
        }
    }
}

@Composable
private fun GroceryTipsCard(
    goal: String,
    compact: Boolean,
) {
    val secondTip = when {
        goal.contains("Symptom", ignoreCase = true) ->
            "Prep simple add-ons like eggs, greens, and yogurt so symptom-friendly meals stay easy."
        goal.contains("Weight", ignoreCase = true) ->
            "Keep protein, vegetables, and steady-carb add-ons ready so weekday meals stay balanced."
        else ->
            "Prep simple add-ons like eggs, greens, and yogurt so weekday cooking feels easier."
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💡",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Shopping Tips",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = "Good food, good mood.",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = PcosinaMuted
                )
            }
        }
        GroceryTipStripe(
            icon = "🍞",
            text = "Prioritize high-fiber staples and steadier-carb swaps for your plan.",
            colors = listOf(Color(0xFFFF91A7), Color(0xFFFF7E95))
        )
        GroceryTipStripe(
            icon = "🍲",
            text = secondTip,
            colors = listOf(Color(0xFFFFC48A), Color(0xFFFFB27D))
        )
        GroceryTipStripe(
            icon = "💸",
            text = "Fresh market prices change week to week, so totals here are best-used as a guide.",
            colors = listOf(Color(0xFFA0A2FF), Color(0xFF8B8DF0))
        )
    }
}

@Composable
private fun GroceryTipStripe(
    icon: String,
    text: String,
    colors: List<Color>,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF3B1B22)
            )
        }
    }
}

@Composable
private fun GroceryBottomCtaCard(
    onAddPantry: () -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.22f)),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Add an item to the pantry",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PcosinaDeepRose
                )
                Text(
                    text = "Use the + button to save a staple.",
                    style = MaterialTheme.typography.labelSmall,
                    color = PcosinaMuted
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = PcosinaPink,
            modifier = Modifier
                .size(if (compact) 54.dp else 60.dp)
                .clickable(onClick = onAddPantry)
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add pantry item",
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 24.dp else 28.dp)
                )
            }
        }
    }
}

@Composable
private fun GroceryFilterSectionTitle(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFFF8FA5),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

@Composable
private fun GroceryFilterChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Color.White.copy(alpha = 0.76f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) PcosinaPink.copy(alpha = 0.18f) else Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PcosinaDeepRose
            )
            Surface(
                shape = CircleShape,
                color = if (selected) PcosinaPink else Color.White,
                border = BorderStroke(1.dp, if (selected) PcosinaPink else PcosinaMuted.copy(alpha = 0.24f))
            ) {
                PcosinaDesignIcon(
                    resId = R.drawable.pcosina_svg_12_check,
                    contentDescription = null,
                    tint = if (selected) Color.White else Color.Transparent,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}

private fun groceryCategoryEmoji(category: String?): String =
    when (category) {
        "Produce" -> "🌽"
        "Meat/Seafood" -> "🥩"
        "Eggs & Dairy" -> "🥚"
        "Dry Goods" -> "🌾"
        "Spices & Condiments" -> "🧂"
        "Canned/Packaged" -> "🥫"
        "Beverages" -> "🥤"
        else -> "🧺"
    }

private fun formatPhp(value: Int): String = "₱%,d".format(Locale.ENGLISH, value)

private fun refinedPantryKey(raw: String): String =
    canonicalGroceryKey(raw)

private fun parsePantryExpiryDate(raw: String?): LocalDate? =
    raw?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { value ->
            runCatching { LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        }

private fun refinedPantryTokens(raw: String): Set<String> =
    refinedPantryKey(raw)
        .split(" ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun refinedPantryMatches(pantryName: String, groceryName: String): Boolean {
    return groceryNamesMatch(pantryName, groceryName)
}
