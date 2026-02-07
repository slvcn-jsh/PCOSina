package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.data.model.DummyData
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.RecipeDetailsUiState

@Composable
fun RecipeDetailsScreen(
    recipeId: String,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    onBack: () -> Unit,
    onAddToGrocery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Observe the centralized state from the ViewModel
    val state by mealPlanViewModel.recipeState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    // Trigger the fetch when the screen opens or ID changes
    LaunchedEffect(recipeId) {
        mealPlanViewModel.loadRecipeDetails(recipeId)
    }

    when (state) {
        is RecipeDetailsUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        }
        is RecipeDetailsUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = (state as RecipeDetailsUiState.Error).message, color = colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Go Back") }
                }
            }
        }
        is RecipeDetailsUiState.Success -> {
            val r = (state as RecipeDetailsUiState.Success).recipe
            
            LazyColumn(
                modifier = modifier.fillMaxSize().background(colorScheme.background),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Header with Title & Emoji
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(colorScheme.primary, colorScheme.tertiary)
                                )
                            )
                            .padding(16.dp),
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colorScheme.onPrimary)
                        }
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🥗", fontSize = 48.sp)
                            Text(
                                text = r.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.onPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 2. Meal Type & Time Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = r.mealType?.uppercase() ?: "HEALTHY",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                color = colorScheme.primary,
                            )
                            Text(text = r.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(text = "⏱ ${r.minutes ?: 20} min", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                                Text(text = "👤 1 serving", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 3. Nutritional Information Card
                item {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = "Nutritional Facts", style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                NutrientTile("Calories", "${r.calories ?: 0}", colorScheme.onSurface)
                                NutrientTile("Protein", "${r.proteinGrams ?: 0}g", colorScheme.primary)
                                NutrientTile("Carbs", "${r.carbsGrams ?: 0}g", colorScheme.primary)
                                NutrientTile("Fiber", "${r.fiberGrams ?: 0}g", colorScheme.primary)
                            }
                        }
                    }
                }

                // 4. Ingredients List
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Ingredients", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                r.ingredients.forEach { ing ->
                                    IngredientRow(ing.name, ing.quantity)
                                }
                            }
                        }
                        Button(
                            onClick = {
                                val items = r.ingredients.map { 
                                    DummyData.GroceryItem(it.name, it.quantity, 0, "Needed") 
                                }
                                groceryViewModel.addItems(items)
                                onAddToGrocery()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Text("Add to Grocery List", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // 5. Cooking Steps
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Cooking Steps", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        r.steps.forEachIndexed { index, step ->
                            InstructionRow(index + 1, step)
                        }
                    }
                }
                
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        else -> {
            // Idle state - can show placeholder or empty screen
            Box(modifier = modifier.fillMaxSize())
        }
    }
}

@Composable
private fun NutrientTile(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IngredientRow(name: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        Text(text = amount, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InstructionRow(step: Int, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = step.toString(), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.width(16.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
