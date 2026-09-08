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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.pcosina.app.data.model.PantryEntry
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.ui.AuthViewModel
import com.pcosina.app.ui.GroceryViewModel
import com.pcosina.app.ui.MealPlanViewModel
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.UserViewModel
import com.pcosina.app.ui.components.AppFeedbackBanner
import com.pcosina.app.ui.components.ArtworkAlignmentKeys
import com.pcosina.app.ui.components.ArtworkAlignmentTarget
import com.pcosina.app.ui.components.DevArtworkAlignmentHotspot
import com.pcosina.app.ui.components.DevEditableArtworkImage
import com.pcosina.app.ui.components.FeedbackBannerData
import com.pcosina.app.ui.components.FeedbackBannerTone
import com.pcosina.app.ui.components.PcosinaAvatar
import com.pcosina.app.ui.components.PcosinaAvatarOptions
import com.pcosina.app.ui.components.PcosinaDesignIcon
import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.notifications.NotificationScheduler
import com.pcosina.app.ui.theme.UiSpacingTokens
import com.pcosina.app.ui.util.parseGoalOptions
import com.pcosina.app.ui.util.primaryGoalLabel
import android.Manifest
import android.os.Build
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingsScreenFocus {
    Profile,
    Reminders,
    Account,
}

private fun settingsFocusFromRouteSection(section: String?): SettingsScreenFocus? =
    when (section?.lowercase(Locale.ENGLISH)) {
        "profile" -> SettingsScreenFocus.Profile
        "reminders" -> SettingsScreenFocus.Reminders
        "account" -> SettingsScreenFocus.Account
        else -> null
    }

private enum class ReminderSettingsFocus {
    Control,
    Meals,
    Week,
    Routine,
}

private const val ReminderControlLockedMessage = "Enable Reminders in the Control tab to access Meals, Week, and Routine."

private data class SettingsTabIcon(
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
    val label: String,
)

private data class SavedProfileDetails(
    val title: String,
    val emptyMessage: String,
    val items: List<String>,
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
    onOpenSupport: () -> Unit = {},
    initialSection: String? = null,
    modifier: Modifier = Modifier
) {
    val profile by userViewModel.userProfile.collectAsState()
    val pantryEntries by userViewModel.pantryEntries.collectAsState()
    val session by authViewModel.session.collectAsState()
    val notificationPrefs by userViewModel.notificationPreferences.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val userName = profile.displayName.ifBlank { "Your account" }
    val requestedSettingsFocus = remember(initialSection) {
        settingsFocusFromRouteSection(initialSection)
    }
    var settingsFocusKey by rememberSaveable {
        mutableStateOf((requestedSettingsFocus ?: SettingsScreenFocus.Profile).name)
    }
    val settingsFocus = remember(settingsFocusKey) {
        runCatching { SettingsScreenFocus.valueOf(settingsFocusKey) }
            .getOrDefault(SettingsScreenFocus.Profile)
    }
    LaunchedEffect(requestedSettingsFocus) {
        requestedSettingsFocus?.let { settingsFocusKey = it.name }
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
    var savedProfileDetails by remember { mutableStateOf<SavedProfileDetails?>(null) }
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
        !appNotificationsEnabled -> "Blocked in phone settings"
        runtimeNotificationPermissionGranted -> "Phone notifications ready"
        else -> "Phone permission needed"
    }
    val notificationStatusSummary = when {
        !notificationPrefs.masterEnabled -> "Reminders are paused on this phone."
        !appNotificationsEnabled -> "Turn on phone notifications before reminders can appear."
        runtimeNotificationPermissionGranted -> "Reminders are ready on this phone."
        else -> "Allow phone notifications before reminders can appear."
    }
    val scheduledWorkersSummary = if (scheduledWorkSummaries.isEmpty()) {
        "No scheduled reminder work yet."
    } else {
        "${scheduledWorkSummaries.size} reminder time(s) lined up"
    }
    val nextReminderSummary = when {
        !notificationPrefs.masterEnabled -> "Reminders are paused."
        nextReminderSummaries.isNotEmpty() -> "Next reminder: ${nextReminderSummaries.first()}"
        else -> "No reminder type is enabled."
    }
    val reminderOverviewLabel = if (notificationPrefs.masterEnabled) {
        "Reminders on"
    } else {
        "Reminders paused"
    }
    val mainGoalLabel = primaryGoalLabel(profile.goal)
    val budgetLabel = profile.weeklyBudgetPhp.takeIf { it > 0 }?.let {
        String.format(Locale.ENGLISH, "₱%,d", it)
    } ?: "Not set"
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
    val pantryDetailItems = remember(pantryEntries, profile.pantryItems) {
        if (pantryEntries.isNotEmpty()) {
            pantryEntries.map(::formatSavedPantryEntry)
        } else {
            profile.pantryItems.map { it.trim() }.filter { it.isNotBlank() }
        }
    }
    val settingsStatusLabel = when (settingsFocus) {
        SettingsScreenFocus.Profile -> if (profile.isProfileCompleted) {
            "Tap a saved list to view its details."
        } else {
            "Complete the required profile details."
        }
        SettingsScreenFocus.Reminders -> notificationStatusSummary
        SettingsScreenFocus.Account -> "Choose only the account action you need."
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
            .testTag("settings_content_scroll")
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        FigmaSettingsHero(
            title = "Settings",
            subtitle = "Profile, reminders, and account controls.",
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
                .padding(horizontal = 32.dp, vertical = UiSpacingTokens.SectionGap),
            verticalArrangement = Arrangement.spacedBy(UiSpacingTokens.SectionGap)
        ) {

        if (settingsFocus == SettingsScreenFocus.Profile) {
            SettingsSection(
                title = "Profile",
                summary = settingsStatusLabel
            ) {
                SettingsMainGoalItem(
                    icon = Icons.Default.Info,
                    goal = profile.goal,
                )
                SettingsDivider()
                SettingsItem(icon = Icons.Default.Info, label = "Weekly budget", value = budgetLabel)
                SettingsDivider()
                SettingsItem(icon = Icons.Default.History, label = "Max cooking time", value = maxCookTimeLabel)
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Default.Info,
                    label = "Food rules",
                    value = dietRulesLabel,
                    onClick = {
                        savedProfileDetails = SavedProfileDetails(
                            title = "Saved food rules",
                            emptyMessage = "No food rules are saved.",
                            items = profile.dietaryRestrictions
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Default.Warning,
                    label = "Allergies",
                    value = allergyLabel,
                    onClick = {
                        savedProfileDetails = SavedProfileDetails(
                            title = "Saved allergies",
                            emptyMessage = "No allergies are saved.",
                            items = profile.allergies
                        )
                    }
                )
                SettingsDivider()
                SettingsItem(
                    icon = Icons.Default.Info,
                    label = "Pantry items",
                    value = pantryLabel,
                    onClick = {
                        savedProfileDetails = SavedProfileDetails(
                            title = "Saved pantry items",
                            emptyMessage = "No pantry items are saved.",
                            items = pantryDetailItems
                        )
                    }
                )
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
            }
        }

        if (settingsFocus == SettingsScreenFocus.Account) {
            SettingsSection(
                title = "Account actions",
                summary = "Clear saved week data or sign out only when needed.",
                titleColor = colorScheme.error,
                containerColor = colorScheme.errorContainer
            ) {
                SettingsActionItem(
                    icon = Icons.Default.History,
                    label = "Clear saved week data",
                    description = "Removes saved plans, grocery snapshots, and progress logs for this account.",
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
                    description = "You can sign back in later. This does not delete this phone's saved account data.",
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
                remindersEnabled = notificationPrefs.masterEnabled,
                onSelect = { key ->
                    if (!notificationPrefs.masterEnabled && key != ReminderSettingsFocus.Control.name) {
                        reminderFocusKey = ReminderSettingsFocus.Control.name
                        notificationPermissionHint = ReminderControlLockedMessage
                    } else {
                        reminderFocusKey = key
                        if (notificationPermissionHint == ReminderControlLockedMessage) {
                            notificationPermissionHint = null
                        }
                    }
                }
            )

            if (reminderFocus == ReminderSettingsFocus.Control) {
            SettingsSection(
                title = "Reminder control",
                summary = "Choose whether PCOSina can nudge you on this phone and keep notifications respectful."
            ) {
                notificationPermissionHint?.takeIf { it == ReminderControlLockedMessage }?.let { hint ->
                    SettingsInlineNotice(
                        message = hint,
                        isError = false
                    )
                }
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
                if (!appNotificationsEnabled) {
                    SettingsActionItem(
                        icon = Icons.Default.Info,
                        label = "Open phone notification settings",
                        description = "Allow reminders for PCOSina in Android settings",
                        color = colorScheme.primary
                    ) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }
                notificationPermissionHint?.takeIf { it != ReminderControlLockedMessage }?.let { hint ->
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
                if (!notificationPrefs.masterEnabled || !notificationPrefs.mealRemindersEnabled) {
                    SettingsInlineNotice(
                        message = if (!notificationPrefs.masterEnabled) {
                            ReminderControlLockedMessage
                        } else {
                            "Meal reminder times are saved, but they will not fire until meal nudges are on."
                        },
                        isError = false
                    )
                }
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
                if (!notificationPrefs.masterEnabled || !notificationPrefs.weeklyResetEnabled) {
                    SettingsInlineNotice(
                        message = if (!notificationPrefs.masterEnabled) {
                            ReminderControlLockedMessage
                        } else {
                            "Weekly reminder timing is saved, but it will not fire until the weekly nudge is on."
                        },
                        isError = false
                    )
                }
            }
            }

            if (reminderFocus == ReminderSettingsFocus.Routine) {
            SettingsSection(
                title = "Check-in reminders",
                summary = "Keep check-ins supportive, lightweight, and easy to ignore when you need quiet."
            ) {
                SettingsToggleItem(
                    label = "Daily check-in reminder",
                    description = "A gentle reminder to log how your meals and day felt.",
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
            }
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    savedProfileDetails?.let { details ->
        SavedProfileDetailsDialog(
            details = details,
            onDismiss = { savedProfileDetails = null }
        )
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
            text = {
                Text(
                    "This removes saved plans, grocery snapshots, and local adherence logs for this account. " +
                        "Your profile settings stay saved."
                )
            }
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
            text = { Text("You will need to sign in again. This does not delete this phone's saved account data.") }
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
                    "PCOSina uses local reminders for meal check-ins and weekly planning. " +
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

@Composable
private fun FigmaSettingsHero(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    var activeArtworkEditorKey by remember { mutableStateOf<String?>(null) }
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
        DevEditableArtworkImage(
            alignmentKey = ArtworkAlignmentKeys.SettingsHeroGear,
            painter = painterResource(id = R.drawable.pcosina_settings_gear),
            contentDescription = null,
            modifier = Modifier
                .size(146.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 58.dp, y = (-2).dp),
            contentScale = ContentScale.Fit,
            externalEditing = activeArtworkEditorKey == ArtworkAlignmentKeys.SettingsHeroGear,
            onExternalEditingChange = { editing ->
                activeArtworkEditorKey = if (editing) ArtworkAlignmentKeys.SettingsHeroGear else null
            },
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
        DevArtworkAlignmentHotspot(
            targets = listOf(
                ArtworkAlignmentTarget(
                    key = ArtworkAlignmentKeys.SettingsHeroGear,
                    label = "Settings gear",
                ),
            ),
            onEditTarget = { activeArtworkEditorKey = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp)
                .zIndex(2f),
        )
    }
}

@Composable
private fun FigmaSettingsProfileBand(
    avatarId: String,
    displayName: String,
    email: String,
    onAvatarClick: () -> Unit,
) {
    var activeArtworkEditorKey by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(212.dp)
            .background(Color.White)
            .clipToBounds()
    ) {
        DevEditableArtworkImage(
            alignmentKey = ArtworkAlignmentKeys.SettingsProfileBackgroundBand,
            painter = painterResource(id = R.drawable.pcosina_settings_burger_background_band),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            contentScale = ContentScale.FillBounds,
            alignment = Alignment.Center,
            alpha = 0.92f,
            externalEditing = activeArtworkEditorKey == ArtworkAlignmentKeys.SettingsProfileBackgroundBand,
            onExternalEditingChange = { editing ->
                activeArtworkEditorKey = if (editing) ArtworkAlignmentKeys.SettingsProfileBackgroundBand else null
            },
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
        DevArtworkAlignmentHotspot(
            targets = listOf(
                ArtworkAlignmentTarget(
                    key = ArtworkAlignmentKeys.SettingsProfileBackgroundBand,
                    label = "Settings profile background",
                ),
            ),
            onEditTarget = { activeArtworkEditorKey = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(64.dp),
        )
    }
}

@Composable
private fun FigmaSettingsTabs(
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    val tabs = buildList {
        add(SettingsScreenFocus.Profile.name to SettingsTabIcon(icon = Icons.Default.Person, label = "Profile"))
        add(SettingsScreenFocus.Reminders.name to SettingsTabIcon(icon = Icons.Default.Notifications, label = "Alerts"))
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
    remindersEnabled: Boolean,
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
            val locked = !remindersEnabled && key != ReminderSettingsFocus.Control.name
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clickable { onSelect(key) },
                shape = RoundedCornerShape(999.dp),
                color = when {
                    selected -> Color(0xFFFF7A92)
                    locked -> Color(0xFFF5EEF1)
                    else -> Color.White
                },
                border = BorderStroke(
                    1.dp,
                    Color(0xFFFF7A92).copy(alpha = if (selected) 0.0f else if (locked) 0.12f else 0.22f)
                ),
                contentColor = when {
                    selected -> Color.White
                    locked -> Color(0xFF8A7178)
                    else -> Color(0xFFFF7A92)
                },
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
private fun FigmaSettingsStatusRow(
    icon: ImageVector,
    label: String,
    value: String,
    description: String,
    emphasized: Boolean,
    accentColor: Color = Color(0xFFFF7A92),
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
            color = Color(0xFF3B3135),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(10.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.widthIn(max = 188.dp)
        ) {
            SettingsStatePill(
                text = value,
                emphasized = emphasized,
                accentColor = accentColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6F5960),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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

private fun formatSavedPantryEntry(entry: PantryEntry): String {
    val structuredAmount = entry.amount?.let { amount ->
        val amountText = if (amount % 1.0 == 0.0) {
            amount.toInt().toString()
        } else {
            String.format(Locale.ENGLISH, "%.2f", amount).trimEnd('0').trimEnd('.')
        }
        "$amountText ${entry.unit.orEmpty()}".trim()
    }
    return listOfNotNull(
        entry.name.trim().takeIf { it.isNotBlank() },
        structuredAmount ?: entry.quantity?.trim()?.takeIf { it.isNotBlank() },
        entry.expiryDate?.trim()?.takeIf { it.isNotBlank() }?.let { "Use by $it" }
    ).joinToString(" | ")
}

@Composable
private fun SavedProfileDetailsDialog(
    details: SavedProfileDetails,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = details.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (details.items.isEmpty()) {
                    Text(
                        text = details.emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    details.items.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF2F5),
                            border = BorderStroke(1.dp, Color(0xFFFFD0D9))
                        ) {
                            Text(
                                text = item,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
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
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFFFF7A92),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "View $label",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFF7A92)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsMainGoalItem(icon: ImageVector, goal: String) {
    val goalLabels = parseGoalOptions(goal)
        .takeIf { it.isNotEmpty() }
        ?.sortedBy { it.ordinal }
        ?.map { it.label }
        ?: listOf(goal.trim().takeIf { it.isNotBlank() } ?: "Not set")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
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
            Text(
                text = "Main goal",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            goalLabels.forEach { label ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Color(0xFFFFDFE6),
                    border = BorderStroke(1.dp, Color(0xFFFFC2CE))
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFFF7A92),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
