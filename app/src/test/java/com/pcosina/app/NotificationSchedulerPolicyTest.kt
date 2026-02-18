package com.pcosina.app

import com.pcosina.app.data.model.NotificationPreferences
import com.pcosina.app.notifications.NotificationScheduler
import java.time.LocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationSchedulerPolicyTest {

    @Test
    fun nextScheduledTimes_returnsDisabledMessage_whenMasterOff() {
        val prefs = NotificationPreferences(masterEnabled = false)
        val lines = NotificationScheduler.nextScheduledTimes(prefs)
        assertTrue(lines.first().contains("disabled", ignoreCase = true))
    }

    @Test
    fun nextScheduledTimes_includesMealAndWeeklyLines_whenEnabled() {
        val prefs = NotificationPreferences(
            masterEnabled = true,
            mealRemindersEnabled = true,
            weeklyResetEnabled = true,
            streakNudgesEnabled = true
        )
        val lines = NotificationScheduler.nextScheduledTimes(prefs)
        assertTrue(lines.any { it.contains("Breakfast") })
        assertTrue(lines.any { it.contains("Lunch") })
        assertTrue(lines.any { it.contains("Dinner") })
        assertTrue(lines.any { it.contains("Weekly reset") })
        assertTrue(lines.any { it.contains("Engagement check") })
    }

    @Test
    fun nextScheduledTimes_respectsConfiguredWeeklyResetDayAndTime() {
        val prefs = NotificationPreferences(
            masterEnabled = true,
            mealRemindersEnabled = false,
            weeklyResetEnabled = true,
            weeklyResetDayOfWeek = 5,
            weeklyResetHour = 14,
            weeklyResetMinute = 45,
            streakNudgesEnabled = false,
            inactivityNudgesEnabled = false
        )
        val lines = NotificationScheduler.nextScheduledTimes(prefs)
        val weeklyLine = lines.firstOrNull { it.contains("Weekly reset") }.orEmpty()
        assertTrue(weeklyLine.contains("Fri", ignoreCase = true))
        assertTrue(weeklyLine.contains("2:45 PM", ignoreCase = true))
    }

    @Test
    fun isNowWithinQuietHours_handlesRangesCrossingMidnight() {
        val prefs = NotificationPreferences(
            quietHoursEnabled = true,
            quietStartHour = 22,
            quietStartMinute = 0,
            quietEndHour = 6,
            quietEndMinute = 30
        )
        assertTrue(
            NotificationScheduler.isNowWithinQuietHours(
                prefs,
                LocalDateTime.of(2026, 1, 8, 23, 15)
            )
        )
        assertTrue(
            NotificationScheduler.isNowWithinQuietHours(
                prefs,
                LocalDateTime.of(2026, 1, 9, 5, 45)
            )
        )
        assertFalse(
            NotificationScheduler.isNowWithinQuietHours(
                prefs,
                LocalDateTime.of(2026, 1, 9, 12, 0)
            )
        )
    }
}
