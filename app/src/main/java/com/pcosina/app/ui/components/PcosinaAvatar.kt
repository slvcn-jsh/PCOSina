package com.pcosina.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pcosina.app.R

data class PcosinaAvatarOption(
    val id: String,
    val label: String,
    val background: Color,
    val accent: Color,
    val drawableRes: Int,
)

val PcosinaAvatarOptions = listOf(
    PcosinaAvatarOption("doctor_dog", "Doctor pup", Color(0xFFFFD4DC), Color(0xFFFC6B7D), R.drawable.avatar_doctor_dog),
    PcosinaAvatarOption("cat", "Lab cat", Color(0xFFD1B5AE), Color(0xFF8B5E5A), R.drawable.avatar_cat),
    PcosinaAvatarOption("chick", "Checklist chick", Color(0xFFFFEDC2), Color(0xFFE4A227), R.drawable.avatar_chick),
    PcosinaAvatarOption("frog", "Frog guide", Color(0xFFC8F3B8), Color(0xFF4F9E45), R.drawable.avatar_frog),
    PcosinaAvatarOption("koala", "Care koala", Color(0xFFB7C4D1), Color(0xFF60758A), R.drawable.avatar_koala),
    PcosinaAvatarOption("pug", "Pug coach", Color(0xFFFFA07B), Color(0xFFE86C4F), R.drawable.avatar_pug),
)

fun selectedPcosinaAvatar(id: String): PcosinaAvatarOption {
    val normalizedId = when (id) {
        "calm_cat" -> "cat"
        "berry_bear" -> "pug"
        "leaf_bunny" -> "frog"
        else -> id
    }
    return PcosinaAvatarOptions.firstOrNull { it.id == normalizedId } ?: PcosinaAvatarOptions.first()
}

@Composable
fun PcosinaAvatar(
    avatarId: String,
    modifier: Modifier = Modifier,
) {
    val option = selectedPcosinaAvatar(avatarId)
    Image(
        painter = painterResource(id = option.drawableRes),
        contentDescription = option.label,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
fun PcosinaAvatarBadge(
    avatarId: String,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    ringColor: Color = Color(0xFFFF7A92),
    containerColor: Color = Color(0xFFFFEDF1),
    shadowElevation: Dp = 5.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(2.dp, ringColor),
        shadowElevation = shadowElevation,
    ) {
        PcosinaAvatar(
            avatarId = avatarId,
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
        )
    }
}

@Composable
fun PcosinaAvatarOptionTile(
    option: PcosinaAvatarOption,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onSelect),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) option.background.copy(alpha = 0.70f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) option.accent else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    color = option.background.copy(alpha = 0.35f),
                ) {}
                PcosinaAvatar(
                    avatarId = option.id,
                    modifier = Modifier.size(58.dp),
                )
            }
            Text(
                text = option.label,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}
