package com.pcosina.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcosina.app.data.model.MealCheckIn
import com.pcosina.app.ui.util.goalMealCheckInPrompt

data class MealCheckInDraft(
    val energyLevel: Int?,
    val fullnessLevel: Int?,
    val cravingsLevel: Int?,
    val satisfactionLevel: Int?,
    val note: String?
)

@Composable
fun MealCheckInDialog(
    goal: String,
    mealTitle: String,
    mealLabel: String,
    initial: MealCheckIn? = null,
    onDismiss: () -> Unit,
    onSave: (MealCheckInDraft) -> Unit
) {
    var energyLevel by rememberSaveable(mealTitle, mealLabel) { mutableStateOf<Int?>(null) }
    var fullnessLevel by rememberSaveable(mealTitle, mealLabel) { mutableStateOf<Int?>(null) }
    var cravingsLevel by rememberSaveable(mealTitle, mealLabel) { mutableStateOf<Int?>(null) }
    var satisfactionLevel by rememberSaveable(mealTitle, mealLabel) { mutableStateOf<Int?>(null) }
    var note by rememberSaveable(mealTitle, mealLabel) { mutableStateOf("") }

    LaunchedEffect(initial?.timestamp, mealTitle, mealLabel) {
        energyLevel = initial?.energyLevel
        fullnessLevel = initial?.fullnessLevel
        cravingsLevel = initial?.cravingsLevel
        satisfactionLevel = initial?.satisfactionLevel
        note = initial?.note.orEmpty()
    }

    val hasAnyResponse = energyLevel != null ||
        fullnessLevel != null ||
        cravingsLevel != null ||
        satisfactionLevel != null ||
        note.isNotBlank()
    val answeredCount = listOf(
        energyLevel,
        fullnessLevel,
        cravingsLevel,
        satisfactionLevel
    ).count { it != null }
    val progressSummary = if (answeredCount == 0 && note.isBlank()) {
        "No answers yet"
    } else {
        "$answeredCount of 4 check-in prompts answered"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Meal check-in",
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = mealLabel,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = mealTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Text(
                            text = goalMealCheckInPrompt(goal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (hasAnyResponse) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    },
                    contentColor = if (hasAnyResponse) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (hasAnyResponse) Icons.Filled.CheckCircle else Icons.Filled.Restaurant,
                            contentDescription = null
                        )
                        Text(
                            text = progressSummary,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                Text(
                    text = "Pick the numbers that best match how this meal felt. You can save a partial check-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MealCheckInScaleRow(
                    title = "Energy after eating",
                    selected = energyLevel,
                    lowAnchor = "Low",
                    highAnchor = "Steady",
                    onSelected = { energyLevel = it }
                )
                MealCheckInScaleRow(
                    title = "Fullness",
                    selected = fullnessLevel,
                    lowAnchor = "Still hungry",
                    highAnchor = "Comfortably full",
                    onSelected = { fullnessLevel = it }
                )
                MealCheckInScaleRow(
                    title = "Cravings after eating",
                    selected = cravingsLevel,
                    lowAnchor = "Calm",
                    highAnchor = "Strong",
                    onSelected = { cravingsLevel = it }
                )
                MealCheckInScaleRow(
                    title = "Satisfaction",
                    selected = satisfactionLevel,
                    lowAnchor = "Flat",
                    highAnchor = "Satisfied",
                    onSelected = { satisfactionLevel = it }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Optional note") },
                    placeholder = { Text("Anything you want to remember about this meal?") },
                    minLines = 2,
                    supportingText = {
                        Text("Use this for symptoms, cravings, or anything unusual about the meal.")
                    }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = hasAnyResponse,
                onClick = {
                    onSave(
                        MealCheckInDraft(
                            energyLevel = energyLevel,
                            fullnessLevel = fullnessLevel,
                            cravingsLevel = cravingsLevel,
                            satisfactionLevel = satisfactionLevel,
                            note = note.takeIf { it.isNotBlank() }
                        )
                    )
                }
            ) {
                Text("Save check-in")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        }
    )
}

@Composable
private fun MealCheckInScaleRow(
    title: String,
    selected: Int?,
    lowAnchor: String,
    highAnchor: String,
    onSelected: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val statusLabel = selected?.let { "Selected: $it/5" } ?: "Choose a number"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected != null) {
                    colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    colorScheme.surfaceVariant.copy(alpha = 0.70f)
                },
                contentColor = if (selected != null) {
                    colorScheme.primary
                } else {
                    colorScheme.onSurfaceVariant
                }
            ) {
                Text(
                    text = statusLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..5).forEach { value ->
                TokenizedFilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    modifier = Modifier.heightIn(min = 44.dp),
                    text = value.toString(),
                    labelMaxWidth = 28.dp
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.surfaceVariant.copy(alpha = 0.52f),
            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "1 = $lowAnchor",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "5 = $highAnchor",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
