package com.pcosina.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = containerColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
        Text(
            text = data.message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!data.actionLabel.isNullOrBlank() && onAction != null) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = data.actionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor,
                    maxLines = 1
                )
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
        modifier = modifier.animateContentSize(),
        colors = colors,
        shape = MaterialTheme.shapes.medium
    ) {
        when (state) {
            FeedbackActionState.Idle -> {
                Text(idleLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FeedbackActionState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(loadingLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FeedbackActionState.Success -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(successLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FeedbackActionState.Error -> {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(errorLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SyncStatusChip(
    state: FeedbackActionState,
    modifier: Modifier = Modifier
) {
    val (label, iconTint) = when (state) {
        FeedbackActionState.Idle -> "Ready" to MaterialTheme.colorScheme.onSurfaceVariant
        FeedbackActionState.Loading -> "Syncing…" to MaterialTheme.colorScheme.primary
        FeedbackActionState.Success -> "Synced" to MaterialTheme.colorScheme.primary
        FeedbackActionState.Error -> "Failed" to MaterialTheme.colorScheme.error
    }
    AssistChip(
        onClick = { },
        modifier = modifier.heightIn(min = 36.dp),
        enabled = false,
        label = { Text(label, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = if (state == FeedbackActionState.Error) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
            disabledLeadingIconContentColor = iconTint
        )
    )
}

@Composable
fun StatusCenterCard(
    queuedActionsLabel: String? = null,
    syncLabel: String? = null,
    planRangeLabel: String,
    nextReminderLabel: String? = null,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Status Center",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            queuedActionsLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            syncLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = planRangeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            nextReminderLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
