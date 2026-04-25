package com.pcosina.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.UiMotionTokens

@Composable
fun ExpandableSection(
    title: String,
    subtitle: String? = null,
    defaultExpanded: Boolean = false,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var internalExpanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    val isExpanded = expanded ?: internalExpanded
    val colorScheme = MaterialTheme.colorScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(UiMotionTokens.ExpandableChevronMs),
        label = "expandableChevronRotation"
    )
    val sectionStateLabel = if (isExpanded) "Expanded" else "Collapsed"
    fun toggleExpanded() {
        val next = !isExpanded
        if (onExpandedChange != null) {
            onExpandedChange(next)
        } else {
            internalExpanded = next
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(animationSpec = tween(UiMotionTokens.ExpandableContentMs))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { stateDescription = sectionStateLabel },
                shape = RoundedCornerShape(18.dp),
                color = if (isExpanded) {
                    colorScheme.surfaceVariant.copy(alpha = 0.38f)
                } else {
                    colorScheme.surface
                },
                border = BorderStroke(
                    1.dp,
                    if (isExpanded) {
                        colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        colorScheme.outlineVariant.copy(alpha = 0.45f)
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClick = { toggleExpanded() }
                        )
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isExpanded) {
                                colorScheme.primary.copy(alpha = 0.10f)
                            } else {
                                colorScheme.surfaceVariant.copy(alpha = 0.70f)
                            },
                            contentColor = if (isExpanded) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            }
                        ) {
                            Text(
                                text = if (isExpanded) "Expanded" else "Tap to expand",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.primary.copy(alpha = 0.10f),
                        contentColor = colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(8.dp)
                                .rotate(chevronRotation),
                            tint = colorScheme.primary
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(UiMotionTokens.ExpandableContentMs)) +
                    expandVertically(animationSpec = tween(UiMotionTokens.ExpandableContentMs)),
                exit = fadeOut(animationSpec = tween(UiMotionTokens.ExpandableContentMs / 2)) +
                    shrinkVertically(animationSpec = tween(UiMotionTokens.ExpandableContentMs)),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.28f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.40f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
