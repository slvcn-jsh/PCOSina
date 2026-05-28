package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.R
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.ArtworkAlignmentKeys
import com.pcosina.app.ui.components.SharedAvatarHeader
import com.pcosina.app.ui.components.SharedTopHeader
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt
import com.pcosina.app.ui.util.rememberIsOnline
import com.pcosina.app.notifications.NotificationHelper
import com.pcosina.app.notifications.NotificationScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NotificationScreen(
    userViewModel: UserViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val profile by userViewModel.userProfile.collectAsState()
    val prefs by userViewModel.notificationPreferences.collectAsState()
    val logs by userViewModel.notificationLogs.collectAsState()
    val todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH))
    val phoneNotificationsReady = NotificationHelper.canPostNotifications(context)
    val nextScheduled = NotificationScheduler.nextScheduledTimes(prefs)
    val latestLog = logs.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("notification_content_list")
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SharedTopHeader(
                online = observedOnline,
                onSettings = onOpenSettings,
                onNotifications = {},
                compact = false,
            )
        }
        item {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = Color.White,
                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.22f)),
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PcosinaPink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        item {
            SharedAvatarHeader(
                title = "Notifications",
                subtitle = "Meal reminders, grocery updates, and weekly planning nudges.",
                avatarId = profile.avatarId,
                dateLabel = todayLabel,
                compact = false,
                avatarArtworkKey = ArtworkAlignmentKeys.NotificationsHeaderAvatar,
                onHeaderClick = onOpenSettings,
            )
        }
        item {
            NotificationSummaryCard(
                title = if (prefs.masterEnabled) "Reminders are on" else "Reminders are paused",
                body = if (prefs.masterEnabled) {
                    "PCOSina can send local reminders after phone notifications are allowed and a scheduled time arrives."
                } else {
                    "Turn reminders back on from Settings when you want meal or weekly planning nudges."
                },
                badge = if (prefs.masterEnabled) "Active" else "Paused",
            )
        }
        item {
            NotificationSummaryCard(
                title = "Phone notifications",
                body = if (phoneNotificationsReady) {
                    "Android can post PCOSina reminders from this phone."
                } else {
                    "Allow phone notifications in Settings before scheduled reminders can appear."
                },
                badge = if (phoneNotificationsReady) "Ready" else "Needs action",
            )
        }
        item {
            NotificationSummaryCard(
                title = "Meal schedule",
                body = "Breakfast ${formatNotificationTime(prefs.breakfastHour, prefs.breakfastMinute)} | " +
                    "Lunch ${formatNotificationTime(prefs.lunchHour, prefs.lunchMinute)} | " +
                    "Dinner ${formatNotificationTime(prefs.dinnerHour, prefs.dinnerMinute)}",
                badge = if (prefs.mealRemindersEnabled) "Meals" else "Off",
            )
        }
        item {
            NotificationSummaryCard(
                title = "Next scheduled",
                body = when {
                    !prefs.masterEnabled -> "Reminders are paused. No notification work is scheduled while the master switch is off."
                    !phoneNotificationsReady -> "Phone notifications need to be allowed before reminder work can run."
                    nextScheduled.isEmpty() -> "No reminder type is enabled yet."
                    else -> nextScheduled.take(3).joinToString("\n")
                },
                badge = when {
                    !prefs.masterEnabled -> "None"
                    !phoneNotificationsReady -> "Blocked"
                    nextScheduled.isEmpty() -> "None"
                    else -> "${nextScheduled.size} next"
                },
            )
        }
        item {
            NotificationSummaryCard(
                title = "Recent activity",
                body = latestLog?.let { log ->
                    "${formatNotificationEventLabel(log.type)} delivered at ${formatNotificationDeliveredAt(log.deliveredAt)}."
                } ?: "No notifications delivered yet on this device. The log starts only after Android posts a reminder or status notification.",
                badge = if (logs.isEmpty()) "Delivered only" else "${logs.size.coerceAtMost(99)} logs",
            )
        }
    }
}

@Composable
private fun NotificationSummaryCard(
    title: String,
    body: String,
    badge: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaDeepRose.copy(alpha = 0.18f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = PcosinaSoftPink,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pcosina_header_notification),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = PcosinaDeepRose,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = PcosinaSurfaceAlt,
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PcosinaMuted,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = PcosinaMuted,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatNotificationTime(hour: Int, minute: Int): String {
    val suffix = if (hour >= 12) "PM" else "AM"
    val displayHour = when (val normalized = hour % 12) {
        0 -> 12
        else -> normalized
    }
    return "%d:%02d %s".format(Locale.ENGLISH, displayHour, minute, suffix)
}

private fun formatNotificationDeliveredAt(epochMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)
    return Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}

private fun formatNotificationEventLabel(type: String): String {
    return type
        .split("_")
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.ENGLISH) else char.toString()
            }
        }
        .ifBlank { "Notification" }
}
