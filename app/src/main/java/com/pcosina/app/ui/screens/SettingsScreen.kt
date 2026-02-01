package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val primaryColor = Color(0xFFFC6B7D)
    val secondaryColor = Color(0xFF8C3A45)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFF9F9))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        GradientHeader(
            title = "Account & Profile",
            subtitle = "Manage your PCOS parameters",
            containerHeight = 180
        )

        // Account Info Section
        SettingsSection(title = "Logged in as") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.AccountCircle, contentDescription = null, tint = primaryColor, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = profile.displayName.ifBlank { "User" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(text = "Active Profile", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }

        // Scientific Parameters Section
        SettingsSection(title = "Health Markers") {
            SettingsItem(icon = Icons.Default.MonitorWeight, label = "Weight", value = "${profile.weightKg} kg")
            SettingsItem(icon = Icons.Default.Height, label = "Height", value = "${profile.heightCm} cm")
            SettingsItem(icon = Icons.Default.Bolt, label = "Activity", value = profile.activityLevel)
            
            Divider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.LightGray)
            
            Button(
                onClick = onNavigateToOnboarding,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor.copy(alpha = 0.1f), contentColor = primaryColor),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text("Update Biometrics", fontWeight = FontWeight.Bold)
            }
        }

        // App Actions Section
        SettingsSection(title = "System") {
            SettingsActionItem(
                icon = Icons.Default.Refresh,
                label = "Reset Demo Data",
                description = "Clear all local session data",
                color = Color.Gray
            ) {
                authViewModel.onLogout()
            }
            
            SettingsActionItem(
                icon = Icons.Default.Logout,
                label = "Logout",
                description = "Securely sign out of your account",
                color = secondaryColor
            ) {
                authViewModel.onLogout()
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "PCOSINA v1.0.4 • Thesis Edition",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = Color.LightGray
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Black),
            color = Color(0xFF8C3A45),
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
            Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(text = value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFC6B7D))
    }
}

@Composable
fun SettingsActionItem(icon: ImageVector, label: String, description: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text = label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = color)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
