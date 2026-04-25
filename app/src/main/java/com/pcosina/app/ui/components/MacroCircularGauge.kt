package com.pcosina.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.ui.theme.UiMotionTokens
import kotlin.math.roundToInt

@Composable
fun MacroCircularGauge(
    label: String,
    currentValue: Int,
    targetValue: Int,
    unit: String = "g",
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = if (targetValue > 0) currentValue.toFloat() / targetValue.toFloat() else 0f
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress = animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = tween(UiMotionTokens.MetricGaugeProgressMs),
        label = "macroGaugeProgress"
    )
    val colorScheme = MaterialTheme.colorScheme
    val targetLabel = when {
        targetValue <= 0 -> "No target"
        progress > 1f -> "Above target"
        progress >= 1f -> "Target met"
        else -> "${(clampedProgress * 100f).roundToInt()}% of $targetValue$unit"
    }
    val statusLabel = when {
        targetValue <= 0 -> "No target"
        progress > 1f -> "Above target"
        progress >= 1f -> "Target met"
        progress >= 0.75f -> "On track"
        progress >= 0.40f -> "Building up"
        else -> "Needs attention"
    }
    
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = color.copy(alpha = 0.10f),
                contentColor = color
            ) {
                Text(
                    text = statusLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
                )
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(88.dp),
                    color = color.copy(alpha = 0.14f),
                    strokeWidth = 9.dp,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round,
                )
                CircularProgressIndicator(
                    progress = { animatedProgress.value },
                    modifier = Modifier.size(88.dp),
                    color = color,
                    strokeWidth = 9.dp,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round,
                )
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$currentValue",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            ),
                            color = color
                        )
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = targetLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
