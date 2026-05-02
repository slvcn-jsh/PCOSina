package com.pcosina.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.pcosina.app.ui.components.ExpandableSection
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.FocusSummaryCard
import com.pcosina.app.ui.components.GradientHeader
import com.pcosina.app.ui.components.RefinedFeatureCard
import com.pcosina.app.ui.components.ScreenFocusOption
import com.pcosina.app.ui.components.ScreenFocusStrip
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

private enum class SettingsScreenFocus {
    Profile,
    Reminders,
    Tools,
    Account,
}

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
    
    val userName = profile.displayName.ifBlank { "Your account" }
    val baseUrl = BuildConfig.BASE_URL.trim().trim('"').trim('\'').trimEnd('/')
    val schemaUrl = "$baseUrl/schema"
    var settingsFocusKey by rememberSaveable { mutableStateOf(SettingsScreenFocus.Profile.name) }
    val settingsFocus = remember(settingsFocusKey) {
        SettingsScreenFocus.valueOf(settingsFocusKey)
    }
    val settingsFocusOptions = remember(adminMode) {
        buildList {
            add(
                ScreenFocusOption(
                    key = SettingsScreenFocus.Profile.name,
                    label = "Profile",
                    summary = "See the details that shape your plans, groceries, and reminders."
                )
            )
            add(
                ScreenFocusOption(
                    key = SettingsScreenFocus.Reminders.name,
                    label = "Reminders",
                    summary = "Choose the nudges that help on this phone."
                )
            )
            add(
                ScreenFocusOption(
                    key = SettingsScreenFocus.Account.name,
                    label = "Account",
                    summary = "Handle sign-out and saved data for this phone."
                )
            )
            if (adminMode) {
                add(
                    ScreenFocusOption(
                        key = SettingsScreenFocus.Tools.name,
                        label = "Tools",
                        summary = "Open hidden review links and local testing tools."
                    )
                )
            }
        }
    }
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
    val notificationStatusSummary = when {
        !notificationPrefs.masterEnabled -> "Reminders are paused."
        !appNotificationsEnabled -> "Phone settings are blocking reminders."
        runtimeNotificationPermissionGranted -> "Reminders are ready for meals and plan updates."
        else -> "Reminder permission still needs approval."
    }
    val scheduledWorkersSummary = if (scheduledWorkSummaries.isEmpty()) {
        "No reminder times lined up yet."
    } else {
        "${scheduledWorkSummaries.size} reminder time(s) lined up"
    }
    val nextReminderSummary = nextReminderSummaries.firstOrNull()?.let { "Next reminder: $it" }
        ?: "No reminder time saved yet."
    val reminderOverviewLabel = if (notificationPrefs.masterEnabled) {
        "Reminders on"
    } else {
        "Reminders paused"
    }
    val mainGoalLabel = primaryGoalLabel(profile.goal)
    val householdLabel = if (profile.householdSize <= 1) {
        "1 person"
    } else {
        "${profile.householdSize} people"
    }
    val ageLabel = profile.age.takeIf { it > 0 }?.let { "$it years old" } ?: "Not set"
    val budgetLabel = profile.weeklyBudgetPhp.takeIf { it > 0 }?.let { "₱$it / week" } ?: "Not set"
    val dietRulesLabel = if (profile.dietaryRestrictions.isEmpty()) {
        "None saved"
    } else {
        "${profile.dietaryRestrictions.size} saved"
    }
    val allergyLabel = if (profile.allergies.isEmpty()) {
        "None saved"
    } else {
        "${profile.allergies.size} saved"
    }
    val pantryLabel = if (profile.pantryItems.isEmpty()) {
        "No pantry saved"
    } else {
        "${profile.pantryItems.size} saved"
    }
    val planningPriorityLabel = profile.planningPriority.ifBlank { "Balanced" }
    val maxCookTimeLabel = "${profile.maxCookingTimeMinutes} min max"
    val settingsStatusLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> if (profile.isProfileCompleted) {
            "Your profile is ready to guide meals, groceries, and reminders."
        } else {
            "Finish your profile so planning feels more personal."
        }
        SettingsScreenFocus.Reminders -> notificationStatusSummary
        SettingsScreenFocus.Tools -> if (adminMode) {
            "Operator tools are available for this account."
        } else {
            "Review-only tools stay hidden in normal use."
        }
        SettingsScreenFocus.Account -> "Account actions stay local to this device."
    }
    val settingsSyncLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Saved food rules: ${profile.dietaryRestrictions.size + profile.allergies.size}"
        SettingsScreenFocus.Reminders -> if (notificationPrefs.masterEnabled) {
            nextReminderSummary
        } else {
            scheduledWorkersSummary
        }
        SettingsScreenFocus.Tools -> "Authenticated operator access is active on this account."
        SettingsScreenFocus.Account -> if (logoutActionPending) {
            "Sign-out is already in progress."
        } else {
            "You stay in control of what stays on this phone."
        }
    }
    val settingsPlanRangeLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Main goal: $mainGoalLabel"
        SettingsScreenFocus.Reminders -> "Phone permission: $permissionStateLabel"
        SettingsScreenFocus.Tools -> "Review tools stay tucked away from everyday settings."
        SettingsScreenFocus.Account -> "Clearing data only affects this phone unless you confirm it."
    }
    val settingsNextLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Next focus: review the details that affect budget, time, and food rules."
        SettingsScreenFocus.Reminders -> nextReminderSummary
        SettingsScreenFocus.Tools -> "Next focus: open the guide or local test tools only when you are reviewing the build."
        SettingsScreenFocus.Account -> "Next focus: confirm before clearing data or signing out."
    }
    val settingsSummaryBadge = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Profile setup"
        SettingsScreenFocus.Reminders -> "Reminder controls"
        SettingsScreenFocus.Tools -> "Review tools"
        SettingsScreenFocus.Account -> "Account actions"
    }
    val settingsSummaryTitle = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Profile details shape your week."
        SettingsScreenFocus.Reminders -> "Keep reminders helpful, not noisy."
        SettingsScreenFocus.Tools -> "Internal tools stay out of normal use."
        SettingsScreenFocus.Account -> "Account controls should stay clear and deliberate."
    }
    val settingsSummaryAccent = when (settingsFocus) {
        SettingsScreenFocus.Profile -> colorScheme.primary
        SettingsScreenFocus.Reminders -> colorScheme.secondary
        SettingsScreenFocus.Tools -> colorScheme.tertiary
        SettingsScreenFocus.Account -> colorScheme.error
    }
    val settingsSummaryHighlights = buildList {
        add(settingsPlanRangeLabel)
        add(settingsSyncLabel)
        add(settingsNextLabel)
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
    LaunchedEffect(adminMode, settingsFocusKey) {
        if (!adminMode && settingsFocusKey == SettingsScreenFocus.Tools.name) {
            settingsFocusKey = SettingsScreenFocus.Profile.name
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
        GradientHeader(
            title = "Settings",
            subtitle = "Make PCOSINA feel calm, useful, and easy to return to.",
            containerHeight = 116
        )
        settingsFeedbackBanner?.let { banner ->
            AppFeedbackBanner(
                data = banner,
                modifier = Modifier.fillMaxWidth()
            )
        }
        ScreenFocusStrip(
            title = "Go to",
            options = settingsFocusOptions,
            selectedKey = settingsFocusKey,
            onSelect = { settingsFocusKey = it },
            labelMaxWidth = 132.dp,
            helperText = "Move between profile, reminders, and account controls without the clutter."
        )
        FocusSummaryCard(
            badge = settingsSummaryBadge,
            title = settingsSummaryTitle,
            body = settingsStatusLabel,
            accentColor = settingsSummaryAccent,
            highlights = settingsSummaryHighlights,
            modifier = Modifier.fillMaxWidth()
        )

        // Personalized Profile Summary Card
        if (settingsFocus == SettingsScreenFocus.Profile) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
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
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsStatePill(
                            text = if (profile.isProfileCompleted) "Profile ready" else "Finish profile",
                            emphasized = profile.isProfileCompleted
                        )
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Saved on this phone and used when you build a week.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingsStatePill(
                                text = mainGoalLabel,
                                emphasized = true
                            )
                            SettingsStatePill(
                                text = reminderOverviewLabel,
                                emphasized = notificationPrefs.masterEnabled
                            )
                            SettingsStatePill(
                                text = budgetLabel,
                                emphasized = profile.weeklyBudgetPhp > 0
                            )
                        }
                    }
                }
            }
        }

        if (settingsFocus == SettingsScreenFocus.Profile) {
            SettingsSection(
                title = "Basics",
                summary = "These details help size targets, portions, and everyday planning choices."
            ) {
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
                val bmiValue = HealthMetrics.bmi(profile.weightKg, profile.heightCm)
                val bmiLabel = if (bmiValue > 0) String.format("%.1f", bmiValue) else "—"
                val bmiCategory = HealthMetrics.bmiCategory(bmiValue)
                SettingsItem(icon = Icons.Default.Info, label = "Age", value = ageLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.MonitorWeight, label = "Weight", value = weightText)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Height, label = "Height", value = heightText)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.LocalFireDepartment, label = "Daily activity", value = profile.activityLevel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Info, label = "Household", value = householdLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.MonitorWeight, label = "BMI", value = "$bmiLabel ($bmiCategory)")
            }
        }

        if (settingsFocus == SettingsScreenFocus.Profile) {
            SettingsSection(
                title = "Planning preferences",
                summary = "These choices shape food matching, budget limits, and how flexible the week feels."
            ) {
                SettingsItem(icon = Icons.Default.Info, label = "Main goal", value = mainGoalLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Info, label = "Weekly budget", value = budgetLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.History, label = "Max cooking time", value = maxCookTimeLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Info, label = "Food rules", value = dietRulesLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Warning, label = "Allergies", value = allergyLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Info, label = "Pantry saved", value = pantryLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Info, label = "Planning priority", value = planningPriorityLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Info, label = "Variety", value = profile.varietyPreference)

                Button(
                    onClick = onNavigateToProfileEdit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit my profile", fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "This is where you update food rules, cooking limits, pantry details, and budget choices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        if (settingsFocus == SettingsScreenFocus.Account) {
            SettingsSection(
                title = "Account and device actions",
                summary = "Use these only when you need a clean reset or want to leave this phone signed out.",
                titleColor = colorScheme.error,
                containerColor = colorScheme.errorContainer.copy(alpha = 0.45f)
            ) {
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Clear saved week data",
                    description = "Removes saved plans, groceries, and logs from this phone only.",
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
                    label = "Sign out on this phone",
                    description = "You can sign back in later without changing your planner rules.",
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
            }
        }


        if (adminMode && settingsFocus == SettingsScreenFocus.Tools) {
            RefinedFeatureCard(
                icon = Icons.Default.Info,
                accentColor = colorScheme.primary,
                statusLabel = "Review mode",
                title = "Team tools are hidden away from the everyday settings flow",
                body = "Use these only when you are testing the build, reviewing internal behavior, or preparing demo data.",
                highlights = listOf(
                    "Open the planner guide or data contract only when you need internal context.",
                    "Testing actions stay here so personal settings remain calm and user-facing."
                )
            )
        }

        if (adminMode && settingsFocus == SettingsScreenFocus.Tools) {
            SettingsSection(
                title = "Internal review tools",
                summary = "Visible only for authenticated operator accounts."
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        label = "App data version",
                        value = BuildConfig.SCHEMA_VERSION
                    )
                    SettingsItem(
                        icon = Icons.Default.History,
                        label = "Server address",
                        value = baseUrl
                    )
                    SettingsActionItem(
                        icon = Icons.Default.Link,
                        label = "Open data contract",
                        description = "Open the current schema for this build",
                        color = colorScheme.primary
                    ) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(schemaUrl))
                        context.startActivity(intent)
                    }
                    SettingsActionItem(
                        icon = Icons.Default.Info,
                        label = "Open planner guide",
                        description = "Open the team guide for how the planner builds a week",
                        color = colorScheme.primary
                    ) {
                        onOpenAdminMethodology()
                    }
                    if (BuildConfig.DEBUG) {
                        SettingsDivider()
                        SettingsActionItem(
                            icon = Icons.Default.Warning,
                            label = "Test crash report",
                            description = "Send a manual test crash to Crashlytics",
                            color = MaterialTheme.colorScheme.error,
                            destructive = true
                        ) {
                            FirebaseCrashlytics.getInstance().log("Manual test crash from Settings")
                            throw RuntimeException("Crashlytics test crash")
                        }
                    }
                }
            }
        }

        if (settingsFocus == SettingsScreenFocus.Reminders) {
            SettingsSection(
                title = "Reminder control",
                summary = "Choose whether PCOSINA can nudge you on this phone and keep notifications respectful."
            ) {
                SettingsToggleItem(
                    label = "Turn reminders on",
                    description = "These reminders stay on this phone. Lock-screen text stays generic for privacy.",
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
                                } else {
                                    updateNotificationPrefs { prefs -> prefs.copy(masterEnabled = true) }
                                    notificationPermissionHint = null
                                }
                            } else {
                                updateNotificationPrefs { prefs -> prefs.copy(masterEnabled = true) }
                                notificationPermissionHint = null
                            }
                        } else {
                            updateNotificationPrefs { prefs -> prefs.copy(masterEnabled = false) }
                            if (userId.isNotBlank()) {
                                NotificationScheduler.cancelAll(context, userId)
                            }
                            notificationPermissionHint = "Notifications paused."
                        }
                    }
                )
                SettingsInlineNotice(
                    message = "Phone permission: $permissionStateLabel",
                    isError = !runtimeNotificationPermissionGranted || !appNotificationsEnabled
                )
                if (!appNotificationsEnabled) {
                    SettingsActionItem(
                        icon = Icons.Default.Info,
                        label = "Open phone notification settings",
                        description = "Allow reminders for PCOSINA in Android settings",
                        color = colorScheme.primary
                    ) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }
                notificationPermissionHint?.let { hint ->
                    SettingsInlineNotice(
                        message = hint,
                        isError = hint.contains("denied", ignoreCase = true)
                    )
                }
            }

            SettingsSection(
                title = "Meal reminders",
                summary = "Pick meal nudges that fit your day instead of chasing ideal times."
            ) {
                SettingsToggleItem(
                    label = "Meal time reminders",
                    description = "Breakfast, lunch, and dinner reminders.",
                    checked = notificationPrefs.mealRemindersEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(mealRemindersEnabled = enabled) }
                    }
                )
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
                Text(
                    text = "Saved meal times: B ${formatTime(notificationPrefs.breakfastHour, notificationPrefs.breakfastMinute)} • " +
                        "L ${formatTime(notificationPrefs.lunchHour, notificationPrefs.lunchMinute)} • " +
                        "D ${formatTime(notificationPrefs.dinnerHour, notificationPrefs.dinnerMinute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SettingsSection(
                title = "Weekly planning nudges",
                summary = "Keep planning reminders helpful without making them feel noisy."
            ) {
                SettingsToggleItem(
                    label = "Plan is ready",
                    description = "Tells you when a new week finishes loading.",
                    checked = notificationPrefs.planReadyEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(planReadyEnabled = enabled) }
                    }
                )
                SettingsToggleItem(
                    label = "Grocery update alerts",
                    description = "Shown when you refresh grocery data yourself.",
                    checked = notificationPrefs.grocerySyncEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(grocerySyncEnabled = enabled) }
                    }
                )
                SettingsToggleItem(
                    label = "New week reminder",
                    description = "A weekly nudge to create your next plan.",
                    checked = notificationPrefs.weeklyResetEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(weeklyResetEnabled = enabled) }
                    }
                )
                if (notificationPrefs.weeklyResetEnabled && notificationPrefs.masterEnabled) {
                    SettingsActionItem(
                        icon = Icons.Default.History,
                        label = "Weekly reminder day",
                        description = formatDayOfWeek(notificationPrefs.weeklyResetDayOfWeek),
                        color = colorScheme.primary
                    ) {
                        showWeeklyResetDayDialog = true
                    }
                    SettingsActionItem(
                        icon = Icons.Default.History,
                        label = "Weekly reminder time",
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
                Text(
                    text = "Weekly reminder: ${formatDayOfWeek(notificationPrefs.weeklyResetDayOfWeek)} ${formatTime(notificationPrefs.weeklyResetHour, notificationPrefs.weeklyResetMinute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SettingsSection(
                title = "Gentle routine nudges",
                summary = "Keep check-ins supportive, lightweight, and easy to ignore when you need quiet."
            ) {
                SettingsToggleItem(
                    label = "Keep-going nudges",
                    description = "Gentle routine nudges, goal-aware and non-judgmental.",
                    checked = notificationPrefs.streakNudgesEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(streakNudgesEnabled = enabled) }
                    }
                )
                SettingsToggleItem(
                    label = "Come-back reminder",
                    description = "A quick check-in after a few quiet days.",
                    checked = notificationPrefs.inactivityNudgesEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(inactivityNudgesEnabled = enabled) }
                    }
                )
                SettingsToggleItem(
                    label = "Do not disturb hours",
                    description = "Pauses reminders during the hours you choose.",
                    checked = notificationPrefs.quietHoursEnabled,
                    enabled = notificationPrefs.masterEnabled,
                    onCheckedChange = { enabled ->
                        updateNotificationPrefs { prefs -> prefs.copy(quietHoursEnabled = enabled) }
                    }
                )
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
                    text = "Upcoming reminders: ${nextReminderSummaries.firstOrNull() ?: "No reminder time saved yet."}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (BuildConfig.DEBUG && adminMode) {
                ExpandableSection(
                    title = "Reminder testing",
                    subtitle = "For review builds only",
                    defaultExpanded = false
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        SettingsActionItem(
                            icon = Icons.Default.Info,
                            label = "Send sample reminder",
                            description = "Send a sample reminder on this phone",
                            color = colorScheme.primary
                        ) {
                            if (userId.isBlank()) {
                                postSettingsFeedback(
                                    tone = FeedbackBannerTone.Error,
                                    message = "No change: sign in first before sending a sample reminder."
                                )
                                return@SettingsActionItem
                            }
                            scope.launch {
                                NotificationScheduler.notifyDebugTest(context, userId)
                                postSettingsFeedback(
                                    tone = FeedbackBannerTone.Success,
                                    message = "Sample reminder sent. The reminder list was refreshed."
                                )
                                scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
                            }
                        }
                        SettingsActionItem(
                            icon = Icons.Default.History,
                            label = "Send meal reminder now",
                            description = "Try the meal reminder right now",
                            color = colorScheme.primary
                        ) {
                            if (userId.isBlank()) {
                                postSettingsFeedback(
                                    tone = FeedbackBannerTone.Error,
                                    message = "No change: sign in first before sending meal reminders."
                                )
                                return@SettingsActionItem
                            }
                            scope.launch {
                                val delivered = NotificationScheduler.notifyMealReminderNow(context, userId)
                                postSettingsFeedback(
                                    tone = if (delivered) FeedbackBannerTone.Success else FeedbackBannerTone.Error,
                                    message = if (delivered) {
                                        "Meal reminder sent. The reminder list was refreshed."
                                    } else {
                                        "No reminder was sent right now because of quiet hours, limits, permission, or sign-in."
                                    }
                                )
                                scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
                            }
                        }
                        SettingsActionItem(
                            icon = Icons.Default.History,
                            label = "Send new week reminder now",
                            description = "Try the new-week reminder right now",
                            color = colorScheme.primary
                        ) {
                            if (userId.isBlank()) {
                                postSettingsFeedback(
                                    tone = FeedbackBannerTone.Error,
                                    message = "No change: sign in first before sending the new-week reminder."
                                )
                                return@SettingsActionItem
                            }
                            scope.launch {
                                val delivered = NotificationScheduler.notifyWeeklyResetNow(context, userId)
                                postSettingsFeedback(
                                    tone = if (delivered) FeedbackBannerTone.Success else FeedbackBannerTone.Error,
                                    message = if (delivered) {
                                        "New-week reminder sent. The reminder list was refreshed."
                                    } else {
                                        "No reminder was sent right now because of quiet hours, limits, permission, or sign-in."
                                    }
                                )
                                scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
                            }
                        }
                        if (nextReminderSummaries.isNotEmpty()) {
                            SettingsDivider()
                        }
                        Text(
                            text = "Upcoming reminders",
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
                                text = "No reminder times lined up yet.",
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
                        SettingsDivider()
                        Text(
                            text = "Recent reminder types",
                            style = MaterialTheme.typography.labelLarge,
                            color = colorScheme.onSurface
                        )
                        if (lastFiredByType.isEmpty()) {
                            Text(
                                text = "No reminders fired yet.",
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
                                    text = "• $type -> $stamp",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        SettingsDivider()
                        Text(
                            text = "Recent reminders sent",
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
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "PCOSINA • Calm planning, private reminders, and easy routines",
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
                SettingsDialogConfirmButton(
                    label = "Clear history",
                    destructive = true,
                    onClick = {
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
                }
                )
            },
            dismissButton = {
                SettingsDialogDismissButton(onClick = { showClearDialog = false })
            },
            title = { Text("Clear meal history?") },
            text = { Text("This removes plans, grocery snapshots, and adherence logs for this account.") }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            confirmButton = {
                SettingsDialogConfirmButton(
                    label = "Sign out",
                    onClick = {
                    showLogoutDialog = false
                    logoutActionPending = true
                }
                )
            },
            dismissButton = {
                SettingsDialogDismissButton(onClick = { showLogoutDialog = false })
            },
            title = { Text("Logout?") },
            text = { Text("You will need to sign in again to access your data.") }
        )
    }

    if (showWeeklyResetDayDialog) {
        AlertDialog(
            onDismissRequest = { showWeeklyResetDayDialog = false },
            confirmButton = {
                SettingsDialogDismissButton(
                    label = "Keep current day",
                    onClick = { showWeeklyResetDayDialog = false }
                )
            },
            title = { Text("Weekly reset day") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose the day that starts your new planning week and reminder reset.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    (1..7).forEach { day ->
                        SettingsDialogOptionButton(
                            label = formatDayOfWeek(day),
                            onClick = {
                                updateNotificationPrefs { prefs ->
                                    prefs.copy(weeklyResetDayOfWeek = day)
                                }
                                showWeeklyResetDayDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        )
    }

    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            confirmButton = {
                SettingsDialogConfirmButton(
                    label = "Allow reminders",
                    onClick = {
                        showNotificationPermissionDialog = false
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            },
            dismissButton = {
                SettingsDialogDismissButton(onClick = { showNotificationPermissionDialog = false })
            },
            title = { Text("Allow local reminders?") },
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
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            }
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsDialogConfirmButton(
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = colorScheme.errorContainer,
                contentColor = colorScheme.onErrorContainer
            )
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsDialogDismissButton(
    onClick: () -> Unit,
    label: String = "Cancel"
) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsDialogOptionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    summary: String? = null,
    content: @Composable () -> Unit
) {
    SettingsSection(
        title = title,
        summary = summary,
        titleColor = MaterialTheme.colorScheme.secondary,
        containerColor = MaterialTheme.colorScheme.surface,
        content = content
    )
}

@Composable
fun SettingsSection(
    title: String,
    summary: String? = null,
    titleColor: Color,
    containerColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsStatePill(
                text = title,
                emphasized = true,
                accentColor = titleColor
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsDivider()
            }
            content()
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        color = if (destructive) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (destructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.20f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.70f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f),
                contentColor = color,
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (destructive) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                },
                contentColor = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (checked && enabled) {
            colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (checked && enabled) {
                colorScheme.primary.copy(alpha = 0.22f)
            } else {
                colorScheme.outlineVariant.copy(alpha = 0.70f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
                SettingsStatePill(
                    text = if (!enabled) {
                        "Unavailable"
                    } else if (checked) {
                        "On"
                    } else {
                        "Off"
                    },
                    emphasized = checked && enabled
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingsStatePill(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val resolvedAccentColor = accentColor ?: colorScheme.primary
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) {
            resolvedAccentColor.copy(alpha = 0.10f)
        } else {
            colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (emphasized) {
                resolvedAccentColor.copy(alpha = 0.16f)
            } else {
                colorScheme.outlineVariant.copy(alpha = 0.60f)
            }
        ),
        contentColor = if (emphasized) {
            resolvedAccentColor
        } else {
            colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )
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
