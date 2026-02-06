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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.TextFieldValue
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.DummyData.GroceryItem
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.theme.PcosinaSuccess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility

@Composable
fun GroceryListScreen(
    groceryViewModel: GroceryViewModel,
    userViewModel: UserViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val addedItems by groceryViewModel.groceryItems.collectAsState()
    val lastPlanTimestamp by groceryViewModel.lastPlanTimestamp.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    
    // Combine dummy static list with dynamic added items
    val allItems = DummyData.groceryList + addedItems
    val totalItems = allItems.size
    val weeklyBudget = if (userProfile.weeklyBudgetPhp > 0) userProfile.weeklyBudgetPhp else 2000
    var checkedNames by remember { mutableStateOf(setOf<String>()) }

    var budgetMode by rememberSaveable { mutableStateOf("Weekly") }
    val displayBudget = if (budgetMode == "Weekly") weeklyBudget.toDouble() else weeklyBudget * 4.33
    val derivedWeekly = if (budgetMode == "Monthly") (displayBudget / 4.33) else displayBudget

    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
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
    val weekLabel = remember(lastPlanTimestamp) { formatWeekRange(lastPlanTimestamp) }

    val totalCost = allItems
        .filter { it.name !in checkedNames }
        .sumOf { it.price }
    val savings = displayBudget.toInt() - totalCost
    val costProgress = (totalCost.toFloat() / displayBudget.toFloat()).coerceIn(0f, 1f)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search items") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryTile(
                    title = "Total Items",
                    value = "${checkedNames.size}/$totalItems",
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
            // Budget alert card
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
                            label = { Text("Monthly") },
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
                            "Estimated ₱$totalCost of ₱${displayBudget.toInt()} monthly budget. Weekly equivalent ₱${derivedWeekly.toInt()}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { costProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (totalCost <= displayBudget) PcosinaSuccess else MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                val listText = allItems.joinToString("\n") { 
                                    "- ${it.name} (${it.quantity})" + (if (it.name in checkedNames) " [CHECKED]" else "")
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
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text("Share/Download List")
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
                OutlinedButton(
                    onClick = {
                        expandedMap = displayCategories.associateWith { !allExpanded }
                    }
                ) {
                    Text(if (allExpanded) "Collapse all" else "Expand all")
                }
            }
        }

        displayCategories.forEach { category ->
            val items = groups[category].orEmpty()
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
                        checkedNames = if (checked) checkedNames + name else checkedNames - name
                    },
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
                        val checked = item.name in checkedNames
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
                                text = "₱${if (checked) 0 else item.price}",
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
    val name = item.name.lowercase(Locale.getDefault()).trim()
    if (name.isBlank()) return "Others"
    return when {
        listOf(
            "lettuce","spinach","cabbage","carrot","broccoli","kale","tomato","onion","garlic","pepper",
            "pechay","ampalaya","okra","eggplant","sayote","squash","ginger","gabi","kamote","cucumber",
            "banana","apple","orange","mango","grape","papaya","pineapple","strawberry","melon","calamansi"
        ).any { name.contains(it) } -> "Produce"
        listOf(
            "chicken","beef","pork","fish","salmon","tuna","shrimp","tilapia","meat","bangus","sardine",
            "galunggong","tocino","longganisa"
        ).any { name.contains(it) } -> "Meat/Seafood"
        listOf("egg","milk","cheese","yogurt","butter","cream").any { name.contains(it) } -> "Eggs & Dairy"
        listOf(
            "rice","oat","bread","pasta","noodles","flour","grains","cereal","quinoa","barley",
            "corn","frozen","dried","beans","lentils"
        ).any { name.contains(it) } -> "Dry Goods"
        listOf("salt","pepper","soy","sauce","vinegar","spice","condiment","oil","sugar","honey","bagoong").any { name.contains(it) } ->
            "Spices & Condiments"
        listOf("canned","packaged","instant","biscuit","cracker","chips","snack","noodles").any { name.contains(it) } ->
            "Canned/Packaged"
        listOf("juice","soda","coffee","tea","water","milk tea").any { name.contains(it) } ->
            "Beverages"
        else -> "Others"
    }
}

private fun formatWeekRange(timestamp: Long?): String {
    val zone = ZoneId.systemDefault()
    val baseDate = if (timestamp != null && timestamp > 0) {
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    } else {
        LocalDate.now(zone)
    }
    val weekFields = WeekFields.of(Locale.getDefault())
    val start = baseDate.with(TemporalAdjusters.previousOrSame(weekFields.firstDayOfWeek))
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
