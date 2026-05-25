package com.pcosina.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.pcosina.app.R
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.PcosinaAvatar
import com.pcosina.app.ui.components.PcosinaAvatarOptions
import com.pcosina.app.ui.components.PcosinaDesignIcon
import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.notifications.NotificationScheduler
import com.pcosina.app.ui.util.primaryGoalLabel
import android.Manifest
import android.os.Build
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingsScreenFocus {
    Profile,
    Reminders,
    Account,
}

private enum class ReminderSettingsFocus {
    Control,
    Meals,
    Week,
    Routine,
}

private const val SettingsProfileMinAge = 18
private const val SettingsProfileMaxAge = 60

private data class SettingsTabIcon(
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
    val label: String,
)

private data class SettingsProfileStepState(
    val stepNumber: Int,
    val title: String,
    val detail: String,
    val complete: Boolean,
)

@Composable
fun SettingsScreen(
    userViewModel: UserViewModel,
    authViewModel: AuthViewModel,
    mealPlanViewModel: MealPlanViewModel,
    groceryViewModel: GroceryViewModel,
    progressViewModel: ProgressViewModel,
    userId: String,
    onBack: () -> Unit,
    onNavigateToProfileEdit: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profile by userViewModel.userProfile.collectAsState()
    val session by authViewModel.session.collectAsState()
    val notificationPrefs by userViewModel.notificationPreferences.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val userName = profile.displayName.ifBlank { "Your account" }
    var settingsFocusKey by rememberSaveable { mutableStateOf(SettingsScreenFocus.Profile.name) }
    val settingsFocus = remember(settingsFocusKey) {
        runCatching { SettingsScreenFocus.valueOf(settingsFocusKey) }
            .getOrDefault(SettingsScreenFocus.Profile)
    }
    var reminderFocusKey by rememberSaveable { mutableStateOf(ReminderSettingsFocus.Control.name) }
    val reminderFocus = remember(reminderFocusKey) {
        ReminderSettingsFocus.valueOf(reminderFocusKey)
    }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var clearActionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var clearActionError by rememberSaveable { mutableStateOf(false) }
    var logoutActionPending by rememberSaveable { mutableStateOf(false) }
    var showNotificationPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var showWeeklyResetDayDialog by rememberSaveable { mutableStateOf(false) }
    var showAvatarPickerDialog by rememberSaveable { mutableStateOf(false) }
    var avatarDraftName by rememberSaveable { mutableStateOf("") }
    var avatarDraftId by rememberSaveable { mutableStateOf("") }
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
    val profileStepStates = remember(profile) { buildSettingsProfileSteps(profile) }
    val planningPriorityLabel = profile.planningPriority.ifBlank { "Balanced" }
    val maxCookTimeLabel = "${profile.maxCookingTimeMinutes} min max"
    val settingsStatusLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> if (profile.isProfileCompleted) {
            "Your profile is ready to guide meals, groceries, and reminders."
        } else {
            "Finish your profile so planning feels more personal."
        }
        SettingsScreenFocus.Reminders -> notificationStatusSummary
        SettingsScreenFocus.Account -> "Account actions stay local to this device."
    }
    val settingsSyncLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Saved food rules: ${profile.dietaryRestrictions.size + profile.allergies.size}"
        SettingsScreenFocus.Reminders -> if (notificationPrefs.masterEnabled) {
            nextReminderSummary
        } else {
            scheduledWorkersSummary
        }
        SettingsScreenFocus.Account -> if (logoutActionPending) {
            "Sign-out is already in progress."
        } else {
            "You stay in control of what stays on this phone."
        }
    }
    val settingsPlanRangeLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Main goal: $mainGoalLabel"
        SettingsScreenFocus.Reminders -> "Phone permission: $permissionStateLabel"
        SettingsScreenFocus.Account -> "Clearing data only affects this phone unless you confirm it."
    }
    val settingsNextLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Next focus: review the details that affect budget, time, and food rules."
        SettingsScreenFocus.Reminders -> nextReminderSummary
        SettingsScreenFocus.Account -> "Next focus: confirm before clearing data or signing out."
    }
    val settingsSummaryBadge = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Profile setup"
        SettingsScreenFocus.Reminders -> "Reminder controls"
        SettingsScreenFocus.Account -> "Account actions"
    }
    val settingsSummaryTitle = when (settingsFocus) {
        SettingsScreenFocus.Profile -> "Profile details shape your week."
        SettingsScreenFocus.Reminders -> "Keep reminders helpful, not noisy."
        SettingsScreenFocus.Account -> "Account controls should stay clear and deliberate."
    }
    val settingsSummaryAccent = when (settingsFocus) {
        SettingsScreenFocus.Profile -> colorScheme.primary
        SettingsScreenFocus.Reminders -> colorScheme.secondary
        SettingsScreenFocus.Account -> colorScheme.error
    }
    val settingsSummaryHighlights = buildList {
        add(settingsPlanRangeLabel)
        add(settingsSyncLabel)
        add(settingsNextLabel)
    }
    LaunchedEffect(userId, notificationPrefs) {
        nextReminderSummaries = NotificationScheduler.nextScheduledTimes(notificationPrefs)
        if (userId.isBlank()) {
            scheduledWorkSummaries = emptyList()
        } else {
            scheduledWorkSummaries = NotificationScheduler.getScheduledWorkSummaries(context)
        }
    }
    LaunchedEffect(showAvatarPickerDialog) {
        if (showAvatarPickerDialog) {
            avatarDraftName = profile.displayName
            avatarDraftId = profile.avatarId
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
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        FigmaSettingsHero(
            title = "ACCOUNT AND SETTINGS",
            subtitle = "Personalize your PCOS journey. Manage your profile, set meal reminders, and customize your app experience.",
            onBack = onBack
        )
        FigmaSettingsProfileBand(
            avatarId = profile.avatarId,
            displayName = userName,
            email = session.currentUserEmail?.takeIf { it.isNotBlank() } ?: "Google account",
            onAvatarClick = { showAvatarPickerDialog = true },
        )
        settingsFeedbackBanner?.let { banner ->
            AppFeedbackBanner(
                data = banner,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 12.dp)
            )
        }
        FigmaSettingsTabs(
            selectedKey = settingsFocusKey,
            onSelect = { settingsFocusKey = it },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

        if (settingsFocus == SettingsScreenFocus.Profile) {
            SettingsSection(
                title = "Profile",
                summary = settingsStatusLabel
            ) {
                SettingsProfileProgressLine(
                    steps = profileStepStates,
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsDivider()
                FigmaSettingsFeatureToggle(
                    icon = Icons.Default.Warning,
                    label = "Symptom Management",
                    checked = profile.goal.equals("symptom_management", ignoreCase = true) || profile.symptoms.isNotEmpty()
                )
                FigmaSettingsFeatureToggle(
                    icon = Icons.Default.Notifications,
                    label = "Reminders",
                    checked = notificationPrefs.masterEnabled
                )
                FigmaSettingsFeatureToggle(
                    icon = Icons.Default.Warning,
                    label = "Alerts",
                    checked = notificationPrefs.grocerySyncEnabled || notificationPrefs.planReadyEnabled
                )
                SettingsDivider()
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
                containerColor = colorScheme.errorContainer
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

        if (settingsFocus == SettingsScreenFocus.Reminders) {
            FigmaSettingsReminderTabs(
                selectedKey = reminderFocusKey,
                onSelect = { reminderFocusKey = it }
            )

            if (reminderFocus == ReminderSettingsFocus.Control) {
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
            }

            if (reminderFocus == ReminderSettingsFocus.Meals) {
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
                    SettingsReminderTimeRow(
                        label = "Breakfast time",
                        time = formatTime(notificationPrefs.breakfastHour, notificationPrefs.breakfastMinute),
                    ) {
                        showTimePicker(notificationPrefs.breakfastHour, notificationPrefs.breakfastMinute) { h, m ->
                            updateNotificationPrefs { prefs -> prefs.copy(breakfastHour = h, breakfastMinute = m) }
                        }
                    }
                    SettingsReminderTimeRow(
                        label = "Lunch time",
                        time = formatTime(notificationPrefs.lunchHour, notificationPrefs.lunchMinute),
                    ) {
                        showTimePicker(notificationPrefs.lunchHour, notificationPrefs.lunchMinute) { h, m ->
                            updateNotificationPrefs { prefs -> prefs.copy(lunchHour = h, lunchMinute = m) }
                        }
                    }
                    SettingsReminderTimeRow(
                        label = "Dinner time",
                        time = formatTime(notificationPrefs.dinnerHour, notificationPrefs.dinnerMinute),
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
            }

            if (reminderFocus == ReminderSettingsFocus.Week) {
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
                    SettingsReminderTimeRow(
                        label = "Weekly reminder time",
                        time = formatTime(notificationPrefs.weeklyResetHour, notificationPrefs.weeklyResetMinute),
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
            }

            if (reminderFocus == ReminderSettingsFocus.Routine) {
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
                    SettingsReminderTimeRow(
                        label = "Quiet hours start",
                        time = formatTime(notificationPrefs.quietStartHour, notificationPrefs.quietStartMinute),
                    ) {
                        showTimePicker(notificationPrefs.quietStartHour, notificationPrefs.quietStartMinute) { h, m ->
                            updateNotificationPrefs { prefs -> prefs.copy(quietStartHour = h, quietStartMinute = m) }
                        }
                    }
                    SettingsReminderTimeRow(
                        label = "Quiet hours end",
                        time = formatTime(notificationPrefs.quietEndHour, notificationPrefs.quietEndMinute),
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

    if (showAvatarPickerDialog) {
        SettingsAvatarEditorDialog(
            name = avatarDraftName,
            selectedAvatarId = avatarDraftId.ifBlank { profile.avatarId },
            onNameChange = { avatarDraftName = it },
            onAvatarSelect = { avatarDraftId = it },
            onDismiss = { showAvatarPickerDialog = false },
            onSave = {
                val trimmedName = avatarDraftName.trim()
                if (trimmedName != profile.displayName) {
                    userViewModel.updateProfileName(trimmedName)
                }
                val selectedAvatar = avatarDraftId.ifBlank { profile.avatarId }
                if (selectedAvatar != profile.avatarId) {
                    userViewModel.updateAvatar(selectedAvatar)
                }
                showAvatarPickerDialog = false
                postSettingsFeedback(
                    tone = FeedbackBannerTone.Success,
                    message = "Name and avatar saved."
                )
            }
        )
    }
}

}

private fun buildSettingsProfileSteps(profile: UserProfile): List<SettingsProfileStepState> {
    val hasPersonalDetails = profile.isProfileCompleted || (
        profile.age in SettingsProfileMinAge..SettingsProfileMaxAge &&
            profile.weightKg in 35..180 &&
            profile.heightCm in 120..200 &&
            profile.activityLevel.isNotBlank()
        )
    val hasMedicalDetails = profile.isProfileCompleted ||
        profile.insulinResistanceLevel.isNotBlank()
    val hasPlanningRules = profile.isProfileCompleted ||
        profile.maxCookingTimeMinutes in 10..240

    return listOf(
        SettingsProfileStepState(
            stepNumber = 1,
            title = "Personal",
            detail = "Body metrics & activity",
            complete = hasPersonalDetails,
        ),
        SettingsProfileStepState(
            stepNumber = 2,
            title = "Medical",
            detail = "Symptoms",
            complete = hasMedicalDetails,
        ),
        SettingsProfileStepState(
            stepNumber = 3,
            title = "Preferences",
            detail = "Food rules & budget",
            complete = hasPlanningRules,
        ),
    )
}

@Composable
private fun SettingsProfileProgressLine(
    steps: List<SettingsProfileStepState>,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val completedCount = steps.count { it.complete }
    val activeStep = steps.firstOrNull { !it.complete }?.stepNumber ?: steps.lastOrNull()?.stepNumber ?: 1

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile setup progress",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colorScheme.primary.copy(alpha = 0.10f),
                contentColor = colorScheme.primary
            ) {
                Text(
                    text = "$completedCount of ${steps.size} ready",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            steps.forEach { step ->
                val highlighted = step.complete || step.stepNumber == activeStep
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (highlighted) {
                                colorScheme.primary
                            } else {
                                colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            steps.forEach { step ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (step.complete) colorScheme.primary else colorScheme.surfaceVariant,
                        contentColor = if (step.complete) Color.White else colorScheme.onSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (step.complete) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = "Step ${step.stepNumber}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = step.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FigmaSettingsHero(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(206.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFF7188), Color(0xFFFF8FA2))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 34.dp, vertical = 22.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.TopStart)
                .zIndex(1f)
                .clickable(onClick = onBack),
            shape = CircleShape,
            color = Color.White,
            contentColor = Color(0xFFFF7F93)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Image(
            painter = painterResource(id = R.drawable.pcosina_settings_gear),
            contentDescription = null,
            modifier = Modifier
                .size(146.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 58.dp, y = (-2).dp),
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 8.dp)
                .fillMaxWidth(0.82f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun FigmaSettingsProfileBand(
    avatarId: String,
    displayName: String,
    email: String,
    onAvatarClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(212.dp)
            .background(Color.White)
            .clipToBounds()
    ) {
        Image(
            painter = painterResource(id = R.drawable.pcosina_settings_burger_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(228.dp)
                .align(Alignment.TopCenter)
                .offset(y = 10.dp),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            alpha = 0.92f,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .size(88.dp)
                        .clickable(onClick = onAvatarClick),
                    shape = CircleShape,
                    color = Color(0xFFFFEDF1),
                    border = BorderStroke(2.dp, Color.White),
                    shadowElevation = 6.dp,
                ) {
                    PcosinaAvatar(
                        avatarId = avatarId,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(3.dp),
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.BottomEnd)
                        .clickable(onClick = onAvatarClick),
                    shape = CircleShape,
                    color = Color(0xFFFF7A92),
                    contentColor = Color.White,
                    border = BorderStroke(2.dp, Color.White),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit avatar",
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF26151A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFF6F5960),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FigmaSettingsTabs(
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val tabs = buildList {
        add(SettingsScreenFocus.Profile.name to SettingsTabIcon(icon = Icons.Default.Person, label = "Profile"))
        add(SettingsScreenFocus.Reminders.name to SettingsTabIcon(icon = Icons.Default.Notifications, label = "Reminders"))
        add(SettingsScreenFocus.Account.name to SettingsTabIcon(icon = Icons.Default.ManageAccounts, label = "Account"))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFCCD4))
            .padding(horizontal = 32.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (key, data) ->
            val selected = selectedKey == key
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clickable { onSelect(key) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) Color(0xFFFF7A92) else Color.White,
                contentColor = if (selected) Color.White else Color(0xFFFF7A92)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (data.iconRes != null) {
                        PcosinaDesignIcon(
                            resId = data.iconRes,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selected) Color.White else Color(0xFFFF7A92)
                        )
                    } else if (data.icon != null) {
                        Icon(
                            imageVector = data.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = data.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FigmaSettingsReminderTabs(
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val tabs = listOf(
        ReminderSettingsFocus.Control.name to "Control",
        ReminderSettingsFocus.Meals.name to "Meals",
        ReminderSettingsFocus.Week.name to "Week",
        ReminderSettingsFocus.Routine.name to "Routine",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (key, label) ->
            val selected = key == selectedKey
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clickable { onSelect(key) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) Color(0xFFFF7A92) else Color.White,
                border = BorderStroke(1.dp, Color(0xFFFF7A92).copy(alpha = if (selected) 0.0f else 0.22f)),
                contentColor = if (selected) Color.White else Color(0xFFFF7A92),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FigmaSettingsFeatureToggle(
    icon: ImageVector,
    label: String,
    checked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            color = Color(0xFFFFD6DE),
            contentColor = Color(0xFFFF6F86)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF3B3135)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFFF7A92),
                checkedTrackColor = Color(0xFFFFC4CE),
                uncheckedThumbColor = Color(0xFFFF7A92),
                uncheckedTrackColor = Color(0xFFD7D7D7)
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsAvatarEditorDialog(
    name: String,
    selectedAvatarId: String,
    onNameChange: (String) -> Unit,
    onAvatarSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding(),
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A92)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save Changes", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            SettingsDialogDismissButton(
                label = "Cancel",
                onClick = onDismiss,
            )
        },
        title = {
            Text(
                text = "Name and Avatar",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFB95C75),
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Edit your name",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF24191D),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { onNameChange(it.take(32)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose your avatar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFF24191D),
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PcosinaAvatarOptions.forEach { option ->
                            SettingsAvatarChoice(
                                avatarId = option.id,
                                selected = selectedAvatarId == option.id,
                                onClick = { onAvatarSelect(option.id) },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingsAvatarChoice(
    avatarId: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(78.dp),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) Color(0xFFFF7A92) else Color(0xFFFFC0CA),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            PcosinaAvatar(
                avatarId = avatarId,
                modifier = Modifier
                    .padding(3.dp)
                    .fillMaxSize(),
            )
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp),
                    shape = CircleShape,
                    color = Color(0xFFFF7A92),
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsReminderTimeRow(
    label: String,
    time: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF1F3),
        border = BorderStroke(1.dp, Color(0xFF6F4E59).copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFFFFCED8),
                contentColor = Color(0xFFFF7A92),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF2B1B20),
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFC06C7D),
                )
            }
            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = Color(0xFFFFCED8),
                contentColor = Color(0xFFFF7A92),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (containerColor == MaterialTheme.colorScheme.errorContainer) {
                Color(0xFFFFEEEE)
            } else {
                Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFE4D8DC))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = titleColor,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = Color(0xFFC06C7D)
                )
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
            color = Color(0xFFFFDFE6),
            border = BorderStroke(1.dp, Color(0xFFFFC2CE))
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color(0xFFFF7A92),
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
        color = if (destructive) Color(0xFFFFEEEE) else Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFF5D4A50).copy(alpha = 0.70f)
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
                color = if (destructive) Color(0xFFFF7A92) else Color(0xFFFFD6DE),
                contentColor = if (destructive) Color.White else color,
                modifier = Modifier.size(44.dp)
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
                    Color(0xFFFF7A92)
                } else {
                    Color(0xFFFFD6DE)
                },
                contentColor = if (destructive) {
                    Color.White
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
            Color(0xFFFFEEF2)
        } else {
            Color.White
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
