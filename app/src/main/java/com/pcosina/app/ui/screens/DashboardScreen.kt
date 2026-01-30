package com.pcosina.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.MacroProgressBar
import com.pcosina.app.ui.components.StatCard

@Composable
fun DashboardScreen(
    userViewModel: UserViewModel,
    onRecipeClick: (String) -> Unit,
    onViewPlan: () -> Unit = {},
    onViewIpo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    val dailyCalorieTarget = userViewModel.dailyCalorieTarget

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "Hello, ${profile.displayName}! 👋",
                    subtitle = "Let's plan your healthy week",
                    containerHeight = 190,
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "Notifications",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = "Profile",
                                    tint = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                        }
                    },
                )
            }
        }

        item {
            // Quick stats row – Adherence, Cal/day, Pantry Use
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    title = "Goal",
                    value = if (profile.goal.contains("Weight Loss")) "Loss" else "Balance",
                    subtitle = "",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Cal/day",
                    value = dailyCalorieTarget.toString(),
                    subtitle = "Target",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Weight",
                    value = "${profile.weightKg}kg",
                    subtitle = "Current",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            // "How PCOSINA Works" card (IPO entry)
            Card(
                onClick = onViewIpo,
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "How PCOSINA Works",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "See the Input-Process-Output flow",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            // "This Week's Plan"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "This Week's Plan",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .then(Modifier)
                        .clickableNoRipple { onViewPlan() },
                )
            }
        }

        item {
            // Plan preview card
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Today • Monday",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text(
                                text = "280 cal",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "14P • 42C • 6F",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = "Red Rice Lugaw with Ginger",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Localized breakfast for PCOS management",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Button(
                        onClick = onViewPlan,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("View Recipe")
                    }
                }
            }
        }

        item {
            // Today's macros
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Today's Macros",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    MacroProgressBar(label = "Protein", progress = 0.56f, valueText = "45g / 80g")
                    MacroProgressBar(label = "Carbs", progress = 0.67f, valueText = "120g / 180g")
                    MacroProgressBar(label = "Fats", progress = 0.70f, valueText = "35g / 50g")
                    MacroProgressBar(label = "Fiber", progress = 0.72f, valueText = "18g / 25g")
                }
            }
        }

        item { Spacer(Modifier.height(6.dp)) }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    composed {
        clickable(
            indication = null,
            interactionSource = MutableInteractionSource(),
            onClick = onClick,
        )
    }
