package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.R
import com.pcosina.app.ui.components.PcosinaDesignIcon
import com.pcosina.app.ui.components.ScreenArtworkAlignment
import com.pcosina.app.ui.components.ScreenArtworkOffset
import com.pcosina.app.ui.components.ScreenArtworkSizing
import com.pcosina.app.ui.components.SharedAvatarHeader
import com.pcosina.app.ui.components.SharedTopHeader
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.util.rememberIsOnline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CommunityScreen(
    onBack: (() -> Unit)? = null,
    onFeedback: (String, Boolean) -> Unit,
    avatarId: String,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
    onOpenNotifications: (() -> Unit)? = null,
    onOpenMealPlan: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val supportDateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            SharedTopHeader(
                online = observedOnline,
                onSettings = onOpenSettings ?: {},
                onNotifications = onOpenNotifications ?: {},
                compact = false,
            )
        }
        item {
            SharedAvatarHeader(
                title = "Support",
                subtitle = "Clear help for planning, logging, and sending feedback.",
                avatarId = avatarId,
                dateLabel = supportDateLabel,
                compact = false,
                avatarAlignment = ScreenArtworkAlignment.SupportHeaderAvatar,
            )
        }
        item {
            SupportFeedbackCard(
                isOnline = observedOnline,
                onFeedback = onFeedback,
            )
        }
        item {
            SupportDirectoryCard()
        }
        item {
            SupportFreshStartCard(onOpenMealPlan = onOpenMealPlan)
        }
        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SupportBrandHeader(
    onBack: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 3.dp,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PcosinaPink,
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = PcosinaSoftPink.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.45f)),
                shadowElevation = 2.dp,
            ) {
                PcosinaDesignIcon(
                    resId = R.drawable.pcosina_svg_24_logo,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(5.dp)
                        .size(28.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PCOSina",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = PcosinaPink,
                ),
            )
            Text(
                text = "\"Take the first step toward smarter PCOS nutrition.\"",
                style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                color = PcosinaDeepRose,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportRoundIcon(
                iconRes = R.drawable.pcosina_svg_44_settings,
                contentDescription = "Settings",
                onClick = onOpenSettings,
            )
            SupportRoundIcon(
                iconRes = R.drawable.pcosina_svg_45_bell,
                contentDescription = "Notification settings",
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun SupportFeedbackCard(
    isOnline: Boolean,
    onFeedback: (String, Boolean) -> Unit,
) {
    var feedbackText by remember { mutableStateOf("") }
    var feedbackStatus by remember { mutableStateOf<String?>(null) }
    val trimmedFeedback = feedbackText.trim()
    val isTooLong = feedbackText.length > 2000
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFE2E5),
        border = BorderStroke(1.dp, PcosinaDeepRose.copy(alpha = 0.40f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PcosinaDesignIcon(
                    resId = R.drawable.pcosina_svg_40_email,
                    contentDescription = null,
                    tint = PcosinaPink,
                    modifier = Modifier.size(38.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Need a hand?",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = PcosinaDeepRose,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Tell us if planning, logging, or feedback feels harder than it should be.",
                        style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                        color = PcosinaDeepRose,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OutlinedTextField(
                value = feedbackText,
                onValueChange = {
                    feedbackText = it
                    feedbackStatus = null
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 4,
                isError = isTooLong,
                placeholder = {
                    Text("Share the screen, step, or issue.")
                },
                supportingText = {
                    Text(
                        text = if (isTooLong) {
                            "Keep feedback under 2,000 characters."
                        } else {
                            "${feedbackText.length}/2000"
                        },
                    )
                },
            )
            Button(
                onClick = {
                    onFeedback(trimmedFeedback, isOnline)
                    feedbackText = ""
                    feedbackStatus = if (isOnline) {
                        "Feedback queued for sending."
                    } else {
                        "Feedback saved and will send when online."
                    }
                },
                enabled = trimmedFeedback.isNotBlank() && !isTooLong,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PcosinaPink),
            ) {
                Text("Send feedback now", fontWeight = FontWeight.Bold)
            }
            feedbackStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = PcosinaDeepRose,
                )
            }
        }
    }
}

@Composable
private fun SupportDirectoryCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaDeepRose.copy(alpha = 0.40f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PcosinaDesignIcon(
                    resId = R.drawable.pcosina_svg_41_book,
                    contentDescription = null,
                    tint = PcosinaPink,
                    modifier = Modifier.size(34.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Directory",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = PcosinaDeepRose),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Quick guide to navigating the PCOSina application.",
                        style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                        color = PcosinaMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SupportDirectoryTile(
                        iconRes = R.drawable.pcosina_nav_home,
                        label = "Home",
                        description = "Dashboard for quick access to goals and today's focus.",
                        modifier = Modifier.weight(1f),
                    )
                    SupportDirectoryTile(
                        iconRes = R.drawable.pcosina_nav_progress,
                        label = "Progress",
                        description = "Monitor daily intake, financial savings, and manage symptoms.",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SupportDirectoryTile(
                        iconRes = R.drawable.pcosina_nav_plan,
                        label = "Plan",
                        description = "See and follow your weekly hormone-friendly meal plan.",
                        modifier = Modifier.weight(1f),
                    )
                    SupportDirectoryTile(
                        iconRes = R.drawable.pcosina_nav_support_clean,
                        label = "Support",
                        description = "Access learning guides and submit your feedback.",
                        modifier = Modifier.weight(1f),
                    )
                }
                SupportDirectoryTile(
                    iconRes = R.drawable.pcosina_nav_grocery,
                    label = "Grocery",
                    description = "Manage pantry inventory and generate smart shopping lists.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SupportFreshStartCard(
    onOpenMealPlan: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ScreenArtworkSizing.SupportFreshStartMinHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.linearGradient(listOf(PcosinaSoftPink, Color(0xFFFFB2C1))),
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.62f)
                    .padding(start = 16.dp, top = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Open this week's plan",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = PcosinaDeepRose),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Review meals, grocery, and progress from one place.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PcosinaDeepRose,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    modifier = if (onOpenMealPlan != null) {
                        Modifier.clickable(onClick = onOpenMealPlan)
                    } else {
                        Modifier
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = PcosinaPink,
                    contentColor = Color.White,
                    shadowElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Open Plan", fontWeight = FontWeight.ExtraBold)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            PcosinaDesignIcon(
                resId = R.drawable.pcosina_svg_42_activity,
                contentDescription = null,
                tint = PcosinaDeepRose.copy(alpha = 0.70f),
                modifier = Modifier
                    .align(ScreenArtworkAlignment.SupportFreshStartIcon)
                    .offset(
                        x = ScreenArtworkOffset.SupportFreshStartIconX,
                        y = ScreenArtworkOffset.SupportFreshStartIconY,
                    )
                    .size(132.dp),
            )
        }
    }
}

@Composable
private fun SupportDirectoryTile(
    iconRes: Int,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color.White,
            border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.55f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PcosinaDesignIcon(
                    resId = iconRes,
                    contentDescription = null,
                    tint = PcosinaPink,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = PcosinaPink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = description,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = PcosinaDeepRose,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SupportRoundIcon(
    iconRes: Int,
    contentDescription: String,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
        shape = CircleShape,
        color = Color.White,
        contentColor = PcosinaPink,
        shadowElevation = 3.dp,
    ) {
        PcosinaDesignIcon(
            resId = iconRes,
            contentDescription = contentDescription,
            tint = PcosinaPink,
            modifier = Modifier
                .padding(9.dp)
                .size(20.dp),
        )
    }
}
