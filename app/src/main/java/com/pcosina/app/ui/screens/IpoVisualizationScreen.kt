package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.theme.UiSpacingTokens

@Composable
fun IpoVisualizationScreen(
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = UiSpacingTokens.SectionGap),
        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "System Methodology",
                    subtitle = "Admin-only planning pipeline",
                    containerHeight = 180
                )
                IconButton(onClick = onBackToDashboard, modifier = Modifier.padding(8.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colorScheme.onPrimary)
                }
            }
        }

        item {
            IpoCard(
                step = "01",
                title = "PROFILE + PANTRY INPUTS",
                description = "Planning starts from your saved profile, pantry, goals, budget, allergies, and exclusions.",
                items = listOf(
                    "Profile: age, weight, height, activity level, and goals",
                    "Food rules: allergies, exclusions, and preference settings",
                    "Local pantry and budget context kept on device first"
                ),
                color = colorScheme.primary
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("↓", fontSize = 24.sp, color = colorScheme.primary)
            }
        }

        item {
            IpoCard(
                step = "02",
                title = "DETERMINISTIC FILTERING",
                description = "Recipes are screened before optimization so infeasible options never reach the final planner.",
                items = listOf(
                    "Hard rules remove forbidden, unsafe, or infeasible meals first",
                    "Pantry feasibility, nutrition limits, and repetition constraints stay enforceable",
                    "This stage remains explainable and repeatable offline"
                ),
                color = colorScheme.secondary
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("↓", fontSize = 24.sp, color = colorScheme.primary)
            }
        }

        item {
            IpoCard(
                step = "03",
                title = "DETERMINISTIC OPTIMIZATION",
                description = "A deterministic solver chooses the final week from the feasible meal candidates.",
                items = listOf(
                    "Balances calories, macros, budget, and variety targets",
                    "ML can assist ranking candidates, but never overrides hard constraints",
                    "Fallback behavior remains deterministic if ML is unavailable"
                ),
                color = colorScheme.primary
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("↓", fontSize = 24.sp, color = colorScheme.primary)
            }
        }

        item {
            IpoCard(
                step = "04",
                title = "EXPLAINABLE OUTPUTS",
                description = "You receive a weekly plan, grocery guidance, and nutrition details with clear fallback messaging.",
                items = listOf(
                    "Weekly meals, grocery deficits, and pantry-aware shopping guidance",
                    "Recipe details and nutrition totals stay visible for review",
                    "No-safe-plan cases return actionable adjustments instead of silent failure"
                ),
                color = colorScheme.secondary
            )
        }

        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(UiSpacingTokens.CardContentPadding),
                    verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.CardContentGap)
                ) {
                    Text(
                        text = "Wellness Decision Support",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "PCOSina supports meal planning and nutrition decisions. It does not diagnose conditions or replace clinical care.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "This view is for internal review. Regular users should only see plan outputs, guidance, and progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Button(
                onClick = onBackToDashboard,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text("Back", fontWeight = FontWeight.Bold)
            }
        }

        item { Spacer(Modifier.height(UiSpacingTokens.CardContentPadding)) }
    }
}

@Composable
private fun IpoCard(
    step: String,
    title: String,
    description: String,
    items: List<String>,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(UiSpacingTokens.CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionHeaderGap)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                    color = color.copy(alpha = 0.2f)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = color
                )
            }
            Text(text = description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
                    Spacer(Modifier.width(UiSpacingTokens.CardContentGap))
                    Text(text = item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
