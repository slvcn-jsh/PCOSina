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
        val scrollState = rememberScrollState()
        val compact = maxHeight < 760.dp || maxWidth < 390.dp

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
                onOpenProgress = { onNavigateToRoute(Routes.Progress) },
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
                    context.startActivity(Intent.createChooser(intent, "Share grocery list"))
                },
                onOpenPantry = { showPantryDialog = true },
                compact = compact
            )

            GroceryKitchenHubHeader(
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                tipLine = tipLine,
                compact = compact
            )

            GroceryCategoryPanel(
                modifier = Modifier.fillMaxWidth(),
                hasPlan = hasPlan,
                groupedEntriesEmpty = groupedEntries.isEmpty(),
                searchFilteredEmpty = searchFiltered.isEmpty(),
                searchQuery = searchQuery,
                selectedCategory = selectedCategory,
                visibleItems = visibleItems,
                previewItems = previewItems,
                pantryMatches = pantryMatches,
                pantryOptOut = pantryOptOut,
                checkedNames = checkedNames,
                selectedCategoryIndex = selectedCategoryIndex,
                categoryEntries = categoryEntries,
                onPreviousCategory = { if (selectedCategoryIndex > 0) selectedCategoryIndex-- },
                onNextCategory = { if (selectedCategoryIndex < categoryEntries.lastIndex) selectedCategoryIndex++ },
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

            GroceryTipsCard(
                householdSize = householdSize,
                goal = userProfile.goal,
                compact = compact
            )

            GroceryBottomCtaCard(
                pantryEntriesEmpty = pantryEntries.isEmpty(),
                onGoToPlan = { onNavigateToRoute(Routes.MealPlan) },
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

@Composable
private fun GroceryHeadlineCard(
    compact: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, PcosinaDeepRose.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(PcosinaBlush, Color(0xFFFF8FA5))
                    )
                )
                .padding(horizontal = if (compact) 14.dp else 18.dp, vertical = if (compact) 12.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.24f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pcosina_logo),
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 38.dp else 44.dp),
                        contentScale = ContentScale.Crop
                    )
                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint = PcosinaDeepRose,
                        modifier = Modifier.size(if (compact) 22.dp else 24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Grocery Pantry",
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = PcosinaDeepRose.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = " ",
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
                Text(
                    text = "All your essentials, budgeted and in one place.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
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
    onOpenProgress: () -> Unit,
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
        contentPadding = PaddingValues(if (compact) 14.dp else 18.dp)
    ) {
        RefinedStatusPill(
            text = budgetLabel,
            containerColor = budgetColor.copy(alpha = 0.18f),
            contentColor = budgetColor
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFFFFF4E8),
                border = BorderStroke(1.dp, Color(0xFFCCB38A).copy(alpha = 0.4f))
            ) {
                Text(
                    text = "₱",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    style = if (compact) {
                        MaterialTheme.typography.headlineMedium.copy(
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Total Estimated Spending",
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
                text = "Set a weekly grocery budget in Progress to compare plan costs against your target.",
                style = MaterialTheme.typography.bodyMedium,
                color = PcosinaMuted
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFFFF3F6),
            border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.16f)),
            modifier = Modifier.clickable(onClick = onOpenProgress)
        ) {
            Text(
                text = "Open Progress to update weekly budget",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose
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
        contentPadding = PaddingValues(if (compact) 14.dp else 18.dp)
    ) {
        Text(
            text = "Grocery Progress",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = PcosinaDeepRose
            )
        )
        Text(
            text = "Built for ${householdSizeLabel(householdSize)} and synced with your saved pantry.",
            style = MaterialTheme.typography.bodyMedium,
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
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFEDF1),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.14f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (compact) 14.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PcosinaPink,
                modifier = Modifier.size(if (compact) 22.dp else 26.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
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
    tipLine: String,
    compact: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🍽️",
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Your Kitchen Hub",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = tipLine,
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
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
                    text = "Search your ingredients...",
                    color = Color.White.copy(alpha = 0.92f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color.White
                )
            },
            trailingIcon = {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White.copy(alpha = 0.72f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.56f),
                focusedContainerColor = PcosinaBlush,
                unfocusedContainerColor = PcosinaBlush,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
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
    previewItems: List<GroceryListEntry>,
    pantryMatches: Set<String>,
    pantryOptOut: Set<String>,
    checkedNames: Set<String>,
    selectedCategoryIndex: Int,
    categoryEntries: List<Pair<String, List<GroceryListEntry>>>,
    onPreviousCategory: () -> Unit,
    onNextCategory: () -> Unit,
    onToggleItem: (GroceryListEntry) -> Unit,
    onSyncIngredients: () -> Unit,
    compact: Boolean,
) {
    RefinedOverviewCard(
        modifier = modifier,
        borderColor = PcosinaDeepRose.copy(alpha = 0.14f),
        contentPadding = PaddingValues(if (compact) 14.dp else 18.dp)
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
                    text = "No ingredients match \"$searchQuery\".",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = "Try a broader search term or clear the search field to view all synced ingredients again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PcosinaMuted
                )
            }

            else -> {
                val coveredInCategory = visibleItems.count { item ->
                    item.name in checkedNames || (item.name in pantryMatches && item.name !in pantryOptOut)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = groceryCategoryEmoji(selectedCategory),
                        style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedCategory.orEmpty(),
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
                        Text(
                            text = "$coveredInCategory out of ${visibleItems.size} items bought/covered",
                            style = MaterialTheme.typography.bodyLarge,
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

                previewItems.forEach { item ->
                    GroceryPreviewRow(
                        item = item,
                        pantryCovered = item.name in pantryMatches && item.name !in pantryOptOut,
                        checked = item.name in checkedNames,
                        onToggle = { onToggleItem(item) }
                    )
                }

                if (visibleItems.size > previewItems.size) {
                    RefinedStatusPill(
                        text = "+${visibleItems.size - previewItems.size} more in ${selectedCategory.orEmpty()}",
                        containerColor = Color(0xFFFFEEF2),
                        contentColor = PcosinaDeepRose
                    )
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💡",
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Shopping Tips",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaDeepRose
                    )
                )
                Text(
                    text = "Good food, good mood.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
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
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(colors), RoundedCornerShape(22.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF3B1B22)
            )
        }
    }
}

@Composable
private fun GroceryBottomCtaCard(
    pantryEntriesEmpty: Boolean,
    onGoToPlan: () -> Unit,
    onAddPantry: () -> Unit,
    compact: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
            color = Color.Transparent,
            border = BorderStroke(2.dp, PcosinaDeepRose.copy(alpha = 0.75f))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFF8BA2), Color(0xFFFFC2CE))
                        )
                    )
                    .padding(horizontal = if (compact) 16.dp else 18.dp, vertical = if (compact) 14.dp else 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ready to Cook & Log Your Meals?",
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
                        Text(
                            text = if (pantryEntriesEmpty) {
                                "Review your synced ingredients, then jump back to your plan when you're ready to cook."
                            } else {
                                "Ingredients are synced and pantry-aware. Open your plan to review recipes and start logging meals."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                        RefinedPrimaryButton(
                            text = "Go to Plan",
                            onClick = onGoToPlan
                        )
                    }
                    Text(
                        text = "🍲",
                        style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PcosinaPink,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAddPantry)
            ) {
                Text(
                    text = "ADD AN ITEM TO THE PANTRY",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            Surface(
                shape = CircleShape,
                color = PcosinaPink,
                modifier = Modifier
                    .size(if (compact) 58.dp else 66.dp)
                    .clickable(onClick = onAddPantry)
            ) {
                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add pantry item",
                        tint = Color.White,
                        modifier = Modifier.size(if (compact) 28.dp else 32.dp)
                    )
                }
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
