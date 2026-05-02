package com.pcosina.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.util.LockedFlowCopy

@Composable
internal fun ProgressLoggingPolicyLearnMoreChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("progress_logging_policy_learn_more"),
        label = { Text(LockedFlowCopy.LearnMoreLabel) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    )
}

@Composable
internal fun ProgressJumpToTodayAction(
    todayIndexInWeek: Int,
    onJump: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(UiMotionTokens.JumpToTodayRevealMs)) +
            expandVertically(animationSpec = tween(UiMotionTokens.JumpToTodayRevealMs))
    ) {
        if (todayIndexInWeek >= 0) {
            OutlinedButton(
                onClick = onJump,
                modifier = modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("progress_jump_to_today"),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Jump to Today")
            }
        } else {
            Text(
                text = "Today is outside this selected week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
    }
}
