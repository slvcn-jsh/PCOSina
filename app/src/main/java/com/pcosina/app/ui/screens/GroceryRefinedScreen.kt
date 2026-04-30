package com.pcosina.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.pcosina.app.ui.components.RefinedActionCard
import com.pcosina.app.ui.components.RefinedHeroBanner
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
import java.util.Locale

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
    val planHistory by mealPlanViewModel.planHistory.collectAsState()
    val planState by mealPlanViewModel.uiState.collectAsState()
    val householdSize = userProfile.householdSize.coerceIn(1, 6)
    val groupedEntries = remember(groceryItems, householdSize) {
        buildGroceryListEntries(groceryItems, householdSize)
    }
    val pantryTokens = remember(pantryEntries) {
        pantryEntries.map { refinedPantryKey(it.name) }.filter { it.isNotBlank() }.toSet()
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
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var showPantryDialog by remember { mutableStateOf(false) }
    var showAddPantryDialog by remember { mutableStateOf(false) }
    var pantryName by rememberSaveable { mutableStateOf("") }
    var pantryQty by rememberSaveable { mutableStateOf("") }

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

    val searchFiltered = remember(groupedEntries, searchQuery) {
        if (searchQuery.isBlank()) groupedEntries
        else groupedEntries.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
    val categoryEntries = remember(searchFiltered) {
        val grouped = searchFiltered.groupBy { it.category }
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
    val previewItems = visibleItems.take(2)
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
                TextButton(onClick = { showPantryDialog = false }) { Text("Close") }
            },
            title = { Text("Saved pantry items") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pantryEntries.isEmpty()) {
                        Text("No pantry items saved yet.")
                    } else {
                        pantryEntries.take(6).forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    if (!entry.quantity.isNullOrBlank()) {
                                        Text(entry.quantity.orEmpty(), style = MaterialTheme.typography.bodySmall, color = PcosinaMuted)
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        userViewModel.updatePantryEntries(
                                            pantryEntries.filterNot { refinedPantryKey(it.name) == refinedPantryKey(entry.name) }
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
                                pantryEntries + PantryEntry(
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
            title = { Text("Add pantry item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = pantryName,
                        onValueChange = { pantryName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Ingredient") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pantryQty,
                        onValueChange = { pantryQty = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Quantity (optional)") },
                        singleLine = true
                    )
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
        val compact = maxHeight < 760.dp

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
                title = "Grocery Pantry",
                subtitle = "Your essentials, budgeted and in one place.",
                icon = Icons.AutoMirrored.Filled.ListAlt,
                compact = compact
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

            RefinedOverviewCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(if (compact) 14.dp else 16.dp)
            ) {
                val withinBudget = remainingBudget == null || remainingBudget >= 0
                RefinedStatusPill(
                    text = when {
                        weeklyBudget == null -> "Budget not set yet."
                        withinBudget -> "Within budget! ₱${remainingBudget ?: 0} left."
                        else -> "Above budget by ₱${kotlin.math.abs(remainingBudget ?: 0)}."
                    },
                    containerColor = if (withinBudget) Color(0xFFCBF4D8) else Color(0xFFFFE1E1),
                    contentColor = if (withinBudget) Color(0xFF138A47) else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Total estimated spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted
                )
                Text(
                    text = if (weeklyBudget != null) "₱$totalEstimated / ₱$weeklyBudget" else "₱$totalEstimated estimated",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PcosinaSurfaceAlt, RoundedCornerShape(999.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(budgetProgress.coerceIn(0f, 1f))
                            .background(if (withinBudget) Color(0xFF138A47) else MaterialTheme.colorScheme.error)
                            .padding(vertical = 8.dp)
                    ) {}
                }
                TextButton(onClick = { onNavigateToRoute(Routes.Progress) }) {
                    Text("Save actual weekly spending in Progress")
                }
            }

            RefinedOverviewCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(if (compact) 14.dp else 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RefinedRingMeter(
                        valueText = "$remainingCount/$totalCount",
                        subtitle = "items left",
                        progress = listProgress,
                        color = PcosinaPink,
                        compact = compact
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Your Kitchen Hub",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = PcosinaDeepRose
                        )
                        Text(
                            text = "Track what is in your pantry and what still needs buying for ${householdSizeLabel(householdSize)}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PcosinaMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RefinedPrimaryButton(
                                text = "Share",
                                onClick = {
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
                                    context.startActivity(Intent.createChooser(intent, "Share grocery list"))
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = PcosinaSurfaceAlt,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showPantryDialog = true }
                            ) {
                                Text(
                                    text = "Pantry",
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PcosinaDeepRose
                                )
                            }
                        }
                    }
                }
            }

            RefinedOverviewCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(if (compact) 14.dp else 16.dp)
            ) {
                Text(
                    text = "Search your ingredients",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your ingredients...") },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp)
                )
                Text(
                    text = tipLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted
                )
            }

            RefinedOverviewCard(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(if (compact) 14.dp else 16.dp)
            ) {
                if (searchFiltered.isEmpty()) {
                    Text(
                        text = if (groupedEntries.isEmpty()) "Your grocery list will appear here after a plan is synced." else "No ingredients match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PcosinaMuted
                    )
                    if (groupedEntries.isEmpty() && hasPlan) {
                        RefinedPrimaryButton(
                            text = "Sync ingredients now",
                            onClick = {
                                mealPlanViewModel.extractGrocerySourcesForPlan { sources ->
                                    groceryViewModel.setPlanSources(sources)
                                    feedbackMessage = "Ingredients synced from your current meal plan."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = CircleShape, color = PcosinaSoftPink.copy(alpha = 0.24f)) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    tint = PcosinaPink,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = selectedCategory ?: "Shopping list",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = PcosinaDeepRose
                                )
                                Text(
                                    text = if (selectedCategory != null) "${visibleItems.size} item(s) in this category" else "${searchFiltered.size} item(s) found",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PcosinaMuted
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryArrowButton(
                                icon = Icons.Filled.ChevronLeft,
                                enabled = selectedCategoryIndex > 0,
                                onClick = { selectedCategoryIndex-- }
                            )
                            CategoryArrowButton(
                                icon = Icons.Filled.ChevronRight,
                                enabled = selectedCategoryIndex < categoryEntries.lastIndex,
                                onClick = { selectedCategoryIndex++ }
                            )
                        }
                    }

                    previewItems.forEach { item ->
                        GroceryPreviewRow(
                            item = item,
                            pantryCovered = item.name in pantryMatches,
                            checked = if (item.name in pantryMatches) item.name !in pantryOptOut else item.name in checkedNames,
                            onToggle = {
                                if (item.name in pantryMatches) {
                                    pantryOptOut = if (item.name in pantryOptOut) pantryOptOut - item.name else pantryOptOut + item.name
                                } else {
                                    checkedNames = if (item.name in checkedNames) checkedNames - item.name else checkedNames + item.name
                                }
                            }
                        )
                    }

                    if (visibleItems.size > previewItems.size) {
                        Text(
                            text = "${visibleItems.size - previewItems.size} more item(s) are ready in $selectedCategory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PcosinaMuted
                        )
                    }
                }
            }

            RefinedActionCard(
                title = "Ready to cook and log your meals?",
                subtitle = "Your ingredient list is synced and ready to support the recipes in your weekly plan.",
                buttonLabel = "Go to Plan",
                onClick = { onNavigateToRoute(Routes.MealPlan) },
                secondaryLabel = if (pantryEntries.isEmpty()) "Add pantry" else "Progress",
                onSecondaryClick = {
                    if (pantryEntries.isEmpty()) {
                        showAddPantryDialog = true
                    } else {
                        onNavigateToRoute(Routes.Progress)
                    }
                },
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
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (checked || pantryCovered) PcosinaPink.copy(alpha = 0.18f) else PcosinaMuted.copy(alpha = 0.24f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (checked || pantryCovered) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    text = item.quantityDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PcosinaMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₱${item.estimatedCostPhp}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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
