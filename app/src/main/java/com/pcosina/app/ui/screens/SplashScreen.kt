package com.pcosina.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.pcosina.app.BuildConfig
import com.pcosina.app.R
import com.pcosina.app.ui.theme.UiMotionTokens
import com.pcosina.app.ui.util.sampleFrameTiming
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto‑navigate after 2.5 seconds
    LaunchedEffect(Unit) {
        if (BuildConfig.DEBUG) {
            val stats = sampleFrameTiming(
                windowMs = UiMotionTokens.SplashFrameProbeWindowMs,
                jankThresholdMs = UiMotionTokens.FrameJankThresholdMs
            )
            Log.i(
                "SplashMotion",
                "frames=${stats.frames} avg=${"%.1f".format(Locale.ENGLISH, stats.avgFrameMs)}ms " +
                    "p95=${"%.1f".format(Locale.ENGLISH, stats.p95FrameMs)}ms " +
                    "max=${"%.1f".format(Locale.ENGLISH, stats.worstFrameMs)}ms " +
                    "jank=${stats.jankFrames}/${stats.frames}"
            )
        }
        delay(UiMotionTokens.SplashAutoAdvanceMs.toLong())
        onContinue()
    }

    // Infinite alpha pulse for logo + loading bar
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = UiMotionTokens.SplashPulseMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashPulseValue",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    )
                )
            ),
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(212.dp)
                    .clip(CircleShape)
                    .background(colorScheme.onPrimary.copy(alpha = 0.08f))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorScheme.onPrimary.copy(alpha = 0.14f))
                        .padding(10.dp)
                        .alpha(pulseAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pcosina_logo),
                        contentDescription = "PCOSINA Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "PCOSINA",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp,
                    ),
                    color = colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Offline-first Filipino PCOS meal planning",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        letterSpacing = 1.sp
                    ),
                    color = colorScheme.onPrimary.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Local-first support for planning, grocery, and progress.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onPrimary.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Wellness decision support, not diagnosis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onPrimary.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colorScheme.onPrimary.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colorScheme.onPrimary)
                        .alpha(pulseAlpha)
                )
            }
            Text(
                text = "Loading your local plan context",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onPrimary.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
