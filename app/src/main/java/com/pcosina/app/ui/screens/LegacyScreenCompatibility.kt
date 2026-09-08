package com.pcosina.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.api.RecipeSummaryDto
import com.pcosina.app.data.model.GroceryItemSource
import com.pcosina.app.data.model.PlannerRecipeSummary
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.navigation.Routes

interface MealPlanNextActionAnalytics {
    fun trackTap(actionType: String, networkState: String, userId: String)
}

@Composable
fun DashboardScreen(
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
    modifier: Modifier = Modifier
) = DashboardRefinedScreen(
    userViewModel = userViewModel,
    authViewModel = authViewModel,
    mealPlanViewModel = mealPlanViewModel,
    groceryViewModel = groceryViewModel,
    progressViewModel = progressViewModel,
    onRecipeClick = onRecipeClick,
    onViewPlan = onViewPlan,
    onOpenMoreTools = onOpenMoreTools,
    onNavigateToSettings = onNavigateToSettings,
    onNavigateToRoute = onNavigateToRoute,
    onlineStateOverride = onlineStateOverride,
    modifier = modifier
)

@Composable
fun MealPlanScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    onRecipeClick: (String, String?) -> Unit,
    onViewProgress: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {},
    nextActionAnalytics: MealPlanNextActionAnalytics? = null,
    onlineStateOverride: Boolean? = null,
    swapOptionsLoader: (suspend (mealLabel: String, limit: Int) -> Result<List<RecipeSummaryDto>>)? = null,
    swapGrocerySourceLoader: (suspend (recipeId: String) -> Result<List<GroceryItemSource>>)? = null,
    swapApplyOverride: (suspend (dayIndex: Int, mealIndex: Int, recipeId: String, title: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val trackedNavigate: (String) -> Unit = { route ->
        if (route == Routes.GroceryList) {
            nextActionAnalytics?.trackTap(
                actionType = "sync_grocery_list",
                networkState = if (onlineStateOverride == false) "offline" else "online",
                userId = userViewModel.activeUserId
            )
        }
        onNavigateToRoute(route)
    }
    MealPlanRefinedScreen(
        userViewModel = userViewModel,
        mealPlanViewModel = mealPlanViewModel,
        groceryViewModel = groceryViewModel,
        progressViewModel = progressViewModel,
        onRecipeClick = onRecipeClick,
        onViewProgress = onViewProgress,
        onNavigateToRoute = trackedNavigate,
        onlineStateOverride = onlineStateOverride,
        swapOptionsLoader = swapOptionsLoader?.let { loader ->
            { mealLabel, limit ->
                loader(mealLabel, limit).map { items ->
                    items.map { PlannerRecipeSummary(it.id, it.title, it.mealType, it.minutes) }
                }
            }
        },
        swapGrocerySourceLoader = swapGrocerySourceLoader,
        swapApplyOverride = swapApplyOverride,
        modifier = modifier
    )
}

@Composable
fun GroceryListScreen(
    groceryViewModel: GroceryViewModel,
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    progressViewModel: ProgressViewModel,
    onNavigateToRoute: (String) -> Unit = {},
    onlineStateOverride: Boolean? = null,
    modifier: Modifier = Modifier
) = GroceryRefinedScreen(
    groceryViewModel = groceryViewModel,
    userViewModel = userViewModel,
    mealPlanViewModel = mealPlanViewModel,
    progressViewModel = progressViewModel,
    onNavigateToRoute = onNavigateToRoute,
    onlineStateOverride = onlineStateOverride,
    modifier = modifier
)

@Composable
fun ProgressScreen(
    userViewModel: UserViewModel,
    mealPlanViewModel: MealPlanViewModel,
    progressViewModel: ProgressViewModel,
    groceryViewModel: GroceryViewModel,
    userId: String,
    onBackToDashboard: () -> Unit,
    onNavigateToRoute: (String) -> Unit = {},
    onlineStateOverride: Boolean? = null,
    feedbackSectionExpandedByDefault: Boolean = false,
    modifier: Modifier = Modifier
) = ProgressRefinedScreen(
    userViewModel = userViewModel,
    mealPlanViewModel = mealPlanViewModel,
    progressViewModel = progressViewModel,
    groceryViewModel = groceryViewModel,
    userId = userId,
    onBackToDashboard = onBackToDashboard,
    onNavigateToRoute = onNavigateToRoute,
    onlineStateOverride = onlineStateOverride,
    feedbackSectionExpandedByDefault = feedbackSectionExpandedByDefault,
    modifier = modifier
)

@Composable
fun DashboardTodayOutcomeCard(
    progress: Float,
    completedMealsLabel: String,
    nextLabel: String,
    deltaLabel: String,
    timeline: List<TodayTimelineStep>,
    ctaLabel: String,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("dashboard_today_outcome_card"),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Text(completedMealsLabel)
            Text(nextLabel)
            Text(deltaLabel)
            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                timeline.forEach { step -> Text("${step.label}: ${step.state.label}") }
            }
            Button(onClick = onPrimaryAction) {
                Text(ctaLabel)
            }
        }
    }
}

@Composable
fun DashboardWeekCloseoutCard(
    visible: Boolean,
    helperCopyMaxLines: Int,
    onReviewWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Surface(
        modifier = modifier.testTag("dashboard_week_closeout_card"),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Week closeout ready", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Sunday meals are complete. Review the week before generating the next plan.",
                maxLines = helperCopyMaxLines
            )
            Button(onClick = onReviewWeek) {
                Text("Review week & generate next plan")
            }
        }
    }
}

@Composable
fun ProgressMealCheckbox(
    checked: Boolean,
    loggable: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = loggable,
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
fun ProgressLoggingPolicyDialog(
    lockReason: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logging policy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ProgressViewModel.LoggingPolicySummary)
                Text(lockReason)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
