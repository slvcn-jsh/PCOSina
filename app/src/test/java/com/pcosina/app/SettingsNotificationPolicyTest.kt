package com.pcosina.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
        assertTrue(
            "Debug quick trigger should call scheduler weekly reset path.",
            source.contains("NotificationScheduler.notifyWeeklyResetNow(context, userId)")
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
