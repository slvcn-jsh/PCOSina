package com.pcosina.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.CanvaTokens
import java.util.Locale

@Composable
fun CanvaStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = CanvaTokens.PanelPinkLight,
    contentColor: Color = CanvaTokens.PanelPink,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = background,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CanvaMetricBar(
    label: String,
    valueText: String,
    progress: Float,
    modifier: Modifier = Modifier,
    barColor: Color = CanvaTokens.AccentPinkStrong,
    trackColor: Color = Color.White.copy(alpha = 0.9f),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CanvaTokens.HeadlineMaroon,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                color = CanvaTokens.HeadlineMaroon,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
            shape = RoundedCornerShape(999.dp),
            color = trackColor,
        ) {
            Box(contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(22.dp)
                        .background(barColor, RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
fun CanvaCircularMetric(
    primaryText: String,
    secondaryText: String,
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = CanvaTokens.AccentPinkStrong,
    size: Dp = 140.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(size),
            color = color,
            trackColor = CanvaTokens.ProgressTrack,
            strokeWidth = 12.dp,
        )
        Surface(
            modifier = Modifier.size(size - 28.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, CanvaTokens.SoftOutline),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = CanvaTokens.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = CanvaTokens.HeadlineMaroon,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun canvaMealPalette(mealLabel: String): Pair<Color, Color> {
    return when (mealLabel.lowercase(Locale.ENGLISH)) {
        "breakfast" -> CanvaTokens.BreakfastOrange to Color(0xFF7B4217)
        "lunch" -> CanvaTokens.PanelPinkLight to Color(0xFF7A2C48)
        "dinner" -> CanvaTokens.DinnerViolet to Color(0xFF3544A8)
        else -> CanvaTokens.PanelPinkLight to CanvaTokens.HeadlineMaroon
    }
}
