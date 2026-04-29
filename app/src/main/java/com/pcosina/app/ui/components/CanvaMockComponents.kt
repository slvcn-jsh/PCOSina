package com.pcosina.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pcosina.app.R
import com.pcosina.app.ui.theme.CanvaTokens

@Composable
fun CanvaBrandBar(
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    onNotifications: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = CanvaTokens.PanelPinkLight,
                modifier = Modifier.size(52.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pcosina_logo),
                    contentDescription = "PCOSina logo",
                    modifier = Modifier.padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "PCOSina",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = CanvaTokens.PanelPink,
                    ),
                )
                Text(
                    text = "“Take the first step toward smarter PCOS nutrition.”",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = CanvaTokens.Ink,
                        fontStyle = FontStyle.Italic,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CanvaRoundIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = onSettings,
            )
            CanvaRoundIconButton(
                icon = Icons.Filled.NotificationsNone,
                contentDescription = "Notifications",
                onClick = onNotifications,
            )
        }
    }
}

@Composable
fun CanvaRoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(54.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, CanvaTokens.PanelPink.copy(alpha = 0.6f)),
    ) {
        IconButton(onClick = { onClick?.invoke() }, enabled = onClick != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = CanvaTokens.PanelPink,
            )
        }
    }
}

@Composable
fun CanvaHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = CanvaTokens.PanelPink,
        border = BorderStroke(3.dp, CanvaTokens.Outline),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(24.dp),
                color = CanvaTokens.PanelPinkLight,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pcosina_logo),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = CanvaTokens.HeadlineMaroon,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CanvaTokens.HeadlineMaroon.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = CanvaTokens.HeadlineMaroon,
                        fontStyle = FontStyle.Italic,
                    ),
                )
            }

            trailingContent?.invoke()
        }
    }
}

@Composable
fun CanvaSectionTitle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconTint: Color = CanvaTokens.PanelPink,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                )
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = CanvaTokens.HeadlineMaroon,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = CanvaTokens.Ink,
                    fontStyle = FontStyle.Italic,
                ),
            )
        }
    }
}

@Composable
fun CanvaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = CanvaTokens.Shadow, spotColor = CanvaTokens.Shadow),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CanvaTokens.AccentPinkStrong,
            contentColor = Color.White,
            disabledContainerColor = CanvaTokens.AccentPinkMuted,
            disabledContentColor = Color.White.copy(alpha = 0.8f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        )
    }
}

@Composable
fun CanvaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    contentColor: Color = CanvaTokens.Ink,
    borderColor: Color = CanvaTokens.Outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(2.dp, borderColor),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun CanvaGradientPanel(
    modifier: Modifier = Modifier,
    brush: Brush = CanvaTokens.SurfaceGlowGradient,
    borderColor: Color = CanvaTokens.Outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        border = BorderStroke(2.dp, borderColor),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .background(brush)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}
