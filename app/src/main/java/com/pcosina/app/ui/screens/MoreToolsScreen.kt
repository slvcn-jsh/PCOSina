package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.FocusSummaryCard
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.RefinedFeatureCard
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip

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
    val focusSummaryTitle = when (focusKey) {
        MoreToolsFocus.Guides.name -> "Open the right guide fast."
        MoreToolsFocus.Feedback.name -> "Send one clear note."
        else -> "Keep review tools separate."
    }
    val focusSummaryBody = when (focusKey) {
        MoreToolsFocus.Guides.name -> "This screen should feel like a quick launcher for help, not a second settings page."
        MoreToolsFocus.Feedback.name -> "Use this space when something felt confusing, visually off, or harder than it should be."
        else -> "Internal review links stay tucked away so everyday planning stays simple."
    }
    val focusSummaryHighlights = buildList {
        add(
            if (showAdminTools) {
                "Admin tools are available for this account."
            } else {
                "Normal accounts only see help and feedback."
            }
        )
        add(
            when (focusKey) {
                MoreToolsFocus.Guides.name -> "Open the guide that answers the current question."
                MoreToolsFocus.Feedback.name -> "Keep feedback short, specific, and tied to one screen or step."
                else -> "Use methodology and admin-only tools only during review or testing."
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "More Tools",
                    subtitle = "Quick help, feedback, and team-only links.",
                    containerHeight = 108
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
        item {
            FocusSummaryCard(
                badge = "Quick access",
                title = focusSummaryTitle,
                body = focusSummaryBody,
                accentColor = colorScheme.secondary,
                highlights = focusSummaryHighlights
            )
        }
        if (focusKey == MoreToolsFocus.Guides.name) {
            item {
                RefinedFeatureCard(
                    icon = Icons.Filled.Info,
                    accentColor = colorScheme.primary,
                    statusLabel = "Guides",
                    title = "Open help guides",
                    body = "See quick tips for planning, shopping, and staying on track without leaving the refined shell.",
                    highlights = listOf(
                        "Open support when you need simple planning or grocery guidance.",
                        "Keep help links separate from your plan so the weekly flow stays focused."
                    ),
                    actionLabel = "Open guides",
                    onAction = onOpenSupport,
                    modifier = Modifier.testTag("more_tools_support_card")
                )
            }
        }

        if (focusKey == MoreToolsFocus.Feedback.name) {
            item {
                RefinedFeatureCard(
                    icon = Icons.Filled.Email,
                    accentColor = colorScheme.secondary,
                    statusLabel = "Feedback",
                    title = "Tell us what felt unclear",
                    body = "Report confusing screens, missing help, or bugs with a prefilled email draft.",
                    highlights = listOf(
                        "Use this when a screen looks inconsistent or a task takes too many steps.",
                        "Short, specific notes make the next UI pass easier to verify."
                    ),
                    actionLabel = "Send feedback",
                    onAction = onFeedback,
                    modifier = Modifier.testTag("more_tools_feedback_card")
                )
            }
        }

        if (showAdminTools && focusKey == MoreToolsFocus.Team.name) {
            item {
                RefinedFeatureCard(
                    icon = Icons.Filled.Settings,
                    accentColor = colorScheme.tertiary,
                    statusLabel = "Team only",
                    title = "Open planner guide",
                    body = "Review how the planner turns saved food rules into a weekly meal plan before you test deeper flows.",
                    highlights = listOf(
                        "This tab is for setup, review, and internal troubleshooting only.",
                        "Regular users should stay on plan, grocery, progress, and support."
                    ),
                    actionLabel = "Open guide",
                    onAction = onOpenMethodology,
                    modifier = Modifier.testTag("more_tools_methodology_card")
                )
            }
        }
    }
}
