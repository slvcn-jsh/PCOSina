package com.pcosina.app

import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.UserProfile
import com.pcosina.app.domain.PlannerProfilePreparationUseCase
import com.pcosina.app.domain.ProgressNextPlanAdjustmentUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressNextPlanAdjustmentUseCaseTest {

    private val useCase = ProgressNextPlanAdjustmentUseCase()

    @Test
    fun build_suggestsOptInGoalAdjustmentsFromLocalProgressSignals() {
        val profile = UserProfile(goal = "Weight Loss", weeklyBudgetPhp = 1800)
        val today = LocalDate.of(2026, 5, 28)
        val logs = mapOf(
            "2026-05-01" to DailyLog(date = "2026-05-01", weightKg = 70.0f, energyLevel = 2, cravingsLevel = 4),
            "2026-05-08" to DailyLog(date = "2026-05-08", energyLevel = 2, cravingsLevel = 5),
            "2026-05-20" to DailyLog(date = "2026-05-20", energyLevel = 3, cravingsLevel = 4),
            "2026-05-28" to DailyLog(date = "2026-05-28", weightKg = 71.0f, energyLevel = 2, cravingsLevel = 4),
        )

        val summary = useCase.build(profile, logs, today)

        assertEquals(4, summary.evidenceDays)
        val tags = summary.suggestions.map { it.tag }
        assertTrue(tags.contains(PlannerProfilePreparationUseCase.GoalWeightTrendSupportTag))
        assertTrue(tags.contains(PlannerProfilePreparationUseCase.GoalCravingSupportTag))
        assertTrue(tags.contains(PlannerProfilePreparationUseCase.GoalEnergySupportTag))
    }

    @Test
    fun build_doesNotSuggestWhenSignalsAreSparseOrStable() {
        val profile = UserProfile(goal = "Weight Loss")
        val today = LocalDate.of(2026, 5, 28)
        val logs = mapOf(
            "2026-05-20" to DailyLog(date = "2026-05-20", weightKg = 70.0f, energyLevel = 4, cravingsLevel = 2),
            "2026-05-28" to DailyLog(date = "2026-05-28", weightKg = 69.8f, energyLevel = 4, cravingsLevel = 2),
        )

        val summary = useCase.build(profile, logs, today)

        assertTrue(summary.suggestions.isEmpty())
    }

    @Test
    fun build_usesStructuredSymptomSeverityAsOptInSupportSignal() {
        val profile = UserProfile(goal = "Symptom Management")
        val today = LocalDate.of(2026, 5, 28)
        val logs = mapOf(
            "2026-05-20" to DailyLog(date = "2026-05-20", symptomSeverityByTag = mapOf("Acne" to 4)),
            "2026-05-22" to DailyLog(date = "2026-05-22", symptomSeverityByTag = mapOf("Acne" to 5)),
            "2026-05-28" to DailyLog(date = "2026-05-28", symptomSeverityByTag = mapOf("Acne" to 4)),
        )

        val summary = useCase.build(profile, logs, today)

        assertEquals(3, summary.evidenceDays)
        assertTrue(summary.suggestions.map { it.tag }.contains(PlannerProfilePreparationUseCase.GoalCravingSupportTag))
    }
}
