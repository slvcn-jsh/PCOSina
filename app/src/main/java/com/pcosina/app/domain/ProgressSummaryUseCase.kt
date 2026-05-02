package com.pcosina.app.domain

import com.pcosina.app.data.model.DailyLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlannedDayCount(
    val dayLabel: String,
    val mealCount: Int,
)

enum class ProgressDayStatus {
    COMPLETE,
    PARTIAL,
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
)

class ProgressSummaryUseCase {

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
}
