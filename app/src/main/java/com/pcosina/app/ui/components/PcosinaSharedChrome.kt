package com.pcosina.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.R
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.theme.PcosinaSurface
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt
import java.util.Locale

@Composable
fun SharedTopHeader(
    online: Boolean,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(if (compact) 36.dp else 40.dp),
                shape = CircleShape,
                color = PcosinaSoftPink.copy(alpha = 0.84f),
                border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.42f)),
                shadowElevation = 2.dp,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pcosina_logo),
                    contentDescription = "PCOSina logo",
                    modifier = Modifier.padding(5.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = "PCOSina",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PcosinaPink,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Take the first step toward smarter PCOS nutrition.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                    ),
                    color = PcosinaDeepRose,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SharedHeaderIconButton(
                iconRes = R.drawable.pcosina_header_settings,
                contentDescription = "Open settings",
                onClick = onSettings,
                compact = compact,
            )
        }
        if (!online) {
            Surface(
                color = PcosinaSurfaceAlt,
                contentColor = PcosinaMuted,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Offline: showing saved data. New plans need internet.",
                    modifier = Modifier.padding(
                        horizontal = if (compact) 10.dp else 12.dp,
                        vertical = if (compact) 5.dp else 6.dp
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SharedHeaderIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean,
) {
    Surface(
        modifier = Modifier
            .size(if (compact) 42.dp else 44.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = PcosinaSurface,
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.22f)),
        shadowElevation = 1.dp,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.padding(if (compact) 8.dp else 10.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun SharedAvatarHeader(
    title: String,
    subtitle: String,
    avatarId: String,
    modifier: Modifier = Modifier,
    dateLabel: String? = null,
    compact: Boolean = false,
    avatarAlignment: Alignment = ScreenArtworkAlignment.SharedAvatarHeaderAvatar,
    avatarArtworkKey: String = ArtworkAlignmentKeys.SharedHeaderAvatar,
    onAvatarClick: (() -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
    headerClickLabel: String = "Open profile settings",
) {
    val headerInteractionSource = remember { MutableInteractionSource() }
    val headerPressed by headerInteractionSource.collectIsPressedAsState()
    val headerScale by animateFloatAsState(
        targetValue = if (onHeaderClick != null && headerPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "avatarHeaderPressScale",
    )
    val avatarSize = if (compact) {
        ScreenArtworkSizing.AvatarHeaderCompactAvatarSize
    } else {
        ScreenArtworkSizing.AvatarHeaderRegularAvatarSize
    }
    val headerMinHeight = if (compact) {
        ScreenArtworkSizing.AvatarHeaderCompactMinHeight
    } else {
        ScreenArtworkSizing.AvatarHeaderRegularMinHeight
    }
    val cardMinHeight = if (compact) {
        ScreenArtworkSizing.AvatarHeaderCompactCardMinHeight
    } else {
        ScreenArtworkSizing.AvatarHeaderRegularCardMinHeight
    }
    val avatarTextInset = if (compact) 118.dp else 128.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = headerMinHeight)
            .graphicsLayer {
                scaleX = headerScale
                scaleY = headerScale
            }
            .then(
                if (onHeaderClick != null) {
                    Modifier.clickable(
                        interactionSource = headerInteractionSource,
                        indication = null,
                        onClickLabel = headerClickLabel,
                        onClick = onHeaderClick,
                    )
                } else {
                    Modifier
                }
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .heightIn(min = cardMinHeight),
            shape = RoundedCornerShape(if (compact) 16.dp else 18.dp),
            color = Color(0xFFFF95A8),
            border = BorderStroke(1.3.dp, PcosinaDeepRose.copy(alpha = 0.58f)),
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = avatarTextInset,
                        end = if (compact) 12.dp else 14.dp,
                        top = if (compact) 10.dp else 12.dp,
                        bottom = if (compact) 10.dp else 12.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = if (compact) {
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = PcosinaDeepRose,
                            )
                        } else {
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = PcosinaDeepRose,
                            )
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 214.dp)
                            .height(1.4.dp)
                            .background(PcosinaDeepRose.copy(alpha = 0.48f), RoundedCornerShape(999.dp))
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                        color = Color(0xFF3A2028),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                dateLabel?.let {
                    SharedAvatarDatePill(
                        dateLabel = it,
                        compact = compact,
                    )
                }
            }
        }
        val avatarModifier = Modifier
            .align(avatarAlignment)
            .offset(x = if (compact) (-3).dp else (-5).dp, y = if (compact) (-2).dp else (-1).dp)
            .size(avatarSize)
            .then(
                when {
                    onAvatarClick != null -> Modifier.clickable(onClick = onAvatarClick)
                    onHeaderClick != null -> Modifier.clickable(
                        onClickLabel = headerClickLabel,
                        onClick = onHeaderClick,
                    )
                    else -> Modifier
                }
            )
        val avatarOption = selectedPcosinaAvatar(avatarId)
        DevEditableArtworkImage(
            alignmentKey = avatarArtworkKey,
            painter = painterResource(id = avatarOption.drawableRes),
            contentDescription = avatarOption.label,
            modifier = avatarModifier,
            contentScale = ContentScale.Fit,
            maxScale = ScreenArtworkSizing.AvatarHeaderMaxArtworkScale,
            artworkScaleMultiplier = ScreenArtworkSizing.AvatarHeaderArtworkScaleMultiplier,
            transformOrigin = TransformOrigin(0.5f, 1f),
        )
    }
}

@Composable
private fun SharedAvatarDatePill(
    dateLabel: String,
    compact: Boolean,
) {
    val parts = dateLabel.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val month = parts.firstOrNull()?.uppercase(Locale.ENGLISH).orEmpty()
    val day = parts.drop(1).firstOrNull()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.26f)),
    ) {
        if (day != null && day.any { it.isDigit() }) {
            Column(
                modifier = Modifier
                    .widthIn(min = if (compact) 42.dp else 46.dp)
                    .padding(horizontal = 8.dp, vertical = if (compact) 5.dp else 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = month.take(3),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                    maxLines = 1,
                )
                Text(
                    text = day,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = PcosinaDeepRose,
                    maxLines = 1,
                )
            }
        } else {
            Text(
                text = dateLabel,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PcosinaDeepRose,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
