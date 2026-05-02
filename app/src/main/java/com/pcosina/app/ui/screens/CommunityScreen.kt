package com.pcosina.app.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.FocusSummaryCard
import com.pcosina.app.ui.components.RefinedFeatureCard
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip

private enum class CommunityFocus {
    Start,
    Tips,
    Feedback,
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
                summary = "See the easiest next step before the week starts to feel crowded."
            ),
            ScreenFocusOption(
                key = CommunityFocus.Tips.name,
                label = "Tips",
                summary = "Open low-pressure help for planning, groceries, and logging."
            ),
            ScreenFocusOption(
                key = CommunityFocus.Feedback.name,
                label = "Feedback",
                summary = "Tell us what felt confusing, rough, or missing."
            )
        )
    }
    val focusSummaryTitle = when (focus) {
        CommunityFocus.Start -> "Start with the next useful step."
        CommunityFocus.Tips -> "Open only the help you need."
        CommunityFocus.Feedback -> "Feedback stays simple here."
    }
    val focusSummaryBody = when (focus) {
        CommunityFocus.Start -> "This help center should feel like a calm reset, not another task list."
        CommunityFocus.Tips -> "Planning, groceries, and logging should feel lighter after one quick reminder."
        CommunityFocus.Feedback -> "A short note about what felt confusing or rough is enough for a useful report."
    }
    val focusSummaryHighlights = buildList {
        add(
            if (onBack != null) {
                "You can return to the previous screen anytime."
            } else {
                "This support space stays separate from the daily meal tabs."
            }
        )
        add(
            when (focus) {
                CommunityFocus.Start -> "Check today's meals, grocery needs, or one progress log first."
                CommunityFocus.Tips -> "Pick the tip that removes the most friction right now."
                CommunityFocus.Feedback -> "Tell us what you expected and what happened instead."
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
                    title = "Support",
                    subtitle = "Clear help for planning, logging, and sending feedback.",
                    containerHeight = 108
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
                labelMaxWidth = 124.dp,
                helperText = "Switch between first steps, practical tips, and feedback."
            )
        }
        item {
            FocusSummaryCard(
                badge = "Help center",
                title = focusSummaryTitle,
                body = focusSummaryBody,
                accentColor = colorScheme.primary,
                highlights = focusSummaryHighlights
            )
        }
        if (focus == CommunityFocus.Start) {
            item {
                RefinedFeatureCard(
                    icon = Icons.Filled.CheckCircle,
                    accentColor = colorScheme.primary,
                    statusLabel = "Start here",
                    title = "Your easiest next steps",
                    body = "If the week feels noisy, shrink it down to the next useful move instead of trying to do everything at once.",
                    highlights = listOf(
                        "Open your plan and check today first instead of scanning the whole week.",
                        "Use the grocery list as a guide, not as a pass-or-fail checklist.",
                        "Log one meal or reflection today so progress stays easy to restart."
                    )
                )
            }
        }

        if (focus == CommunityFocus.Tips) {
            item {
                RefinedFeatureCard(
                    icon = Icons.Filled.RestaurantMenu,
                    accentColor = colorScheme.secondary,
                    statusLabel = "Helpful tips",
                    title = "Make the week feel lighter",
                    body = "These quick reminders are here to reduce friction, not to give you more rules.",
                    highlights = listOf(
                        "Save a realistic pantry before you create a week so suggestions feel usable.",
                        "Swap meals that miss your taste, time, or budget instead of forcing them.",
                        "Use Plan, Grocery, and Progress together so each screen does less work on its own.",
                        "Balanced meals and steady routines matter more than perfect days."
                    )
                )
            }
        }

        if (focus == CommunityFocus.Feedback) {
            item {
                RefinedFeatureCard(
                    icon = Icons.Filled.CheckCircle,
                    accentColor = colorScheme.secondary,
                    statusLabel = "What to mention",
                    title = "Useful feedback is short and specific",
                    body = "You do not need to write a long report. One clear note is usually enough for us to understand the problem.",
                    highlights = listOf(
                        "Tell us which screen felt confusing or visually off.",
                        "Mention what you expected to happen and what actually happened instead.",
                        "Include the last step you tapped if something broke or felt stuck."
                    )
                )
            }
            item {
                RefinedFeatureCard(
                    icon = Icons.Filled.Email,
                    accentColor = colorScheme.primary,
                    statusLabel = "Send feedback",
                    title = "Need a hand?",
                    body = "Tell us if something feels confusing, missing, or harder than it should be. Clear feedback helps the app feel smoother for the next session.",
                    highlights = listOf(
                        "Report unclear steps, visual inconsistencies, or bugs from your last session.",
                        "Tell us what made the flow feel heavier than necessary."
                    ),
                    actionLabel = "Send feedback now",
                    onAction = onFeedback
                )
            }
        }
    }
}
