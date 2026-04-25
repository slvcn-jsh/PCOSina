package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.StatusCenterCard
import com.pcosina.app.ui.theme.UiSpacingTokens

@Composable
fun IpoVisualizationScreen(
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val methodologyStatus = "Deterministic filtering and optimization remain the planning backbone."
    val mlBoundaryStatus = "Hard rules always win. ML can assist ranking, but never override constraints."
    val localFirstStatus = "Profile, pantry, and household inputs stay local-first before planning starts."
    val nextReviewStatus = "Use this view for internal review only. Regular users should stay in plan, grocery, and progress."

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = UiSpacingTokens.SectionGap),
        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "Planning Methodology",
                    subtitle = "Internal view of the deterministic planning pipeline.",
                    containerHeight = 180
                )
                IconButton(onClick = onBackToDashboard, modifier = Modifier.padding(8.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colorScheme.onPrimary)
                }
            }
        }

        item {
            StatusCenterCard(
                queuedActionsLabel = methodologyStatus,
                syncLabel = mlBoundaryStatus,
                planRangeLabel = localFirstStatus,
                nextReminderLabel = nextReviewStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("methodology_status_center_card")
            )
        }

        item {
            IpoCard(
                step = "01",
                stageLabel = "Inputs",
                title = "Profile + Pantry Inputs",
                description = "Planning starts from your saved profile, pantry, goals, budget, allergies, and exclusions.",
                items = listOf(
                    "Profile: age, weight, height, activity level, insulin level, goals, and symptoms",
                    "Hard rules: allergies, exclusions, budget caps when set, and max cooking time",
                    "Local pantry and household context stay device-first for shopping guidance"
                ),
                color = colorScheme.primary,
                icon = Icons.Filled.Info
            )
        }

        item {
            MethodologyConnector(label = "Only feasible recipes move forward")
        }

        item {
            IpoCard(
                step = "02",
                stageLabel = "Rules first",
                title = "Deterministic Filtering",
                description = "Recipes are screened before optimization so infeasible options never reach the final planner.",
                items = listOf(
                    "Hard rules remove forbidden, unsafe, or infeasible meals first",
                    "Allergy families, exclusions, pantry feasibility, and cook-time limits are enforced here",
                    "This stage remains explainable and repeatable offline"
                ),
                color = colorScheme.secondary,
                icon = Icons.Filled.CheckCircle
            )
        }

        item {
            MethodologyConnector(label = "The solver builds the week from filtered options")
        }

        item {
            IpoCard(
                step = "03",
                stageLabel = "Optimization",
                title = "Deterministic Optimization",
                description = "A deterministic solver chooses the final week from the feasible meal candidates.",
                items = listOf(
                    "Balances calories, macros, variety, symptoms, and planning-priority targets",
                    "ML can assist ranking candidates, but never overrides hard constraints",
                    "Household size scales shopping outputs while nutrition targets remain per person"
                ),
                color = colorScheme.primary,
                icon = Icons.Filled.Settings
            )
        }

        item {
            MethodologyConnector(label = "Outputs stay explainable and recoverable")
        }

        item {
            IpoCard(
                step = "04",
                stageLabel = "Outputs",
                title = "Explainable Outputs",
                description = "You receive a weekly plan, grocery guidance, and nutrition details with clear fallback messaging.",
                items = listOf(
                    "Weekly meals, exclusion summaries, and pantry-aware shopping guidance",
                    "Recipe details and nutrition totals stay visible for review",
                    "No-safe-plan cases return actionable adjustments instead of silent failure"
                ),
                color = colorScheme.secondary,
                icon = Icons.Filled.RestaurantMenu
            )
        }

        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(UiSpacingTokens.CardContentPadding),
                    verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.CardContentGap)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MethodologyPill(
                            text = "Decision support",
                            emphasized = true
                        )
                        MethodologyPill(
                            text = "Internal only",
                            emphasized = false
                        )
                    }
                    Text(
                        text = "Decision-support boundary",
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
                Text("Back to Help & Tools", fontWeight = FontWeight.Bold)
            }
        }

        item { Spacer(Modifier.height(UiSpacingTokens.CardContentPadding)) }
    }
}

@Composable
private fun IpoCard(
    step: String,
    stageLabel: String,
    title: String,
    description: String,
    items: List<String>,
    color: Color,
    icon: ImageVector
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.16f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(UiSpacingTokens.CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionHeaderGap)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.12f),
                    contentColor = color
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MethodologyPill(
                            text = "Stage $step",
                            emphasized = true
                        )
                        MethodologyPill(
                            text = stageLabel,
                            emphasized = false
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = color
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(8.dp)
                            .background(color, CircleShape)
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodologyConnector(
    label: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        MethodologyPill(
            text = label,
            emphasized = false
        )
    }
}

@Composable
private fun MethodologyPill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (emphasized) {
            colorScheme.primary.copy(alpha = 0.10f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.70f)
        },
        contentColor = if (emphasized) {
            colorScheme.primary
        } else {
            colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
