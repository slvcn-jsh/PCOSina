package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.api.GeneratePlanResponse
import com.pcosina.app.data.api.PlannedMealDto
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanUiState
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.navigation.Routes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val PcosinaPink = Color(0xFFF76587)
private val PcosinaSoftPink = Color(0xFFFF9DB2)
private val PcosinaDeepText = Color(0xFF5B1527)
private val PcosinaMutedText = Color(0xFF7A5B64)
private val PcosinaCream = Color(0xFFFFF7F8)
private val PcosinaCardPink = Color(0xFFFF7192)
private val PcosinaPeach = Color(0xFFFFB074)
private val PcosinaPurple = Color(0xFF7A7BFF)
private val PcosinaLavender = Color(0xFFCC75F5)
private val PcosinaBrown = Color(0xFFC58B68)
private val PcosinaLocked = Color(0xFF6C6266)

private data class HomeGoalUi(
    val label: String,
    val selected: Boolean
)

private data class HomeMealUi(
    val mealLabel: String,
    val recipeId: String,
    val title: String,
    val calories: Int?,
    val completed: Boolean
)

private data class HomeDayProgressUi(
    val label: String,
    val dateKey: String,
    val completionRatio: Float,
    val isFuture: Boolean,
    val isToday: Boolean
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun PolishedDashboardScreen(
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
    onlineStateOverride: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    val mealPlanState by mealPlanViewModel.uiState.collectAsState()
    val logs by progressViewModel.dailyLogs.collectAsState()
    val activeWeekStart by mealPlanViewModel.activeWeekStart.collectAsState()
    val groceryItems by groceryViewModel.groceryItems.collectAsState()

    val today = remember { LocalDate.now() }
    val todayKey = remember(today) {
        today.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    val currentPlan = (mealPlanState as? MealPlanUiState.Success)?.response

    val weekStart = remember(activeWeekStart, today) {
        activeWeekStart
            ?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
            ?: today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    val todayPlan = remember(currentPlan, weekStart, today) {
        resolveTodayPlan(currentPlan, weekStart, today)
    }

    val todayMeals = todayPlan?.meals.orEmpty()
    var recipeCaloriesById by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(todayMeals.map { it.recipeId }.joinToString("|")) {
        val ids = todayMeals.map { it.recipeId }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) {
            recipeCaloriesById = emptyMap()
            return@LaunchedEffect
        }

        val loaded = mutableMapOf<String, Int>()
        ids.forEach { recipeId ->
            val detail = mealPlanViewModel.getRecipeDetails(recipeId).getOrNull()
            val calories = detail?.calories
            if (calories != null && calories > 0) {
                loaded[recipeId] = calories
            }
        }
        recipeCaloriesById = loaded
    }

    val todayCompletedIds = logs[todayKey]?.completedMealIds.orEmpty()

    val mealItems = remember(todayMeals, recipeCaloriesById, todayCompletedIds) {
        todayMeals.map { meal ->
            HomeMealUi(
                mealLabel = meal.mealLabel.ifBlank { "Meal" },
                recipeId = meal.recipeId,
                title = meal.title.ifBlank { "Recipe details unavailable" },
                calories = recipeCaloriesById[meal.recipeId],
                completed = isMealCompleted(
                    completedIds = todayCompletedIds,
                    mealLabel = meal.mealLabel,
                    recipeId = meal.recipeId
                )
            )
        }
    }

    val targetCalories = currentPlan?.explanation?.targetCalories
        ?.takeIf { it > 0 }
        ?: todayPlan?.totalCalories?.takeIf { it > 0 }
        ?: userViewModel.dailyCalorieTarget.takeIf { it > 0 }

    val consumedCalories = mealItems
        .filter { it.completed }
        .sumOf { it.calories ?: 0 }

    val calorieProgress = if (targetCalories != null && targetCalories > 0) {
        (consumedCalories.toFloat() / targetCalories.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val goalItems = remember(profile.goal, profile.isProfileCompleted) {
        buildGoalItems(profile)
    }

    val dailyTip = remember(currentPlan, profile) {
        currentPlan?.explanation?.goalStrategy?.firstOrNull { it.isNotBlank() }
            ?: currentPlan?.explanation?.symptomStrategy?.firstOrNull { it.isNotBlank() }
            ?: currentPlan?.humanGuidance?.firstOrNull { it.isNotBlank() }
            ?: buildProfileBasedTip(profile)
    }

    val weekProgress = remember(currentPlan, logs, weekStart, today) {
        buildWeekProgress(
            currentPlan = currentPlan,
            logs = logs.mapValues { it.value.completedMealIds },
            weekStart = weekStart,
            today = today
        )
    }

    PolishedHomeContent(
        profile = profile,
        today = today,
        goalItems = goalItems,
        dailyTip = dailyTip,
        mealItems = mealItems,
        calorieProgress = calorieProgress,
        consumedCalories = consumedCalories,
        targetCalories = targetCalories,
        weekProgress = weekProgress,
        hasPlan = currentPlan != null,
        groceryCount = groceryItems.size,
        onSettings = onNavigateToSettings,
        onNotifications = onOpenMoreTools,
        onGoToPlan = onViewPlan,
        onOpenGrocery = { onNavigateToRoute(Routes.GroceryList) },
        onOpenProgress = { onNavigateToRoute(Routes.Progress) },
        onMealClick = { meal -> onRecipeClick(meal.recipeId, meal.mealLabel) },
        modifier = modifier
    )
}

@Composable
private fun PolishedHomeContent(
    profile: UserProfile,
    today: LocalDate,
    goalItems: List<HomeGoalUi>,
    dailyTip: String,
    mealItems: List<HomeMealUi>,
    calorieProgress: Float,
    consumedCalories: Int,
    targetCalories: Int?,
    weekProgress: List<HomeDayProgressUi>,
    hasPlan: Boolean,
    groceryCount: Int,
    onSettings: () -> Unit,
    onNotifications: () -> Unit,
    onGoToPlan: () -> Unit,
    onOpenGrocery: () -> Unit,
    onOpenProgress: () -> Unit,
    onMealClick: (HomeMealUi) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PcosinaCream)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 18.dp,
                bottom = 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeBrandHeader(
                    onSettings = onSettings,
                    onNotifications = onNotifications
                )
            }

            item {
                WelcomeDateCard(
                    name = profile.displayName.ifBlank { "there" },
                    today = today
                )
            }

            item {
                GoalsAndTipRow(
                    goalItems = goalItems,
                    dailyTip = dailyTip,
                    calorieProgress = calorieProgress,
                    consumedCalories = consumedCalories,
                    targetCalories = targetCalories
                )
            }

            item {
                TodayMealPlanSection(
                    mealItems = mealItems,
                    hasPlan = hasPlan,
                    onMealClick = onMealClick,
                    onGoToPlan = onGoToPlan
                )
            }

            item {
                WeeklyProgressCard(
                    progress = weekProgress,
                    onOpenProgress = onOpenProgress
                )
            }

            item {
                StartMealPlanCard(
                    hasPlan = hasPlan,
                    groceryCount = groceryCount,
                    onGoToPlan = onGoToPlan,
                    onOpenGrocery = onOpenGrocery
                )
            }
        }
    }
}

@Composable
private fun HomeBrandHeader(
    onSettings: () -> Unit,
    onNotifications: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = PcosinaSoftPink.copy(alpha = 0.45f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "P",
                    color = PcosinaPink,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PCOSina",
                color = PcosinaPink,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "“Take the first step toward smarter PCOS nutrition.”",
                color = PcosinaDeepText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        CircleIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = onSettings
        )

        Spacer(Modifier.width(8.dp))

        CircleIconButton(
            icon = Icons.Filled.Notifications,
            contentDescription = "Notifications and support",
            onClick = onNotifications
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaSoftPink.copy(alpha = 0.6f)),
        shadowElevation = 2.dp,
        modifier = Modifier.size(46.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = PcosinaPink
            )
        }
    }
}

@Composable
private fun WelcomeDateCard(
    name: String,
    today: LocalDate
) {
    val month = today.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
    val day = today.format(DateTimeFormatter.ofPattern("dd", Locale.ENGLISH))
    val weekday = today.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)).uppercase(Locale.ENGLISH)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PcosinaCardPink),
        border = BorderStroke(2.dp, PcosinaDeepText.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(PcosinaCardPink, PcosinaSoftPink)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "♡",
                        color = PcosinaPink,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome, $name!",
                    color = PcosinaDeepText,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "You’re doing well today! Ready for your next goal?",
                        color = PcosinaDeepText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(min = 62.dp)
            ) {
                Text(
                    text = month,
                    color = PcosinaDeepText,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = day,
                    color = PcosinaDeepText,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = weekday,
                    color = PcosinaDeepText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GoalsAndTipRow(
    goalItems: List<HomeGoalUi>,
    dailyTip: String,
    calorieProgress: Float,
    consumedCalories: Int,
    targetCalories: Int?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GoalProgressCard(
            goals = goalItems,
            progress = calorieProgress,
            consumedCalories = consumedCalories,
            targetCalories = targetCalories,
            modifier = Modifier.weight(1.55f)
        )

        DailyTipsCard(
            tip = dailyTip,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GoalProgressCard(
    goals: List<HomeGoalUi>,
    progress: Float,
    consumedCalories: Int,
    targetCalories: Int?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.defaultMinSize(minHeight = 142.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PcosinaCardPink),
        border = BorderStroke(2.dp, PcosinaDeepText.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Your Goals",
                    color = PcosinaDeepText,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, PcosinaDeepText.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Edit",
                        color = PcosinaDeepText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            goals.take(3).forEach { goal ->
                GoalRow(goal)
            }

            if (goals.isEmpty()) {
                Text(
                    text = "Complete your profile to show your goals.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniProgressRing(
                    progress = progress,
                    modifier = Modifier.size(58.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        color = PcosinaDeepText,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (targetCalories != null) {
                            "$consumedCalories / $targetCalories kcal"
                        } else {
                            "No target yet"
                        },
                        color = PcosinaDeepText.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: HomeGoalUi) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (goal.selected) PcosinaPink else Color.White,
                border = BorderStroke(1.dp, PcosinaPink),
                modifier = Modifier.size(20.dp)
            ) {
                if (goal.selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(3.dp)
                    )
                }
            }

            Spacer(Modifier.width(7.dp))

            Text(
                text = goal.label,
                color = PcosinaDeepText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = PcosinaPink,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun DailyTipsCard(
    tip: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.defaultMinSize(minHeight = 142.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PcosinaBrown),
        border = BorderStroke(2.dp, PcosinaDeepText.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Daily Tips",
                color = PcosinaDeepText,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = tip,
                color = PcosinaDeepText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TodayMealPlanSection(
    mealItems: List<HomeMealUi>,
    hasPlan: Boolean,
    onMealClick: (HomeMealUi) -> Unit,
    onGoToPlan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Your Meal Plan for Today",
            color = PcosinaDeepText,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = if (hasPlan) "Good food, good mood." else "Create a plan to show today’s meals.",
            color = PcosinaMutedText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        )

        if (mealItems.isEmpty()) {
            EmptyPlanCard(onGoToPlan = onGoToPlan)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(mealItems) { index, meal ->
                    MealCard(
                        meal = meal,
                        accent = when (index % 3) {
                            0 -> PcosinaPeach
                            1 -> PcosinaCardPink
                            else -> PcosinaPurple
                        },
                        onClick = { onMealClick(meal) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlanCard(onGoToPlan: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, PcosinaSoftPink.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "No meal plan for today yet.",
                color = PcosinaDeepText,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Generate your week first, then your meals will appear here automatically.",
                color = PcosinaMutedText,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onGoToPlan,
                colors = ButtonDefaults.buttonColors(containerColor = PcosinaPink)
            ) {
                Text("Go to Plan")
            }
        }
    }
}

@Composable
private fun MealCard(
    meal: HomeMealUi,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .height(132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = accent),
        border = BorderStroke(2.dp, PcosinaDeepText.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.RestaurantMenu,
                            contentDescription = null,
                            tint = PcosinaDeepText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = meal.mealLabel,
                    color = PcosinaDeepText,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = meal.title,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = meal.calories?.toString() ?: "—",
                    color = PcosinaDeepText,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "kcal",
                    color = PcosinaDeepText,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 7.dp)
                )

                if (meal.completed) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyProgressCard(
    progress: List<HomeDayProgressUi>,
    onOpenProgress: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Your Weekly Progress",
            color = PcosinaDeepText,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Consistency is key. You’re doing amazing!",
            color = PcosinaMutedText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenProgress),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(2.dp, PcosinaDeepText.copy(alpha = 0.85f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                progress.forEach { day ->
                    DayProgressNode(day)
                }
            }
        }
    }
}

@Composable
private fun DayProgressNode(day: HomeDayProgressUi) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(48.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = when {
                day.isFuture -> Color(0xFFF1D9DE)
                day.completionRatio >= 1f -> PcosinaPink
                day.completionRatio > 0f -> PcosinaPink.copy(alpha = 0.88f)
                else -> Color.White
            },
            border = BorderStroke(
                width = 3.dp,
                color = if (day.isToday) PcosinaDeepText else PcosinaPink
            ),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    day.isFuture -> Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = PcosinaLocked,
                        modifier = Modifier.size(18.dp)
                    )
                    day.completionRatio >= 1f -> Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    day.completionRatio > 0f -> Text(
                        text = "${(day.completionRatio * 100).roundToInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelSmall
                    )
                    else -> Text(
                        text = "0%",
                        color = PcosinaMutedText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Text(
            text = day.label,
            color = PcosinaDeepText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun StartMealPlanCard(
    hasPlan: Boolean,
    groceryCount: Int,
    onGoToPlan: () -> Unit,
    onOpenGrocery: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = PcosinaSoftPink),
        border = BorderStroke(2.dp, PcosinaDeepText.copy(alpha = 0.85f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 145.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(PcosinaCardPink, Color(0xFFFFC7D2))
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (hasPlan) "Ready to continue your meal plan?" else "Ready to start your meal plan?",
                    color = PcosinaDeepText,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = if (hasPlan) {
                        if (groceryCount > 0) {
                            "Your meals and grocery list are ready."
                        } else {
                            "Review your week and prepare your grocery list."
                        }
                    } else {
                        "Let’s find the best recipes for you today!"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Button(
                    onClick = if (hasPlan && groceryCount > 0) onOpenGrocery else onGoToPlan,
                    colors = ButtonDefaults.buttonColors(containerColor = PcosinaPink),
                    shape = RoundedCornerShape(50),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp)
                ) {
                    Text(
                        text = if (hasPlan && groceryCount > 0) "Open Grocery" else "Go to Plan",
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            PlateDecoration(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(122.dp)
            )
        }
    }
}

@Composable
private fun PlateDecoration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            color = PcosinaDeepText.copy(alpha = 0.35f),
            radius = size.minDimension * 0.47f,
            style = stroke
        )
        drawCircle(
            color = PcosinaDeepText.copy(alpha = 0.30f),
            radius = size.minDimension * 0.35f,
            style = stroke
        )
        drawLine(
            color = PcosinaDeepText.copy(alpha = 0.45f),
            start = androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.75f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.27f),
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = PcosinaDeepText.copy(alpha = 0.45f),
            start = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.88f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.46f, size.height * 0.60f),
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MiniProgressRing(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = Color.White.copy(alpha = 0.55f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = PcosinaDeepText,
                startAngle = 150f,
                sweepAngle = 240f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }

        Text(
            text = "${(progress * 100).roundToInt()}%",
            color = PcosinaDeepText,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun resolveTodayPlan(
    currentPlan: GeneratePlanResponse?,
    weekStart: LocalDate,
    today: LocalDate
) = currentPlan?.let { plan ->
    val index = ChronoUnit.DAYS.between(weekStart, today).toInt()
    plan.days.getOrNull(index)
        ?: plan.days.firstOrNull {
            it.dayLabel.equals(
                today.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)),
                ignoreCase = true
            )
        }
}

private fun buildGoalItems(profile: UserProfile): List<HomeGoalUi> {
    val raw = profile.goal.trim()
    if (raw.isBlank()) return emptyList()

    return raw
        .split(",", "/", "|", ";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ENGLISH) }
        .map { HomeGoalUi(label = prettifyGoalLabel(it), selected = true) }
}

private fun prettifyGoalLabel(value: String): String {
    val normalized = value.trim()
    return when {
        normalized.equals("weight_loss", ignoreCase = true) -> "Weight Management"
        normalized.equals("weight loss", ignoreCase = true) -> "Weight Management"
        normalized.contains("symptom", ignoreCase = true) -> "PCOS Symptom Management"
        normalized.contains("general", ignoreCase = true) -> "General Health"
        else -> normalized.replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
                }
            }
    }
}

private fun buildProfileBasedTip(profile: UserProfile): String {
    return when {
        profile.allergies.isNotEmpty() ->
            "Your allergy filters are active. Recipes with matching ingredients will be avoided."
        profile.pantryItems.isNotEmpty() ->
            "Use pantry ingredients first to reduce waste and keep your grocery list practical."
        profile.weeklyBudgetPhp > 0 ->
            "Your weekly budget is considered when generating your meal plan."
        profile.insulinResistanceLevel.equals("Severe", ignoreCase = true) ->
            "Choose steady meals with protein and fiber to support insulin-friendly eating."
        profile.insulinResistanceLevel.equals("Moderate", ignoreCase = true) ->
            "Pair protein with each meal to help keep meals balanced."
        else ->
            "Keep meals balanced with protein, fiber-rich food, and steady portions."
    }
}

private fun buildWeekProgress(
    currentPlan: GeneratePlanResponse?,
    logs: Map<String, List<String>>,
    weekStart: LocalDate,
    today: LocalDate
): List<HomeDayProgressUi> {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val labelFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

    return (0..6).map { offset ->
        val date = weekStart.plusDays(offset.toLong())
        val key = date.format(formatter)
        val dayPlan = currentPlan?.days?.getOrNull(offset)
        val totalMeals = dayPlan?.meals?.size ?: 0
        val completedCount = logs[key].orEmpty().size

        val ratio = when {
            totalMeals > 0 -> (completedCount.toFloat() / totalMeals.toFloat()).coerceIn(0f, 1f)
            completedCount > 0 -> 1f
            else -> 0f
        }

        HomeDayProgressUi(
            label = date.format(labelFormatter).uppercase(Locale.ENGLISH),
            dateKey = key,
            completionRatio = ratio,
            isFuture = date.isAfter(today),
            isToday = date == today
        )
    }
}

private fun isMealCompleted(
    completedIds: List<String>,
    mealLabel: String,
    recipeId: String
): Boolean {
    if (recipeId.isBlank()) return false
    val direct = completedIds.contains(recipeId)
    val keyed = completedIds.any { entry ->
        entry.equals("$mealLabel::$recipeId", ignoreCase = true) ||
            entry.endsWith("::$recipeId", ignoreCase = true)
    }
    return direct || keyed
}
