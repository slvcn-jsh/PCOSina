package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.MacroCircularGauge
import com.pcosina.app.ui.components.StatCard

@Composable
fun DashboardScreen(
    userViewModel: UserViewModel,
    authViewModel: AuthViewModel,
    mealPlanViewModel: MealPlanViewModel,
    onRecipeClick: (String) -> Unit,
    onViewPlan: () -> Unit = {},
    onViewIpo: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onFeedback: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    val dailyCalorieTarget = userViewModel.dailyCalorieTarget
    val mealPlanState by mealPlanViewModel.uiState.collectAsState()
    val metrics by mealPlanViewModel.planMetrics.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val showMarkersInfo = rememberSaveable { mutableStateOf(false) }
    val showTargetInfo = rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "Hello, ${profile.displayName.ifBlank { "Warrior" }}! 👋",
                    subtitle = "Scientific Nutrition for PCOS",
                    containerHeight = 200,
                    trailing = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                            )
                        }
                    },
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(title = "Goal", value = if (profile.goal.contains("Weight Loss")) "Loss" else "Control", subtitle = "Focus", modifier = Modifier.weight(1f))
                StatCard(title = "Target", value = dailyCalorieTarget.toString(), subtitle = "kcal/day", modifier = Modifier.weight(1f))
                StatCard(title = "Current", value = "${profile.weightKg}", subtitle = "kg", modifier = Modifier.weight(1f))
            }
            TextButton(onClick = { showTargetInfo.value = true }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("How target kcal/day is computed?")
            }
        }

        item {
            val hasPlan = mealPlanState is MealPlanUiState.Success
            
            Row(
                modifier = Modifier.padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Metabolic Markers",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
                    color = colorScheme.secondary,
                    modifier = Modifier.alpha(if (hasPlan) 1f else 0.5f)
                )
                IconButton(onClick = { showMarkersInfo.value = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "Info", tint = colorScheme.onSurfaceVariant)
                }
            }
            
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().alpha(if (hasPlan) 1f else 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MacroCircularGauge(label = "Protein", currentValue = metrics.avgProtein, targetValue = 85, color = colorScheme.primary, modifier = Modifier.weight(1f))
                    MacroCircularGauge(label = "Carbs", currentValue = metrics.avgCarbs, targetValue = 220, color = colorScheme.tertiary, modifier = Modifier.weight(1f))
                    MacroCircularGauge(label = "Fiber", currentValue = metrics.avgFiber, targetValue = 25, color = colorScheme.secondary, modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            val planState = mealPlanState
            val currentMeal = if (planState is MealPlanUiState.Success) {
                planState.response.days.firstOrNull()?.meals?.firstOrNull()
                        } else null

            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "NEXT OPTIMIZED MEAL", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp), color = colorScheme.primary)
                    Text(text = currentMeal?.title ?: "No Active Plan", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    
                    if (currentMeal == null) {
                        Text(text = "Tap to generate your scientifically balanced MILP plan.", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = { if (currentMeal != null) onRecipeClick(currentMeal.recipeId) else onViewPlan() },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (currentMeal != null) "View Cooking Steps" else "Initialize Engine")
                    }
                }
            }
        }

        item {
            Card(
                onClick = onViewIpo,
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(colorScheme.primary).padding(12.dp), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = colorScheme.onPrimary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(text = "The PCOSINA Methodology", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.onSurface)
                        Text(text = "Learn how our MILP solver works", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Card(
                onClick = onFeedback,
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(colorScheme.secondary).padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Filled.Email, contentDescription = null, tint = colorScheme.onSecondary)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(text = "Send Feedback", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colorScheme.secondary)
                        Text(text = "Help us improve PCOSINA", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }

    if (showMarkersInfo.value) {
        AlertDialog(
            onDismissRequest = { showMarkersInfo.value = false },
            confirmButton = {
                TextButton(onClick = { showMarkersInfo.value = false }) { Text("Got it") }
            },
            title = { Text("Live Metabolic Markers") },
            text = {
                Text(
                    "These values are computed from your weekly plan averages (21 meals). " +
                    "If you log meal completion in Progress, the app uses completed meals for these averages."
                )
            }
        )
    }

    if (showTargetInfo.value) {
        val w = if (profile.weightKg > 0) profile.weightKg else 60
        val h = if (profile.heightCm > 0) profile.heightCm else 155
        val a = if (profile.age > 0) profile.age else 25
        val bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
        val multiplier = when (profile.activityLevel) {
            "Sedentary" -> 1.2
            "Lightly Active" -> 1.375
            "Moderately Active" -> 1.55
            "Very Active" -> 1.725
            else -> 1.375
        }
        val maintenance = (bmr * multiplier).toInt()
        val adjusted = if (profile.goal.contains("Weight Loss", true)) maintenance - 500 else maintenance
        AlertDialog(
            onDismissRequest = { showTargetInfo.value = false },
            confirmButton = {
                TextButton(onClick = { showTargetInfo.value = false }) { Text("Got it") }
            },
            title = { Text("Target kcal/day") },
            text = {
                Text(
                    "Computed using Mifflin-St Jeor (female):\n" +
                    "BMR = 10×$w + 6.25×$h − 5×$a − 161 = ${bmr.toInt()}.\n" +
                    "Activity multiplier (${profile.activityLevel}) = $multiplier.\n" +
                    "Maintenance ≈ $maintenance kcal/day.\n" +
                    "Goal adjustment → $adjusted kcal/day."
                )
            }
        )
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    composed {
        this.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    }
