package com.pcosina.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto‑navigate after 2.5 seconds
    LaunchedEffect(Unit) {
        delay(2500)
        onContinue()
    }

    // Infinite alpha pulse for logo + loading bar
    val infiniteTransition = rememberInfiniteTransition(label = "splashPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
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
                        Color(0xFFFC6B7D), // Primary Pink
                        Color(0xFFFF8A97), // Vibrant Pink
                        Color(0xFFFFB4BC), // Light Pink
                    )
                )
            )
            .padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(1.dp))

            // Center content (logo + title/subtitle)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Gradient circle logo with 🌸
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFC6B7D),
                                    Color(0xFFFFB4BC),
                                )
                            )
                        )
                        .alpha(pulseAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "🌸",
                        fontSize = 40.sp,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "PCOSINA",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Personalized Meal Planning for PCOS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Bottom pulsing loading bar
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .fillMaxSize(fraction = 0.18f)
            ) {
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .fillMaxSize(fraction = 0.55f)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFC6B7D),
                                        Color(0xFFFFB4BC),
                                    )
                                )
                            )
                            .alpha(pulseAlpha),
                    )
                }
            }
        }
    }
}
