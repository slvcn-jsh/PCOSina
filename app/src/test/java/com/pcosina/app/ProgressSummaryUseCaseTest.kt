package com.pcosina.app

import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.MealCheckIn
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

    @Test
    fun buildFourWeekTrendSummary_reportsImprovingLocalCheckIns() {
        val today = LocalDate.of(2026, 5, 28)
        val logs = mapOf(
            "2026-05-02" to DailyLog(date = "2026-05-02", energyLevel = 2, moodLevel = 3, cravingsLevel = 4),
            "2026-05-08" to DailyLog(date = "2026-05-08", energyLevel = 2, moodLevel = 3, cravingsLevel = 4),
            "2026-05-20" to DailyLog(date = "2026-05-20", energyLevel = 4, moodLevel = 4, cravingsLevel = 2),
            "2026-05-27" to DailyLog(
                date = "2026-05-27",
                energyLevel = 5,
                moodLevel = 5,
                cravingsLevel = 1,
                symptomSeverityByTag = mapOf("Acne" to 2)
            ),
        )

        val summary = useCase.buildFourWeekTrendSummary(logs, today)

        assertEquals(4, summary.checkInDays)
        assertEquals("improving", summary.energyDirection)
        assertEquals("Energy is trending up", summary.headline)
        assertTrue((summary.averageEnergy ?: 0.0) > 3.0)
        assertEquals(2.0, summary.averageSymptomSeverity ?: 0.0, 0.01)
        assertEquals(1, summary.symptomSeverityDays)
    }

    @Test
    fun buildFourWeekTrendSummary_ignoresOlderLogs() {
        val today = LocalDate.of(2026, 5, 28)
        val logs = mapOf(
            "2026-04-01" to DailyLog(date = "2026-04-01", energyLevel = 5),
            "2026-05-28" to DailyLog(date = "2026-05-28", energyLevel = 3)
        )

        val summary = useCase.buildFourWeekTrendSummary(logs, today)

        assertEquals(1, summary.checkInDays)
        assertEquals(3.0, summary.averageEnergy ?: 0.0, 0.01)
    }

    @Test
    fun buildCheckInHistory_returnsRecentDailyAndMealCheckInsNewestFirst() {
        val today = LocalDate.of(2026, 5, 28)
        val logs = mapOf(
            "2026-05-10" to DailyLog(
                date = "2026-05-10",
                energyLevel = 3,
                mealCheckIns = listOf(
                    MealCheckIn(
                        mealKey = "Lunch::r1",
                        recipeId = "r1",
                        mealLabel = "Lunch",
                        fullnessLevel = 4,
                        satisfactionLevel = 5,
                        timestamp = 10
                    )
                )
            ),
            "2026-05-27" to DailyLog(
                date = "2026-05-27",
                moodLevel = 4,
                cravingsLevel = 2,
                symptomsNote = "Felt steady"
            ),
            "2026-04-01" to DailyLog(date = "2026-04-01", energyLevel = 5),
            "2026-05-26" to DailyLog(date = "2026-05-26")
        )

        val history = useCase.buildCheckInHistory(logs, today)

        assertEquals(2, history.size)
        assertEquals(LocalDate.of(2026, 5, 27), history[0].date)
        assertEquals("Felt steady", history[0].symptomsNote)
        assertEquals("Lunch", history[1].mealCheckIns.single().mealLabel)
    }
}
