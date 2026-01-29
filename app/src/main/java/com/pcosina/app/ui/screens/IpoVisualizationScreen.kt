package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader

@Composable
fun IpoVisualizationScreen(
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GradientHeader(
                title = "How PCOSINA Works",
                subtitle = "Input → Process → Output",
                colors = listOf(
                    Color(0xFF6A11CB),
                    Color(0xFF2575FC),
                ),
            )
        }

        // Overview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Personalized PCOS Meal Planning System",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "PCOSINA uses your personal information, preferences, and health goals to create customized meal plans that support your PCOS management journey.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // INPUT
        item {
            NumberedSection(
                numberColor = Color(0xFF2D9CDB),
                number = "1",
                title = "INPUT",
                borderColor = Color(0xFF2D9CDB),
            ) {
                BulletBlock(
                    heading = "User Profile",
                    body = "Age, weight, height, activity level, insulin resistance level, PCOS symptoms, comorbidities, fertility goals",
                )
                BulletBlock(
                    heading = "Health Goals",
                    body = "Weight loss, PCOS symptom management, general health improvement",
                )
                BulletBlock(
                    heading = "Preferences & Constraints",
                    body = "Dietary restrictions, food allergies, religious restrictions, pantry inventory, weekly budget",
                )
                BulletBlock(
                    heading = "Filipino Recipe Database",
                    body = "Curated collection of PCOS-friendly Filipino recipes with nutritional information",
                )
            }
        }

        // Simple arrow separator
        item {
            Text(
                text = "↓",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // PROCESS
        item {
            NumberedSection(
                numberColor = Color(0xFF0ABF6A),
                number = "2",
                title = "PROCESS",
                borderColor = Color(0xFF0ABF6A),
            ) {
                BulletBlock(
                    heading = "Preference Analysis",
                    body = "Analyzes your dietary restrictions, cultural preferences, and food aversions to filter suitable recipes",
                )
                BulletBlock(
                    heading = "Adherence-Aware Planning",
                    body = "Creates gradual diet transitions (e.g., slowly introducing vegetables) to improve long-term adherence",
                )
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "MILP Optimization Engine",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    )
                    Text(
                        text = "Mixed Integer Linear Programming algorithm optimizes meal selection based on:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• Nutritional targets (macros, fiber, glycemic index)\n" +
                            "• Budget constraints\n" +
                            "• Pantry utilization\n" +
                            "• Meal variety and balance\n" +
                            "• Pinggang Pinoy proportions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Text(
                            text = "How it works: The optimization algorithm considers thousands of possible meal combinations and selects the best 7-day plan that meets your goals while staying within your budget and using pantry items efficiently.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }

        // OUTPUT
        item {
            NumberedSection(
                numberColor = Color(0xFF6A11CB),
                number = "3",
                title = "OUTPUT",
                borderColor = Color(0xFF6A11CB),
            ) {
                BulletBlock(
                    heading = "Weekly Meal Plan",
                    body = "7-day personalized meal plan with breakfast, lunch, dinner, and snacks featuring Filipino recipes",
                )
                BulletBlock(
                    heading = "Auto-Generated Grocery List",
                    body = "Shopping list of ingredients not in your pantry, categorized and budget-optimized with estimated costs",
                )
                BulletBlock(
                    heading = "Nutritional Transparency",
                    body = "Detailed breakdown of calories, macros, fiber, and glycemic index for each meal and daily totals",
                )
                BulletBlock(
                    heading = "Lifestyle Recommendations",
                    body = "Physical activity tips, gradual dietary change suggestions, and motivational support for PCOS management",
                )
                BulletBlock(
                    heading = "Progress Tracking",
                    body = "Visual charts showing adherence rates, macro intake, symptom improvements, and achievement milestones",
                )
            }
        }

        // Key Benefits
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "✨ Key Benefits",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "• Culturally Filipino: All recipes respect Filipino food culture and traditions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• PCOS-Optimized: Focuses on low-GI foods and balanced macros for insulin management",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• Budget-Friendly: Maximizes pantry use and stays within your weekly budget",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• Adherence-First: Gradual transitions make healthy eating sustainable long-term",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // CTA
        item {
            Button(
                onClick = onBackToDashboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = Color.White,
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF6A11CB),
                                    Color(0xFF2575FC),
                                )
                            )
                        ),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Text("Back to Dashboard")
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun NumberedSection(
    numberColor: Color,
    number: String,
    title: String,
    borderColor: Color,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = numberColor),
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Card(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                content()
            }
        }
    }
}

@Composable
private fun BulletBlock(
    heading: String,
    body: String,
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = heading,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            ),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

