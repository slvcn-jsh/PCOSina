package com.pcosina.app

import com.pcosina.app.domain.HealthMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthMetricsTest {
    @Test
    fun bmi_isComputedCorrectly() {
        val bmi = HealthMetrics.bmi(weightKg = 65, heightCm = 160)
        assertEquals(25.4, String.format("%.1f", bmi).toDouble(), 0.0)
    }

    @Test
    fun targetCalories_weightLoss_usesDeficit() {
        val breakdown = HealthMetrics.targetCaloriesPerDay(
            weightKg = 60,
            heightCm = 155,
            age = 25,
            activityLevel = "Lightly Active",
            goal = "Weight Loss"
        )
        assertEquals(-500, breakdown.goalAdjustment)
        // bmr= 10*60 + 6.25*155 - 5*25 - 161 = 1282.75 -> 1283
        assertEquals(1283, breakdown.bmr)
        assertEquals((1283 * 1.375).toInt(), breakdown.tdee)
        assertEquals(breakdown.tdee - 500, breakdown.target)
    }
}
