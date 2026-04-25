package com.pcosina.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.UiMotionTokens

data class CompactWidgetSpec(
    val title: String,
    val value: String,
    val hint: String,
    val accentColor: Color,
    val badge: String? = null,
)

@Composable
fun FocusModePanel(
    targetKey: String,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedContentScope.(String) -> Unit,
) {
    AnimatedContent(
        targetState = targetKey,
        transitionSpec = {
            (fadeIn(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs)) +
                slideInVertically(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs)) { it / 6 } +
                scaleIn(
                    initialScale = 0.98f,
                    animationSpec = tween(UiMotionTokens.FocusPanelScaleMs)
                )) togetherWith
                (fadeOut(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs / 2)) +
                    slideOutVertically(animationSpec = tween(UiMotionTokens.FocusPanelSwapMs / 2)) { -it / 8 } +
                    scaleOut(
                        targetScale = 1.01f,
                        animationSpec = tween(UiMotionTokens.FocusPanelSwapMs / 2)
                    ))
        },
        modifier = modifier.animateContentSize(),
        label = "focusModePanel",
        content = content
    )
}

@Composable
fun CompactWidgetGrid(
    widgets: List<CompactWidgetSpec>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        widgets.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { widget ->
                    CompactMetricWidget(
                        spec = widget,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CompactMetricWidget(
    spec: CompactWidgetSpec,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            spec.accentColor.copy(alpha = 0.16f),
                            spec.accentColor.copy(alpha = 0.07f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )
        Surface(
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, spec.accentColor.copy(alpha = 0.16f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = spec.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(spec.accentColor.copy(alpha = 0.20f))
                                .size(width = 24.dp, height = 4.dp)
                        )
                    }
                    spec.badge?.let { badge ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = spec.accentColor.copy(alpha = 0.12f),
                            contentColor = spec.accentColor
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = spec.value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = spec.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FriendlyEmptyStateCard(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    eyebrow: String = "Ready when you are",
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val floatTransition = rememberInfiniteTransition(label = "emptyStateFloat")
    val glowAlpha by floatTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(UiMotionTokens.EmptyStateGlowMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyStateGlowAlpha"
    )
    val drift by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(UiMotionTokens.EmptyStateFloatMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyStateDrift"
    )
    val shape = RoundedCornerShape(28.dp)
    val accentGradient = remember(accentColor, glowAlpha, surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.16f + (glowAlpha / 4f)),
                accentColor.copy(alpha = 0.08f),
                surfaceColor
            )
        )
    }
    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(accentGradient)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 14.dp)
                .size(72.dp)
                .graphicsLayer {
                    translationX = drift
                    translationY = drift / 2f
                }
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.10f + (glowAlpha / 4f)))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 14.dp)
                .size(width = 56.dp, height = 56.dp)
                .graphicsLayer {
                    translationX = -drift / 2f
                    translationY = -drift / 3f
                }
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.08f))
        )
        Surface(
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        color = accentColor.copy(alpha = if (index == 1) 0.34f else 0.18f),
                                        shape = CircleShape
                                    )
                                    .then(
                                        if (index == 1) {
                                            Modifier.size(width = 20.dp, height = 8.dp)
                                        } else {
                                            Modifier.size(8.dp)
                                        }
                                    )
                            )
                        }
                        Text(
                            text = eyebrow,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = accentColor
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    FilledTonalButton(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentColor.copy(alpha = 0.14f),
                            contentColor = accentColor
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}
