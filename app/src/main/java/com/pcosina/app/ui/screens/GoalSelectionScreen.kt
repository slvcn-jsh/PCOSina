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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.R
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.util.GoalOption
import com.pcosina.app.ui.util.goalTextFromOptions
import com.pcosina.app.ui.util.parseGoalOptions

private val GoalCoral = Color(0xFFEF6F7D)
private val GoalTargetPink = Color(0xFFFFC8CF)
private val GoalSelectedSurface = Color(0xFFFFEEF1)
private val GoalBorder = Color(0xFFE4D7DB)

@Composable
fun GoalSelectionScreen(
    userViewModel: UserViewModel,


    onFinish: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val profile by userViewModel.userProfile.collectAsState()
    var selectedGoalName by rememberSaveable(profile.goal) {
        mutableStateOf(parseGoalOptions(profile.goal).firstOrNull()?.name)
    }
    val selectedGoal = GoalOption.entries.firstOrNull { it.name == selectedGoalName }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GoalCoral),
    ) {
        GoalHeader(
            title = "CHOOSE YOUR GOALS",
            subtitle = "Select your main goal to personalize your meal plan.",
            onBack = onBack,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(0.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 14.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Choose one. You can change this later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )

                    GoalCard(
                        title = "Weight Loss",
                        description = "Prioritizes calorie balance, satisfying meals, and realistic weekly adherence.",
                        supportLabel = "Supports filling meals and realistic progress without overriding your hard rules.",
                        selected = selectedGoal == GoalOption.WeightLoss,
                        onToggle = { selectedGoalName = GoalOption.WeightLoss.name },
                        testTag = "goal_option_weight_loss",
                    )

                    GoalCard(
                        title = "PCOS Symptom Management",
                        description = "Prioritizes symptom-aware nudges, steadier meals, and metabolic support.",
                        supportLabel = "Adds low-GI, steady-energy emphasis while keeping planning deterministic.",
                        selected = selectedGoal == GoalOption.SymptomManagement,
                        onToggle = { selectedGoalName = GoalOption.SymptomManagement.name },
                        testTag = "goal_option_symptom_management",
                    )

                    GoalCard(
                        title = "General Health Improvement",
                        description = "Balances overall nutrition quality, consistency, and everyday wellness.",
                        supportLabel = "Keeps the week broad, balanced, and easier to sustain.",
                        selected = selectedGoal == GoalOption.GeneralHealth,
                        onToggle = { selectedGoalName = GoalOption.GeneralHealth.name },
                        testTag = "goal_option_general_health",
                    )

                    if (selectedGoal == null) {
                        Text(
                            text = "Please select one goal before continuing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Button(
                    onClick = {
                        selectedGoal?.let { goal ->
                            userViewModel.updateGoal(goalTextFromOptions(setOf(goal)))
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("goal_save_continue_cta"),
                    enabled = selectedGoal != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoalCoral,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = Color.White,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text("Save Goals", fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.size(7.dp))
                    Text(
                        text = "Your progress is saved automatically on this device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(194.dp)
            .background(GoalCoral)
            .statusBarsPadding()
            .padding(start = 28.dp, top = 14.dp, end = 24.dp, bottom = 17.dp),
    ) {
        GoalTargetGraphic(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp, y = 6.dp)
                .size(104.dp)
        )
        IconButton(
            onClick = { onBack?.invoke() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White),
            enabled = onBack != null,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GoalCoral,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(end = 70.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.94f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GoalTargetGraphic(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(id = R.drawable.pcosina_svg_20_goal),
        contentDescription = null,
        modifier = modifier,
        tint = GoalTargetPink.copy(alpha = 0.58f),
    )
}

@Composable
private fun GoalCard(
    title: String,
    description: String,
    supportLabel: String,
    selected: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = if (selected) {
        GoalCoral
    } else {
        GoalBorder
    }

    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                GoalSelectedSurface
            } else {
                Color.White
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onToggle,
                role = Role.RadioButton,
            )
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = if (selected) {
                    GoalCoral
                } else {
                    Color.White
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) GoalCoral else colorScheme.outline.copy(alpha = 0.72f)
                ),
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(7.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                    color = GoalCoral.copy(alpha = if (selected) 0.92f else 0.76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
