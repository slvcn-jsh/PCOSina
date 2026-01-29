package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingSlide(
    val title: String,
    val description: String,
)

private val onboardingSlides = listOf(
    OnboardingSlide(
        title = "Support Weight Management",
        description = "Get personalized meal plans tailored to your PCOS needs and weight goals with Filipino recipes you love.",
    ),
    OnboardingSlide(
        title = "Assist PCOS Symptom Management",
        description = "Our meal plans help manage insulin resistance and hormonal balance through balanced nutrition.",
    ),
    OnboardingSlide(
        title = "Provide Lifestyle Recommendations",
        description = "Get gradual dietary transitions, activity tips, and continuous support for sustainable healthy living.",
    ),
)

@Composable
fun OnboardingScreen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    val lastIndex = onboardingSlides.lastIndex
    val slide = onboardingSlides[currentIndex]

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top graphic (gradient circle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(120.dp)
                        .fillMaxWidth(0.4f)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0ABF6A),
                                    Color(0xFF2D9CDB),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "🥗",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            // Middle: title + description
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = slide.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }

            // Bottom controls: dots + buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Dots
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onboardingSlides.forEachIndexed { index, _ ->
                        val selected = index == currentIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected)
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF0ABF6A),
                                                Color(0xFF2D9CDB),
                                            )
                                        )
                                    else
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                MaterialTheme.colorScheme.surfaceVariant,
                                            )
                                        )
                                )
                                .height(8.dp)
                                .fillMaxWidth(if (selected) 0.06f else 0.03f),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Primary + secondary buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isLast = currentIndex == lastIndex
                    val primaryText = if (isLast) "Get Started" else "Next"

                    Button(
                        onClick = {
                            if (!isLast) {
                                currentIndex += 1
                            } else {
                                onNext()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF0ABF6A),
                                            Color(0xFF2D9CDB),
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = primaryText,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }

                    if (!isLast) {
                        OutlinedButton(
                            onClick = { onNext() }, // Skip → UserProfile
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text("Skip")
                        }
                    }
                }
            }
        }
    }
}

