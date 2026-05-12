@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pcosina.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.PcosinaSuccess
import com.pcosina.app.ui.theme.UiMotionTokens

enum class FeedbackActionState {
    Idle,
    Loading,
    Success,
    Error
}

enum class FeedbackBannerTone {
    Success,
    Loading,
    Error
}

data class FeedbackBannerData(
    val tone: FeedbackBannerTone,
    val message: String,
    val actionLabel: String? = null
)

@Composable
fun AppFeedbackBanner(
    data: FeedbackBannerData,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor: Color
    val toneLabel = when (data.tone) {
        FeedbackBannerTone.Success -> "Saved"
        FeedbackBannerTone.Loading -> "Working"
        FeedbackBannerTone.Error -> "Action needed"
    }
    val icon = when (data.tone) {
        FeedbackBannerTone.Success -> Icons.Filled.CheckCircle
        FeedbackBannerTone.Loading -> Icons.Filled.Notifications
        FeedbackBannerTone.Error -> Icons.Filled.ErrorOutline
    }
    val accentColor: Color
    when (data.tone) {
        FeedbackBannerTone.Success -> {
            containerColor = colorScheme.primary.copy(alpha = 0.10f)
            accentColor = colorScheme.primary
        }
        FeedbackBannerTone.Loading -> {
            containerColor = colorScheme.surfaceVariant
            accentColor = colorScheme.primary
        }
        FeedbackBannerTone.Error -> {
            containerColor = colorScheme.errorContainer
            accentColor = colorScheme.error
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .semantics { stateDescription = toneLabel },
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (data.tone == FeedbackBannerTone.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.10f),
                    contentColor = accentColor
                ) {
                    Text(
                        text = toneLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = data.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!data.actionLabel.isNullOrBlank() && onAction != null) {
                    Surface(
                        onClick = onAction,
                        shape = RoundedCornerShape(14.dp),
                        color = accentColor.copy(alpha = 0.08f),
                        contentColor = accentColor,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = data.actionLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingActionButton(
    state: FeedbackActionState,
    idleLabel: String,
    loadingLabel: String,
    successLabel: String,
    errorLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
    )
) {
    val haptics = LocalHapticFeedback.current
    val lastHapticState = remember { mutableStateOf(FeedbackActionState.Idle) }
    val buttonStateLabel = when (state) {
        FeedbackActionState.Idle -> "Ready"
        FeedbackActionState.Loading -> "Working"
        FeedbackActionState.Success -> "Completed"
        FeedbackActionState.Error -> "Needs attention"
    }
    val buttonScale by animateFloatAsState(
        targetValue = when (state) {
            FeedbackActionState.Success -> 1.02f
            FeedbackActionState.Loading -> 0.99f
            else -> 1f
        },
        animationSpec = tween(UiMotionTokens.PrimaryActionStateMs),
        label = "loadingActionButtonScale"
    )
    LaunchedEffect(state) {
        if (state != lastHapticState.value) {
            when (state) {
                FeedbackActionState.Success -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                FeedbackActionState.Error -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                else -> Unit
            }
            lastHapticState.value = state
        }
    }
    Button(
        onClick = onClick,
        enabled = enabled && state != FeedbackActionState.Loading,
        modifier = modifier
            .heightIn(min = 52.dp)
            .animateContentSize()
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .semantics { stateDescription = buttonStateLabel },
        colors = colors,
        shape = MaterialTheme.shapes.large
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(animationSpec = tween(UiMotionTokens.PrimaryActionStateMs)) +
                    slideInVertically(animationSpec = tween(UiMotionTokens.PrimaryActionStateMs)) { it / 2 }) togetherWith
                    (fadeOut(animationSpec = tween(UiMotionTokens.PrimaryActionStateMs / 2)) +
                        slideOutVertically(animationSpec = tween(UiMotionTokens.PrimaryActionStateMs / 2)) { -it / 2 })
            },
            label = "loadingActionButtonContent"
        ) { currentState ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (currentState) {
                    FeedbackActionState.Idle -> {
                        Text(
                            idleLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FeedbackActionState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            loadingLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FeedbackActionState.Success -> {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            successLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FeedbackActionState.Error -> {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            errorLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusChip(
    state: FeedbackActionState,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val label: String
    val iconVector: androidx.compose.ui.graphics.vector.ImageVector
    val chipContainer: Color
    val chipContent: Color
    val chipBorder: Color
    when (state) {
        FeedbackActionState.Idle -> {
            label = "Sync ready"
            iconVector = Icons.Filled.CheckCircle
            chipContainer = colorScheme.primary.copy(alpha = 0.10f)
            chipContent = colorScheme.primary
            chipBorder = colorScheme.primary.copy(alpha = 0.20f)
        }
        FeedbackActionState.Loading -> {
            label = "Syncing"
            iconVector = Icons.Filled.Refresh
            chipContainer = colorScheme.surface
            chipContent = colorScheme.primary
            chipBorder = colorScheme.primary.copy(alpha = 0.18f)
        }
        FeedbackActionState.Success -> {
            label = "Synced"
            iconVector = Icons.Filled.CheckCircle
            chipContainer = PcosinaSuccess.copy(alpha = 0.12f)
            chipContent = PcosinaSuccess
            chipBorder = PcosinaSuccess.copy(alpha = 0.22f)
        }
        FeedbackActionState.Error -> {
            label = "Offline-safe"
            iconVector = Icons.Filled.Info
            chipContainer = colorScheme.surfaceVariant
            chipContent = colorScheme.onSurfaceVariant
            chipBorder = colorScheme.outline.copy(alpha = 0.30f)
        }
    }
    Surface(
        modifier = modifier
            .heightIn(min = 38.dp)
            .semantics { stateDescription = label },
        shape = RoundedCornerShape(999.dp),
        color = chipContainer,
        contentColor = chipContent,
        border = BorderStroke(1.dp, chipBorder),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (state == FeedbackActionState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = chipContent
                )
            } else {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = chipContent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = chipContent,
                maxLines = 1
            )
        }
    }
}

@Composable
fun StatusCenterCard(
    queuedActionsLabel: String? = null,
    syncLabel: String? = null,
    planRangeLabel: String,
    nextReminderLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(UiMotionTokens.ExpandableContentMs)),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = colorScheme.surface
        ),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.70f)),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Status center",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                queuedActionsLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    StatusCenterItem(
                        icon = Icons.Filled.CheckCircle,
                        label = "Now",
                        text = label
                    )
                }
                syncLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    StatusCenterItem(
                        icon = Icons.Filled.Refresh,
                        label = "Ready",
                        text = label
                    )
                }
                StatusCenterItem(
                    icon = Icons.Filled.Info,
                    label = "Guide",
                    text = planRangeLabel
                )
                nextReminderLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    StatusCenterItem(
                        icon = Icons.Filled.Notifications,
                        label = "Next",
                        text = label
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCenterItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    text: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 144.dp, max = 260.dp)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = colorScheme.primary
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(7.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.primary
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
