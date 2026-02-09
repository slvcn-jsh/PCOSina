package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pcosina.app.ui.components.GradientHeader

@Composable
fun IpoVisualizationScreen(
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box {
                GradientHeader(
                    title = "The PCOSINA Method",
                    subtitle = "Scientific Optimization Pipeline",
                    containerHeight = 180
                )
                IconButton(onClick = onBackToDashboard, modifier = Modifier.padding(8.dp)) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colorScheme.onPrimary)
                }
            }
        }

        item {
            IpoCard(
                step = "01",
                title = "SYSTEM INPUT",
                description = "User biometric data and clinical markers are ingested.",
                items = listOf(
                    "Profile: Age, BMI, Activity Level",
                    "Medical: Insulin Resistance, Symptoms",
                    "Constraints: Budget & Restrictions"
                ),
                color = colorScheme.primary
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("↓", fontSize = 24.sp, color = colorScheme.primary)
            }
        }

        item {
            IpoCard(
                step = "02",
                title = "MILP PROCESS",
                description = "Mixed Integer Linear Programming finds the mathematical global optimum.",
                items = listOf(
                    "Optimization: Calorie variance minimized",
                    "Logic: Maximum recipe variety enforced",
                    "Filtering: Case-insensitive ingredient scan"
                ),
                color = colorScheme.secondary
            )
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("↓", fontSize = 24.sp, color = colorScheme.primary)
            }
        }

        item {
            IpoCard(
                step = "03",
                title = "SYSTEM OUTPUT",
                description = "Personalized and validated weekly health plan.",
                items = listOf(
                    "7-Day Optimized Meal Plan",
                    "Consolidated Shopping List",
                    "Real-time Macro Progress Metrics"
                ),
                color = colorScheme.primary
            )
        }

        item {
            Button(
                onClick = onBackToDashboard,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text("Back to Home", fontWeight = FontWeight.Bold)
            }
        }
        
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun IpoCard(
    step: String,
    title: String,
    description: String,
    items: List<String>,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                    color = color.copy(alpha = 0.2f)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = color
                )
            }
            Text(text = description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Text(text = item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
