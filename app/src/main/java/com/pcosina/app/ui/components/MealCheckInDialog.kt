package com.pcosina.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "How did this meal feel?",
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$mealLabel • $mealTitle",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = goalMealCheckInPrompt(goal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MealCheckInScaleRow(
                    title = "Energy after eating",
                    selected = energyLevel,
                    onSelected = { energyLevel = it }
                )
                MealCheckInScaleRow(
                    title = "Fullness",
                    selected = fullnessLevel,
                    onSelected = { fullnessLevel = it }
                )
                MealCheckInScaleRow(
                    title = "Cravings after eating",
                    selected = cravingsLevel,
                    onSelected = { cravingsLevel = it }
                )
                MealCheckInScaleRow(
                    title = "Satisfaction",
                    selected = satisfactionLevel,
                    onSelected = { satisfactionLevel = it }
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Optional note") },
                    placeholder = { Text("Anything you want to remember about this meal?") }
                )
            }
        },
        confirmButton = {
            TextButton(
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
                Text("Skip for now")
            }
        }
    )
}

@Composable
private fun MealCheckInScaleRow(
    title: String,
    selected: Int?,
    onSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..5).forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    modifier = Modifier.heightIn(min = 44.dp),
                    label = { Text(value.toString()) }
                )
            }
        }
    }
}
