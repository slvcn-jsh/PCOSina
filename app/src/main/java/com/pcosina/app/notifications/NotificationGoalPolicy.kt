package com.pcosina.app.notifications

import com.pcosina.app.ui.util.GoalOption
import com.pcosina.app.ui.util.parseGoalOptions
import java.util.concurrent.TimeUnit

internal enum class NotificationGoalTrack {
    WeightLoss,
    SymptomSupport,
    GeneralHealth
}

internal object NotificationGoalPolicy {
    fun fromGoal(goal: String): NotificationGoalTrack {
        val primary = parseGoalOptions(goal).firstOrNull()
        return when (primary) {
            GoalOption.WeightLoss -> NotificationGoalTrack.WeightLoss
            GoalOption.SymptomManagement -> NotificationGoalTrack.SymptomSupport
            GoalOption.GeneralHealth -> NotificationGoalTrack.GeneralHealth
            null -> NotificationGoalTrack.GeneralHealth
        }
    }

    fun mealReminderBody(track: NotificationGoalTrack): String = when (track) {
        NotificationGoalTrack.WeightLoss ->
            "Meal check-in time. Keep your routine consistent today."
        NotificationGoalTrack.SymptomSupport ->
            "Meal check-in time. Keep your routine balanced today."
        NotificationGoalTrack.GeneralHealth ->
            "Meal check-in time. Keep your healthy rhythm today."
    }

    fun inactivityBody(track: NotificationGoalTrack): String = when (track) {
        NotificationGoalTrack.WeightLoss ->
            "A short check-in helps keep your weekly routine on track."
        NotificationGoalTrack.SymptomSupport ->
            "A short check-in helps keep your routine steady."
        NotificationGoalTrack.GeneralHealth ->
            "A short check-in keeps your weekly insights up to date."
    }

    fun streakBody(track: NotificationGoalTrack): String = when (track) {
        NotificationGoalTrack.WeightLoss ->
            "Consistency adds up. Log today when you're ready."
        NotificationGoalTrack.SymptomSupport ->
            "Steady routines matter. Log today when you're ready."
        NotificationGoalTrack.GeneralHealth ->
            "Keep your rhythm going with one quick check-in today."
    }

    fun streakMinIntervalMs(track: NotificationGoalTrack): Long = when (track) {
        NotificationGoalTrack.WeightLoss -> TimeUnit.DAYS.toMillis(1)
        NotificationGoalTrack.SymptomSupport -> TimeUnit.DAYS.toMillis(2)
        NotificationGoalTrack.GeneralHealth -> TimeUnit.DAYS.toMillis(3)
    }
}
