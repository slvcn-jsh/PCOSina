package com.pcosina.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pcosina.app.BuildConfig
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.notifications.NotificationHelper
import android.Manifest
import android.os.Build
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
fun SettingsScreen(
    userViewModel: UserViewModel,
    authViewModel: AuthViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    userId: String,
    onNavigateToProfileEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by userViewModel.userProfile.collectAsState()
    val adminMode by userViewModel.adminMode.collectAsState()
    val remindersEnabled by userViewModel.remindersEnabled.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    
    val userName = profile.displayName.ifBlank { "Warrior" }
    val baseUrl = BuildConfig.BASE_URL.trim().trim('"').trim('\'').trimEnd('/')
    val schemaUrl = "$baseUrl/schema"
    var tapCount by rememberSaveable { mutableStateOf(0) }

    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            userViewModel.setRemindersEnabled(true)
            NotificationHelper.scheduleDailyReminder(context)
        } else {
            Toast.makeText(context, "Notification permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            val enabled = !adminMode
                            userViewModel.toggleAdminMode()
                            Toast.makeText(
                                context,
                                if (enabled) "Admin mode enabled" else "Admin mode disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
                .clickable {
                    tapCount += 1
                }
        ) {
            GradientHeader(
                title = "Hi, $userName! ✨",
                subtitle = "Your PCOS journey is uniquely yours.",
                containerHeight = 180
            )
        }

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
            val weightText = if (profile.weightUnit == UnitConverter.WEIGHT_LB) {
                "${UnitConverter.kgToLb(profile.weightKg)} lb"
            } else {
                "${profile.weightKg} kg"
            }
            val heightText = if (profile.heightUnit == UnitConverter.HEIGHT_FT_IN) {
                val (ft, inch) = UnitConverter.cmToFeetInches(profile.heightCm)
                "${ft}ft ${inch}in"
            } else {
                "${profile.heightCm} cm"
            }
            SettingsItem(icon = Icons.Default.MonitorWeight, label = "Current Weight", value = weightText)
            SettingsItem(icon = Icons.Default.Height, label = "Height", value = heightText)
            SettingsItem(icon = Icons.Default.LocalFireDepartment, label = "Activity Level", value = profile.activityLevel)
            val bmiValue = HealthMetrics.bmi(profile.weightKg, profile.heightCm)
            val bmiLabel = if (bmiValue > 0) String.format("%.1f", bmiValue) else "—"
            val bmiCategory = HealthMetrics.bmiCategory(bmiValue)
            SettingsItem(icon = Icons.Default.MonitorWeight, label = "BMI", value = "$bmiLabel ($bmiCategory)")
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
            
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
                onClick = onNavigateToProfileEdit,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Update Health Data", fontWeight = FontWeight.Bold)
            }
            Text(
                text = "Includes budget and preferences on Step 3.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        // Account Actions
        SettingsSection(title = "Account Settings") {
            SettingsActionItem(
                icon = Icons.Default.History,
                label = "Clear Meal History",
                description = "Reset generated plans for this account",
                color = colorScheme.onSurfaceVariant
            ) {
                showClearDialog = true
            }
            
            SettingsActionItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Logout",
                description = "Securely sign out of PCOSINA",
                color = colorScheme.secondary
            ) {
                showLogoutDialog = true
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

        if (!adminMode && tapCount >= 5) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tip: long‑press the header to enable Admin mode.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        if (adminMode) {
            SettingsSection(title = "System") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    label = "Schema Version",
                    value = BuildConfig.SCHEMA_VERSION
                )
                SettingsItem(
                    icon = Icons.Default.History,
                    label = "API Base URL",
                    value = baseUrl
                )
                SettingsActionItem(
                    icon = Icons.Default.Link,
                    label = "View API Contract",
                    description = schemaUrl,
                    color = colorScheme.primary
                ) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(schemaUrl))
                    context.startActivity(intent)
                }
                if (BuildConfig.DEBUG) {
                    SettingsActionItem(
                        icon = Icons.Default.Info,
                        label = "Seed Demo Weeks",
                        description = "Generate 3 weeks of demo plans + progress",
                        color = colorScheme.primary
                    ) {
                        if (userId.isBlank()) {
                            Toast.makeText(context, "Sign in first to seed demo weeks.", Toast.LENGTH_SHORT).show()
                        } else {
                            val start = LocalDate.now().with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
                            progressViewModel.loadForUser(userId, start.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            val seeds = mealPlanViewModel.seedDemoWeeks(profile)
                            progressViewModel.seedDemoWeeks(seeds)
                            Toast.makeText(context, "Seeded 3 demo weeks.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        SettingsSection(title = "Notifications") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Daily Reminder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Reminds you to check your plan and log progress (7:00 PM).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@Switch
                                }
                            }
                            userViewModel.setRemindersEnabled(true)
                            NotificationHelper.scheduleDailyReminder(context)
                        } else {
                            userViewModel.setRemindersEnabled(false)
                            NotificationHelper.cancelDailyReminder(context)
                        }
                    }
                )
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

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    if (userId.isNotBlank()) {
                        mealPlanViewModel.clearPlanHistory()
                        groceryViewModel.clearForUser()
                        progressViewModel.clearReflectionsForUser()
                    }
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            },
            title = { Text("Clear meal history?") },
            text = { Text("This removes plans, grocery snapshots, and adherence logs for this account.") }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    authViewModel.onLogout()
                }) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            },
            title = { Text("Logout?") },
            text = { Text("You will need to sign in again to access your data.") }
        )
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
