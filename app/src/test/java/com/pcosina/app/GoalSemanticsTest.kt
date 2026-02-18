package com.pcosina.app

import com.pcosina.app.ui.util.GoalOption
import com.pcosina.app.ui.util.goalTextForApi
import com.pcosina.app.ui.util.hasGoalSelection
import com.pcosina.app.ui.util.hasKnownGoalSelection
import com.pcosina.app.ui.util.parseGoalOptions
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
}
