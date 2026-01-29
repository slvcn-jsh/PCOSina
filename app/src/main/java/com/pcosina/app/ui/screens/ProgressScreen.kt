package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.MacroProgressBar
import com.pcosina.app.ui.components.StatCard

@Composable
fun ProgressScreen(
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var feedback by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "Your Progress",
                    subtitle = "Week 1 • Jan 20-26, 2025",
                    containerHeight = 180,
                )
                IconButton(
                    onClick = onBackToDashboard,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    title = "Avg Adherence",
                    value = "87%",
                    subtitle = "",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "This Week",
                    value = "-1.2kg",
                    subtitle = "",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Meals Prep",
                    value = "21",
                    subtitle = "",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Weekly Adherence",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val days = listOf(
                        "Mon" to 85,
                        "Tue" to 90,
                        "Wed" to 75,
                        "Thu" to 95,
                        "Fri" to 88,
                        "Sat" to 92,
                        "Sun" to 87,
                    )
                    days.forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(40.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .weight(1f)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = value / 100f)
                                        .height(8.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFF0ABF6A),
                                                    Color(0xFF2D9CDB),
                                                )
                                            ),
                                            shape = MaterialTheme.shapes.extraSmall,
                                        ),
                                )
                            }
                            Text(
                                text = "$value%",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Average Daily Macros",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    MacroProgressBar(label = "Protein", progress = 0.78f, valueText = "78 / 80g")
                    MacroProgressBar(label = "Carbs", progress = 0.91f, valueText = "165 / 180g")
                    MacroProgressBar(label = "Fats", progress = 0.9f, valueText = "45 / 50g")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Share Your Feedback",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = feedback,
                        onValueChange = { feedback = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        placeholder = { Text("Type your feedback here...") },
                    )
                    Button(
                        onClick = { /* no-op */ },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Submit Feedback")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}
