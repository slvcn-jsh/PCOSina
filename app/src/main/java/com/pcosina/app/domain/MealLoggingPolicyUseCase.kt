package com.pcosina.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

data class MealLoggingDecision(
    val allowed: Boolean,
    val reason: String = ""
)

class MealLoggingPolicyUseCase(
    private val defaultPlannedMealLabels: List<String> = listOf("Breakfast", "Lunch", "Dinner")
) {

    fun evaluate(
        date: LocalDate,
        mealLabel: String?,
        completedMealLabels: List<String>,
        plannedMealLabels: List<String> = emptyList(),
        now: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): MealLoggingDecision {
        val dateReason = loggingLockReason(date, now)
        if (dateReason.isNotBlank()) {
            return MealLoggingDecision(allowed = false, reason = dateReason)
        }
        val windowReason = timeWindowLockReason(mealLabel, currentTime)
        if (windowReason.isNotBlank()) {
            return MealLoggingDecision(allowed = false, reason = windowReason)
        }
        val sequenceReason = sequenceLockReason(
            completedMealLabels = completedMealLabels,
            mealLabel = mealLabel,
            plannedMealLabels = plannedMealLabels,
            currentTime = currentTime
        )
        if (sequenceReason.isNotBlank()) {
            return MealLoggingDecision(allowed = false, reason = sequenceReason)
        }
        return MealLoggingDecision(allowed = true)
    }

    fun isDateLoggable(date: LocalDate, now: LocalDate = LocalDate.now()): Boolean = date == now

    fun loggingLockReason(date: LocalDate, now: LocalDate = LocalDate.now()): String {
        return when {
            date.isAfter(now) -> "Future-day logging is locked. You can only log meals for today."
            date.isBefore(now) -> "Past-day logging is locked. Log meals on the same day to keep insights accurate."
            else -> ""
        }
    }

    fun timeWindowLockReason(mealLabel: String?, currentTime: LocalTime = LocalTime.now()): String {
        val slot = canonicalMealSlot(mealLabel) ?: return ""
        val window = mealWindows[slot] ?: return ""
        return when {
            currentTime.isBefore(window.start) ->
                "${displayMealSlot(slot)} logging opens at ${window.startLabel}."
            currentTime.isAfter(window.end) ->
                "${displayMealSlot(slot)} logging closed at ${window.endLabel}."
            else -> ""
        }
    }

    fun sequenceLockReason(
        completedMealLabels: List<String>,
        mealLabel: String?,
        plannedMealLabels: List<String> = emptyList(),
        currentTime: LocalTime = LocalTime.now()
    ): String {
        val targetSlot = canonicalMealSlot(mealLabel) ?: return ""
        val plannedSlots = plannedMealLabels
            .mapNotNull(::canonicalMealSlot)
            .distinct()
            .ifEmpty { defaultPlannedMealLabels.mapNotNull(::canonicalMealSlot).distinct() }
        if (targetSlot !in plannedSlots) return ""
        val requiredSlots = plannedSlots.takeWhile { it != targetSlot }
        if (requiredSlots.isEmpty()) return ""
        val completedSlots = completedMealLabels
            .mapNotNull(::canonicalMealSlot)
            .toSet()
        val missingSlots = requiredSlots.filterNot { it in completedSlots }
            .filter { slot ->
                mealWindows[slot]?.end?.let { end -> !currentTime.isAfter(end) } ?: true
            }
        if (missingSlots.isEmpty()) return ""
        val missingText = missingSlots.joinToString(" and ") {
            displayMealSlot(it).lowercase(Locale.ENGLISH)
        }
        return "Log $missingText before ${displayMealSlot(targetSlot).lowercase(Locale.ENGLISH)}."
    }

    private fun canonicalMealSlot(raw: String?): String? {
        val normalized = raw?.trim()?.lowercase(Locale.ENGLISH).orEmpty()
        return when {
            normalized.contains("breakfast") -> "breakfast"
            normalized.contains("lunch") -> "lunch"
            normalized.contains("dinner") -> "dinner"
            else -> null
        }
    }

    private fun displayMealSlot(slot: String): String = when (slot) {
        "breakfast" -> "Breakfast"
        "lunch" -> "Lunch"
        "dinner" -> "Dinner"
        else -> slot.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
        }
    }

    private data class MealWindow(
        val start: LocalTime,
        val end: LocalTime,
        val startLabel: String,
        val endLabel: String
    )

    private val mealWindows = mapOf(
        "breakfast" to MealWindow(LocalTime.of(5, 0), LocalTime.of(11, 30), "5:00 AM", "11:30 AM"),
        "lunch" to MealWindow(LocalTime.of(10, 30), LocalTime.of(15, 30), "10:30 AM", "3:30 PM"),
        "dinner" to MealWindow(LocalTime.of(16, 30), LocalTime.of(22, 30), "4:30 PM", "10:30 PM")
    )
}
