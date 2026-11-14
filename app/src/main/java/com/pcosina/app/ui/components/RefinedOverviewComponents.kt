package com.pcosina.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pcosina.app.R
import com.pcosina.app.ui.theme.PcosinaDeepRose
import com.pcosina.app.ui.theme.PcosinaLightPink
import com.pcosina.app.ui.theme.PcosinaMidnight
import com.pcosina.app.ui.theme.PcosinaMuted
import com.pcosina.app.ui.theme.PcosinaPink
import com.pcosina.app.ui.theme.PcosinaSoftPink
import com.pcosina.app.ui.theme.PcosinaSurface
import com.pcosina.app.ui.theme.PcosinaSurfaceAlt

@Composable
fun RefinedTabBrandHeader(
    online: Boolean,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    avatarId: String = "doctor_dog",
) {
    SharedTopHeader(
        online = online,
        onSettings = onSettings,
        modifier = modifier,
        compact = compact,
    )
}

@Composable
fun RefinedHeroBanner(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 26.dp else 30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFFB3C1), Color(0xFFFF7E97))
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 16.dp else 18.dp, vertical = if (compact) 14.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, PcosinaDeepRose.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PcosinaDeepRose,
                    modifier = Modifier.padding(if (compact) 12.dp else 14.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = if (compact) {
                        MaterialTheme.typography.headlineSmall.copy(
                            color = PcosinaDeepRose,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        MaterialTheme.typography.headlineMedium.copy(
                            color = PcosinaDeepRose,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PcosinaDeepRose
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun RefinedOverviewCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    borderColor: Color = PcosinaDeepRose.copy(alpha = 0.16f),
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
fun RefinedActionCard(
    title: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonEnabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(PcosinaLightPink.copy(alpha = 0.92f), PcosinaPink.copy(alpha = 0.9f))
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 18.dp else 22.dp, vertical = if (compact) 18.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            Text(
                text = title,
                style = if (compact) {
                    MaterialTheme.typography.headlineSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else {
                    MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RefinedPrimaryButton(
                    text = buttonLabel,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    enabled = buttonEnabled
                )
                if (!secondaryLabel.isNullOrBlank() && onSecondaryClick != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable(onClick = onSecondaryClick)
                    ) {
                        Text(
                            text = secondaryLabel,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RefinedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = PcosinaDeepRose,
            contentColor = Color.White,
            disabledContainerColor = PcosinaDeepRose.copy(alpha = 0.42f),
            disabledContentColor = Color.White.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun RefinedStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = PcosinaSurfaceAlt,
    contentColor: Color = PcosinaDeepRose,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor
        )
    }
}

@Composable
fun RefinedRingMeter(
    valueText: String,
    subtitle: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val size = if (compact) 104.dp else 122.dp
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { 1f },
            color = PcosinaSurfaceAlt,
            strokeWidth = if (compact) 10.dp else 12.dp,
            modifier = Modifier.fillMaxSize()
        )
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = color,
            strokeWidth = if (compact) 10.dp else 12.dp,
            modifier = Modifier.fillMaxSize()
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = valueText,
                style = if (compact) {
                    MaterialTheme.typography.headlineMedium.copy(
                        color = PcosinaDeepRose,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else {
                    MaterialTheme.typography.headlineLarge.copy(
                        color = PcosinaDeepRose,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PcosinaMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun RefinedMetricBar(
    label: String,
    valueText: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = PcosinaDeepRose
                )
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = PcosinaDeepRose
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(PcosinaSurfaceAlt)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(color)
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun RefinedTabIconAction(
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
    contentDescription: String,
    onClick: () -> Unit,
    compact: Boolean,
) {
    Surface(
        shape = CircleShape,
        color = PcosinaSurface,
        border = BorderStroke(1.dp, PcosinaPink.copy(alpha = 0.22f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        if (iconRes != null) {
            PcosinaDesignIcon(
                resId = iconRes,
                contentDescription = contentDescription,
                tint = PcosinaPink,
                modifier = Modifier.padding(if (compact) 10.dp else 12.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = PcosinaPink,
                modifier = Modifier.padding(if (compact) 10.dp else 12.dp)
            )
        }
    }
}
