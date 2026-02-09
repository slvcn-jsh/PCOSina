package com.pcosina.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.DummyData.GroceryItem
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.theme.PcosinaSuccess
import com.pcosina.app.domain.PriceCatalog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction

@Composable
fun GroceryListScreen(
    groceryViewModel: GroceryViewModel,
    userViewModel: UserViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val addedItems by groceryViewModel.groceryItems.collectAsState()
    val lastPlanTimestamp by groceryViewModel.lastPlanTimestamp.collectAsState()
    val activePlanId by groceryViewModel.activePlanId.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    val focusManager = LocalFocusManager.current
    
    // Combine dummy static list with dynamic added items
    val allItems = DummyData.groceryList + addedItems
    val totalItems = allItems.size
    val weeklyBudget = userProfile.weeklyBudgetPhp.takeIf { it > 0 }
    var checkedNames by remember { mutableStateOf(setOf<String>()) }
    var pantryOptOut by remember { mutableStateOf(setOf<String>()) }

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
    val filteredItems = if (searchQuery.text.isBlank()) allItems else allItems.filter {
        it.name.contains(searchQuery.text, ignoreCase = true)
    }

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
    val extraCategories = groups.keys.filter { it !in categoryOrder }.sorted()
    val displayCategories = categoryOrder + extraCategories

    var expandedMap by rememberSaveable { mutableStateOf(displayCategories.associateWith { true }) }
    val allExpanded = expandedMap.values.all { it }
    val weekLabel = remember(activePlanId, lastPlanTimestamp) {
        formatWeekRange(activePlanId, lastPlanTimestamp)
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GradientHeader(
                title = "Grocery List",
                subtitle = weekLabel,
            )
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
                    Text(
                        text = "Pantry Inventory",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Optional. Items here are treated as “use-first” during planning.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    var pantryInput by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
                    var pantryQty by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
                    var pantryExpiry by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = pantryInput,
                            onValueChange = { pantryInput = it },
                            label = { Text("Add pantry item") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pantryQty,
                            onValueChange = { pantryQty = it },
                            label = { Text("Qty") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = pantryExpiry,
                            onValueChange = { pantryExpiry = it },
                            label = { Text("Expiry (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val name = pantryInput.text.trim()
                                if (name.isNotBlank()) {
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
                                }
                            }
                        ) {
                            Text("Add")
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
                                    },
                                    label = { Text(labelText) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Remove",
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
                label = { Text("Search items") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )
        }

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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = budgetMode == "Weekly",
                                onClick = { budgetMode = "Weekly" },
                                label = { Text("Weekly") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            FilterChip(
                                selected = budgetMode == "Monthly",
                                onClick = { budgetMode = "Monthly" },
                                label = { Text("Monthly (≈ weekly × 4.33)") },
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
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                val listText = allItems.joinToString("\n") {
                                    val pantryTag = if (it.name in pantryMatches) " [PANTRY]" else ""
                                    "- ${it.name} (${it.quantity})" + pantryTag + (if (it.name in effectiveCheckedNames) " [CHECKED]" else "")
                                }
                                putExtra(Intent.EXTRA_TEXT, "My PCOSINA Grocery List:\n\n$listText")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Grocery List"))
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

        // Category cards
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
                        label = { Text("A–Z") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    OutlinedButton(
                        onClick = {
                            expandedMap = displayCategories.associateWith { !allExpanded }
                        }
                    ) {
                        Text(if (allExpanded) "Collapse all" else "Expand all")
                    }
                }
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
                        } else {
                            checkedNames = if (checked) checkedNames + name else checkedNames - name
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

        item { Spacer(Modifier.height(8.dp)) }
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
                IconButton(onClick = onToggle) {
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
