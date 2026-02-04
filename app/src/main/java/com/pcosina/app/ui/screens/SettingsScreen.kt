package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pcosina.app.BuildConfig
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader

@Composable
fun SettingsScreen(
    userViewModel: UserViewModel,
    authViewModel: AuthViewModel,
    onNavigateToOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by userViewModel.userProfile.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    
    val userName = profile.displayName.ifBlank { "Warrior" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        GradientHeader(
            title = "Hi, $userName! ✨",
            subtitle = "Your PCOS journey is uniquely yours.",
            containerHeight = 180
        )

        // Personalized Profile Summary Card
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    )
                }
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "PCOS Management Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Health & Goals Section
        SettingsSection(title = "Health Markers") {
            SettingsItem(icon = Icons.Default.MonitorWeight, label = "Current Weight", value = "${profile.weightKg} kg")
            SettingsItem(icon = Icons.Default.Height, label = "Height", value = "${profile.heightCm} cm")
            SettingsItem(icon = Icons.Default.LocalFireDepartment, label = "Activity Level", value = profile.activityLevel)
            
            Divider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            
            Text(
                text = "Current Focus:",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.secondary
            )
            Text(
                text = profile.goal,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))
            
            Button(
                onClick = onNavigateToOnboarding,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Update Health Data", fontWeight = FontWeight.Bold)
            }
        }

        // Account Actions
        SettingsSection(title = "Account Settings") {
            SettingsActionItem(
                icon = Icons.Default.History,
                label = "Clear Meal History",
                description = "Reset generated plans for this account",
                color = colorScheme.onSurfaceVariant
            ) {
                // Feature to clear specific plan data could be added here
            }
            
            SettingsActionItem(
                icon = Icons.Default.Logout,
                label = "Logout",
                description = "Securely sign out of PCOSINA",
                color = colorScheme.secondary
            ) {
                authViewModel.onLogout()
            }

            if (BuildConfig.DEBUG) {
                SettingsActionItem(
                    icon = Icons.Default.Warning,
                    label = "Test Crash (Debug only)",
                    description = "Send a test crash to Crashlytics",
                    color = MaterialTheme.colorScheme.error
                ) {
                    FirebaseCrashlytics.getInstance().log("Manual test crash from Settings")
                    throw RuntimeException("Crashlytics test crash")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "PCOSINA • Empowerment through Nutrition",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SettingsActionItem(icon: ImageVector, label: String, description: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Box(
                modifier = Modifier.size(44.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = color)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
