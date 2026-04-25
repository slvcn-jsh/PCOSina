package com.pcosina.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class GuidedJourneyStep(
    val stepIndex: Int,
    val totalSteps: Int = 6,
    val title: String,
    val rationale: String,
    val route: String,
    val requiresInternet: Boolean = false,
    val ctaLabel: String = "Continue"
)

@Composable
fun GuidedJourneyCard(
    step: GuidedJourneyStep,
    onContinue: (GuidedJourneyStep) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val progress = (step.stepIndex.toFloat() / step.totalSteps.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val remainingSteps = (step.totalSteps - step.stepIndex).coerceAtLeast(0)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = colorScheme.outlineVariant.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GuidedJourneyPill(
                    text = "Next step",
                    emphasized = true
                )
                GuidedJourneyPill(
                    text = "Step ${step.stepIndex} of ${step.totalSteps}",
                    emphasized = false
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        color = colorScheme.primary.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(999.dp)
                    ),
                color = colorScheme.primary,
                trackColor = colorScheme.primary.copy(alpha = 0.12f),
            )
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = step.rationale,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GuidedJourneyPill(
                    text = if (remainingSteps == 0) "Final step" else "$remainingSteps left",
                    emphasized = false
                )
                if (step.requiresInternet) {
                    GuidedJourneyPill(
                        text = "Internet",
                        emphasized = false
                    )
                }
            }
            Button(
                onClick = { onContinue(step) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(step.ctaLabel, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun GuidedJourneyPill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (emphasized) {
            colorScheme.primary.copy(alpha = 0.10f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.70f)
        },
        contentColor = if (emphasized) {
            colorScheme.primary
        } else {
            colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
