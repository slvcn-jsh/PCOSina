package com.pcosina.app.domain

import com.pcosina.app.data.model.UserProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class WeightGoalGuardrailSummary(
    val hasTarget: Boolean,
    val direction: String,
    val weeklyChangeKg: Double?,
    val status: String,
    val title: String,
    val detail: String,
)

class WeightGoalGuardrailUseCase {

    fun build(
        profile: UserProfile,
        today: LocalDate = LocalDate.now(),
    ): WeightGoalGuardrailSummary {
        val currentWeight = profile.weightKg.takeIf { it > 0 }
        val targetWeight = profile.targetWeightKg?.takeIf { it > 0 }
        if (currentWeight == null || targetWeight == null) {
            return WeightGoalGuardrailSummary(
                hasTarget = false,
                direction = "not_set",
                weeklyChangeKg = null,
                status = "not_set",
                title = "Weight target not set",
                detail = "Add an optional target weight in Profile to review pace and keep weight-loss planning as support, not a promise.",
            )
        }

        val deltaKg = targetWeight - currentWeight
        if (deltaKg == 0) {
            return WeightGoalGuardrailSummary(
                hasTarget = true,
                direction = "maintenance",
                weeklyChangeKg = 0.0,
                status = "maintenance",
                title = "Maintenance target saved",
                detail = "Your target matches your current profile weight, so future plans should stay focused on balanced weekly nutrition.",
            )
        }

        val direction = if (deltaKg < 0) "loss" else "gain"
        val targetDate = profile.targetDate
            ?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull() }
        if (targetDate == null) {
            return WeightGoalGuardrailSummary(
                hasTarget = true,
                direction = direction,
                weeklyChangeKg = null,
                status = "direction_only",
                title = "Target direction saved",
                detail = "Add a target date to estimate weekly pace before using this as planning context.",
            )
        }

        val days = ChronoUnit.DAYS.between(today, targetDate)
        if (days <= 0) {
            return WeightGoalGuardrailSummary(
                hasTarget = true,
                direction = direction,
                weeklyChangeKg = null,
                status = "review_pace",
                title = "Review target date",
                detail = "Use a future target date so the app can estimate a weekly pace from your saved target.",
            )
        }

        val weeklyChange = deltaKg.toDouble() / (days.toDouble() / 7.0)
        val pace = abs(weeklyChange)
        val paceText = String.format(java.util.Locale.ENGLISH, "%.2f", pace)
        return when {
            pace > 1.0 -> WeightGoalGuardrailSummary(
                hasTarget = true,
                direction = direction,
                weeklyChangeKg = weeklyChange,
                status = "review_pace",
                title = "Review target pace",
                detail = "This target implies about $paceText kg/week. Choose a slower pace before using it as next-plan context.",
            )
            pace < 0.1 -> WeightGoalGuardrailSummary(
                hasTarget = true,
                direction = direction,
                weeklyChangeKg = weeklyChange,
                status = "gentle_pace",
                title = "Gentle target pace",
                detail = "This target implies about $paceText kg/week, so planning can stay focused on steady nutrition support.",
            )
            else -> WeightGoalGuardrailSummary(
                hasTarget = true,
                direction = direction,
                weeklyChangeKg = weeklyChange,
                status = "within_guardrail",
                title = "Target pace is in app guardrails",
                detail = "This target implies about $paceText kg/week and remains a non-clinical planning support signal.",
            )
        }
    }
}
