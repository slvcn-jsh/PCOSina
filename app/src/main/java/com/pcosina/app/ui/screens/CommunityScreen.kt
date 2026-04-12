package com.pcosina.app.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.theme.UiSpacingTokens

@Composable
fun CommunityScreen(
    onBack: (() -> Unit)? = null,
    onFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
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
                    title = "Community",
                    subtitle = "Guides, meal tips, and simple support for your week",
                    containerHeight = 176
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
            CommunityCard(
                icon = Icons.Filled.CheckCircle,
                iconTint = colorScheme.primary,
                title = "Start here this week",
                body = "If you're unsure where to begin, keep it simple: review today's meals, check your grocery list, and log one meal before the day ends.",
                bullets = listOf(
                    "Open your plan and check today's meals first.",
                    "Use the grocery list as your shopping guide, not a strict rulebook.",
                    "Log one meal or reflection today to build a steady habit."
                )
            )
        }

        item {
            CommunityCard(
                icon = Icons.Filled.RestaurantMenu,
                iconTint = colorScheme.secondary,
                title = "Beginner guides",
                body = "Short, practical help for common first-week questions.",
                bullets = listOf(
                    "How to build a realistic pantry before generating a plan",
                    "What to do if you don't like a suggested meal",
                    "How to use grocery, swaps, and progress together"
                )
            )
        }

        item {
            CommunityCard(
                icon = Icons.Filled.Favorite,
                iconTint = colorScheme.tertiary,
                title = "PCOS-friendly eating basics",
                body = "PCOSina focuses on repeatable habits that feel doable in real life.",
                bullets = listOf(
                    "Aim for balanced meals with protein, fiber, and steady energy.",
                    "Budget-friendly meals still count when they match your week.",
                    "Consistency matters more than perfect days."
                )
            )
        }

        item {
            CommunityCard(
                icon = Icons.Filled.Info,
                iconTint = colorScheme.primary,
                title = "Community corner",
                body = "This space is reserved for future stories, shared wins, and learning sessions.",
                bullets = listOf(
                    "Member stories and practical wins",
                    "Simple weekly challenges",
                    "Coach or community Q&A highlights"
                ),
                placeholderLabel = "Coming soon"
            )
        }

        item {
            Card(
                onClick = onFeedback,
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiSpacingTokens.CardContentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Need a hand?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Send feedback if something feels confusing, missing, or hard to use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
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
    title: String,
    body: String,
    bullets: List<String>,
    placeholderLabel: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    placeholderLabel?.let {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(it) },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = colorScheme.surfaceVariant,
                                disabledLabelColor = colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            bullets.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
