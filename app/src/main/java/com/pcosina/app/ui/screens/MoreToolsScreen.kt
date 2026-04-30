package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip
import com.pcosina.app.ui.theme.UiSpacingTokens

private enum class MoreToolsFocus {
    Guides,
    Feedback,
    Team,
}

@Composable
fun MoreToolsScreen(
    onBack: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenMethodology: () -> Unit,
    onFeedback: () -> Unit,
    showAdminTools: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = colorScheme.surface
    var focusKey by rememberSaveable { mutableStateOf(MoreToolsFocus.Guides.name) }
    val focusOptions = remember(showAdminTools) {
        buildList {
            add(
                ScreenFocusOption(
                    key = MoreToolsFocus.Guides.name,
                    label = "Guides",
                    summary = "Open quick help first."
                )
            )
            add(
                ScreenFocusOption(
                    key = MoreToolsFocus.Feedback.name,
                    label = "Feedback",
                    summary = "Report an issue fast."
                )
            )
            if (showAdminTools) {
                add(
                    ScreenFocusOption(
                        key = MoreToolsFocus.Team.name,
                        label = "Team",
                        summary = "Open team-only planner guides."
                    )
                )
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = UiSpacingTokens.SectionGap),
        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "Tools",
                    subtitle = "Quick help and extra links",
                    containerHeight = 116
                )
                IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.primary
                    )
                }
            }
        }

        item {
            ScreenFocusStrip(
                title = "Show",
                options = focusOptions,
                selectedKey = focusKey,
                onSelect = { focusKey = it },
                labelMaxWidth = 120.dp
            )
        }
        if (focusKey == MoreToolsFocus.Guides.name) {
            item {
            ToolActionCard(
                icon = Icons.Filled.Info,
                iconTint = colorScheme.primary,
                statusLabel = "Guides",
                title = "Open help guides",
                body = "See quick tips for planning, shopping, and staying on track.",
                actionLabel = "Open guides",
                onClick = onOpenSupport,
                modifier = Modifier.testTag("more_tools_support_card")
            )
        }
        }

        if (focusKey == MoreToolsFocus.Feedback.name) {
            item {
            ToolActionCard(
                icon = Icons.Filled.Email,
                iconTint = colorScheme.secondary,
                statusLabel = "Feedback",
                title = "Tell us what felt unclear",
                body = "Report confusing screens, missing help, or bugs with a prefilled email draft.",
                actionLabel = "Send feedback",
                onClick = onFeedback,
                modifier = Modifier.testTag("more_tools_feedback_card")
            )
        }
        }

        if (showAdminTools && focusKey == MoreToolsFocus.Team.name) {
            item {
                ToolActionCard(
                    icon = Icons.Filled.Settings,
                    iconTint = colorScheme.tertiary,
                    statusLabel = "Team only",
                    title = "Open planner guide",
                    body = "See how the planner turns saved food rules into a weekly meal plan.",
                    actionLabel = "Open guide",
                    onClick = onOpenMethodology,
                    modifier = Modifier.testTag("more_tools_methodology_card")
                )
            }
        }
    }
}

@Composable
private fun ToolActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    statusLabel: String,
    title: String,
    body: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(UiSpacingTokens.CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.CardContentGap)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconTint.copy(alpha = 0.10f),
                    contentColor = iconTint
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HelpToolsStatusPill(
                        text = statusLabel,
                        emphasized = true
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onClick,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = iconTint.copy(alpha = 0.12f),
                    contentColor = iconTint
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = actionLabel,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpToolsStatusPill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (emphasized) {
            colorScheme.primary.copy(alpha = 0.10f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.65f)
        },
        contentColor = if (emphasized) {
            colorScheme.primary
        } else {
            colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
