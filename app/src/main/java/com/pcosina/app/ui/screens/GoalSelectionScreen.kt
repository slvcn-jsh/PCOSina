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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.util.GoalOption
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
    val whyCopy = remember(selectedGoals) {
        when {
            selectedGoals.isEmpty() ->
                "Choose at least one goal so PCOSINA can optimize calories, macros, and meal swaps for you."
            selectedGoals.contains(GoalOption.WeightLoss) ->
                "Your plan will prioritize satiety and calorie balance so weekly targets are easier to sustain."
            selectedGoals.contains(GoalOption.SymptomManagement) ->
                "Your plan will emphasize steadier energy and lower-glycemic structure to support symptom control."
            else ->
                "Your plan will focus on balanced nutrition and consistency for long-term PCOS-friendly habits."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GradientHeader(
            title = "Select Your Goals",
            subtitle = "Choose one or more goals (you can change later)",
            containerHeight = 180,
        )
        Text(
            text = "Step 2 of 4: Select Goal",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().testTag("goal_step2_label")
        )

        Text(
            text = "What would you like to achieve with PCOSINA?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().testTag("goal_why_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Why this helps your goal",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = whyCopy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Next: Save goals -> Generate plan -> Sync grocery list",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        GoalCard(
            title = "Weight Loss",
            description = "Achieve healthy weight through balanced nutrition",
            selected = weightLoss,
            onToggle = { weightLoss = !weightLoss },
            testTag = "goal_option_weight_loss"
        )

        GoalCard(
            title = "PCOS Symptom Management",
            description = "Control insulin resistance and hormonal balance",
            selected = symptomMgmt,
            onToggle = { symptomMgmt = !symptomMgmt },
            testTag = "goal_option_symptom_management"
        )

        GoalCard(
            title = "General Health Improvement",
            description = "Boost energy and overall wellness",
            selected = generalHealth,
            onToggle = { generalHealth = !generalHealth },
            testTag = "goal_option_general_health"
        )

        if (!hasSelection) {
            Text(
                text = "Please select at least one goal to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(8.dp))

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
                Text("Save Goals & Continue")
            }
        }
    }
}

@Composable
private fun GoalCard(
    title: String,
    description: String,
    selected: Boolean,
    onToggle: () -> Unit,
    testTag: String,
) {
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val scale = if (selected) 1.02f else 1.0f

    Card(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .graphicsLayer(scaleX = scale, scaleY = scale),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
