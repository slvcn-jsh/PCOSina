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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
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

        // Weekly adherence (simple bar rows)
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
                    Text(
                        text = "Great job! You're consistently meeting your meal plan goals 🎉",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Average daily macros
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
                    MacroProgressBar(label = "Protein", progress = 78f / 80f, valueText = "78 / 80g")
                    MacroProgressBar(label = "Carbs", progress = 165f / 180f, valueText = "165 / 180g")
                    MacroProgressBar(label = "Fats", progress = 45f / 50f, valueText = "45 / 50g")
                    MacroProgressBar(label = "Fiber", progress = 22f / 25f, valueText = "22 / 25g")
                }
            }
        }

        // PCOS Symptom Tracking
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
                        text = "PCOS Symptom Tracking",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SymptomRow("Energy Levels", "↑ Improved", filled = 5, total = 5)
                    SymptomRow("Mood Stability", "↑ Better", filled = 4, total = 5)
                    SymptomRow("Cravings Control", "↑ Much Better", filled = 4, total = 5)
                }
            }
        }

        // This Week's Recommendations
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF6A11CB),
                                    Color(0xFF2575FC),
                                )
                            ),
                            shape = MaterialTheme.shapes.large,
                        )
                        .padding(14.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "This Week's Recommendations",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                        )
                        RecommendationCard(
                            title = "💪 Physical Activity",
                            body = "Try 20-minute walks after meals to help with insulin sensitivity. Start with 3 days this week!",
                        )
                        RecommendationCard(
                            title = "🥗 Gradual Transition",
                            body = "Great job increasing vegetable intake! This week, try adding more leafy greens to your meals.",
                        )
                        RecommendationCard(
                            title = "😴 Sleep Hygiene",
                            body = "Aim for 7-8 hours of sleep. Good sleep helps regulate hormones and manage PCOS symptoms.",
                        )
                    }
                }
            }
        }

        // Feedback section
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
                    Text(
                        text = "How are you feeling about your meal plan? Any recipes you loved or want to change?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
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

        // Motivational card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "🌟", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "You're doing amazing!",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Keep up the great work! Small consistent steps lead to big changes.",
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
private fun SymptomRow(
    label: String,
    status: String,
    filled: Int,
    total: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row {
            repeat(total) { index ->
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (index < filled) Color(0xFFFFC107) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    title: String,
    body: String,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                ),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.9f),
                ),
            )
        }
    }
}

