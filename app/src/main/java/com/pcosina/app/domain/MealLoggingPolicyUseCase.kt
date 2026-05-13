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
        skippedMealLabels: List<String> = emptyList(),
        plannedMealLabels: List<String> = emptyList(),
        now: LocalDate = LocalDate.now(),
        currentTime: LocalTime = LocalTime.now()
    ): MealLoggingDecision {
        val dateReason = loggingLockReason(date, now)
        if (dateReason.isNotBlank()) {
            return MealLoggingDecision(allowed = false, reason = dateReason)
        }
        val sequenceReason = sequenceLockReason(
            completedMealLabels = completedMealLabels,
            skippedMealLabels = skippedMealLabels,
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

    @Suppress("UNUSED_PARAMETER")
    fun timeWindowLockReason(mealLabel: String?, currentTime: LocalTime = LocalTime.now()): String {
        return ""
    }

    @Suppress("UNUSED_PARAMETER")
    fun sequenceLockReason(
        completedMealLabels: List<String>,
        skippedMealLabels: List<String> = emptyList(),
        mealLabel: String?,
        plannedMealLabels: List<String> = emptyList(),
        currentTime: LocalTime = LocalTime.now()
    ): String {
        val targetSlot = canonicalMealSlot(mealLabel) ?: return ""
        val plannedSlots = canonicalPlannedSlots(plannedMealLabels)
        if (targetSlot !in plannedSlots) return ""
        val requiredSlots = plannedSlots.takeWhile { it != targetSlot }
        if (requiredSlots.isEmpty()) return ""
        val handledSlots = (completedMealLabels + skippedMealLabels)
            .mapNotNull(::canonicalMealSlot)
            .toSet()
        val missingSlots = requiredSlots.filterNot { it in handledSlots }
        if (missingSlots.isEmpty()) return ""
        val missingText = missingSlots.joinToString(" and ") {
            displayMealSlot(it).lowercase(Locale.ENGLISH)
        }
        return "Log or skip $missingText before ${displayMealSlot(targetSlot).lowercase(Locale.ENGLISH)}."
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

    private fun canonicalPlannedSlots(plannedMealLabels: List<String>): List<String> {
        val explicitSlots = plannedMealLabels
            .mapNotNull(::canonicalMealSlot)
            .distinct()
        val slots = explicitSlots.ifEmpty {
            defaultPlannedMealLabels.mapNotNull(::canonicalMealSlot).distinct()
        }
        return slots.sortedBy(::mealSlotOrder)
    }

    private fun mealSlotOrder(slot: String): Int = when (slot) {
        "breakfast" -> 0
        "lunch" -> 1
        "dinner" -> 2
        else -> Int.MAX_VALUE
    }

    private fun displayMealSlot(slot: String): String = when (slot) {
        "breakfast" -> "Breakfast"
        "lunch" -> "Lunch"
        "dinner" -> "Dinner"
        else -> slot.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
        }
    }
}
