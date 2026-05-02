package com.pcosina.app

import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.domain.PlannedDayCount
import com.pcosina.app.domain.ProgressDayStatus
import com.pcosina.app.domain.ProgressSummaryUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressSummaryUseCaseTest {

    private val useCase = ProgressSummaryUseCase()

    @Test
    fun buildWeekNodes_marksCompletedAndFutureDaysCorrectly() {
        val weekStart = LocalDate.of(2026, 5, 4)
        val today = LocalDate.of(2026, 5, 5)
        val plannedDays = listOf(
            PlannedDayCount("Mon", 3),
            PlannedDayCount("Tue", 3),
            PlannedDayCount("Wed", 3)
        )
        val logs = mapOf(
            "2026-05-04" to DailyLog(
                date = "2026-05-04",
                completedMealIds = listOf("Breakfast::r1", "Lunch::r2", "Dinner::r3")
            ),
            "2026-05-05" to DailyLog(
                date = "2026-05-05",
                completedMealIds = listOf("Breakfast::r1")
            )
        )

        val nodes = useCase.buildWeekNodes(plannedDays, logs, weekStart, today)

        assertEquals(7, nodes.size)
        assertEquals(ProgressDayStatus.COMPLETE, nodes[0].status)
        assertEquals("✓", nodes[0].valueText)
        assertEquals(ProgressDayStatus.PARTIAL, nodes[1].status)
        assertEquals("33%", nodes[1].valueText)
        assertEquals(ProgressDayStatus.FUTURE, nodes[2].status)
    }

    @Test
    fun buildWeeklyMealSummary_capsCompletedMealsToPlannedMeals() {
        val weekStart = LocalDate.of(2026, 5, 4)
        val today = LocalDate.of(2026, 5, 6)
        val plannedDays = listOf(
            PlannedDayCount("Mon", 2),
            PlannedDayCount("Tue", 2),
            PlannedDayCount("Wed", 2)
        )
        val logs = mapOf(
            "2026-05-04" to DailyLog(
                date = "2026-05-04",
                completedMealIds = listOf("Breakfast::r1", "Lunch::r2", "Dinner::r3")
            ),
            "2026-05-05" to DailyLog(
                date = "2026-05-05",
                completedMealIds = listOf("Breakfast::r1")
            )
        )

        val summary = useCase.buildWeeklyMealSummary(plannedDays, logs, weekStart, today)

        assertEquals(6, summary.plannedMeals)
        assertEquals(3, summary.completedMeals)
        assertEquals(50, summary.adherencePercent)
        assertEquals(ProgressDayStatus.COMPLETE, summary.chartPoints[0].status)
        assertEquals("100%", summary.chartPoints[0].valueText)
        assertEquals(ProgressDayStatus.PARTIAL, summary.chartPoints[1].status)
        assertTrue(summary.chartPoints[1].ratio > 0f)
    }
}
