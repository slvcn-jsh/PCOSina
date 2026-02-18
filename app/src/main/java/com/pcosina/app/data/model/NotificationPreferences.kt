package com.pcosina.app.data.model

data class NotificationPreferences(
    val masterEnabled: Boolean = false,
    val mealRemindersEnabled: Boolean = true,
    val planReadyEnabled: Boolean = true,
    val grocerySyncEnabled: Boolean = true,
    val weeklyResetEnabled: Boolean = true,
    val weeklyResetDayOfWeek: Int = 1,
    val weeklyResetHour: Int = 9,
    val weeklyResetMinute: Int = 0,
    val streakNudgesEnabled: Boolean = false,
    val inactivityNudgesEnabled: Boolean = true,
    val breakfastHour: Int = 8,
    val breakfastMinute: Int = 0,
    val lunchHour: Int = 12,
    val lunchMinute: Int = 30,
    val dinnerHour: Int = 19,
    val dinnerMinute: Int = 0,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = 22,
    val quietStartMinute: Int = 0,
    val quietEndHour: Int = 6,
    val quietEndMinute: Int = 30
)

data class NotificationLogEntry(
    val type: String,
    val title: String,
    val body: String,
    val deliveredAt: Long = System.currentTimeMillis()
)
