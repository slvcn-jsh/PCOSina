package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaRoseShadow
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
    val primaryActionLabel = "Save goals and build your week"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GoalCoral),
    ) {
        GoalHeader(
            title = "CHOOSE YOUR GOALS",
            subtitle = "Pick the focus areas that should shape your first journey",
            onBack = onBack,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
<<<<<<< HEAD
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
=======
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.76f),
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 22.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Your first week will balance the goals you selected while still respecting your saved hard food rules and preferences.",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = PcosinaDeepRose,
                    )

                    GoalCard(
                        title = "Weight Loss",
                        description = "Prioritizes calorie balance, satisfying meals, and realistic weekly adherence.",
                        supportLabel = "Supports filling meals and realistic progress without overriding your hard rules.",
                        selected = weightLoss,
                        onToggle = { weightLoss = !weightLoss },
                        testTag = "goal_option_weight_loss",
                    )

                    GoalCard(
                        title = "PCOS Symptom Management",
                        description = "Prioritizes symptom-aware nudges, steadier meals, and metabolic support.",
                        supportLabel = "Adds low-GI, steady-energy emphasis while keeping planning deterministic.",
                        selected = symptomMgmt,
                        onToggle = { symptomMgmt = !symptomMgmt },
                        testTag = "goal_option_symptom_management",
                    )

                    GoalCard(
                        title = "General Health Improvement",
                        description = "Balances overall nutrition quality, consistency, and everyday wellness.",
                        supportLabel = "Keeps the week broad, balanced, and easier to sustain.",
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

                    Text(
                        text = "You can change these focus areas later in Settings if your priorities shift.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = PcosinaDeepRose.copy(alpha = 0.78f),
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
                        .height(58.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(22.dp),
                            spotColor = PcosinaRoseShadow.copy(alpha = 0.20f),
                            ambientColor = PcosinaRoseShadow.copy(alpha = 0.16f),
                        )
                        .testTag("goal_save_continue_cta"),
                    enabled = hasSelection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoalCoral,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = Color.White,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    shape = RoundedCornerShape(22.dp),
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
>>>>>>> 761cd7a (Update UI changes)
    }
}

@Composable
<<<<<<< HEAD
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
=======
private fun GoalHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)?,
>>>>>>> 761cd7a (Update UI changes)
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(234.dp)
            .background(GoalCoral)
            .statusBarsPadding()
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 44.dp),
    ) {
        GoalTargetGraphic(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 42.dp, y = 8.dp)
                .size(172.dp)
        )
        IconButton(
            onClick = { onBack?.invoke() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(42.dp)
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
                .padding(end = 52.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        listOf(
            164.dp to 0.16f,
            118.dp to 0.22f,
            72.dp to 0.30f,
            28.dp to 0.42f,
        ).forEach { (size, alpha) ->
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(GoalTargetPink.copy(alpha = alpha))
            )
        }
    }
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
        GoalCoral.copy(alpha = 0.45f)
    } else {
        GoalBorder
    }

    Card(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                GoalSelectedSurface
            } else {
                Color.White
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    text = supportLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontStyle = FontStyle.Italic),
                    color = if (selected) PcosinaDeepRose else PcosinaDeepRose.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
