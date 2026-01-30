package com.pcosina.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader

@Composable
fun MealPlanScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by mealPlanViewModel.uiState.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()

    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }

    when (val state = uiState) {
        is MealPlanUiState.Idle -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { mealPlanViewModel.generateMealPlan(userProfile) }) {
                    Text("Generate My Optimized Plan")
                }
            }
        }
        is MealPlanUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFC6B7D))
                    Spacer(Modifier.height(16.dp))
                    Text("MILP Engine is optimizing your plan...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is MealPlanUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { mealPlanViewModel.generateMealPlan(userProfile) }) {
                        Text("Retry")
                    }
                }
            }
        }
        is MealPlanUiState.Success -> {
            val plan = state.response
            val selectedDay = plan.days[selectedDayIndex]

            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    GradientHeader(
                        title = "Your Optimized Plan",
                        subtitle = plan.weekLabel,
                    )
                }

                // Day selector chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        plan.days.forEachIndexed { index, day ->
                            val selected = index == selectedDayIndex
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) Color.White
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
                                modifier = Modifier.weight(1f),
                                onClick = { selectedDayIndex = index },
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = day.dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) Color(0xFFFC6B7D)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    tint = Color(0xFFFC6B7D),
                                )
                                Column {
                                    Text(
                                        text = "${selectedDay.dayLabel}'s Total",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = "MILP Optimized",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    text = "${selectedDay.totalCalories} cal",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Daily Sum",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Meals list
                items(selectedDay.meals) { plannedMeal ->
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
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("🍽️", style = MaterialTheme.typography.titleLarge)
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = plannedMeal.mealLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = plannedMeal.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
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
                                    onClick = { onRecipeClick(plannedMeal.recipeId) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFC6B7D),
                                    ),
                                ) {
                                    Text("View Recipe")
                                }
                                OutlinedButton(
                                    onClick = { /* no-op */ },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Regenerate")
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Optimization Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = plan.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
