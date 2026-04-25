package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.util.GoalOption
import com.pcosina.app.ui.util.goalPlanFocusCopy
import com.pcosina.app.ui.util.goalTextFromOptions
import com.pcosina.app.ui.util.parseGoalOptions

@Composable
fun GoalSelectionScreen(
    userViewModel: UserViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    var weightLoss by rememberSaveable(profile.goal) {
        mutableStateOf(parseGoalOptions(profile.goal).contains(GoalOption.WeightLoss))
    }
    var symptomMgmt by rememberSaveable(profile.goal) {
        mutableStateOf(parseGoalOptions(profile.goal).contains(GoalOption.SymptomManagement))
    }
    var generalHealth by rememberSaveable(profile.goal) {
        mutableStateOf(parseGoalOptions(profile.goal).contains(GoalOption.GeneralHealth))
    }
    
    val hasSelection = weightLoss || symptomMgmt || generalHealth
    val selectedGoals = remember(weightLoss, symptomMgmt, generalHealth) {
        buildList {
            if (weightLoss) add(GoalOption.WeightLoss)
            if (symptomMgmt) add(GoalOption.SymptomManagement)
            if (generalHealth) add(GoalOption.GeneralHealth)
        }
    }
    val selectedGoalText = remember(selectedGoals) {
        if (selectedGoals.isEmpty()) "" else goalTextFromOptions(selectedGoals.toSet())
    }
    val whyCopy = remember(selectedGoals, selectedGoalText) {
        when {
            selectedGoals.isEmpty() ->
                "Choose at least one focus so PCOSINA can shape your meals around what matters most to you."
            selectedGoals.size > 1 ->
                "Your first week will balance the goals you selected while still respecting your saved hard food rules and preferences."
            else ->
                goalPlanFocusCopy(selectedGoalText)
        }
    }
    val goalStatusSummary = when {
        selectedGoals.isEmpty() -> "Choose at least one focus before building your first week."
        selectedGoals.size == 1 -> "Selected focus: $selectedGoalText."
        else -> "Selected ${selectedGoals.size} goals: $selectedGoalText."
    }
    val goalStorageSummary = "Saved locally and used to steer weekly plan ranking, nutrition emphasis, and progress guidance."
    val goalNextFocusLabel = if (hasSelection) {
        "Next focus: save goals and build your first week"
    } else {
        "Next focus: pick at least one focus"
    }
    val primaryActionLabel = "Save goals & build week"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GradientHeader(
            title = "Choose your planning focus",
            subtitle = "Step 3 of 6 • Pick the focus areas that should shape your first week.",
            containerHeight = 112,
        )
        GoalSetupSummaryCard(
            goalStatusSummary = goalStatusSummary,
            goalStorageSummary = goalStorageSummary,
            goalNextFocusLabel = goalNextFocusLabel,
            whyCopy = whyCopy,
            hasSelection = hasSelection,
            selectedGoalCount = selectedGoals.size,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("goal_status_center_card")
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GoalCard(
            title = "Weight Loss",
            description = "Prioritizes calorie balance, satisfying meals, and realistic weekly adherence.",
            emphasisLabel = "Calorie balance",
            supportLabel = "Best when body-weight change is your main weekly goal.",
            selected = weightLoss,
            onToggle = { weightLoss = !weightLoss },
            testTag = "goal_option_weight_loss",
            modifier = Modifier.weight(1f)
            )

            GoalCard(
            title = "PCOS Symptom Management",
            description = "Prioritizes symptom-aware nudges, steadier meals, and metabolic support.",
            emphasisLabel = "Symptom-aware nudges",
            supportLabel = "Best when cravings, energy swings, or symptom support matter most.",
            selected = symptomMgmt,
            onToggle = { symptomMgmt = !symptomMgmt },
            testTag = "goal_option_symptom_management",
            modifier = Modifier.weight(1f)
            )

            GoalCard(
            title = "General Health Improvement",
            description = "Balances overall nutrition quality, consistency, and everyday wellness.",
            emphasisLabel = "Balanced nutrition",
            supportLabel = "Best when you want a broad, sustainable weekly reset.",
            selected = generalHealth,
            onToggle = { generalHealth = !generalHealth },
            testTag = "goal_option_general_health",
            modifier = Modifier.weight(1f)
            )
        }

        if (!hasSelection) {
            Text(
                text = "Please select at least one goal before building your first week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = { 
                if (hasSelection) {
                    val goals = linkedSetOf<GoalOption>()
                    if (weightLoss) goals.add(GoalOption.WeightLoss)
                    if (symptomMgmt) goals.add(GoalOption.SymptomManagement)
                    if (generalHealth) goals.add(GoalOption.GeneralHealth)
                    userViewModel.updateGoal(goalTextFromOptions(goals))
                    onFinish() 
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("goal_save_continue_cta"),
            enabled = hasSelection,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            val colorScheme = MaterialTheme.colorScheme
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (hasSelection)
                            Brush.horizontalGradient(
                                colors = listOf(
                                    colorScheme.primary,
                                    colorScheme.secondary,
                                )
                            )
                        else Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(primaryActionLabel, fontWeight = FontWeight.Bold)
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
        Text(
            text = "You can change these focus areas later in Settings if your priorities shift.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GoalSetupSummaryCard(
    goalStatusSummary: String,
    goalStorageSummary: String,
    goalNextFocusLabel: String,
    whyCopy: String,
    hasSelection: Boolean,
    selectedGoalCount: Int,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Final setup before Meal Plan",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (hasSelection) colorScheme.primary.copy(alpha = 0.10f) else colorScheme.surfaceVariant,
                    contentColor = if (hasSelection) colorScheme.primary else colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = if (hasSelection) "$selectedGoalCount selected" else "Choose 1+",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            Text(
                text = goalStatusSummary,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = whyCopy,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$goalNextFocusLabel • $goalStorageSummary",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GoalCard(
    title: String,
    description: String,
    emphasisLabel: String,
    supportLabel: String,
    selected: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor =
        if (selected) colorScheme.primary.copy(alpha = 0.22f) else colorScheme.outlineVariant.copy(alpha = 0.65f)
    val scale = if (selected) 1.02f else 1.0f

    Card(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colorScheme.primary.copy(alpha = 0.04f) else colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .graphicsLayer(scaleX = scale, scaleY = scale),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    colorScheme.surfaceVariant
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) {
                        colorScheme.primary
                    } else {
                        colorScheme.onSurfaceVariant.copy(alpha = 0.50f)
                    },
                    modifier = Modifier.padding(10.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) {
                            colorScheme.primary.copy(alpha = 0.10f)
                        } else {
                            colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        },
                        contentColor = if (selected) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurfaceVariant
                        }
                    ) {
                        Text(
                            text = if (selected) "Selected" else emphasisLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (selected) {
                        "Included in first-week optimization • $supportLabel"
                    } else {
                        "$emphasisLabel • $supportLabel"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
