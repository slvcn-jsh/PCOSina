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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pcosina.app.BuildConfig
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.domain.HealthMetrics
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.notifications.NotificationScheduler
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.util.primaryGoalLabel
import android.Manifest
import android.os.Build
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    userViewModel: UserViewModel,
    authViewModel: AuthViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    userId: String,
    onNavigateToProfileEdit: () -> Unit,
    onOpenAdminMethodology: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile by userViewModel.userProfile.collectAsState()
    val adminMode by userViewModel.adminMode.collectAsState()
    val notificationPrefs by userViewModel.notificationPreferences.collectAsState()
    val notificationLogs by userViewModel.notificationLogs.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val userName = profile.displayName.ifBlank { "Warrior" }
    val baseUrl = BuildConfig.BASE_URL.trim().trim('"').trim('\'').trimEnd('/')
    val schemaUrl = "$baseUrl/schema"
    var tapCount by rememberSaveable { mutableStateOf(0) }

    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var clearActionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var clearActionError by rememberSaveable { mutableStateOf(false) }
    var logoutActionPending by rememberSaveable { mutableStateOf(false) }
    var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var showWeeklyResetDayDialog by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionHint by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsFeedbackBanner by remember { mutableStateOf<FeedbackBannerData?>(null) }
    var scheduledWorkSummaries by remember { mutableStateOf<List<String>>(emptyList()) }
    var nextReminderSummaries by remember { mutableStateOf<List<String>>(emptyList()) }
    val runtimeNotificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val permissionStateLabel = when {
        !appNotificationsEnabled -> "Blocked in system settings"
        runtimeNotificationPermissionGranted -> "Allowed"
        else -> "Permission not granted"
    }
    val lastFiredByType = remember(notificationLogs) {
        notificationLogs
            .groupBy { it.type }
            .mapValues { (_, logs) -> logs.maxOfOrNull { it.deliveredAt } ?: 0L }
            .toList()
            .sortedByDescending { it.second }
    }

    LaunchedEffect(userId, notificationPrefs) {
        nextReminderSummaries = NotificationScheduler.nextScheduledTimes(notificationPrefs)
        if (userId.isBlank()) {
            scheduledWorkSummaries = emptyList()
        } else {
            scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
        }
    }
    fun postSettingsFeedback(
        tone: FeedbackBannerTone,
        message: String,
        autoClearMs: Long = 2400L
    ) {
        settingsFeedbackBanner = FeedbackBannerData(
            tone = tone,
            message = message
        )
        Log.i("SettingsUX", message)
        if (tone != FeedbackBannerTone.Loading && autoClearMs > 0L) {
            scope.launch {
                delay(autoClearMs)
                if (settingsFeedbackBanner?.message == message) {
                    settingsFeedbackBanner = null
                }
            }
        }
    }

    fun updateNotificationPrefs(
        transform: (NotificationPreferences) -> NotificationPreferences
    ) {
        val updated = userViewModel.updateNotificationPreferences(transform)
        if (userId.isBlank()) return
        scope.launch {
            NotificationScheduler.rescheduleAll(
                context = context,
                userId = userId,
                prefs = updated
            )
            nextReminderSummaries = NotificationScheduler.nextScheduledTimes(updated)
            scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
        }
    }

    fun showTimePicker(
        hour: Int,
        minute: Int,
        onSelected: (Int, Int) -> Unit
    ) {
        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                onSelected(selectedHour, selectedMinute)
            },
            hour,
            minute,
            false
        ).show()
    }
    LaunchedEffect(logoutActionPending) {
        if (logoutActionPending) {
            delay(450)
            authViewModel.onLogout()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationPermissionHint = "Notifications enabled."
            postSettingsFeedback(
                tone = FeedbackBannerTone.Success,
                message = "Notifications enabled. Meal and weekly reminders can now run."
            )
            updateNotificationPrefs { prefs ->
                prefs.copy(masterEnabled = true)
            }
        } else {
            notificationPermissionHint = "Permission denied. Enable notifications from system settings to receive reminders."
            postSettingsFeedback(
                tone = FeedbackBannerTone.Error,
                message = "No change: notification permission was denied."
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)
    ) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            val enabled = !adminMode
                            userViewModel.toggleAdminMode()
                            postSettingsFeedback(
                                tone = FeedbackBannerTone.Success,
                                message = if (enabled) {
                                    "Admin mode enabled. System actions are now visible."
                                } else {
                                    "Admin mode disabled. System actions are now hidden."
                                }
                            )
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
        settingsFeedbackBanner?.let { banner ->
            AppFeedbackBanner(
                data = banner,
                modifier = Modifier.fillMaxWidth()
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                Spacer(Modifier.width(14.dp))
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
            SettingsDivider()
            SettingsItem(icon = Icons.Default.Height, label = "Height", value = heightText)
            SettingsDivider()
            SettingsItem(icon = Icons.Default.LocalFireDepartment, label = "Activity Level", value = profile.activityLevel)
            SettingsDivider()
            val bmiValue = HealthMetrics.bmi(profile.weightKg, profile.heightCm)
            val bmiLabel = if (bmiValue > 0) String.format("%.1f", bmiValue) else "—"
            val bmiCategory = HealthMetrics.bmiCategory(bmiValue)
            SettingsItem(icon = Icons.Default.MonitorWeight, label = "BMI", value = "$bmiLabel ($bmiCategory)")
            SettingsDivider()
            SettingsItem(icon = Icons.Default.Info, label = "Current Focus", value = primaryGoalLabel(profile.goal))

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
                text = "Includes profile, preferences, and budget.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        // Destructive Account Actions
        SettingsSection(
            title = "Danger Zone",
            titleColor = colorScheme.error,
            containerColor = colorScheme.errorContainer.copy(alpha = 0.35f)
        ) {
            Text(
                text = "Local device actions only.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            SettingsActionItem(
                icon = Icons.Default.History,
                label = "Clear Meal History",
                description = "Delete local plans, groceries, and logs",
                color = colorScheme.error,
                destructive = true
            ) {
                clearActionMessage = null
                clearActionError = false
                showClearDialog = true
            }
            clearActionMessage?.let { message ->
                SettingsInlineNotice(
                    message = message,
                    isError = clearActionError
                )
            }
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Logout",
                description = "Sign out from this device",
                color = colorScheme.error,
                destructive = true
            ) {
                logoutActionPending = false
                showLogoutDialog = true
            }
            if (logoutActionPending) {
                SettingsInlineNotice(
                    message = "Signing out…",
                    isError = false
                )
            }

            if (BuildConfig.DEBUG) {
                SettingsDivider()
                SettingsActionItem(
                    icon = Icons.Default.Warning,
                    label = "Test Crash (Debug only)",
                    description = "Send a test crash to Crashlytics",
                    color = MaterialTheme.colorScheme.error,
                    destructive = true
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
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    label = "View planning methodology",
                    description = "Open the admin-only technical pipeline view",
                    color = colorScheme.primary
                ) {
                    onOpenAdminMethodology()
                }
                if (BuildConfig.DEBUG) {
                    SettingsActionItem(
                        icon = Icons.Default.Info,
                        label = "Seed Demo Weeks",
                        description = "Generate 3 weeks of demo plans + progress",
                        color = colorScheme.primary
                    ) {
                        if (userId.isBlank()) {
                            postSettingsFeedback(
                                tone = FeedbackBannerTone.Error,
                                message = "No change: sign in first to seed demo weeks."
                            )
                        } else {
                            val start = LocalDate.now().with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
                            progressViewModel.loadForUser(userId, start.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            val seeds = mealPlanViewModel.seedDemoWeeks(profile)
                            progressViewModel.seedDemoWeeks(seeds)
                            postSettingsFeedback(
                                tone = FeedbackBannerTone.Success,
                                message = "Seeded ${seeds.size} demo weeks. Plan and progress timelines were refreshed."
                            )
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
                    Text("Enable Notifications", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Local reminders only. Generic lock-screen text is used for privacy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    showNotificationPermissionDialog = true
                                    return@Switch
                                }
                            }
                            updateNotificationPrefs { prefs -> prefs.copy(masterEnabled = true) }
                            notificationPermissionHint = null
                        } else {
                            updateNotificationPrefs { prefs -> prefs.copy(masterEnabled = false) }
                            if (userId.isNotBlank()) {
                                NotificationScheduler.cancelAll(context, userId)
                            }
                            notificationPermissionHint = "Notifications paused."
                        }
                    }
                )
            }
            Text(
                text = "Permission state: $permissionStateLabel",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            if (!appNotificationsEnabled) {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    label = "Open system notification settings",
                    description = "Allow notifications for PCOSINA in Android settings",
                    color = colorScheme.primary
                ) {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            }
            notificationPermissionHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Meal reminders", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Breakfast, lunch, and dinner reminders (max 3/day).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationPrefs.mealRemindersEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(mealRemindersEnabled = enabled) }
                    }
                )
            }
            if (notificationPrefs.mealRemindersEnabled && notificationPrefs.masterEnabled) {
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Breakfast time",
                    description = formatTime(notificationPrefs.breakfastHour, notificationPrefs.breakfastMinute),
                    color = colorScheme.primary
                ) {
                    showTimePicker(notificationPrefs.breakfastHour, notificationPrefs.breakfastMinute) { h, m ->
                        updateNotificationPrefs { prefs -> prefs.copy(breakfastHour = h, breakfastMinute = m) }
                    }
                }
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Lunch time",
                    description = formatTime(notificationPrefs.lunchHour, notificationPrefs.lunchMinute),
                    color = colorScheme.primary
                ) {
                    showTimePicker(notificationPrefs.lunchHour, notificationPrefs.lunchMinute) { h, m ->
                        updateNotificationPrefs { prefs -> prefs.copy(lunchHour = h, lunchMinute = m) }
                    }
                }
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Dinner time",
                    description = formatTime(notificationPrefs.dinnerHour, notificationPrefs.dinnerMinute),
                    color = colorScheme.primary
                ) {
                    showTimePicker(notificationPrefs.dinnerHour, notificationPrefs.dinnerMinute) { h, m ->
                        updateNotificationPrefs { prefs -> prefs.copy(dinnerHour = h, dinnerMinute = m) }
                    }
                }
            }
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Plan ready notification", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Sent when a weekly plan generation finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationPrefs.planReadyEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(planReadyEnabled = enabled) }
                    }
                )
            }
            if (notificationPrefs.weeklyResetEnabled && notificationPrefs.masterEnabled) {
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Weekly reset day",
                    description = formatDayOfWeek(notificationPrefs.weeklyResetDayOfWeek),
                    color = colorScheme.primary
                ) {
                    showWeeklyResetDayDialog = true
                }
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Weekly reset time",
                    description = formatTime(notificationPrefs.weeklyResetHour, notificationPrefs.weeklyResetMinute),
                    color = colorScheme.primary
                ) {
                    showTimePicker(notificationPrefs.weeklyResetHour, notificationPrefs.weeklyResetMinute) { h, m ->
                        updateNotificationPrefs { prefs ->
                            prefs.copy(
                                weeklyResetHour = h,
                                weeklyResetMinute = m
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Grocery sync status", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Notifies only when you manually start sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationPrefs.grocerySyncEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(grocerySyncEnabled = enabled) }
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weekly reset reminder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Weekly reminder to generate your next plan (max 1/week).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationPrefs.weeklyResetEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(weeklyResetEnabled = enabled) }
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Consistency nudges", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Gentle routine nudges, goal-aware and non-judgmental.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationPrefs.streakNudgesEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(streakNudgesEnabled = enabled) }
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Inactivity reminder", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "A quick check-in reminder after inactivity (max 1/3 days).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationPrefs.inactivityNudgesEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(inactivityNudgesEnabled = enabled) }
                    }
                )
            }
            SettingsDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Quiet hours", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "Pauses routine reminders within selected time window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = notificationPrefs.quietHoursEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(quietHoursEnabled = enabled) }
                    }
                )
            }
            if (notificationPrefs.quietHoursEnabled && notificationPrefs.masterEnabled) {
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Quiet hours start",
                    description = formatTime(notificationPrefs.quietStartHour, notificationPrefs.quietStartMinute),
                    color = colorScheme.primary
                ) {
                    showTimePicker(notificationPrefs.quietStartHour, notificationPrefs.quietStartMinute) { h, m ->
                        updateNotificationPrefs { prefs -> prefs.copy(quietStartHour = h, quietStartMinute = m) }
                    }
                }
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Quiet hours end",
                    description = formatTime(notificationPrefs.quietEndHour, notificationPrefs.quietEndMinute),
                    color = colorScheme.primary
                ) {
                    showTimePicker(notificationPrefs.quietEndHour, notificationPrefs.quietEndMinute) { h, m ->
                        updateNotificationPrefs { prefs -> prefs.copy(quietEndHour = h, quietEndMinute = m) }
                    }
                }
            }
            Text(
                text = "Next reminders: B ${formatTime(notificationPrefs.breakfastHour, notificationPrefs.breakfastMinute)} • " +
                    "L ${formatTime(notificationPrefs.lunchHour, notificationPrefs.lunchMinute)} • " +
                    "D ${formatTime(notificationPrefs.dinnerHour, notificationPrefs.dinnerMinute)} • " +
                    "Weekly ${formatDayOfWeek(notificationPrefs.weeklyResetDayOfWeek)} ${formatTime(notificationPrefs.weeklyResetHour, notificationPrefs.weeklyResetMinute)}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (BuildConfig.DEBUG) {
                SettingsDivider()
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    label = "Send test notification",
                    description = "Triggers a local debug notification",
                    color = colorScheme.primary
                ) {
                    if (userId.isBlank()) {
                        postSettingsFeedback(
                            tone = FeedbackBannerTone.Error,
                            message = "No change: sign in first to send a test notification."
                        )
                        return@SettingsActionItem
                    }
                    scope.launch {
                        NotificationScheduler.notifyDebugTest(context, userId)
                        postSettingsFeedback(
                            tone = FeedbackBannerTone.Success,
                            message = "Test notification sent. Delivery logs were updated."
                        )
                        scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
                    }
                }
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Send meal reminder now",
                    description = "Fires meal reminder path with caps and quiet-hours checks",
                    color = colorScheme.primary
                ) {
                    if (userId.isBlank()) {
                        postSettingsFeedback(
                            tone = FeedbackBannerTone.Error,
                            message = "No change: sign in first to send meal reminders."
                        )
                        return@SettingsActionItem
                    }
                    scope.launch {
                        val delivered = NotificationScheduler.notifyMealReminderNow(context, userId)
                        postSettingsFeedback(
                            tone = if (delivered) FeedbackBannerTone.Success else FeedbackBannerTone.Error,
                            message = if (delivered) {
                                "Meal reminder delivered. Scheduling state was refreshed."
                            } else {
                                "No reminder sent: blocked by caps, quiet hours, permission, or session."
                            }
                        )
                        scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
                    }
                }
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Send weekly reset now",
                    description = "Fires weekly reset path with 7-day cap checks",
                    color = colorScheme.primary
                ) {
                    if (userId.isBlank()) {
                        postSettingsFeedback(
                            tone = FeedbackBannerTone.Error,
                            message = "No change: sign in first to send weekly reset reminders."
                        )
                        return@SettingsActionItem
                    }
                    scope.launch {
                        val delivered = NotificationScheduler.notifyWeeklyResetNow(context, userId)
                        postSettingsFeedback(
                            tone = if (delivered) FeedbackBannerTone.Success else FeedbackBannerTone.Error,
                            message = if (delivered) {
                                "Weekly reset reminder delivered. Scheduling state was refreshed."
                            } else {
                                "No reminder sent: blocked by caps, quiet hours, permission, or session."
                            }
                        )
                        scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
                    }
                }
                Text(
                    text = "Scheduled workers",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurface
                )
                nextReminderSummaries.forEach { summary ->
                    Text(
                        text = "• $summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (nextReminderSummaries.isNotEmpty()) {
                    SettingsDivider()
                }
                if (scheduledWorkSummaries.isEmpty()) {
                    Text(
                        text = "No active notification workers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    scheduledWorkSummaries.take(6).forEach { summary ->
                        Text(
                            text = "• $summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "Last fired timestamps by type",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurface
                )
                if (lastFiredByType.isEmpty()) {
                    Text(
                        text = "No fired notifications yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    lastFiredByType.take(8).forEach { (type, timestamp) ->
                        val stamp = Instant.ofEpochMilli(timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH))
                        Text(
                            text = "• $type → $stamp",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "Last delivered notifications",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurface
                )
                if (notificationLogs.isEmpty()) {
                    Text(
                        text = "No notifications delivered yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                } else {
                    notificationLogs.take(5).forEach { log ->
                        Text(
                            text = "• ${log.type}: ${log.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    if (userId.isNotBlank()) {
                        mealPlanViewModel.clearPlanHistory()
                        groceryViewModel.clearForUser()
                        progressViewModel.clearReflectionsForUser()
                        clearActionMessage = "✓ Meal history cleared."
                        clearActionError = false
                    } else {
                        clearActionMessage = "Couldn’t clear meal history."
                        clearActionError = true
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
                    logoutActionPending = true
                }) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
            },
            title = { Text("Logout?") },
            text = { Text("You will need to sign in again to access your data.") }
        )
    }

    if (showWeeklyResetDayDialog) {
        AlertDialog(
            onDismissRequest = { showWeeklyResetDayDialog = false },
            confirmButton = {
                TextButton(onClick = { showWeeklyResetDayDialog = false }) {
                    Text("Done")
                }
            },
            title = { Text("Weekly reset day") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { day ->
                        TextButton(
                            onClick = {
                                updateNotificationPrefs { prefs ->
                                    prefs.copy(weeklyResetDayOfWeek = day)
                                }
                                showWeeklyResetDayDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatDayOfWeek(day),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        )
    }

    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationPermissionDialog = false
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Allow notifications?") },
            text = {
                Text(
                    "PCOSINA uses local reminders for meal check-ins and weekly planning. " +
                        "Notification text stays generic on lock screen."
                )
            }
        )
    }
}

@Composable
private fun SettingsInlineNotice(
    message: String,
    isError: Boolean
) {
    Surface(
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        },
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    SettingsSection(
        title = title,
        titleColor = MaterialTheme.colorScheme.secondary,
        containerColor = MaterialTheme.colorScheme.surface,
        content = content
    )
}

@Composable
fun SettingsSection(
    title: String,
    titleColor: Color,
    containerColor: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionHeaderGap)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Black),
            color = titleColor,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(UiSpacingTokens.CardContentPadding),
                verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.CardContentGap)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SettingsActionItem(
    icon: ImageVector,
    label: String,
    description: String,
    color: Color,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (destructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f) else Color.Transparent,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier.size(28.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = color
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (destructive) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 1.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val normalizedHour = hour.coerceIn(0, 23)
    val normalizedMinute = minute.coerceIn(0, 59)
    val amPm = if (normalizedHour >= 12) "PM" else "AM"
    val displayHour = when (val h = normalizedHour % 12) {
        0 -> 12
        else -> h
    }
    return String.format(Locale.ENGLISH, "%d:%02d %s", displayHour, normalizedMinute, amPm)
}

private fun formatDayOfWeek(day: Int): String {
    return when (day.coerceIn(1, 7)) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        else -> "Sunday"
    }
}
