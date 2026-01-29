package com.pcosina.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun GradientHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    containerHeight: Int = 160,
    colors: List<Color> = listOf(
        Color(0xFF0ABF6A), // green
        Color(0xFF0FB0B7), // teal
        Color(0xFF2D9CDB), // blue
    ),
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight.dp)
            .clip(shape)
            .background(
                brush = Brush.linearGradient(colors)
            )
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Box(contentAlignment = Alignment.TopEnd) {
                    trailing()
                }
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

