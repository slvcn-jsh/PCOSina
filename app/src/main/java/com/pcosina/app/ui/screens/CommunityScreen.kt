package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.components.ScreenArtworkAlignment
import com.pcosina.app.ui.components.SharedAvatarHeader
import com.pcosina.app.ui.components.SharedTopHeader
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.util.rememberIsOnline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.pcosina.app.ui.components.ArtworkAlignmentKeys
import com.pcosina.app.data.model.FeedbackEntry
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(
    onBack: (() -> Unit)? = null,
    onFeedback: suspend (String, Boolean) -> Boolean,
    feedbackEntries: List<FeedbackEntry> = emptyList(),
    onRetryFeedback: (String, Boolean) -> Unit = { _, _ -> },
    avatarId: String,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val observedOnline by rememberIsOnline(context)
    val supportDateLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        val compact = maxHeight < 760.dp || maxWidth < 390.dp
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = if (compact) 14.dp else 18.dp,
                vertical = if (compact) 10.dp else 14.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
        ) {
            item {
                SharedTopHeader(
                    online = observedOnline,
                    onSettings = onOpenSettings ?: {},
                    compact = compact,
                )
            }
            item {
                SharedAvatarHeader(
                    title = "Support",
                    subtitle = "Clear help for planning, logging, and sending feedback.",
                    avatarId = avatarId,
                    dateLabel = supportDateLabel,
                    compact = compact,
                    avatarAlignment = ScreenArtworkAlignment.SupportHeaderAvatar,
                    avatarArtworkKey = ArtworkAlignmentKeys.SupportHeaderAvatar,
                    onHeaderClick = onOpenSettings,
                )
            }
            item {
                SupportFeedbackCard(
                    isOnline = observedOnline,
                    onFeedback = onFeedback,
                )
            }
            if (feedbackEntries.isNotEmpty()) {
                item {
                    SupportFeedbackHistoryCard(
                        entries = feedbackEntries,
                        isOnline = observedOnline,
                        onRetryFeedback = onRetryFeedback,
                    )
                }
            }
            item {
                SupportSettingsCard(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SupportSettingsCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFFA8B3),
        border = BorderStroke(1.5.dp, PcosinaPink),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Manage your experience",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Update your profile, preferences, and reminders, or send feedback to help improve your experience.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = { onClick?.invoke() },
                enabled = onClick != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PcosinaDeepRose),
            ) {
                Text("Go to Settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SupportFeedbackCard(
    isOnline: Boolean,
    onFeedback: suspend (String, Boolean) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var feedbackText by remember { mutableStateOf("") }
    var feedbackStatus by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
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
                Column(modifier = Modifier.fillMaxWidth()) {
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
                    Text("Describe the step or issue.")
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
                    submitting = true
                    feedbackStatus = "Saving feedback on this device..."
                    scope.launch {
                        val saved = onFeedback(trimmedFeedback, isOnline)
                        submitting = false
                        if (saved) {
                            feedbackText = ""
                            feedbackStatus = if (isOnline) {
                                "Feedback saved. Sending will continue in the background."
                            } else {
                                "Feedback saved on this device and will send when online."
                            }
                        } else {
                            feedbackStatus = "Feedback was not saved. Your text is still here so you can retry."
                        }
                    }
                },
                enabled = trimmedFeedback.isNotBlank() && !isTooLong && !submitting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PcosinaPink),
            ) {
                Text(if (submitting) "Saving..." else "Send feedback now", fontWeight = FontWeight.Bold)
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
private fun SupportFeedbackHistoryCard(
    entries: List<FeedbackEntry>,
    isOnline: Boolean,
    onRetryFeedback: (String, Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PcosinaDeepRose.copy(alpha = 0.20f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Feedback status",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PcosinaDeepRose,
            )
            Text(
                text = "Saved feedback remains visible here so offline, sending, sent, and failed states are clear.",
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaDeepRose.copy(alpha = 0.72f),
            )
            entries.sortedByDescending { it.createdAt }.take(5).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = PcosinaDeepRose,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when (entry.status) {
                                "Queued" -> if (isOnline) "Saved, waiting to send" else "Saved offline"
                                "Sending" -> "Sending"
                                "Sent" -> "Sent"
                                "Failed" -> entry.lastError?.let { "Failed: $it" } ?: "Failed to send"
                                else -> entry.status
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.status == "Failed") MaterialTheme.colorScheme.error else PcosinaPink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (entry.status == "Failed") {
                        TextButton(
                            onClick = { onRetryFeedback(entry.id, isOnline) },
                            enabled = isOnline,
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
