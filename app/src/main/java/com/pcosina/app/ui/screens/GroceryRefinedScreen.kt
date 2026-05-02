package com.pcosina.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.R
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.domain.GroceryListEntry
import com.pcosina.app.domain.buildGroceryListEntries
import com.pcosina.app.domain.householdSizeLabel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
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
import java.util.Locale

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
    val householdSize = userProfile.householdSize.coerceIn(1, 6)
    val groupedEntries = remember(groceryItems, householdSize) {
        buildGroceryListEntries(groceryItems, householdSize)
    }
    val pantryTokens = remember(effectivePantryEntries) {
        effectivePantryEntries.map { refinedPantryKey(it.name) }.filter { it.isNotBlank() }.toSet()
    }
    val pantryMatches = remember(groupedEntries, pantryTokens) {
        groupedEntries.filter { item ->
            pantryTokens.any { token -> refinedPantryMatches(token, item.name) }
        }.map { it.name }.toSet()
    }
    var checkedNames by rememberSaveable(activePlanId) { mutableStateOf(setOf<String>()) }
    var pantryOptOut by rememberSaveable(activePlanId) { mutableStateOf(setOf<String>()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryIndex by rememberSaveable(activePlanId) { mutableStateOf(0) }
    var expandedCategories by rememberSaveable(activePlanId) { mutableStateOf(setOf<String>()) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var showPantryDialog by remember { mutableStateOf(false) }
    var showAddPantryDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var pantryName by rememberSaveable { mutableStateOf("") }
    var pantryQty by rememberSaveable { mutableStateOf("") }
    var groceryFilterScope by rememberSaveable { mutableStateOf(GroceryFilterScope.AllItems.name) }
    var selectedFilterCategories by rememberSaveable { mutableStateOf(setOf<String>()) }

    val effectiveChecked = remember(checkedNames, pantryMatches, pantryOptOut) {
        checkedNames + pantryMatches.filter { it !in pantryOptOut }
    }
    val hasPlan = planHistory.isNotEmpty() || planState is MealPlanUiState.Success

    LaunchedEffect(hasPlan, groupedEntries.isEmpty(), mealSources.isEmpty()) {
        if (hasPlan && groupedEntries.isEmpty() && mealSources.isEmpty()) {
            mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
                groceryViewModel.setPlanSources(sources)
            }
        }
    }

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
    val activeFilterScope = remember(groceryFilterScope) {
        runCatching { GroceryFilterScope.valueOf(groceryFilterScope) }.getOrDefault(GroceryFilterScope.AllItems)
    }
    val filteredEntries = remember(
        groupedEntries,
        searchQuery,
        activeFilterScope,
        selectedFilterCategories,
        pantryMatches,
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
    LaunchedEffect(categoryEntries.size) {
        if (selectedCategoryIndex > categoryEntries.lastIndex) {
            selectedCategoryIndex = 0
        }
    }
    val selectedCategoryPair = categoryEntries.getOrNull(selectedCategoryIndex)
    val selectedCategory = selectedCategoryPair?.first
    val visibleItems = selectedCategoryPair?.second.orEmpty()
    val totalCount = groupedEntries.size
    val coveredCount = groupedEntries.count { it.name in effectiveChecked }
    val remainingCount = (totalCount - coveredCount).coerceAtLeast(0)
    val totalEstimated = groupedEntries.filter { it.name !in effectiveChecked }.sumOf { it.estimatedCostPhp }
    val weeklyBudget = userProfile.weeklyBudgetPhp.takeIf { it > 0 }
    val remainingBudget = weeklyBudget?.minus(totalEstimated)
    val budgetProgress = if ((weeklyBudget ?: 0) > 0) totalEstimated.toFloat() / weeklyBudget!!.toFloat() else 0f
    val listProgress = if (totalCount > 0) coveredCount.toFloat() / totalCount.toFloat() else 0f
    val tipLine = remember(userProfile.goal, householdSize) {
        when {
            userProfile.goal.contains("Symptom", ignoreCase = true) ->
                "Keep pantry staples simple so symptom-friendly meals stay easy to repeat."
            userProfile.goal.contains("Weight", ignoreCase = true) ->
                "Prioritize high-fiber staples and steady-carb swaps before extras."
            else -> "Use pantry matches first so your grocery list stays practical and budget-aware."
        }
    }

    if (showPantryDialog) {
        AlertDialog(
            onDismissRequest = { showPantryDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPantryDialog = false
                        showAddPantryDialog = true
                    }
                ) {
                    Text(if (effectivePantryEntries.isEmpty()) "Add item" else "Add pantry item")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPantryDialog = false }) { Text("Close") }
            },
            title = {
                Text(
                    text = "Manage Pantry",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Save what you already have so Grocery can mark pantry-covered ingredients clearly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PcosinaMuted
                    )
                    if (effectivePantryEntries.isEmpty()) {
                        Text(
                            text = "No pantry items saved yet. Add staples you already have so the list stays realistic.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PcosinaDeepRose
                        )
                    } else {
                        effectivePantryEntries.forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFFFF3F6),
                                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.16f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = PcosinaDeepRose
                                        )
                                        Text(
                                            text = entry.quantity.orEmpty().ifBlank { "Saved pantry staple" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PcosinaMuted
                                        )
                                    }
                                    TextButton(
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .semantics { contentDescription = "Remove pantry item" },
                                        onClick = {
                                            userViewModel.updatePantryEntries(
                                                effectivePantryEntries.filterNot {
                                                    refinedPantryKey(it.name) == refinedPantryKey(entry.name)
                                                }
                                            )
                                            feedbackMessage = "${entry.name} removed from pantry."
                                        }
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showAddPantryDialog) {
        AlertDialog(
            onDismissRequest = { showAddPantryDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = pantryName.trim()
                        if (trimmed.isNotBlank()) {
                            userViewModel.updatePantryEntries(
                                effectivePantryEntries + PantryEntry(
                                    name = trimmed,
                                    quantity = pantryQty.trim().takeIf { it.isNotBlank() }
                                )
                            )
                            feedbackMessage = "$trimmed added to pantry."
                            pantryName = ""
                            pantryQty = ""
                            showAddPantryDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPantryDialog = false }) { Text("Cancel") }
            },
            title = {
                Text(
                    text = "Manage Pantry",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFF8FA5),
                        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f))
                    ) {
                        Text(
                            text = "Save what you already have",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    OutlinedTextField(
                        value = pantryName,
                        onValueChange = { pantryName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Pantry item") },
                        placeholder = { Text("Pantry item") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pantryQty,
                            onValueChange = { pantryQty = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Qty") },
                            placeholder = { Text("Qty") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            modifier = Modifier.weight(1f),
                            label = { Text("Expiry Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            enabled = false,
                            singleLine = true
                        )
                    }
                    Text(
                        text = "Quantity is optional. Expiry stays visual only for now until pantry tracking expands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PcosinaMuted
                    )
                }
            }
        )
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) { Text("Done") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        groceryFilterScope = GroceryFilterScope.AllItems.name
                        selectedFilterCategories = emptySet()
                    }
                ) {
                    Text("Clear all")
                }
            },
            title = {
                Text(
                    text = "Select Filters",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GroceryFilterSectionTitle("All Items")
                    GroceryFilterChoiceRow(
                        title = "All items",
                        selected = activeFilterScope == GroceryFilterScope.AllItems,
                        onClick = { groceryFilterScope = GroceryFilterScope.AllItems.name }
                    )
                    GroceryFilterChoiceRow(
                        title = "Need to buy",
                        selected = activeFilterScope == GroceryFilterScope.NeedToBuy,
                        onClick = { groceryFilterScope = GroceryFilterScope.NeedToBuy.name }
                    )
                    GroceryFilterChoiceRow(
                        title = "Bought/Pantry",
                        selected = activeFilterScope == GroceryFilterScope.BoughtOrPantry,
                        onClick = { groceryFilterScope = GroceryFilterScope.BoughtOrPantry.name }
                    )
                    GroceryFilterSectionTitle("Categories")
                    categoryOrder.forEach { category ->
                        GroceryFilterChoiceRow(
                            title = category,
                            selected = selectedFilterCategories.isEmpty() || category in selectedFilterCategories,
                            onClick = {
                                selectedFilterCategories = when {
                                    selectedFilterCategories.isEmpty() -> setOf(category)
                                    category in selectedFilterCategories -> {
                                        val updated = selectedFilterCategories - category
                                        if (updated.isEmpty()) emptySet() else updated
                                    }
                                    else -> selectedFilterCategories + category
                                }
                            }
                        )
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
        val scrollState = rememberScrollState()
        val compact = maxHeight < 760.dp || maxWidth < 390.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
        ) {
            RefinedTabBrandHeader(
                online = isOnline,
                onSettings = { onNavigateToRoute(Routes.Settings) },
                onSupport = { onNavigateToRoute(Routes.Ipo) },
                compact = compact
            )

            GroceryHeadlineCard(compact = compact)

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
                householdSize = householdSize,
                onShare = {
                    val body = buildString {
                        append("PCOSina Grocery List\n\n")
                        groupedEntries.forEach { item ->
                            val pantryTag = if (item.name in pantryMatches) " • pantry" else ""
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
                selectedCategory = selectedCategory,
                visibleItems = visibleItems,
                pantryMatches = pantryMatches,
                pantryOptOut = pantryOptOut,
                checkedNames = checkedNames,
                selectedCategoryIndex = selectedCategoryIndex,
                categoryEntries = categoryEntries,
                expanded = selectedCategory != null && selectedCategory in expandedCategories,
                onPreviousCategory = { if (selectedCategoryIndex > 0) selectedCategoryIndex-- },
                onNextCategory = { if (selectedCategoryIndex < categoryEntries.lastIndex) selectedCategoryIndex++ },
                onToggleExpanded = {
                    selectedCategory?.let { key ->
                        expandedCategories = if (key in expandedCategories) {
                            expandedCategories - key
                        } else {
                            expandedCategories + key
                        }
                    }
                },
                onToggleItem = { item ->
                    if (item.name in pantryMatches) {
                        pantryOptOut = if (item.name in pantryOptOut) pantryOptOut - item.name else pantryOptOut + item.name
                    } else {
                        checkedNames = if (item.name in checkedNames) checkedNames - item.name else checkedNames + item.name
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
private fun GroceryPreviewRow(
    item: GroceryListEntry,
    pantryCovered: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        color = if (checked || pantryCovered) Color(0xFFFFF1F4) else Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (checked || pantryCovered) PcosinaPink.copy(alpha = 0.18f) else PcosinaMuted.copy(alpha = 0.24f)),
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
                shape = RoundedCornerShape(12.dp),
                color = if (checked || pantryCovered) PcosinaPink else Color.White,
                border = BorderStroke(1.dp, if (checked || pantryCovered) PcosinaPink else PcosinaMuted.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (checked || pantryCovered) Color.White else Color.Transparent,
                    modifier = Modifier.padding(8.dp)
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
                    textDecoration = if (checked || pantryCovered) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = item.quantityDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₱${item.estimatedCostPhp}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PcosinaDeepRose
                )
                RefinedStatusPill(
                    text = if (pantryCovered) "In Pantry" else if (checked) "Bought" else "To Buy",
                    containerColor = if (pantryCovered) Color(0xFFFFE8EE) else PcosinaSurfaceAlt,
                    contentColor = if (pantryCovered) PcosinaPink else PcosinaMuted
                )
            }
        }
    }
}

@Composable
private fun CategoryArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (enabled) Color.White else PcosinaSurfaceAlt,
        border = BorderStroke(1.dp, PcosinaMuted.copy(alpha = 0.25f)),
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
private fun GroceryHeadlineCard(
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 22.dp else 24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, PcosinaDeepRose.copy(alpha = 0.78f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(PcosinaBlush, Color(0xFFFF8FA5))
                    )
                )
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 12.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.24f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pcosina_logo),
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 30.dp else 34.dp),
                        contentScale = ContentScale.Crop
                    )
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint = PcosinaDeepRose,
                        modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Grocery Pantry",
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
                    text = "All your essentials, budgeted and in one place.",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                    color = PcosinaDeepRose
                )
            }
        }
    }
}

@Composable
private fun GroceryBudgetCard(
    weeklyBudget: Int?,
    totalEstimated: Int,
    remainingBudget: Int?,
    budgetProgress: Float,
    compact: Boolean,
) {
    val withinBudget = remainingBudget == null || remainingBudget >= 0
    val budgetColor = if (withinBudget) Color(0xFF19B764) else Color(0xFFE2526E)
    val budgetLabel = when {
        weeklyBudget == null -> "Budget not set yet."
        withinBudget -> "Within Budget! ${formatPhp(remainingBudget ?: 0)} left."
        else -> "Over Budget by ${formatPhp(kotlin.math.abs(remainingBudget ?: 0))}."
    }

    RefinedOverviewCard(
        borderColor = PcosinaDeepRose.copy(alpha = 0.14f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        RefinedStatusPill(
            text = budgetLabel,
            containerColor = budgetColor.copy(alpha = 0.18f),
            contentColor = budgetColor
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFFF4E8),
                border = BorderStroke(1.dp, Color(0xFFCCB38A).copy(alpha = 0.4f))
            ) {
                Text(
                    text = "₱",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = if (compact) {
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PcosinaDeepRose
                        )
                    } else {
                        MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PcosinaDeepRose
                        )
                    }
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Estimated this week",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PcosinaMuted
                )
                Text(
                    text = if (weeklyBudget != null) {
                        "${formatPhp(totalEstimated)} / ${formatPhp(weeklyBudget)}"
                    } else {
                        formatPhp(totalEstimated)
                    },
                    style = if (compact) {
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PcosinaDeepRose
                        )
                    } else {
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PcosinaDeepRose
                        )
                    }
                )
            }
        }
        if (weeklyBudget != null) {
            RefinedMetricBar(
                label = "Weekly budget",
                valueText = formatPhp((remainingBudget ?: 0).coerceAtLeast(0)) + " left",
                progress = budgetProgress,
                color = budgetColor
            )
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
    householdSize: Int,
    onShare: () -> Unit,
    onOpenPantry: () -> Unit,
    compact: Boolean,
) {
    RefinedOverviewCard(
        borderColor = PcosinaDeepRose.copy(alpha = 0.14f),
        contentPadding = PaddingValues(if (compact) 12.dp else 14.dp)
    ) {
        Text(
            text = "Grocery Progress",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = PcosinaDeepRose
            )
        )
        Text(
            text = "Built for ${householdSizeLabel(householdSize)} and synced with your saved pantry.",
            style = MaterialTheme.typography.bodySmall,
            color = PcosinaMuted
        )
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RefinedRingMeter(
                    valueText = "${remainingCount}/${totalCount.coerceAtLeast(1)}",
                    subtitle = "items left",
                    progress = 1f - listProgress,
                    color = PcosinaPink,
                    compact = compact
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GroceryActionTile(
                        title = "Share Grocery List?",
                        icon = Icons.AutoMirrored.Filled.ListAlt,
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        compact = compact
                    )
                    GroceryActionTile(
                        title = "View Pantry List?",
                        icon = Icons.Filled.ShoppingCart,
                        onClick = onOpenPantry,
                        modifier = Modifier.weight(1f),
                        compact = compact
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroceryActionTile(
                    title = "Share Grocery List?",
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
                RefinedRingMeter(
                    valueText = "${remainingCount}/${totalCount.coerceAtLeast(1)}",
                    subtitle = "items left",
                    progress = 1f - listProgress,
                    color = PcosinaPink,
                    compact = compact
                )
                GroceryActionTile(
                    title = "View Pantry List?",
                    icon = Icons.Filled.ShoppingCart,
                    onClick = onOpenPantry,
                    modifier = Modifier.weight(1f),
                    compact = compact
                )
            }
        }
    }
}

@Composable
private fun GroceryActionTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFEDF1),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.14f)),
        modifier = modifier.clickable(onClick = onClick)
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
                Icon(
                    imageVector = icon,
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
            Text(
                text = "🍽️",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    modifier = Modifier.clickable(onClick = onOpenFilters)
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
                focusedContainerColor = Color(0xFFFFEEF2),
                unfocusedContainerColor = Color(0xFFFFEEF2),
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
    selectedCategory: String?,
    visibleItems: List<GroceryListEntry>,
    pantryMatches: Set<String>,
    pantryOptOut: Set<String>,
    checkedNames: Set<String>,
    selectedCategoryIndex: Int,
    categoryEntries: List<Pair<String, List<GroceryListEntry>>>,
    expanded: Boolean,
    onPreviousCategory: () -> Unit,
    onNextCategory: () -> Unit,
    onToggleExpanded: () -> Unit,
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
                val title = selectedCategory.orEmpty()
                val toggleCategoryLabel = if (expanded) {
                    "Collapse $title category"
                } else {
                    "Expand $title category"
                }
                val collapsedCount = 2
                val displayedItems = if (expanded) visibleItems else visibleItems.take(collapsedCount)
                val coveredInCategory = visibleItems.count { item ->
                    item.name in checkedNames || (item.name in pantryMatches && item.name !in pantryOptOut)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = groceryCategoryEmoji(selectedCategory),
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = selectedCategory.orEmpty(),
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
                            text = "$coveredInCategory of ${visibleItems.size} items bought or covered",
                            style = MaterialTheme.typography.bodySmall,
                            color = PcosinaMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryArrowButton(
                            icon = Icons.Filled.ChevronLeft,
                            enabled = selectedCategoryIndex > 0,
                            onClick = onPreviousCategory
                        )
                        CategoryArrowButton(
                            icon = Icons.Filled.ChevronRight,
                            enabled = selectedCategoryIndex < categoryEntries.lastIndex,
                            onClick = onNextCategory
                        )
                    }
                }

                displayedItems.forEach { item ->
                    GroceryPreviewRow(
                        item = item,
                        pantryCovered = item.name in pantryMatches && item.name !in pantryOptOut,
                        checked = item.name in checkedNames,
                        onToggle = { onToggleItem(item) }
                    )
                }

                if (visibleItems.size > displayedItems.size) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFFEEF2),
                        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = toggleCategoryLabel }
                            .clickable(onClick = onToggleExpanded)
                    ) {
                        Text(
                            text = "View all ${visibleItems.size} items",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = PcosinaDeepRose
                        )
                    }
                } else if (expanded && visibleItems.size > collapsedCount) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, PcosinaMuted.copy(alpha = 0.24f)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = toggleCategoryLabel }
                            .clickable(onClick = onToggleExpanded)
                    ) {
                        Text(
                            text = "Show less",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = PcosinaDeepRose
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroceryTipsCard(
    householdSize: Int,
    goal: String,
    compact: Boolean,
) {
    val householdLabel = if (householdSize == 1) "1 person" else "$householdSize people"
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
            text = "Prioritize high-fiber staples and steadier-carb swaps for $householdLabel.",
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
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFFFE8EE) else Color.White,
        border = BorderStroke(1.dp, if (selected) PcosinaPink.copy(alpha = 0.28f) else PcosinaMuted.copy(alpha = 0.16f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                Icon(
                    imageVector = Icons.Filled.Check,
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
    raw.trim()
        .lowercase(Locale.ENGLISH)
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun refinedPantryTokens(raw: String): Set<String> =
    refinedPantryKey(raw)
        .split(" ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun refinedPantryMatches(pantryName: String, groceryName: String): Boolean {
    val pantryKey = refinedPantryKey(pantryName)
    val groceryKey = refinedPantryKey(groceryName)
    if (pantryKey.isBlank() || groceryKey.isBlank()) return false
    if (pantryKey == groceryKey) return true
    val pantryTokens = refinedPantryTokens(pantryName)
    val groceryTokens = refinedPantryTokens(groceryName)
    if (pantryTokens.size <= 1 || groceryTokens.size <= 1) return false
    return pantryTokens.containsAll(groceryTokens) || groceryTokens.containsAll(pantryTokens)
}
