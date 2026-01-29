package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun RecipeDetailsScreen(
    recipeId: String,
    onBackToMealPlan: () -> Unit,
    onAddToGrocery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // This screen is currently specialized for the
    // "Grilled Tilapia with Pinakbet" recipe (id = "pinakbet").
    if (recipeId != "pinakbet") {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = onBackToMealPlan) {
                Text("Back to Meal Plan")
            }
        }
        return
    }

    val totalIngredients = 9
    val inPantry = 6
    val notInPantry = totalIngredients - inPantry
    val pantryPercent = inPantry.toFloat() / totalIngredients.toFloat()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Custom gradient header with back + emoji
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(192.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0ABF6A),
                                Color(0xFF2D9CDB),
                            )
                        )
                    )
                    .padding(16.dp),
            ) {
                IconButton(
                    onClick = onBackToMealPlan,
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "🐟", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "Grilled Tilapia with Pinakbet",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        item {
            // Recipe header card
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
                        text = "Lunch",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Grilled Tilapia with Pinakbet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "35 min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "2 servings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Easy",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            // Nutrition card
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
                        text = "Nutritional Information",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        NutrientTile(label = "Calories", value = "420")
                        NutrientTile(label = "Protein", value = "35g")
                        NutrientTile(label = "Carbs", value = "45g")
                        NutrientTile(label = "Fats", value = "15g")
                    }
                    Text(
                        text = "Fiber: 12g",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Glycemic Index: Low GI",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            // Pantry usage card
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
                        Column {
                            Text(
                                text = "Pantry Usage",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "$inPantry of $totalIngredients ingredients already in pantry",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "${(pantryPercent * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { pantryPercent },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0ABF6A),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }

        item {
            // Ingredients card
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
                        text = "Ingredients",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IngredientRow("Tilapia fillet", "250g", inPantry = false)
                    IngredientRow("Eggplant", "2 pcs", inPantry = true)
                    IngredientRow("Sitaw (string beans)", "100g", inPantry = false)
                    IngredientRow("Kalabasa (squash)", "150g", inPantry = true)
                    IngredientRow("Tomatoes", "2 pcs", inPantry = false)
                    IngredientRow("Onion", "1 pc", inPantry = true)
                    IngredientRow("Garlic", "3 cloves", inPantry = true)
                    IngredientRow("Bagoong (shrimp paste)", "2 tbsp", inPantry = true)
                    IngredientRow("Cooking oil", "1 tbsp", inPantry = true)

                    if (notInPantry > 0) {
                        Button(
                            onClick = onAddToGrocery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("Add $notInPantry items to Grocery List")
                        }
                    }
                }
            }
        }

        item {
            // Instructions card
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
                        text = "Cooking Instructions",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    InstructionRow(1, "Heat oil in a pan over medium heat. Sauté garlic and onion until fragrant.")
                    InstructionRow(2, "Add tomatoes and cook until softened.")
                    InstructionRow(3, "Add bagoong and stir well.")
                    InstructionRow(4, "Add kalabasa and cook for 5 minutes.")
                    InstructionRow(5, "Add eggplant and sitaw. Cover and simmer for 8-10 minutes.")
                    InstructionRow(6, "Meanwhile, grill tilapia until cooked through and slightly charred.")
                    InstructionRow(7, "Serve pinakbet with grilled tilapia on the side.")
                    InstructionRow(8, "Enjoy with a small portion of brown rice (optional).")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { /* mark prepared - no-op for now */ },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("Mark as Prepared ✓")
                        }
                        Button(
                            onClick = { /* no-op */ },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(),
                        ) {
                            Text("Find Similar Recipes")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun NutrientTile(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IngredientRow(
    name: String,
    amount: String,
    inPantry: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (inPantry) "✔" else "○"
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyLarge,
            color = if (inPantry) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstructionRow(
    step: Int,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .width(28.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0ABF6A),
                            Color(0xFF2D9CDB),
                        )
                    ),
                    shape = MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.labelLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

