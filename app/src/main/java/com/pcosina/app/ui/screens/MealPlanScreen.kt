package com.pcosina.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader

private data class UiMeal(
    val id: String,
    val emoji: String,
    val type: String,
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fats: Int,
)

private data class DayMeals(
    val key: String,           // "Monday"
    val labelShort: String,    // "Mon"
    val totalCalories: Int,
    val meals: List<UiMeal>,
)

private val mealPlanDays: List<DayMeals> = listOf(
    DayMeals(
        key = "Monday",
        labelShort = "Mon",
        totalCalories = 1250,
        meals = listOf(
            UiMeal("m1", "🍚", "Breakfast", "Champorado with Tuyo", 350, 20, 40, 12),
            UiMeal("pinakbet", "🐟", "Lunch", "Grilled Tilapia with Pinakbet", 420, 35, 45, 15),
            UiMeal("m3", "🍲", "Dinner", "Chicken Tinola", 380, 30, 35, 14),
            UiMeal("m4", "🥭", "Snack", "Fresh Mango Slices", 100, 1, 25, 0),
        ),
    ),
    DayMeals(
        key = "Tuesday",
        labelShort = "Tue",
        totalCalories = 1190,
        meals = listOf(
            UiMeal("m5", "🥣", "Breakfast", "Oatmeal with Banana", 320, 12, 55, 8),
            UiMeal("m6", "🥘", "Lunch", "Sinigang na Baboy (Lean)", 400, 28, 40, 16),
            UiMeal("m7", "🐠", "Dinner", "Grilled Bangus", 360, 32, 25, 18),
            UiMeal("m8", "🍠", "Snack", "Boiled Kamote", 110, 2, 26, 0),
        ),
    ),
    DayMeals(
        key = "Wednesday",
        labelShort = "Wed",
        totalCalories = 1120,
        meals = listOf(
            UiMeal("m9", "🍆", "Breakfast", "Tortang Talong", 280, 15, 30, 12),
            UiMeal("m10", "🍗", "Lunch", "Adobong Manok", 410, 33, 38, 16),
            UiMeal("m11", "🫘", "Dinner", "Monggo Guisado", 340, 18, 48, 10),
            UiMeal("m12", "🍍", "Snack", "Pineapple Chunks", 90, 1, 22, 0),
        ),
    ),
)

@Composable
fun MealPlanScreen(
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDayKey by rememberSaveable { mutableStateOf("Monday") }
    val selectedDay = mealPlanDays.first { it.key == selectedDayKey }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GradientHeader(
                title = "Your Meal Plan",
                subtitle = "Week of Jan 20-26, 2025",
            )
        }

        // Day selector chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                mealPlanDays.forEach { day ->
                    val selected = day.key == selectedDayKey
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) Color.White
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
                        modifier = Modifier.weight(1f),
                        onClick = { selectedDayKey = day.key },
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = day.labelShort,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = day.labelShort,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Daily summary card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        androidx.compose.foundation.layout.Column {
                            Text(
                                text = "${selectedDay.key}'s Total",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Target: 1,520 cal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.End,
                    ) {
                        Text(
                            text = "${selectedDay.totalCalories} cal",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Conditional notice (only Monday)
        if (selectedDayKey == "Monday") {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Week 1 Focus",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Gradually introducing more vegetables and fiber-rich foods. We're starting with familiar Filipino dishes!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Meals list
        items(selectedDay.meals, key = { it.id }) { meal ->
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    Text(meal.emoji, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                            androidx.compose.foundation.layout.Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = meal.type,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = meal.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${meal.calories} cal",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "P: ${meal.protein}g",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "C: ${meal.carbs}g",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "F: ${meal.fats}g",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onRecipeClick(meal.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("View Recipe")
                        }
                        OutlinedButton(
                            onClick = { /* no-op */ },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Alternatives")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Pinggang Pinoy Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Our meals follow the Filipino healthy plate model: ¼ Go (carbs), ¼ Grow (protein), ½ Glow (vegetables & fruits)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

