@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pcosina.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pcosina.app.data.model.MealCheckIn
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 407.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFFFF6FA),
            shadowElevation = 18.dp,
            border = BorderStroke(1.dp, PcosinaSoftPink.copy(alpha = 0.58f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFFFF6E8),
                        border = BorderStroke(1.dp, Color(0xFFECD2A9).copy(alpha = 0.58f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.18f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Restaurant,
                                    contentDescription = null,
                                    tint = PcosinaPink,
                                    modifier = Modifier.padding(9.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = mealLabel,
                                    modifier = Modifier.semantics { heading() },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = PcosinaPink
                                )
                                Text(
                                    text = mealTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.Black
                                )
                                Text(
                                    text = goalMealCheckInPrompt(goal),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PcosinaMuted
                                )
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (hasAnyResponse) PcosinaSoftPink.copy(alpha = 0.62f) else PcosinaSurfaceAlt,
                        contentColor = if (hasAnyResponse) PcosinaPink else PcosinaMuted
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (hasAnyResponse) Icons.Filled.CheckCircle else Icons.Filled.Restaurant,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = progressSummary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
                            )
                        }
                    }
                    Text(
                        text = "Pick the numbers that best match how this meal felt. You can save a partial check-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PcosinaMuted
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MealCheckInActionButton(
                        text = "Not now",
                        filled = false,
                        enabled = true,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    MealCheckInActionButton(
                        text = "Save check-in",
                        filled = true,
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
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MealCheckInScaleRow(
    title: String,
    selected: Int?,
    lowAnchor: String,
    highAnchor: String,
    onSelected: (Int) -> Unit
) {
    val statusLabel = selected?.let { "Selected: $it/5" } ?: "Choose a number"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.Black
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected != null) PcosinaSoftPink.copy(alpha = 0.62f) else PcosinaSurfaceAlt,
                contentColor = if (selected != null) PcosinaPink else PcosinaMuted
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..5).forEach { value ->
                val active = selected == value
                Surface(
                    modifier = Modifier
                        .size(46.dp)
                        .clickable { onSelected(value) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) PcosinaPink else Color.White,
                    border = BorderStroke(1.dp, if (active) PcosinaPink else PcosinaMuted.copy(alpha = 0.28f)),
                    shadowElevation = if (active) 4.dp else 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (active) Color.White else PcosinaDeepRose,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFFFF6E8),
            border = BorderStroke(1.dp, Color(0xFFECD2A9).copy(alpha = 0.58f))
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
                    color = PcosinaMuted
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "5 = $highAnchor",
                    style = MaterialTheme.typography.labelSmall,
                    color = PcosinaMuted
                )
            }
        }
    }
}

@Composable
private fun MealCheckInActionButton(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = when {
            filled && enabled -> PcosinaPink
            filled -> PcosinaPink.copy(alpha = 0.46f)
            else -> Color.White
        },
        border = BorderStroke(1.dp, if (filled) PcosinaPink else PcosinaPink.copy(alpha = 0.82f)),
        shadowElevation = if (filled && enabled) 8.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = if (filled) Color.White else PcosinaPink,
                textAlign = TextAlign.Center
            )
        }
    }
}
