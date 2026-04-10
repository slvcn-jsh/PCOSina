package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.util.TodayMealDescriptor
import com.pcosina.app.ui.util.buildTodayLogSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicateMealSlotUiFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun duplicateRecipeSlots_keepSecondSlotPendingAfterFirstLog() {
        composeRule.setContent {
            MaterialTheme {
                DuplicateSlotStatus()
            }
        }

        composeRule.onNodeWithText("progress:1/2").assertIsDisplayed()
        composeRule.onNodeWithText("next:Dinner").assertIsDisplayed()
    }
}

@Composable
private fun DuplicateSlotStatus() {
    val meals = listOf(
        TodayMealDescriptor(mealLabel = "Breakfast", title = "Oat Bowl", recipeId = "recipe_same"),
        TodayMealDescriptor(mealLabel = "Dinner", title = "Oat Bowl", recipeId = "recipe_same")
    )
    val logged = listOf(ProgressViewModel.buildMealKey("Breakfast", "recipe_same"))
    val snapshot = buildTodayLogSnapshot(todayMeals = meals, completedMealIds = logged)

    Text("progress:${snapshot.completedCount}/${snapshot.plannedCount}")
    Text("next:${snapshot.nextMeal?.mealLabel}")
}
