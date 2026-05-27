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
    fun reminderSettings_explainDeliveryGatesWithoutDisplayOnlySwitches() {
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
            "Settings should show phone notification readiness in user-facing copy.",
            source.contains("Phone notifications ready")
        )
        assertTrue(
            "Settings should explain that delivery history starts only after Android posts a notification.",
            source.contains("History starts only after Android posts a notification.")
        )
        assertTrue(
            "Settings should show scheduled reminders as a status row.",
            source.contains("label = \"Scheduled reminders\"")
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
