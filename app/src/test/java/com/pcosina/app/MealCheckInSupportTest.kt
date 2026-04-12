package com.pcosina.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.data.model.MealCheckIn
import com.pcosina.app.ui.util.goalMealCheckInInsight
import com.pcosina.app.ui.util.goalMealReasonCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealCheckInSupportTest {

    private val gson = Gson()
    private val logType = object : TypeToken<List<DailyLog>>() {}.type

    @Test
    fun dailyLog_roundTripsMealCheckInsThroughJson() {
        val original = listOf(
            DailyLog(
                date = "2026-04-12",
                mealCheckIns = listOf(
                    MealCheckIn(
                        mealKey = "Lunch::recipe-1",
                        recipeId = "recipe-1",
                        mealLabel = "Lunch",
                        energyLevel = 4,
                        fullnessLevel = 5,
                        cravingsLevel = 2,
                        satisfactionLevel = 4,
                        note = "Felt steady after this one."
                    )
                )
            )
        )

        val restored: List<DailyLog> = gson.fromJson(gson.toJson(original), logType)

        assertEquals(1, restored.first().mealCheckIns.size)
        assertEquals("Lunch", restored.first().mealCheckIns.first().mealLabel)
        assertEquals(5, restored.first().mealCheckIns.first().fullnessLevel)
    }

    @Test
    fun goalMealReasonCopy_translatesTechnicalReasonsIntoUserFacingCopy() {
        val reasons = goalMealReasonCopy(
            goal = "Symptom Management",
            reasons = listOf("Constraints met", "Pantry-aware", "Macro-aligned")
        )

        assertTrue(reasons.any { it.contains("preferences and limits", ignoreCase = true) })
        assertTrue(reasons.any { it.contains("ingredients", ignoreCase = true) })
        assertTrue(reasons.any { it.contains("steadier energy", ignoreCase = true) })
    }

    @Test
    fun goalMealCheckInInsight_returnsSupportiveSuggestionForLowFullness() {
        val insight = goalMealCheckInInsight(
            goal = "Weight Loss",
            checkIn = MealCheckIn(
                mealKey = "Dinner::recipe-2",
                recipeId = "recipe-2",
                mealLabel = "Dinner",
                fullnessLevel = 2,
                cravingsLevel = 4
            )
        )

        assertTrue(insight.contains("protein", ignoreCase = true) || insight.contains("fiber", ignoreCase = true))
    }
}
