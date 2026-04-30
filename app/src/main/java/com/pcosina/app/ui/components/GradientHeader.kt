package com.pcosina.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaSurface
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt

@Composable
fun GradientHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    containerHeight: Int = 176,
    colors: List<Color>? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(30.dp)
    val colorScheme = MaterialTheme.colorScheme
    val compactHeader = containerHeight <= 136
    val gradientColors = colors ?: listOf(
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
    )
    val accentBrush = remember(gradientColors) {
        Brush.horizontalGradient(
            listOf(
                gradientColors[0].copy(alpha = 0.92f),
                gradientColors.getOrElse(1) { gradientColors[0] }.copy(alpha = 0.72f),
                gradientColors.getOrElse(2) { gradientColors[0] }.copy(alpha = 0.46f),
            )
        )
    }
    val backgroundBrush = remember(gradientColors) {
        Brush.verticalGradient(
            listOf(
                PcosinaSurface,
                gradientColors[0].copy(alpha = 0.06f),
                PcosinaSurfaceAlt
            )
        )
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .height(containerHeight.dp)
                .clip(shape)
                .border(
                    width = 1.dp,
                    color = gradientColors[0].copy(alpha = 0.18f),
                    shape = shape
                )
                .background(backgroundBrush)
                .padding(
                    horizontal = if (compactHeader) 14.dp else 18.dp,
                    vertical = if (compactHeader) 14.dp else 18.dp
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(if (compactHeader) 92.dp else 132.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(accentBrush)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compactHeader) 16.dp else 20.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = if (compactHeader) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        color = PcosinaDeepRose,
                        maxLines = if (compactHeader) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(Color.White.copy(alpha = 0.9f))
                                .border(
                                    width = 1.dp,
                                    color = gradientColors[0].copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.large
                                )
                                .padding(horizontal = 12.dp, vertical = if (compactHeader) 8.dp else 10.dp)
                        ) {
                            Text(
                                text = subtitle,
                                style = if (compactHeader) {
                                    MaterialTheme.typography.bodySmall
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = colorScheme.onSurfaceVariant,
                                maxLines = if (compactHeader) 2 else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.95f))
                            .border(
                                width = 1.dp,
                                color = gradientColors[0].copy(alpha = 0.16f),
                                shape = CircleShape
                            )
                            .padding(
                                horizontal = if (compactHeader) 8.dp else 10.dp,
                                vertical = if (compactHeader) 6.dp else 8.dp
                            ),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        trailing()
                    }
                }
            }
        }
    }
}
