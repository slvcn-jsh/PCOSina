package com.pcosina.app.notifications

import com.pcosina.app.ui.util.GoalOption
import com.pcosina.app.ui.util.parseGoalOptions
import java.util.concurrent.TimeUnit

internal enum class NotificationGoalTrack {
    WeightLoss,
    SymptomSupport,
    GeneralHealth,
    Combined
}

internal object NotificationGoalPolicy {
    fun fromGoal(goal: String): NotificationGoalTrack {
        val goals = parseGoalOptions(goal)
        return when {
            goals.size > 1 -> NotificationGoalTrack.Combined
            goals.contains(GoalOption.WeightLoss) -> NotificationGoalTrack.WeightLoss
            goals.contains(GoalOption.SymptomManagement) -> NotificationGoalTrack.SymptomSupport
            goals.contains(GoalOption.GeneralHealth) -> NotificationGoalTrack.GeneralHealth
            else -> NotificationGoalTrack.GeneralHealth
        }
    }

    fun mealReminderBody(track: NotificationGoalTrack): String = when (track) {
        NotificationGoalTrack.WeightLoss ->
            "Meal check-in time. Keep your routine consistent today."
        NotificationGoalTrack.SymptomSupport ->
            "Meal check-in time. Keep your routine balanced today."
        NotificationGoalTrack.GeneralHealth ->
            "Meal check-in time. Keep your healthy rhythm today."
        NotificationGoalTrack.Combined ->
            "Meal check-in time. Keep your selected goals balanced today."
    }

    fun inactivityBody(track: NotificationGoalTrack): String = when (track) {
        NotificationGoalTrack.WeightLoss ->
            "A short check-in helps keep your weekly routine on track."
        NotificationGoalTrack.SymptomSupport ->
            "A short check-in helps keep your routine steady."
        NotificationGoalTrack.GeneralHealth ->
            "A short check-in keeps your weekly insights up to date."
        NotificationGoalTrack.Combined ->
            "A short check-in helps tune your next plan across your selected goals."
    }

    fun streakBody(track: NotificationGoalTrack): String = when (track) {
        NotificationGoalTrack.WeightLoss ->
            "Consistency adds up. Log today when you're ready."
        NotificationGoalTrack.SymptomSupport ->
            "Steady routines matter. Log today when you're ready."
        NotificationGoalTrack.GeneralHealth ->
            "Keep your rhythm going with one quick check-in today."
        NotificationGoalTrack.Combined ->
            "Consistency helps PCOSina balance your selected goals. Log today when you're ready."
    }

    fun streakMinIntervalMs(track: NotificationGoalTrack): Long = when (track) {
        NotificationGoalTrack.WeightLoss -> TimeUnit.DAYS.toMillis(1)
        NotificationGoalTrack.SymptomSupport -> TimeUnit.DAYS.toMillis(2)
        NotificationGoalTrack.GeneralHealth -> TimeUnit.DAYS.toMillis(3)
        NotificationGoalTrack.Combined -> TimeUnit.DAYS.toMillis(1)
    }
}
