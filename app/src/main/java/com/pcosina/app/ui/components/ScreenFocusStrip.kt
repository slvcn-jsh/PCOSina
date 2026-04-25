package com.pcosina.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pcosina.app.ui.theme.UiMotionTokens

data class ScreenFocusOption(
    val key: String,
    val label: String,
    val summary: String,
)

@Composable
fun ScreenFocusStrip(
    title: String,
    options: List<ScreenFocusOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    labelMaxWidth: Dp,
    modifier: Modifier = Modifier,
    helperText: String = "Show one main task at a time.",
) {
    val colorScheme = MaterialTheme.colorScheme
    val selectedOption = options.firstOrNull { it.key == selectedKey } ?: options.firstOrNull()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colorScheme.onSurfaceVariant
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = colorScheme.primary.copy(alpha = 0.08f),
            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "Focus",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedContent(
                    targetState = selectedOption?.summary ?: helperText,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(UiMotionTokens.FocusHintFadeMs)) +
                            slideInVertically(animationSpec = tween(UiMotionTokens.FocusHintFadeMs)) { it / 2 }) togetherWith
                            (fadeOut(animationSpec = tween(UiMotionTokens.FocusHintFadeMs / 2)) +
                                slideOutVertically(animationSpec = tween(UiMotionTokens.FocusHintFadeMs / 2)) { -it / 2 })
                    },
                    label = "focusSummary"
                ) { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(end = 2.dp)
        ) {
            items(options, key = { it.key }) { option ->
                TokenizedFilterChip(
                    selected = selectedKey == option.key,
                    onClick = { onSelect(option.key) },
                    text = option.label,
                    labelMaxWidth = labelMaxWidth
                )
            }
        }
    }
}
