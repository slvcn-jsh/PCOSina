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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.theme.PcosinaBlushBorder
import com.pcosina.app.ui.theme.PcosinaBlushStrong
import com.pcosina.app.ui.theme.PcosinaBlushSurface
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaRoseShadow
import com.pcosina.app.ui.theme.PcosinaSurface
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
            .background(PcosinaSurface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GradientHeader(
            title = "CHOOSE YOUR GOALS",
            subtitle = "Pick the focus areas that should shape your first journey.",
            containerHeight = 150,
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
                .testTag("goal_status_center_card"),
        )

        GoalCard(
            title = "Weight Loss",
            description = "Prioritizes calorie balance, satisfying meals, and realistic weekly adherence.",
            emphasisLabel = "Calorie balance",
            supportLabel = "Best when body-weight change is your main weekly goal.",
            selected = weightLoss,
            onToggle = { weightLoss = !weightLoss },
            testTag = "goal_option_weight_loss",
        )

        GoalCard(
            title = "PCOS Symptom Management",
            description = "Prioritizes symptom-aware nudges, steadier meals, and metabolic support.",
            emphasisLabel = "Symptom-aware nudges",
            supportLabel = "Best when cravings, energy swings, or symptom support matter most.",
            selected = symptomMgmt,
            onToggle = { symptomMgmt = !symptomMgmt },
            testTag = "goal_option_symptom_management",
        )

        GoalCard(
            title = "General Health Improvement",
            description = "Balances overall nutrition quality, consistency, and everyday wellness.",
            emphasisLabel = "Balanced nutrition",
            supportLabel = "Best when you want a broad, sustainable weekly reset.",
            selected = generalHealth,
            onToggle = { generalHealth = !generalHealth },
            testTag = "goal_option_general_health",
        )

        if (!hasSelection) {
            Text(
                text = "Please select at least one goal before building your first week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        GoalPrimaryActionButton(
            primaryActionLabel = primaryActionLabel,
            hasSelection = hasSelection,
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
            modifier = Modifier.testTag("goal_save_continue_cta"),
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PcosinaBlushSurface,
            border = BorderStroke(1.dp, PcosinaBlushBorder),
        ) {
            Text(
                text = "You can change these focus areas later in Settings if your priorities shift.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun GoalPrimaryActionButton(
    primaryActionLabel: String,
    hasSelection: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = PcosinaRoseShadow.copy(alpha = 0.24f),
                ambientColor = PcosinaRoseShadow.copy(alpha = 0.18f),
            ),
        enabled = hasSelection,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (hasSelection) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                PcosinaBlushStrong,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(primaryActionLabel, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                )
            }
        }
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, PcosinaBlushBorder.copy(alpha = 0.70f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Final setup",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PcosinaDeepRose,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (hasSelection) colorScheme.primary.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.92f),
                    contentColor = if (hasSelection) colorScheme.primary else colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = if (hasSelection) "$selectedGoalCount selected" else "Choose 1+",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            Text(
                text = whyCopy,
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = PcosinaDeepRose.copy(alpha = 0.78f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$goalStatusSummary $goalNextFocusLabel • $goalStorageSummary",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
    val borderColor = if (selected) {
        colorScheme.primary.copy(alpha = 0.44f)
    } else {
        PcosinaBlushBorder
    }

    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                colorScheme.primary.copy(alpha = 0.22f)
            } else {
                Color.White
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = if (selected) {
                    colorScheme.primary
                } else {
                    Color.White
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) colorScheme.primary else colorScheme.outline.copy(alpha = 0.72f)
                ),
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (selected) {
                        "Included in first-week optimization • $supportLabel"
                    } else {
                        "$emphasisLabel • $supportLabel"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                    color = if (selected) PcosinaDeepRose else PcosinaDeepRose.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
