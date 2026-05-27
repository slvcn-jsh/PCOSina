package com.pcosina.app

import com.pcosina.app.ui.util.GoalOption
import com.pcosina.app.ui.util.goalMealCheckInPrompt
import com.pcosina.app.ui.util.goalPlanFocusCopy
import com.pcosina.app.ui.util.goalShoppingTips
import com.pcosina.app.ui.util.goalTextForApi
import com.pcosina.app.ui.util.hasGoalSelection
import com.pcosina.app.ui.util.hasKnownGoalSelection
import com.pcosina.app.ui.util.parseGoalOptions
import com.pcosina.app.ui.util.primaryGoalLabel
import com.pcosina.app.ui.util.primaryGoalShortLabel
import com.pcosina.app.ui.util.supportedGoalApiValues
import com.pcosina.app.ui.util.unknownGoalTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalSemanticsTest {
    @Test
    fun goalContract_apiValuesMatchExpectedSet() {
        val expected = setOf("Weight Loss", "Symptom Management", "General Health")
        assertEquals(expected, supportedGoalApiValues())
    }

    @Test
    fun parseGoalOptions_supportsKnownAliases() {
        val parsed = parseGoalOptions("weight loss, support pcos symptom management")
        assertTrue(parsed.contains(GoalOption.WeightLoss))
        assertTrue(parsed.contains(GoalOption.SymptomManagement))
        assertFalse(parsed.contains(GoalOption.GeneralHealth))
    }

    @Test
    fun parseGoalOptions_supportsApiAndLabelTokens() {
        val parsed = parseGoalOptions("General Health, Symptom Management")
        assertTrue(parsed.contains(GoalOption.GeneralHealth))
        assertTrue(parsed.contains(GoalOption.SymptomManagement))
    }

    @Test
    fun everyGoalOption_isParseableFromContractFields() {
        GoalOption.entries.forEach { option ->
            assertTrue(parseGoalOptions(option.apiValue).contains(option))
            assertTrue(parseGoalOptions(option.label).contains(option))
            assertTrue(option.aliases.isNotEmpty())
        }
    }

    @Test
    fun unknownGoalTokens_flagsUnsupportedValues() {
        val unknown = unknownGoalTokens("Weight Loss, Future Goal X")
        assertEquals(setOf("future goal x"), unknown)
    }

    @Test
    fun hasGoalSelection_acceptsUnknownForForwardCompat() {
        assertTrue(hasGoalSelection("future backend goal"))
    }

    @Test
    fun hasKnownGoalSelection_rejectsUnknownFreeText() {
        assertFalse(hasKnownGoalSelection("custom metabolism goal"))
    }

    @Test
    fun goalTextForApi_normalizesToContractValues() {
        val normalized = goalTextForApi("general health improvement, support pcos symptom management")
        assertEquals("Symptom Management, General Health", normalized)
    }

    @Test
    fun combinationGoalCopy_reflectsMultipleSelectedGoals() {
        val goal = "Weight Loss, Symptom Management"

        assertEquals("Weight Loss, Symptom Management", primaryGoalLabel(goal))
        assertEquals("2 goals", primaryGoalShortLabel(goal))
        assertTrue(goalPlanFocusCopy(goal).contains("calorie fit", ignoreCase = true))
        assertTrue(goalPlanFocusCopy(goal).contains("steadier-carb", ignoreCase = true))
        assertTrue(goalMealCheckInPrompt(goal).contains("full", ignoreCase = true))
        assertTrue(goalMealCheckInPrompt(goal).contains("cravings", ignoreCase = true))
    }

    @Test
    fun allThreeGoalShoppingTips_keepAllSelectedPrioritiesVisible() {
        val tips = goalShoppingTips("Weight Loss, Symptom Management, General Health")

        assertTrue(tips.any { it.contains("protein", ignoreCase = true) })
        assertTrue(tips.any { it.contains("high-fiber", ignoreCase = true) })
        assertTrue(tips.any { it.contains("produce", ignoreCase = true) })
        assertTrue(tips.any { it.contains("all selected goals", ignoreCase = true) })
    }
}
