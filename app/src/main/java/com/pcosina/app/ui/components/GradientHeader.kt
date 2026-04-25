package com.pcosina.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.UiMotionTokens

@Composable
fun GradientHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    containerHeight: Int = 176,
    colors: List<Color>? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val colorScheme = MaterialTheme.colorScheme
    val compactHeader = containerHeight <= 136
    val gradientColors = colors ?: listOf(
        colorScheme.primary,
        colorScheme.secondary,
        colorScheme.tertiary,
    )
    val infiniteTransition = rememberInfiniteTransition(label = "headerFloat")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(UiMotionTokens.HeaderGlowMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headerGlowAlpha"
    )
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(UiMotionTokens.HeaderDriftMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headerDrift"
    )
    val overlayGradient = remember(glowAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f + (glowAlpha / 5f)),
                Color.Transparent,
                Color.Black.copy(alpha = 0.08f)
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight.dp)
            .clip(shape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = shape
            )
            .background(
                brush = Brush.linearGradient(gradientColors)
            )
            .padding(
                horizontal = if (compactHeader) 14.dp else 20.dp,
                vertical = if (compactHeader) 14.dp else 20.dp
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = overlayGradient
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
                .width(if (compactHeader) 64.dp else 116.dp)
                .height(if (compactHeader) 64.dp else 116.dp)
                .graphicsLayer {
                    translationX = drift
                    translationY = drift / 2f
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f + (glowAlpha / 6f)))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 4.dp)
                .width(if (compactHeader) 52.dp else 92.dp)
                .height(if (compactHeader) 52.dp else 92.dp)
                .graphicsLayer {
                    translationX = -drift / 2f
                    translationY = -drift / 3f
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 10.dp)
                .width(if (compactHeader) 40.dp else 72.dp)
                .height(if (compactHeader) 40.dp else 72.dp)
                .graphicsLayer {
                    translationY = drift / 2f
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = if (compactHeader) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    color = Color.White,
                    maxLines = if (compactHeader) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    if (compactHeader) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(Color.White.copy(alpha = 0.14f))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.large
                                )
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.large)
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.large
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.14f),
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
        Spacer(Modifier.height(0.dp))
    }
}
