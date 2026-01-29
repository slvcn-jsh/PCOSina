package com.pcosina.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.data.model.DummyData.GroceryItem
import com.pcosina.app.ui.components.GradientHeader

@Composable
fun GroceryListScreen(
    modifier: Modifier = Modifier,
) {
    val allItems = DummyData.groceryList
    val totalItems = allItems.size
    val totalBudget = 2000
    var checkedNames by remember { mutableStateOf(setOf<String>()) }

    val groups: Map<String, List<GroceryItem>> = allItems.groupBy { it.category }

    val totalCost = allItems
        .filter { it.name !in checkedNames }
        .sumOf { it.price }
    val savings = totalBudget - totalCost
    val costProgress = (totalCost.toFloat() / totalBudget.toFloat()).coerceIn(0f, 1f)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GradientHeader(
                title = "Grocery List",
                subtitle = "Week of Jan 20-26, 2025",
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
                    Text(
                        text = "Within Budget! 🎉",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Estimated ₱$totalCost of ₱2,000 weekly budget. Saving ₱$savings!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { costProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0ABF6A),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { /* no-op */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text("Download List")
                    }
                }
            }
        }

        // Category cards
        groups.forEach { (category, items) ->
            item {
                CategoryCard(
                    icon = when (category) {
                        "Produce" -> "🥬"
                        "Meat & Seafood" -> "🐟"
                        "Dry Goods" -> "🌾"
                        else -> "🧂"
                    },
                    title = category,
                    items = items,
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
            }
            items.forEach { item ->
                val checked = item.name in checkedNames
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onCheckedChange(item.name, it) },
                        )
                        Column {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                            )
                            Text(
                                text = item.quantity,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
