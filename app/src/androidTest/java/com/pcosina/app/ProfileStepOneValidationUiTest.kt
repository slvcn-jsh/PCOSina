package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pcosina.app.domain.UnitConverter
import com.pcosina.app.ui.screens.StepOneIdentity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileStepOneValidationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stepOneInlineValidation_showsFormatAndRangeHints_forAgeWeightHeight() {
        composeRule.setContent {
            MaterialTheme {
                var age by remember { mutableStateOf("") }
                var weight by remember { mutableStateOf("") }
                var heightCm by remember { mutableStateOf("") }

                StepOneIdentity(
                    name = "",
                    onName = {},
                    age = age,
                    onAge = { age = it },
                    weight = weight,
                    onWeight = { weight = it },
                    weightUnit = UnitConverter.WEIGHT_KG,
                    onWeightUnit = {},
                    heightUnit = UnitConverter.HEIGHT_CM,
                    onHeightUnit = {},
                    heightCm = heightCm,
                    onHeightCm = { heightCm = it },
                    heightFt = "",
                    onHeightFt = {},
                    heightIn = "",
                    onHeightIn = {},
                    activity = "Lightly Active",
                    onActivity = {},
                    color = MaterialTheme.colorScheme.primary,
                    showName = false
                )
            }
        }

        composeRule.onNodeWithTag("profile_step1_age_input").performTextInput("abc")
        composeRule.onNodeWithTag("profile_step1_weight_input").performTextInput("abc")
        composeRule.onNodeWithTag("profile_step1_height_cm_input").performTextInput("abc")
        composeRule.onAllNodesWithText("Enter a whole number.").assertCountEquals(2)
        composeRule.onNodeWithText("Enter height in centimeters.").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_step1_age_input").performTextClearance()
        composeRule.onNodeWithTag("profile_step1_age_input").performTextInput("12")
        composeRule.onNodeWithTag("profile_step1_weight_input").performTextClearance()
        composeRule.onNodeWithTag("profile_step1_weight_input").performTextInput("20")
        composeRule.onNodeWithTag("profile_step1_height_cm_input").performTextClearance()
        composeRule.onNodeWithTag("profile_step1_height_cm_input").performTextInput("100")

        composeRule.onNodeWithText("Age must be 13–60.").assertIsDisplayed()
        composeRule.onNodeWithText("Allowed range: 35–180 kg equivalent.").assertIsDisplayed()
        composeRule.onNodeWithText("Height must be 120–200 cm.").assertIsDisplayed()
    }
}
