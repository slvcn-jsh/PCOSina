package com.pcosina.app.domain

import kotlin.math.roundToInt

data class CalorieTargetBreakdown(
    val bmr: Int,
    val activityMultiplier: Double,
    val tdee: Int,
    val goalAdjustment: Int,
    val target: Int
)

enum class GoalType {
    WEIGHT_LOSS,
    SYMPTOM_MANAGEMENT,
    GENERAL_HEALTH
}

object HealthMetrics {
    fun goalTypeFromText(goal: String): GoalType {
        val g = goal.lowercase()
        return when {
            g.contains("weight loss") -> GoalType.WEIGHT_LOSS
            g.contains("symptom") -> GoalType.SYMPTOM_MANAGEMENT
            else -> GoalType.GENERAL_HEALTH
        }
    }

    fun bmi(weightKg: Int, heightCm: Int): Double {
        if (weightKg <= 0 || heightCm <= 0) return 0.0
        val heightM = heightCm / 100.0
        return weightKg / (heightM * heightM)
    }

    fun bmiCategory(bmi: Double): String {
        return when {
            bmi <= 0.0 -> "—"
            bmi < 18.5 -> "Underweight"
            bmi < 25.0 -> "Normal"
            bmi < 30.0 -> "Overweight"
            else -> "Obese"
        }
    }

    fun activityMultiplier(level: String): Double {
        return when (level) {
            "Sedentary" -> 1.2
            "Lightly Active" -> 1.375
            "Moderately Active" -> 1.55
            "Very Active" -> 1.725
            else -> 1.375
        }
    }

    fun bmrMifflinStJeorFemale(weightKg: Int, heightCm: Int, age: Int): Int {
        val w = if (weightKg > 0) weightKg else 60
        val h = if (heightCm > 0) heightCm else 155
        val a = if (age > 0) age else 25
        val bmr = (10 * w) + (6.25 * h) - (5 * a) - 161
        return bmr.roundToInt()
    }

    fun targetCaloriesPerDay(
        weightKg: Int,
        heightCm: Int,
        age: Int,
        activityLevel: String,
        goal: String
    ): CalorieTargetBreakdown {
        val bmr = bmrMifflinStJeorFemale(weightKg, heightCm, age)
        val multiplier = activityMultiplier(activityLevel)
        val tdee = (bmr * multiplier).roundToInt()
        val goalType = goalTypeFromText(goal)
        val adjustment = when (goalType) {
            GoalType.WEIGHT_LOSS -> -500
            GoalType.SYMPTOM_MANAGEMENT -> 0
            GoalType.GENERAL_HEALTH -> 0
        }
        return CalorieTargetBreakdown(
            bmr = bmr,
            activityMultiplier = multiplier,
            tdee = tdee,
            goalAdjustment = adjustment,
            target = tdee + adjustment
        )
    }
}
