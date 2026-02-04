package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.MacroProgressBar
import com.pcosina.app.ui.components.StatCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProgressScreen(
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var feedback by rememberSaveable { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme
    
    // Dynamic date range
    val today = LocalDate.now()
    val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    val endOfWeek = startOfWeek.plusDays(6)
    val formatter = DateTimeFormatter.ofPattern("MMM dd")
    val weekLabel = "${startOfWeek.format(formatter)} - ${endOfWeek.format(formatter)}"

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "Weekly Insights",
                    subtitle = "Monitoring your metabolic markers",
                    containerHeight = 180,
                )
                IconButton(
                    onClick = onBackToDashboard,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onPrimary,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    title = "Plan Adherence",
                    value = "92%",
                    subtitle = weekLabel,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Weight Delta",
                    value = "-0.8kg",
                    subtitle = "This Week",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.TrendingDown, contentDescription = null, tint = colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Daily Compliance",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.secondary
                        )
                    }
                    val days = listOf(
                        "Mon" to 0.85f, "Tue" to 0.95f, "Wed" to 0.70f, 
                        "Thu" to 1.0f, "Fri" to 0.90f, "Sat" to 0.88f, "Sun" to 0.92f
                    )
                    days.forEach { (label, progress) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(40.dp),
                                color = colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                                color = if (progress >= 0.9f) colorScheme.primary else colorScheme.primary.copy(alpha = 0.5f),
                                trackColor = colorScheme.surfaceVariant,
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Aggregated Macros",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface
                    )
                    MacroProgressBar(label = "Protein", progress = 0.82f, valueText = "Avg 75g")
                    MacroProgressBar(label = "Carbs", progress = 0.45f, valueText = "Avg 110g")
                    MacroProgressBar(label = "Fats", progress = 0.68f, valueText = "Avg 48g")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Weekly Journal",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.secondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { Text("How do you feel this week? (e.g., Energy levels, symptoms)") },
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = colorScheme.primary)
                )
                Button(
                    onClick = { /* no-op */ },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                ) {
                    Text("Save Weekly Reflection", fontWeight = FontWeight.Bold)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
