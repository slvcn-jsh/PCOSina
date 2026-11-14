package com.pcosina.app.domain

import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.MealCheckIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlannedDayCount(
    val dayLabel: String,
    val mealCount: Int,
)

data class PlannedMealSlot(
    val dayLabel: String,
    val mealLabel: String,
    val recipeId: String,
)

enum class ProgressDayStatus {
    COMPLETE,
    PARTIAL,
    PENDING,
    FUTURE,
}

enum class ProgressMealSlotStatus {
    COMPLETED,
    SKIPPED,
    MISSED,
    PENDING,
    FUTURE,
}

data class ProgressWeekNodeSummary(
    val label: String,
    val valueText: String,
    val status: ProgressDayStatus,
)

data class ProgressAdherencePoint(
    val label: String,
    val ratio: Float,
    val valueText: String,
    val status: ProgressDayStatus,
)

data class WeeklyMealSummaryResult(
    val plannedMeals: Int,
    val completedMeals: Int,
    val adherencePercent: Int,
    val chartPoints: List<ProgressAdherencePoint>,
    val duePlannedMeals: Int = plannedMeals,
    val skippedMeals: Int = 0,
    val missedMeals: Int = 0,
    val pendingMeals: Int = 0,
    val weeklyCompletionPercent: Int = adherencePercent,
    val mealSlots: List<ProgressMealSlotSummary> = emptyList(),
)

data class ProgressMealSlotSummary(
    val dayLabel: String,
    val mealLabel: String,
    val recipeId: String,
    val status: ProgressMealSlotStatus,
)

data class ProgressTrendSummary(
    val checkInDays: Int,
    val mealCheckIns: Int,
    val averageEnergy: Double?,
    val averageMood: Double?,
    val averageCravings: Double?,
    val averageSymptomSeverity: Double?,
    val symptomSeverityDays: Int,
    val energyDirection: String,
    val headline: String,
    val detail: String,
)

data class ProgressCheckInHistoryDay(
    val date: LocalDate,
    val label: String,
    val energyLevel: Int?,
    val moodLevel: Int?,
    val cravingsLevel: Int?,
    val symptomsNote: String?,
    val mealCheckIns: List<MealCheckIn>,
)

class ProgressSummaryUseCase {

    fun buildWeeklyMealSlotSummary(
        plannedSlots: List<PlannedMealSlot>,
        logs: Map<String, DailyLog>,
        weekStart: LocalDate,
        today: LocalDate,
    ): WeeklyMealSummaryResult {
        val formatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
        val slotsByDayToken = plannedSlots.groupBy { normalizeDayToken(it.dayLabel) }
        val slotSummaries = mutableListOf<ProgressMealSlotSummary>()
        val chartPoints = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dayToken = normalizeDayToken(date.format(formatter))
            val daySlots = slotsByDayToken[dayToken].orEmpty()
            val log = logs[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]
            val daySummaries = daySlots.map { slot ->
                ProgressMealSlotSummary(
                    dayLabel = date.format(formatter).uppercase(Locale.ENGLISH).take(3),
                    mealLabel = slot.mealLabel,
                    recipeId = slot.recipeId,
                    status = mealSlotStatus(slot, daySlots, log, date, today),
                )
            }
            slotSummaries += daySummaries
            val completed = daySummaries.count { it.status == ProgressMealSlotStatus.COMPLETED }
            val handled = daySummaries.count {
                it.status == ProgressMealSlotStatus.COMPLETED || it.status == ProgressMealSlotStatus.SKIPPED
            }
            val ratio = when {
                date.isAfter(today) || daySlots.isEmpty() -> 0f
                else -> (completed.toFloat() / daySlots.size.toFloat()).coerceIn(0f, 1f)
            }
            ProgressAdherencePoint(
                label = date.format(formatter).uppercase(Locale.ENGLISH).take(3),
                ratio = ratio,
                valueText = when {
                    date.isAfter(today) || daySlots.isEmpty() -> "--"
                    completed == daySlots.size -> "100%"
                    handled > 0 -> "${(ratio * 100f).toInt()}%"
                    else -> "0%"
                },
                status = when {
                    date.isAfter(today) -> ProgressDayStatus.FUTURE
                    daySlots.isEmpty() -> ProgressDayStatus.PENDING
                    completed == daySlots.size -> ProgressDayStatus.COMPLETE
                    handled > 0 -> ProgressDayStatus.PARTIAL
                    else -> ProgressDayStatus.PENDING
                }
            )
        }
        val plannedMeals = slotSummaries.size
        val dueSlots = slotSummaries.filter { it.status != ProgressMealSlotStatus.FUTURE }
        val completedMeals = slotSummaries.count { it.status == ProgressMealSlotStatus.COMPLETED }
        val duePlannedMeals = dueSlots.size
        val skippedMeals = dueSlots.count { it.status == ProgressMealSlotStatus.SKIPPED }
        val missedMeals = dueSlots.count { it.status == ProgressMealSlotStatus.MISSED }
        val pendingMeals = dueSlots.count { it.status == ProgressMealSlotStatus.PENDING }
        val adherencePercent = if (duePlannedMeals > 0) {
            ((completedMeals.toFloat() / duePlannedMeals.toFloat()) * 100f).toInt()
        } else {
            0
        }
        val weeklyCompletionPercent = if (plannedMeals > 0) {
            ((completedMeals.toFloat() / plannedMeals.toFloat()) * 100f).toInt()
        } else {
            0
        }
        return WeeklyMealSummaryResult(
            plannedMeals = plannedMeals,
            completedMeals = completedMeals,
            adherencePercent = adherencePercent,
            chartPoints = chartPoints,
            duePlannedMeals = duePlannedMeals,
            skippedMeals = skippedMeals,
            missedMeals = missedMeals,
            pendingMeals = pendingMeals,
            weeklyCompletionPercent = weeklyCompletionPercent,
            mealSlots = slotSummaries,
        )
    }

    fun buildWeekNodes(
        plannedDays: List<PlannedDayCount>,
        logs: Map<String, DailyLog>,
        weekStart: LocalDate,
        today: LocalDate,
    ): List<ProgressWeekNodeSummary> {
        val dayCounts = plannedDays.associate { it.dayLabel.lowercase(Locale.ENGLISH) to it.mealCount }
        val formatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
        return (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dateKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val label = date.format(formatter).uppercase(Locale.ENGLISH).take(3)
            val planned = dayCounts[date.format(formatter).lowercase(Locale.ENGLISH)] ?: 0
            val completed = logs[dateKey]?.completedMealIds?.size ?: 0
            when {
                date.isAfter(today) -> ProgressWeekNodeSummary(label, "", ProgressDayStatus.FUTURE)
                planned == 0 -> ProgressWeekNodeSummary(label, "--", ProgressDayStatus.PENDING)
                completed >= planned && planned > 0 -> ProgressWeekNodeSummary(label, "✓", ProgressDayStatus.COMPLETE)
                completed > 0 -> ProgressWeekNodeSummary(
                    label,
                    "${((completed.toFloat() / planned.toFloat()) * 100f).toInt()}%",
                    ProgressDayStatus.PARTIAL
                )
                else -> ProgressWeekNodeSummary(label, "0%", ProgressDayStatus.PENDING)
            }
        }
    }

    private fun mealSlotStatus(
        slot: PlannedMealSlot,
        daySlots: List<PlannedMealSlot>,
        log: DailyLog?,
        date: LocalDate,
        today: LocalDate,
    ): ProgressMealSlotStatus {
        if (date.isAfter(today)) return ProgressMealSlotStatus.FUTURE
        val completed = log?.completedMealIds.orEmpty()
            .any { storedKey -> storedKeyMatchesSlot(storedKey, slot, daySlots) }
        if (completed) return ProgressMealSlotStatus.COMPLETED
        val skipped = log?.skippedMealIds.orEmpty()
            .any { storedKey -> storedKeyMatchesSlot(storedKey, slot, daySlots) }
        if (skipped) return ProgressMealSlotStatus.SKIPPED
        return if (date.isBefore(today)) ProgressMealSlotStatus.MISSED else ProgressMealSlotStatus.PENDING
    }

    private fun storedKeyMatchesSlot(
        storedKey: String,
        slot: PlannedMealSlot,
        daySlots: List<PlannedMealSlot>,
    ): Boolean {
        val normalizedStored = storedKey.trim()
        if (normalizedStored.isBlank()) return false
        val storedRecipeId = extractRecipeId(normalizedStored)
        if (storedRecipeId != slot.recipeId) return false
        val storedMealLabel = extractMealLabel(normalizedStored)
        if (storedMealLabel == null) {
            return daySlots.count { it.recipeId == slot.recipeId } == 1
        }
        return normalizeMealLabel(storedMealLabel) == normalizeMealLabel(slot.mealLabel)
    }

    private fun normalizeDayToken(label: String): String =
        label.trim().take(3).lowercase(Locale.ENGLISH)

    private fun normalizeMealLabel(label: String): String =
        label.trim().lowercase(Locale.ENGLISH)

    private fun extractMealLabel(mealKey: String): String? =
        if (mealKey.contains("::")) mealKey.substringBefore("::").takeIf { it.isNotBlank() } else null

    private fun extractRecipeId(mealKey: String): String =
        if (mealKey.contains("::")) mealKey.substringAfter("::") else mealKey

    fun buildWeeklyMealSummary(
        plannedDays: List<PlannedDayCount>,
        logs: Map<String, DailyLog>,
        weekStart: LocalDate,
        today: LocalDate,
    ): WeeklyMealSummaryResult {
        val formatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
        val plannedByDay = plannedDays.associate { it.dayLabel.lowercase(Locale.ENGLISH) to it.mealCount }
        val chartPoints = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            val dayLabel = date.format(formatter).lowercase(Locale.ENGLISH)
            val planned = plannedByDay[dayLabel] ?: 0
            val completed = (logs[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]?.completedMealIds?.size ?: 0)
                .coerceAtMost(planned)
            val ratio = when {
                date.isAfter(today) || planned == 0 -> 0f
                else -> (completed.toFloat() / planned.toFloat()).coerceIn(0f, 1f)
            }
            ProgressAdherencePoint(
                label = date.format(formatter).uppercase(Locale.ENGLISH).take(3),
                ratio = ratio,
                valueText = if (date.isAfter(today) || planned == 0) "--" else "${(ratio * 100f).toInt()}%",
                status = when {
                    date.isAfter(today) -> ProgressDayStatus.FUTURE
                    ratio >= 1f -> ProgressDayStatus.COMPLETE
                    ratio > 0f -> ProgressDayStatus.PARTIAL
                    else -> ProgressDayStatus.PENDING
                }
            )
        }
        val completedMeals = chartPoints.indices.sumOf { index ->
            val date = weekStart.plusDays(index.toLong())
            val dayLabel = date.format(formatter).lowercase(Locale.ENGLISH)
            val planned = plannedByDay[dayLabel] ?: 0
            (logs[date.format(DateTimeFormatter.ISO_LOCAL_DATE)]?.completedMealIds?.size ?: 0).coerceAtMost(planned)
        }
        val plannedMeals = chartPoints.indices.sumOf { index ->
            val date = weekStart.plusDays(index.toLong())
            val dayLabel = date.format(formatter).lowercase(Locale.ENGLISH)
            plannedByDay[dayLabel] ?: 0
        }
        val adherencePercent = if (plannedMeals > 0) {
            ((completedMeals.toFloat() / plannedMeals.toFloat()) * 100f).toInt()
        } else {
            0
        }
        return WeeklyMealSummaryResult(
            plannedMeals = plannedMeals,
            completedMeals = completedMeals,
            adherencePercent = adherencePercent,
            chartPoints = chartPoints
        )
    }

    fun buildFourWeekTrendSummary(
        logs: Map<String, DailyLog>,
        today: LocalDate,
    ): ProgressTrendSummary {
        val start = today.minusDays(27)
        val scopedLogs = logs.values
            .mapNotNull { log ->
                val date = runCatching { LocalDate.parse(log.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                    ?: return@mapNotNull null
                if (date.isBefore(start) || date.isAfter(today)) null else date to log
            }
            .sortedBy { it.first }
        val energyValues = scopedLogs.mapNotNull { it.second.energyLevel }
        val moodValues = scopedLogs.mapNotNull { it.second.moodLevel }
        val cravingValues = scopedLogs.mapNotNull { it.second.cravingsLevel }
        val symptomSeverityValues = scopedLogs.flatMap { it.second.symptomSeverityByTag.values }
        val mealCheckIns = scopedLogs.sumOf { it.second.mealCheckIns.size }
        val midpoint = today.minusDays(13)
        val firstHalfAverage = scopedLogs
            .filter { it.first.isBefore(midpoint) }
            .mapNotNull { it.second.energyLevel }
            .takeIf { it.isNotEmpty() }
            ?.average()
        val secondHalfAverage = scopedLogs
            .filter { !it.first.isBefore(midpoint) }
            .mapNotNull { it.second.energyLevel }
            .takeIf { it.isNotEmpty() }
            ?.average()
        val direction = when {
            firstHalfAverage == null || secondHalfAverage == null -> "not_enough_data"
            secondHalfAverage - firstHalfAverage >= 0.4 -> "improving"
            firstHalfAverage - secondHalfAverage >= 0.4 -> "declining"
            else -> "steady"
        }
        val averageEnergy = energyValues.takeIf { it.isNotEmpty() }?.average()
        val headline = when {
            scopedLogs.isEmpty() -> "No local check-in history yet"
            direction == "improving" -> "Energy is trending up"
            direction == "declining" -> "Energy may need support"
            direction == "steady" -> "Energy looks steady"
            else -> "Check-in history is starting"
        }
        val detail = when {
            scopedLogs.isEmpty() -> "Save daily or meal check-ins to build a private trend view on this device."
            direction == "improving" -> "Your latest check-ins are higher than the earlier part of this 4-week window."
            direction == "declining" -> "Your latest check-ins are lower than the earlier part of this 4-week window."
            direction == "steady" -> "Your recent check-ins are close to your earlier 4-week average."
            else -> "A few more check-ins will make the trend clearer."
        }
        return ProgressTrendSummary(
            checkInDays = scopedLogs.count { (_, log) ->
                log.energyLevel != null || log.moodLevel != null || log.cravingsLevel != null || log.mealCheckIns.isNotEmpty()
            },
            mealCheckIns = mealCheckIns,
            averageEnergy = averageEnergy,
            averageMood = moodValues.takeIf { it.isNotEmpty() }?.average(),
            averageCravings = cravingValues.takeIf { it.isNotEmpty() }?.average(),
            averageSymptomSeverity = symptomSeverityValues.takeIf { it.isNotEmpty() }?.average(),
            symptomSeverityDays = scopedLogs.count { it.second.symptomSeverityByTag.isNotEmpty() },
            energyDirection = direction,
            headline = headline,
            detail = detail,
        )
    }

    fun buildCheckInHistory(
        logs: Map<String, DailyLog>,
        today: LocalDate,
        daysBack: Long = 28,
    ): List<ProgressCheckInHistoryDay> {
        val start = today.minusDays((daysBack - 1).coerceAtLeast(0))
        val labelFormatter = DateTimeFormatter.ofPattern("MMM d, EEE", Locale.ENGLISH)
        return logs.values
            .mapNotNull { log ->
                val date = runCatching { LocalDate.parse(log.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                    ?: return@mapNotNull null
                if (date.isBefore(start) || date.isAfter(today)) return@mapNotNull null
                val hasDailyReflection = log.energyLevel != null ||
                    log.moodLevel != null ||
                    log.cravingsLevel != null ||
                    log.symptomSeverityByTag.isNotEmpty() ||
                    !log.symptomsNote.isNullOrBlank()
                val mealCheckIns = log.mealCheckIns.sortedByDescending { it.timestamp }
                if (!hasDailyReflection && mealCheckIns.isEmpty()) return@mapNotNull null
                ProgressCheckInHistoryDay(
                    date = date,
                    label = date.format(labelFormatter),
                    energyLevel = log.energyLevel,
                    moodLevel = log.moodLevel,
                    cravingsLevel = log.cravingsLevel,
                    symptomsNote = log.symptomsNote,
                    mealCheckIns = mealCheckIns,
                )
            }
            .sortedByDescending { it.date }
    }
}
