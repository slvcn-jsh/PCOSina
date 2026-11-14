package com.pcosina.app.domain

import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.UserProfile
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ProgressNextPlanAdjustmentSuggestion(
    val tag: String,
    val title: String,
    val detail: String,
)

data class ProgressNextPlanAdjustmentSummary(
    val evidenceDays: Int,
    val suggestions: List<ProgressNextPlanAdjustmentSuggestion>,
)

class ProgressNextPlanAdjustmentUseCase {

    fun build(
        profile: UserProfile,
        logs: Map<String, DailyLog>,
        today: LocalDate,
    ): ProgressNextPlanAdjustmentSummary {
        val start = today.minusDays(27)
        val scopedLogs = logs.values
            .mapNotNull { log ->
                val date = runCatching { LocalDate.parse(log.date, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                    ?: return@mapNotNull null
                if (date.isBefore(start) || date.isAfter(today)) null else date to log
            }
            .sortedBy { it.first }
        val suggestions = linkedMapOf<String, ProgressNextPlanAdjustmentSuggestion>()

        buildWeightTrendSuggestion(profile, scopedLogs)?.let { suggestions[it.tag] = it }
        buildCravingSuggestion(scopedLogs)?.let { suggestions[it.tag] = it }
        buildSymptomSeveritySuggestion(scopedLogs)?.let { suggestions[it.tag] = it }
        buildEnergySuggestion(scopedLogs)?.let { suggestions[it.tag] = it }

        return ProgressNextPlanAdjustmentSummary(
            evidenceDays = scopedLogs.count { (_, log) -> hasProgressSignal(log) },
            suggestions = suggestions.values.toList(),
        )
    }

    private fun buildWeightTrendSuggestion(
        profile: UserProfile,
        scopedLogs: List<Pair<LocalDate, DailyLog>>,
    ): ProgressNextPlanAdjustmentSuggestion? {
        if (!profile.goal.contains("Weight Loss", ignoreCase = true)) return null
        val weightEntries = scopedLogs.mapNotNull { (date, log) ->
            log.weightKg?.let { date to it.toDouble() }
        }
        if (weightEntries.size < 2) return null
        val first = weightEntries.first()
        val last = weightEntries.last()
        val days = ChronoUnit.DAYS.between(first.first, last.first).coerceAtLeast(1L)
        val weeklyDeltaKg = ((last.second - first.second) / days.toDouble()) * 7.0
        if (weeklyDeltaKg <= 0.10) return null
        return ProgressNextPlanAdjustmentSuggestion(
            tag = PlannerProfilePreparationUseCase.GoalWeightTrendSupportTag,
            title = "Tighten next-plan nutrition fit",
            detail = "Weight check-ins are trending upward, so the next plan can prioritize tighter nutrition fit while keeping safety and budget rules intact.",
        )
    }

    private fun buildCravingSuggestion(
        scopedLogs: List<Pair<LocalDate, DailyLog>>,
    ): ProgressNextPlanAdjustmentSuggestion? {
        val values = scopedLogs.mapNotNull { it.second.cravingsLevel }
        if (values.size < 3 || values.average() < 4.0) return null
        return ProgressNextPlanAdjustmentSuggestion(
            tag = PlannerProfilePreparationUseCase.GoalCravingSupportTag,
            title = "Use steadier-carb support",
            detail = "Recent craving check-ins are high, so the next plan can apply symptom-aware nutrition nudges after you approve it.",
        )
    }

    private fun buildEnergySuggestion(
        scopedLogs: List<Pair<LocalDate, DailyLog>>,
    ): ProgressNextPlanAdjustmentSuggestion? {
        val values = scopedLogs.mapNotNull { it.second.energyLevel }
        if (values.size < 3 || values.average() > 2.5) return null
        return ProgressNextPlanAdjustmentSuggestion(
            tag = PlannerProfilePreparationUseCase.GoalEnergySupportTag,
            title = "Prioritize tighter meal balance",
            detail = "Recent energy check-ins are low, so the next plan can favor tighter nutrition balance without changing hard exclusions.",
        )
    }

    private fun buildSymptomSeveritySuggestion(
        scopedLogs: List<Pair<LocalDate, DailyLog>>,
    ): ProgressNextPlanAdjustmentSuggestion? {
        val values = scopedLogs.flatMap { it.second.symptomSeverityByTag.values }
        val days = scopedLogs.count { it.second.symptomSeverityByTag.isNotEmpty() }
        if (days < 3 || values.isEmpty() || values.average() < 4.0) return null
        return ProgressNextPlanAdjustmentSuggestion(
            tag = PlannerProfilePreparationUseCase.GoalCravingSupportTag,
            title = "Use symptom-aware support",
            detail = "Recent structured symptom severity is high, so the next plan can apply deterministic PCOS nutrition nudges after you approve it.",
        )
    }

    private fun hasProgressSignal(log: DailyLog): Boolean =
        log.weightKg != null ||
            log.energyLevel != null ||
            log.moodLevel != null ||
            log.cravingsLevel != null ||
            log.mealCheckIns.isNotEmpty() ||
            log.symptomSeverityByTag.isNotEmpty() ||
            !log.symptomsNote.isNullOrBlank()
}
