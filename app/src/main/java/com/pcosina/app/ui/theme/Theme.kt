package com.pcosina.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PcosinaPink,
    primaryContainer = Color(0xFF5C2A33),
    secondary = PcosinaDeepRose,
    secondaryContainer = Color(0xFF4A2329),
    tertiary = PcosinaLightPink,
    tertiaryContainer = Color(0xFF6E3A42),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2326),
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFFFFDCE2),
    onSecondary = Color.White,
    onSecondaryContainer = Color(0xFFFFD9E0),
    onTertiary = Color.Black,
    onTertiaryContainer = Color(0xFFFFDDE2),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFE6D7DB),
    outline = Color(0xFF3F3538),
    error = Color(0xFFCF6679),
    onError = Color.Black,
)

private val LightColorScheme = lightColorScheme(
    primary = PcosinaPink,
    primaryContainer = PcosinaSoftPink,
    secondary = PcosinaDeepRose,
    secondaryContainer = PcosinaLightPink,
    tertiary = PcosinaLightPink,
    tertiaryContainer = PcosinaSoftPink,
    background = PcosinaSurface,
    surface = Color.White,
    surfaceVariant = PcosinaSurfaceAlt,
    onPrimary = Color.White,
    onPrimaryContainer = PcosinaDeepRose,
    onSecondary = Color.White,
    onSecondaryContainer = PcosinaDeepRose,
    onTertiary = Color.Black,
    onTertiaryContainer = PcosinaDeepRose,
    onBackground = PcosinaMidnight,
    onSurface = PcosinaMidnight,
    onSurfaceVariant = PcosinaMuted,
    outline = Color(0xFFE4D7DB),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun PCOSINATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    // But for brand consistency in a thesis, we often disable it.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
