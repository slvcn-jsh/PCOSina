package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader

@Composable
fun GoalSelectionScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var weightLoss by rememberSaveable { mutableStateOf(false) }
    var symptomMgmt by rememberSaveable { mutableStateOf(false) }
    var generalHealth by rememberSaveable { mutableStateOf(false) }
    val hasSelection = weightLoss || symptomMgmt || generalHealth

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
            text = "What would you like to achieve with PCOSINA?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        GoalCard(
            title = "Weight Loss",
            description = "Achieve healthy weight through balanced nutrition",
            selected = weightLoss,
            onToggle = { weightLoss = !weightLoss },
        )

        GoalCard(
            title = "PCOS Symptom Management",
            description = "Control insulin resistance and hormonal balance",
            selected = symptomMgmt,
            onToggle = { symptomMgmt = !symptomMgmt },
        )

        GoalCard(
            title = "General Health Improvement",
            description = "Boost energy and overall wellness",
            selected = generalHealth,
            onToggle = { generalHealth = !generalHealth },
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
            onClick = { if (hasSelection) onFinish() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = hasSelection,
            colors = ButtonDefaults.buttonColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = androidx.compose.ui.graphics.Color.White,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (hasSelection)
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color(0xFF0ABF6A),
                                    androidx.compose.ui.graphics.Color(0xFF2D9CDB),
                                )
                            )
                        else androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant,
                            )
                        )
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Text("Next")
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
            .graphicsLayer(scaleX = scale, scaleY = scale),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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

