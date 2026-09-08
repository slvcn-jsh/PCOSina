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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@Composable
fun CommunityScreen(
    onBack: (() -> Unit)? = null,
    onFeedback: (String, Boolean) -> Unit,
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
                SupportSettingsCard(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SupportFeedbackCard(
                    isOnline = observedOnline,
                    onFeedback = onFeedback,
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
