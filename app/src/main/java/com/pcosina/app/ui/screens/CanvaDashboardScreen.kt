package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.PlannedMealDto
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.navigation.Routes
import com.pcosina.app.ui.components.CanvaBrandBar
import com.pcosina.app.ui.components.CanvaCard
import com.pcosina.app.ui.components.CanvaGradientPanel
import com.pcosina.app.ui.components.CanvaHeroCard
import com.pcosina.app.ui.components.CanvaPrimaryButton
import com.pcosina.app.ui.components.CanvaSectionTitle
import com.pcosina.app.ui.components.canvaMealPalette
import com.pcosina.app.ui.theme.CanvaTokens
import com.pcosina.app.ui.util.GoalOption
import com.pcosina.app.ui.util.goalShoppingTips
import com.pcosina.app.ui.util.parseGoalOptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private data class DashboardMealCardModel(
    val meal: PlannedMealDto,
    val calories: Int?,
    val color: Color,
    val labelColor: Color,
)

private enum class DashboardWeekState {
    Done,
    InProgress,
    FutureLocked,
    Pending,
}

private data class DashboardWeekNode(
    val label: String,
    val state: DashboardWeekState,
    val percent: Int,
)

@Composable
fun CanvaDashboardScreen(
    userViewModel: UserViewModel,
    authViewModel: AuthViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    onRecipeClick: (String, String?) -> Unit,
    onViewPlan: () -> Unit = {},
    onOpenMoreTools: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    val mealPlanState by mealPlanViewModel.uiState.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()

    val today = LocalDate.now()
    val todayLabel = today.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
    val currentPlan = (mealPlanState as? MealPlanUiState.Success)?.response
    val todayPlan = remember(currentPlan, todayLabel) {
        currentPlan?.days?.firstOrNull { it.dayLabel.equals(todayLabel, ignoreCase = true) }
    }
    val todayMeals = todayPlan?.meals.orEmpty()
    val mealCalories = remember { mutableStateMapOf<String, Int?>() }

    LaunchedEffect(todayMeals.map { it.recipeId }.joinToString(",")) {
        todayMeals.forEach { meal ->
            if (mealCalories.containsKey(meal.recipeId)) return@forEach
            mealPlanViewModel.getRecipeDetails(meal.recipeId)
                .onSuccess { detail -> mealCalories[meal.recipeId] = detail.calories }
                .onFailure { mealCalories[meal.recipeId] = null }
        }
    }

    val goalSelections = remember(profile.goal) { parseGoalOptions(profile.goal) }
    val shoppingTips = remember(profile.goal, profile.householdSize) {
        goalShoppingTips(profile.goal, profile.householdSize)
    }
    val weekStart = remember(activeWeekStart) {
        activeWeekStart?.let {
            runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        } ?: today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
    }
    val weekNodes = remember(currentPlan, logs, weekStart, today) {
        buildDashboardWeekNodes(currentPlan, logs, weekStart, today)
    }
    val displayName = profile.displayName.ifBlank { "there" }
        .substringBefore(" ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
    val mealCards = remember(todayMeals, mealCalories.toMap()) {
        todayMeals.map { meal ->
            val (cardColor, labelColor) = canvaMealPalette(meal.mealLabel)
            DashboardMealCardModel(
                meal = meal,
                calories = mealCalories[meal.recipeId],
                color = cardColor,
                labelColor = labelColor,
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CanvaTokens.CanvasBackground)
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            CanvaBrandBar(
                onSettings = onNavigateToSettings,
                onNotifications = onOpenMoreTools,
            )
        }

        item {
            CanvaHeroCard(
                title = "Welcome, $displayName!",
                subtitle = "You're doing well today! Ready for your next goal?",
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = today.month.name.take(3),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = CanvaTokens.HeadlineMaroon,
                            ),
                        )
                        Text(
                            text = today.dayOfMonth.toString(),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = CanvaTokens.HeadlineMaroon,
                            ),
                        )
                        Text(
                            text = today.dayOfWeek.name.lowercase(Locale.ENGLISH)
                                .replaceFirstChar { it.titlecase(Locale.ENGLISH) },
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = CanvaTokens.HeadlineMaroon,
                            ),
                        )
                    }
                },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CanvaGoalsCard(
                    selections = goalSelections,
                    onEdit = onNavigateToSettings,
                    modifier = Modifier.weight(1.25f),
                )
                CanvaTipsCard(
                    tip = shoppingTips.firstOrNull() ?: "Fresh market prices change week to week, so use totals as a guide.",
                    modifier = Modifier.weight(0.85f),
                )
            }
        }

        item {
            CanvaSectionTitle(
                icon = Icons.Filled.RestaurantMenu,
                title = "Your Meal Plan for Today",
                subtitle = "Good food, good mood.",
            )
        }

        if (mealCards.isEmpty()) {
            item {
                CanvaCard {
                    Text(
                        text = "No meals are saved for today yet. Generate a weekly plan to unlock today’s cards.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CanvaTokens.Ink,
                    )
                    CanvaPrimaryButton(
                        text = "Go to Plan",
                        onClick = { onNavigateToRoute(Routes.MealPlan) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(mealCards) { model ->
                        CanvaTodayMealCard(
                            model = model,
                            onClick = { onRecipeClick(model.meal.recipeId, model.meal.mealLabel) },
                        )
                    }
                }
            }
        }

        item {
            CanvaSectionTitle(
                icon = Icons.Filled.CalendarMonth,
                title = "Your Weekly Progress",
                subtitle = "Consistency is key. You're doing amazing!",
            )
        }

        item {
            CanvaWeekProgressCard(nodes = weekNodes)
        }

        item {
            CanvaGradientPanel(brush = CanvaTokens.MainCtaGradient) {
                Text(
                    text = "Ready to start your meal plan?",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = CanvaTokens.HeadlineMaroon,
                    ),
                )
                Text(
                    text = if (currentPlan == null) {
                        "Let's build a plan that fits your goals, pantry, and weekly budget."
                    } else {
                        "Open your plan to review the full week, swap meals, and move to groceries."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = CanvaTokens.HeadlineMaroon,
                )
                CanvaPrimaryButton(
                    text = "Go to Plan",
                    onClick = { onNavigateToRoute(Routes.MealPlan) },
                )
            }
        }
    }
}

@Composable
private fun CanvaGoalsCard(
    selections: Set<GoalOption>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CanvaCard(
        modifier = modifier,
        containerColor = CanvaTokens.PanelPink,
        contentColor = CanvaTokens.HeadlineMaroon,
        borderColor = CanvaTokens.Outline,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = CanvaTokens.AccentPinkStrong,
                    shadowElevation = 8.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
                Text(
                    text = "Your Goals",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = CanvaTokens.HeadlineMaroon,
                )
            }

            Surface(
                modifier = Modifier.clickable(onClick = onEdit),
                shape = RoundedCornerShape(999.dp),
                color = Color.White,
                border = BorderStroke(1.dp, CanvaTokens.SoftOutline),
            ) {
                Text(
                    text = "Edit",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = CanvaTokens.Ink,
                )
            }
        }

        GoalOption.entries.forEach { option ->
            val selected = selections.contains(option)
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = if (selected) 0.98f else 0.7f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = CircleShape,
                            color = if (selected) CanvaTokens.AccentPinkStrong else CanvaTokens.ProgressTrack,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                            color = CanvaTokens.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = CanvaTokens.AccentPinkStrong.copy(alpha = 0.95f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvaTipsCard(
    tip: String,
    modifier: Modifier = Modifier,
) {
    CanvaCard(
        modifier = modifier,
        containerColor = CanvaTokens.TipsBrown,
        contentColor = Color.White,
        borderColor = CanvaTokens.Outline,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFFFF1A6),
                    )
                }
            }
            Text(
                text = "Daily Tips",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF5A201B),
            )
        }

        Text(
            text = tip,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Color(0xFF2A1815),
        )
    }
}

@Composable
private fun CanvaTodayMealCard(
    model: DashboardMealCardModel,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(width = 196.dp, height = 218.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = model.color,
        border = BorderStroke(2.dp, CanvaTokens.Outline),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.35f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.RestaurantMenu,
                            contentDescription = null,
                            tint = model.labelColor,
                        )
                    }
                }
                Text(
                    text = model.meal.mealLabel,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = model.labelColor,
                    maxLines = 1,
                )
                Text(
                    text = model.meal.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = CanvaTokens.Ink,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = model.calories?.toString() ?: "—",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.Black,
                )
                Text(
                    text = "kcal",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                    color = CanvaTokens.Ink,
                )
            }
        }
    }
}

@Composable
private fun CanvaWeekProgressCard(
    nodes: List<DashboardWeekNode>,
) {
    CanvaCard {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(nodes) { node ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(58.dp),
                        shape = CircleShape,
                        color = when (node.state) {
                            DashboardWeekState.Done,
                            DashboardWeekState.InProgress -> CanvaTokens.PanelPink
                            DashboardWeekState.FutureLocked -> Color(0xFFEAEAEA)
                            DashboardWeekState.Pending -> Color.White
                        },
                        border = BorderStroke(
                            2.dp,
                            when (node.state) {
                                DashboardWeekState.Done,
                                DashboardWeekState.InProgress -> CanvaTokens.AccentPinkStrong
                                DashboardWeekState.FutureLocked -> Color(0xFF7A7A7A)
                                DashboardWeekState.Pending -> CanvaTokens.SoftOutline
                            }
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (node.state) {
                                DashboardWeekState.Done -> Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = CanvaTokens.Ink,
                                    modifier = Modifier.size(28.dp),
                                )
                                DashboardWeekState.InProgress -> Text(
                                    text = "${node.percent}%",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                                DashboardWeekState.FutureLocked -> Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF7A7A7A),
                                    modifier = Modifier.size(24.dp),
                                )
                                DashboardWeekState.Pending -> Text(
                                    text = "0%",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = CanvaTokens.SupportGray,
                                )
                            }
                        }
                    }

                    Text(
                        text = node.label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = CanvaTokens.Ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun buildDashboardWeekNodes(
    response: GeneratePlanResponse?,
    logs: Map<String, com.pcosina.app.data.model.DailyLog>,
    weekStart: LocalDate,
    today: LocalDate,
): List<DashboardWeekNode> {
    return (0..6).map { index ->
        val date = weekStart.plusDays(index.toLong())
        val key = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val planDay = response?.days?.getOrNull(index)
        val planned = planDay?.meals?.size ?: 0
        val completed = logs[key]?.completedMealIds?.size ?: 0
        val percent = if (planned > 0) ((completed.toFloat() / planned.toFloat()) * 100f).toInt().coerceIn(0, 100) else 0
        val state = when {
            date.isAfter(today) -> DashboardWeekState.FutureLocked
            planned > 0 && completed >= planned -> DashboardWeekState.Done
            planned > 0 && completed > 0 -> DashboardWeekState.InProgress
            else -> DashboardWeekState.Pending
        }
        DashboardWeekNode(
            label = date.dayOfWeek.name.take(3),
            state = state,
            percent = percent,
        )
    }
}
