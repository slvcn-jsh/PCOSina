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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip
import com.pcosina.app.ui.theme.UiSpacingTokens

private enum class CommunityFocus {
    Start,
    Guides,
    Support,
}

@Composable
fun CommunityScreen(
    onBack: (() -> Unit)? = null,
    onFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var focusKey by rememberSaveable { mutableStateOf(CommunityFocus.Start.name) }
    val focus = remember(focusKey) { CommunityFocus.valueOf(focusKey) }
    val focusOptions = remember {
        listOf(
            ScreenFocusOption(
                key = CommunityFocus.Start.name,
                label = "Start",
                summary = "See the first thing to do this week."
            ),
            ScreenFocusOption(
                key = CommunityFocus.Guides.name,
                label = "Guides",
                summary = "Open simple help for planning and eating."
            ),
            ScreenFocusOption(
                key = CommunityFocus.Support.name,
                label = "Support",
                summary = "Send feedback when something feels unclear."
            )
        )
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
                    title = "Help",
                    subtitle = "Simple help for this week",
                    containerHeight = 116
                )
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        item {
            ScreenFocusStrip(
                title = "Show",
                options = focusOptions,
                selectedKey = focusKey,
                onSelect = { focusKey = it },
                labelMaxWidth = 124.dp
            )
        }
        if (focus == CommunityFocus.Start) {
            item {
            CommunityCard(
                icon = Icons.Filled.CheckCircle,
                iconTint = colorScheme.primary,
                statusLabel = "Start here",
                title = "Start here this week",
                body = "If you're unsure where to begin, keep it simple: review today's meals, check your grocery list, and log one meal before the day ends.",
                bullets = listOf(
                    "Open your plan and check today's meals first.",
                    "Use the grocery list as your shopping guide, not a strict rulebook.",
                    "Log one meal or reflection today to build a steady habit."
                )
            )
        }
        }

        if (focus == CommunityFocus.Guides) {
            item {
            CommunityCard(
                icon = Icons.Filled.RestaurantMenu,
                iconTint = colorScheme.secondary,
                statusLabel = "Easy tips",
                title = "Simple planning guides",
                body = "Short, practical help for common questions.",
                bullets = listOf(
                    "Build a realistic pantry before you create a plan.",
                    "Change a meal if it does not fit your taste or budget.",
                    "Use Grocery, meal changes, and Progress together.",
                    "Aim for balanced meals with protein, fiber, and steady energy.",
                    "Budget-friendly meals still count when they match your week.",
                    "Consistency matters more than perfect days."
                )
            )
        }
        }

        if (focus == CommunityFocus.Support) {
            item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacingTokens.CardContentPadding),
                    verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.CardContentGap)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.primary.copy(alpha = 0.10f),
                            contentColor = colorScheme.primary
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Email,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Need a hand?",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            SupportStatusPill(
                                text = "Send feedback",
                                emphasized = true
                            )
                            Text(
                                text = "Tell us if something feels confusing, missing, or harder than it should be. Shared tips and wins can live here later too.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onFeedback,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            text = "Send feedback now",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CommunityCard(
    icon: ImageVector,
    iconTint: Color,
    statusLabel: String,
    title: String,
    body: String,
    bullets: List<String>,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacingTokens.CardContentPadding),
            verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.CardContentGap)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconTint.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SupportStatusPill(
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
            bullets.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .background(iconTint, CircleShape)
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportStatusPill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
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
