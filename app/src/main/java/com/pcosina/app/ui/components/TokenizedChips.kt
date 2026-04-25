package com.pcosina.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.UiMotionTokens

@Composable
fun TokenizedFilterChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    labelMaxWidth: Dp,
    modifier: Modifier = Modifier,
    colors: SelectableChipColors? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val scale = animateFloatAsState(
        targetValue = if (selected) UiMotionTokens.FocusChipSelectedScale else 1f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.78f),
        label = "filterChipScale"
    )
    val yOffset = animateFloatAsState(
        targetValue = if (selected) -2f else 0f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.82f),
        label = "filterChipYOffset"
    )
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            translationY = yOffset.value
        }
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
            label = {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = labelMaxWidth)
                )
            },
            shape = MaterialTheme.shapes.large,
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = colorScheme.outlineVariant.copy(alpha = 0.65f),
                selectedBorderColor = colorScheme.primary.copy(alpha = 0.22f),
                disabledBorderColor = colorScheme.outlineVariant.copy(alpha = 0.35f),
                disabledSelectedBorderColor = colorScheme.primary.copy(alpha = 0.16f),
                borderWidth = 1.dp,
                selectedBorderWidth = 1.dp
            ),
            colors = colors ?: FilterChipDefaults.filterChipColors(
                containerColor = colorScheme.surface,
                labelColor = colorScheme.onSurface,
                iconColor = colorScheme.onSurfaceVariant,
                selectedContainerColor = colorScheme.primary.copy(alpha = 0.12f),
                selectedLabelColor = colorScheme.primary,
                selectedLeadingIconColor = colorScheme.primary,
                selectedTrailingIconColor = colorScheme.primary
            )
        )
    }
}
