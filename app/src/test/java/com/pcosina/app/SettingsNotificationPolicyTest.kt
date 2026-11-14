package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNotificationPolicyTest {

    @Test
    fun settingsScreen_weeklyResetControls_areBoundToNotificationPreferences() {
        val source = read(
            resolve(
                "app",
                "src",
                "main",
                "java",
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "SettingsScreen.kt"
            )
        )
        assertTrue(
            "Settings should expose weekly reset day control.",
            source.contains("title = { Text(\"Weekly reset day\") }")
        )
        assertTrue(
            "Settings should expose weekly reset time control.",
            source.contains("label = \"Weekly reminder time\"")
        )
        assertTrue(
            "Weekly day selection should update NotificationPreferences.weeklyResetDayOfWeek.",
            source.contains("prefs.copy(weeklyResetDayOfWeek = day)")
        )
        assertTrue(
            "Weekly time selection should update NotificationPreferences weekly reset time fields.",
            source.contains("weeklyResetHour = h") && source.contains("weeklyResetMinute = m")
        )
        assertFalse(
            "Settings should not expose admin-only debug quick reminder triggers in the user app.",
            source.contains("NotificationScheduler.notifyWeeklyResetNow(context, userId)")
        )
    }

    @Test
    fun reminderSettings_hideDisplayOnlyReminderStatusRows() {
        val source = read(
            resolve(
                "app",
                "src",
                "main",
                "java",
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "SettingsScreen.kt"
            )
        )

        assertFalse(
            "Settings should not show a display-only phone notification status row in Reminder control.",
            source.contains("label = \"Phone notifications\"")
        )
        assertFalse(
            "Settings should not show a display-only delivery history row in Reminder control.",
            source.contains("label = \"Delivery history\"")
        )
        assertFalse(
            "Settings should not show scheduled reminders as a display-only status row.",
            source.contains("label = \"Scheduled reminders\"")
        )
        assertFalse(
            "Settings should not show delivery-log explanation copy in Reminder control.",
            source.contains("Delivery logs appear only after Android posts a notification.")
        )
        assertFalse(
            "Settings should not show Android delivery timing copy in Routine.",
            source.contains("Quiet hours and Android delivery timing can still affect when it appears.")
        )
        assertFalse(
            "Settings should not use display-only Switch rows with null handlers.",
            source.contains("onCheckedChange = null")
        )
        assertFalse(
            "Settings should not keep the redundant saved meal times footer.",
            source.contains("Saved meal times:")
        )
        assertFalse(
            "Settings should not surface raw permission copy as a prominent reminder status.",
            source.contains("Reminder status:")
        )
        assertTrue(
            "Reminder sub-tabs should explain the Control-tab gate in user-facing language.",
            source.contains("Enable Reminders in the Control tab to access Meals, Week, and Routine.")
        )
        assertTrue(
            "Settings budget display should show only the peso value, not a redundant weekly suffix.",
            source.contains("String.format(Locale.ENGLISH, \"₱%,d\", it)") &&
                !source.contains("₱%,d weekly")
        )
    }

    @Test
    fun notificationScreen_emptyStateExplainsDeliveredOnlyHistory() {
        val source = read(
            resolve(
                "app",
                "src",
                "main",
                "java",
                "com",
                "pcosina",
                "app",
                "ui",
                "screens",
                "NotificationScreen.kt"
            )
        )

        assertTrue(
            "Notification screen should include a phone notification readiness card.",
            source.contains("title = \"Phone notifications\"")
        )
        assertTrue(
            "Notification screen should include a next scheduled card.",
            source.contains("title = \"Next scheduled\"")
        )
        assertTrue(
            "Next scheduled copy should respect the phone-notification gate.",
            source.contains("Phone notifications need to be allowed before reminder work can run.")
        )
        assertTrue(
            "Empty notification history should explain that logs are delivered-only.",
            source.contains("The log starts only after Android posts a reminder or status notification.")
        )
        assertTrue(
            "Notification troubleshooting should include an actual local delivery test.",
            source.contains("Send test notification") &&
                source.contains("NotificationScheduler.notifyDebugTest(context, userId)")
        )
        assertFalse(
            "Notification screen should not imply an empty log means reminders are broken.",
            source.contains("No notification has been delivered yet on this device.")
        )
    }

    private fun resolve(vararg parts: String): Path {
        val first = Paths.get(parts.first(), *parts.drop(1).toTypedArray())
        if (Files.exists(first)) return first
        val fallbackParts = parts.drop(1).toTypedArray()
        val second = Paths.get(fallbackParts.first(), *fallbackParts.drop(1).toTypedArray())
        if (Files.exists(second)) return second
        error("Could not locate file: ${parts.joinToString("/")}")
    }

    private fun read(path: Path): String = String(Files.readAllBytes(path))
}
