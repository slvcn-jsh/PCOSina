package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.google.firebase.analytics.FirebaseAnalytics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    onRecipeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by mealPlanViewModel.uiState.collectAsState()
    val userProfile by userViewModel.userProfile.collectAsState()
    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val analytics = FirebaseAnalytics.getInstance(context)
    val isOnline = remember { mutableStateOf(isNetworkAvailable(context)) }
    LaunchedEffect(Unit) {
        isOnline.value = isNetworkAvailable(context)
    }
    val colorScheme = MaterialTheme.colorScheme
    
    // Track if we are currently extracting ingredients
    var isSyncingGroceries by remember { mutableStateOf(false) }
    
    when (val state = uiState) {
        is MealPlanUiState.Idle -> {
            Box(modifier = modifier.fillMaxSize().background(colorScheme.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!isOnline.value) {
                        Text(
                            text = "Offline. Connect to the internet to generate your first plan.",
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    Button(
                        onClick = {
                            isOnline.value = isNetworkAvailable(context)
                            if (!isOnline.value) {
                                mealPlanViewModel.showError("Offline. Connect to the internet to generate a new plan.")
                                return@Button
                            }
                            analytics.logEvent("generate_plan", null)
                            mealPlanViewModel.generateMealPlan(userProfile)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.height(56.dp).padding(horizontal = 32.dp)
                    ) {
                        Text(
                            if (isOnline.value) "Generate My Optimized Plan" else "Generate (Internet required)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        is MealPlanUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize().background(colorScheme.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("MILP Engine is optimizing...", color = colorScheme.secondary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "First run can take up to ~30s. Please keep the app open.",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
        is MealPlanUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    if (!isOnline.value) {
                        Text(
                            text = "You are offline. Saved plans will still be available.",
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Button(onClick = {
                        isOnline.value = isNetworkAvailable(context)
                        if (!isOnline.value) {
                            mealPlanViewModel.showError("Offline. Connect to the internet to generate a new plan.")
                            return@Button
                        }
                        mealPlanViewModel.generateMealPlan(userProfile)
                    }) { Text("Retry") }
                }
            }
        }
        is MealPlanUiState.Success -> {
            val plan = state.response
            val selectedDay = plan.days[selectedDayIndex]

            LazyColumn(
                modifier = modifier.fillMaxSize().background(colorScheme.background),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    if (!isOnline.value) {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Text(
                                text = "Offline mode: showing your last saved plan.",
                                modifier = Modifier.padding(14.dp),
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    GradientHeader(
                        title = "PCOS-Optimized Plan",
                        subtitle = "Target: ${userViewModel.dailyCalorieTarget} kcal/day",
                        containerHeight = 180
                    )
                }

                // 1. Generate Grocery List Action (FIXED with Feedback)
                item {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Ready to shop?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Text("Consolidate all 21 meals", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { 
                                    isOnline.value = isNetworkAvailable(context)
                                    if (!isOnline.value) {
                                        mealPlanViewModel.showError("Offline. Sync requires internet for recipe details.")
                                        return@Button
                                    }
                                    analytics.logEvent("sync_groceries", null)
                                    isSyncingGroceries = true
                                    mealPlanViewModel.extractAllGroceryItems { items ->
                                        groceryViewModel.addItems(items)
                                        isSyncingGroceries = false
                                    }
                                },
                                enabled = !isSyncingGroceries && isOnline.value,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                            ) {
                                if (isSyncingGroceries) {
                                    CircularProgressIndicator(color = colorScheme.onPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (isOnline.value) "Sync" else "Sync (Internet required)")
                                }
                            }
                        }
                    }
                }

                // 2. Day selector chips (FIXED: Now Horizontal Scrollable)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        plan.days.forEachIndexed { index, day ->
                            val selected = index == selectedDayIndex
                            FilterChip(
                                selected = selected,
                                onClick = { selectedDayIndex = index },
                                label = { Text(day.dayLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary,
                                    selectedLabelColor = colorScheme.onPrimary,
                                    labelColor = colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                // 3. Daily summary card
                item {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(imageVector = Icons.Filled.CalendarMonth, contentDescription = null, tint = colorScheme.primary)
                                Column {
                                    Text(text = "${selectedDay.dayLabel}'s Total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Text(text = "MILP Validated", style = MaterialTheme.typography.bodySmall, color = colorScheme.primary)
                                }
                            }
                            Text(text = "${selectedDay.totalCalories} kcal", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = colorScheme.secondary))
                        }
                    }
                }

                // 4. Meals list
                items(selectedDay.meals) { plannedMeal ->
                    Card(
                        onClick = { onRecipeClick(plannedMeal.recipeId) },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).background(colorScheme.surfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = if(plannedMeal.mealLabel == "Breakfast") "🍳" else if(plannedMeal.mealLabel == "Lunch") "🍱" else "🥘", fontSize = 24.sp)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = plannedMeal.mealLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                                Text(text = plannedMeal.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
