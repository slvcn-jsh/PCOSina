package com.pcosina.app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.domain.GroceryListEntry
import com.pcosina.app.domain.buildGroceryListEntries
import com.pcosina.app.domain.householdSizeLabel
import com.pcosina.app.ui.GroceryViewModel
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
import com.pcosina.app.ui.components.CanvaPrimaryButton
import com.pcosina.app.ui.components.CanvaStatusChip
import com.pcosina.app.ui.theme.CanvaTokens
import java.util.Locale

private enum class CanvaGroceryFilter(val label: String) {
    All("All items"),
    NeedToBuy("To Buy"),
    Covered("Bought / Pantry"),
}

@Composable
fun CanvaGroceryListScreen(
    groceryViewModel: GroceryViewModel,
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    progressViewModel: ProgressViewModel,
    onNavigateToRoute: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val groceryItems by groceryViewModel.groceryItems.collectAsState()
    val activePlanId by groceryViewModel.activePlanId.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    val pantryEntries by userViewModel.pantryEntries.collectAsState()
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val planState by mealPlanViewModel.uiState.collectAsState()

    val hasPlan = planHistory.isNotEmpty() || planState is MealPlanUiState.Success
    val householdSize = userProfile.householdSize.coerceIn(1, 6)
    val groupedEntries = remember(groceryItems, householdSize) {
        buildGroceryListEntries(groceryItems, householdSize)
    }
    val pantryTokens = remember(pantryEntries) {
        pantryEntries.map { normalizedPantryEntryKey(it.name) }.filter { it.isNotBlank() }.toSet()
    }
    val pantryMatches = remember(groupedEntries, pantryTokens) {
        groupedEntries.filter { item ->
            pantryTokens.any { token -> pantryEntryMatchesGroceryItem(token, item.name) }
        }.map { it.name }.toSet()
    }

    var checkedNames by rememberSaveable(activePlanId) { mutableStateOf(setOf<String>()) }
    var pantryOptOut by rememberSaveable(activePlanId) { mutableStateOf(setOf<String>()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CanvaGroceryFilter.All) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var showPantryDialog by remember { mutableStateOf(false) }
    var showAddPantryDialog by remember { mutableStateOf(false) }

    var pantryName by rememberSaveable { mutableStateOf("") }
    var pantryQuantity by rememberSaveable { mutableStateOf("") }

    val effectiveChecked = remember(checkedNames, pantryMatches, pantryOptOut) {
        checkedNames + pantryMatches.filter { it !in pantryOptOut }
    }
    val filteredEntries = remember(groupedEntries, searchQuery, filter, effectiveChecked) {
        groupedEntries.filter { entry ->
            val matchesSearch = searchQuery.isBlank() || entry.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filter) {
                CanvaGroceryFilter.All -> true
                CanvaGroceryFilter.NeedToBuy -> entry.name !in effectiveChecked
                CanvaGroceryFilter.Covered -> entry.name in effectiveChecked
            }
            matchesSearch && matchesFilter
        }
    }
    val categories = remember(filteredEntries) { filteredEntries.groupBy { it.category } }
    val expandedByCategory = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(categories.keys) {
        categories.keys.forEach { key ->
            if (expandedByCategory[key] == null) expandedByCategory[key] = expandedByCategory.isEmpty()
        }
    }

    val remainingCount = filteredEntries.count { it.name !in effectiveChecked }
    val totalCount = filteredEntries.size
    val completedCount = (totalCount - remainingCount).coerceAtLeast(0)
    val totalCost = filteredEntries
        .filter { it.name !in effectiveChecked }
        .sumOf { it.estimatedCostPhp }
    val weeklyBudget = userProfile.weeklyBudgetPhp.takeIf { it > 0 }
    val budgetDelta = (weeklyBudget ?: 0) - totalCost
    val shoppingProgress = if (groupedEntries.isNotEmpty()) {
        effectiveChecked.size.toFloat() / groupedEntries.size.toFloat()
    } else {
        0f
    }
    val budgetProgress = if ((weeklyBudget ?: 0) > 0) {
        totalCost.toFloat() / weeklyBudget!!.toFloat()
    } else {
        0f
    }

    if (showPantryDialog) {
        AlertDialog(
            onDismissRequest = { showPantryDialog = false },
            confirmButton = {
                TextButton(onClick = { showPantryDialog = false }) { Text("Close") }
            },
            title = { Text("Pantry Items") },
            text = {
                if (pantryEntries.isEmpty()) {
                    Text("No pantry items are saved yet.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pantryEntries.forEach { entry ->
                            Text(
                                text = listOfNotNull(entry.name, entry.quantity).joinToString(" • "),
                                style = MaterialTheme.typography.bodyLarge,
                                color = CanvaTokens.Ink,
                            )
                        }
                    }
                }
            },
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
                            val updated = pantryEntries + PantryEntry(
                                name = trimmed,
                                quantity = pantryQuantity.trim().takeIf { it.isNotBlank() },
                            )
                            userViewModel.updatePantryEntries(updated)
                            pantryName = ""
                            pantryQuantity = ""
                            showAddPantryDialog = false
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pantryName = ""
                        pantryQuantity = ""
                        showAddPantryDialog = false
                    },
                ) { Text("Cancel") }
            },
            title = { Text("Add an item to the pantry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pantryName,
                        onValueChange = { pantryName = it },
                        label = { Text("Item name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pantryQuantity,
                        onValueChange = { pantryQuantity = it },
                        label = { Text("Quantity") },
                        singleLine = true,
                    )
                }
            },
        )
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
                title = "Grocery Pantry",
                subtitle = "All your essentials, budgeted and in one place.",
            )
        }

        item {
            CanvaCard {
                val withinBudget = weeklyBudget != null && budgetDelta >= 0
                CanvaStatusChip(
                    text = when {
                        weeklyBudget == null -> "Budget not set yet"
                        withinBudget -> "Within Budget! ₱$budgetDelta left."
                        else -> "Over budget by ₱${-budgetDelta}"
                    },
                    background = if (withinBudget) Color(0xFFD7F7E4) else Color(0xFFFFE1E5),
                    contentColor = if (withinBudget) CanvaTokens.GreenDeep else CanvaTokens.RedStrong,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFF3FFF7),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = null,
                                tint = CanvaTokens.GreenStrong,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Total Estimated Spending",
                            style = MaterialTheme.typography.titleMedium,
                            color = CanvaTokens.SupportGray,
                        )
                        Text(
                            text = if (weeklyBudget != null) {
                                "₱$totalCost / ₱$weeklyBudget"
                            } else {
                                "₱$totalCost / Budget not set"
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = CanvaTokens.Ink,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(Color(0xFFD6F7E4), RoundedCornerShape(999.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(budgetProgress.coerceIn(0f, 1f))
                            .height(20.dp)
                            .background(
                                if (weeklyBudget != null && budgetDelta >= 0) CanvaTokens.GreenStrong else CanvaTokens.RedStrong,
                                RoundedCornerShape(999.dp),
                            )
                    )
                }
            }
        }

        item {
            CanvaCard {
                Text(
                    text = "Grocery Progress",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = CanvaTokens.HeadlineMaroon,
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val compact = maxWidth < 380.dp
                    if (compact) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CanvaCircularMetric(
                                primaryText = "$completedCount/$totalCount",
                                secondaryText = "items covered",
                                progress = shoppingProgress,
                                size = 136.dp,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                GroceryQuickActionCard(
                                    icon = Icons.Filled.Send,
                                    label = "Share Grocery List?",
                                    modifier = Modifier.weight(1f),
                                ) {
                                    val shareText = buildShareText(filteredEntries)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share grocery list"))
                                }
                                GroceryQuickActionCard(
                                    icon = Icons.Filled.RestaurantMenu,
                                    label = "View Pantry List?",
                                    modifier = Modifier.weight(1f),
                                ) {
                                    showPantryDialog = true
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GroceryQuickActionCard(
                                icon = Icons.Filled.Send,
                                label = "Share Grocery List?",
                                modifier = Modifier.size(width = 116.dp, height = 102.dp),
                            ) {
                                val shareText = buildShareText(filteredEntries)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share grocery list"))
                            }
                            CanvaCircularMetric(
                                primaryText = "$completedCount/$totalCount",
                                secondaryText = "items covered",
                                progress = shoppingProgress,
                                size = 160.dp,
                            )
                            GroceryQuickActionCard(
                                icon = Icons.Filled.RestaurantMenu,
                                label = "View Pantry List?",
                                modifier = Modifier.size(width = 116.dp, height = 102.dp),
                            ) {
                                showPantryDialog = true
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Your Kitchen Hub",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = CanvaTokens.HeadlineMaroon,
                )
                Text(
                    text = "Easily track what's in your pantry vs. what's still on your shopping list.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = CanvaTokens.Ink,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    placeholder = { Text("Search your ingredients...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CanvaTokens.PanelPink,
                        unfocusedContainerColor = CanvaTokens.PanelPink,
                        focusedBorderColor = CanvaTokens.PanelPink,
                        unfocusedBorderColor = CanvaTokens.PanelPink,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.88f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.88f),
                    ),
                )
                Box {
                    Surface(
                        modifier = Modifier
                            .size(58.dp)
                            .clickable { filterMenuExpanded = true },
                        shape = CircleShape,
                        color = CanvaTokens.PanelPink,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "Filter grocery items",
                                tint = Color.White,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                    ) {
                        CanvaGroceryFilter.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    filter = option
                                    filterMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!hasPlan) {
            item {
                CanvaCard {
                    Text(
                        text = "Generate a weekly plan first so PCOSina can build the grocery list from your actual meals.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CanvaTokens.Ink,
                    )
                    CanvaPrimaryButton(
                        text = "Go to Plan",
                        onClick = { onNavigateToRoute(Routes.MealPlan) },
                    )
                }
            }
        } else if (groupedEntries.isEmpty()) {
            item {
                CanvaCard {
                    Text(
                        text = "Your grocery list is still empty for this plan. Open the meal plan and refresh or generate the current week again.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CanvaTokens.Ink,
                    )
                    CanvaPrimaryButton(
                        text = "Open Plan",
                        onClick = { onNavigateToRoute(Routes.MealPlan) },
                    )
                }
            }
        } else {
            categories.forEach { (category, entries) ->
                item {
                    CanvaCategoryCard(
                        category = category,
                        entries = entries,
                        expanded = expandedByCategory[category] ?: true,
                        onToggle = { expandedByCategory[category] = !(expandedByCategory[category] ?: true) },
                        checkedNames = checkedNames,
                        pantryMatches = pantryMatches,
                        pantryOptOut = pantryOptOut,
                        onItemToggle = { entryName, shouldCheck, pantryMatch ->
                            if (pantryMatch) {
                                pantryOptOut = if (shouldCheck) {
                                    pantryOptOut - entryName
                                } else {
                                    pantryOptOut + entryName
                                }
                            } else {
                                checkedNames = if (shouldCheck) checkedNames + entryName else checkedNames - entryName
                            }
                        },
                    )
                }
            }
        }

        item {
            CanvaGradientPanel(brush = CanvaTokens.MainCtaGradient) {
                Text(
                    text = "Shopping help",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = CanvaTokens.HeadlineMaroon,
                )
                Text(
                    text = "Household mode: ${householdSizeLabel(householdSize)}. Set your pantry first, then shop only the gap.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = CanvaTokens.HeadlineMaroon,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CanvaPrimaryButton(
                        text = "Open Plan",
                        onClick = { onNavigateToRoute(Routes.MealPlan) },
                        modifier = Modifier.weight(1f),
                    )
                    CanvaPrimaryButton(
                        text = "Open Progress",
                        onClick = { onNavigateToRoute(Routes.Progress) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CanvaPrimaryButton(
                    text = "ADD AN ITEM TO THE PANTRY",
                    onClick = { showAddPantryDialog = true },
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .size(78.dp)
                        .clickable { showAddPantryDialog = true },
                    shape = CircleShape,
                    color = CanvaTokens.AccentPinkStrong,
                    shadowElevation = 12.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add pantry item",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvaCategoryCard(
    category: String,
    entries: List<GroceryListEntry>,
    expanded: Boolean,
    onToggle: () -> Unit,
    checkedNames: Set<String>,
    pantryMatches: Set<String>,
    pantryOptOut: Set<String>,
    onItemToggle: (String, Boolean, Boolean) -> Unit,
) {
    val coveredCount = entries.count { entry ->
        if (entry.name in pantryMatches) entry.name !in pantryOptOut else entry.name in checkedNames
    }
    CanvaCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.Black,
                )
                Text(
                    text = "$coveredCount out of ${entries.size} items bought/covered",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = CanvaTokens.AccentPinkStrong,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(34.dp),
            )
        }

        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.forEach { entry ->
                    val pantryMatch = entry.name in pantryMatches
                    val checked = if (pantryMatch) entry.name !in pantryOptOut else entry.name in checkedNames
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = if (checked) Color(0xFFFFEBEF) else Color.White,
                        border = BorderStroke(1.dp, if (checked) Color(0xFFFFB4C2) else CanvaTokens.SoftOutline),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        onItemToggle(entry.name, !checked, pantryMatch)
                                    },
                                shape = RoundedCornerShape(6.dp),
                                color = if (checked) CanvaTokens.AccentPinkStrong else Color.Transparent,
                                border = BorderStroke(
                                    2.dp,
                                    if (checked) CanvaTokens.AccentPinkStrong else CanvaTokens.SupportGray,
                                ),
                            ) {
                                if (checked) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                                )
                                Text(
                                    text = entry.quantityDisplay,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CanvaTokens.Ink,
                                    maxLines = 1,
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (!checked) {
                                    Text(
                                        text = "₱ ${entry.estimatedCostPhp}",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                                        color = Color.Black,
                                    )
                                }
                                CanvaStatusChip(
                                    text = when {
                                        pantryMatch && checked -> "In Pantry"
                                        checked -> "Bought"
                                        else -> "To Buy"
                                    },
                                    background = if (pantryMatch && checked) Color(0xFFFFEDF2) else Color(0xFFEDE8EA),
                                    contentColor = if (pantryMatch && checked) CanvaTokens.AccentPinkStrong else CanvaTokens.SupportGray,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildShareText(entries: List<GroceryListEntry>): String {
    if (entries.isEmpty()) return "My PCOSina grocery list is currently empty."
    return buildString {
        appendLine("PCOSina Grocery List")
        appendLine()
        entries.forEach { entry ->
            appendLine("- ${entry.name} • ${entry.quantityDisplay} • ₱${entry.estimatedCostPhp}")
        }
    }.trim()
}

@Composable
private fun GroceryQuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFE6EC),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CanvaTokens.AccentPinkStrong,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = CanvaTokens.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
