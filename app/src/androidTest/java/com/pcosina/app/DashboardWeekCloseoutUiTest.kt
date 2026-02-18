package com.pcosina.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pcosina.app.data.model.DailyLog
import com.pcosina.app.ui.ProgressViewModel
import com.pcosina.app.ui.screens.DashboardWeekCloseoutCard
import com.pcosina.app.ui.screens.isSundayCloseoutReady
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardWeekCloseoutUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun weekCloseoutCard_visible_whenSundayIsComplete() {
        composeRule.setContent {
            MaterialTheme {
                DashboardWeekCloseoutCard(
                    visible = true,
                    helperCopyMaxLines = 2,
                    onReviewWeek = {}
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_week_closeout_card").assertIsDisplayed()
        composeRule.onNodeWithText("Week closeout ready").assertIsDisplayed()
        composeRule.onNodeWithText("Review week & generate next plan").assertIsDisplayed()
    }

    @Test
    fun weekCloseoutCard_hidden_whenSundayIsIncomplete() {
        composeRule.setContent {
            MaterialTheme {
                DashboardWeekCloseoutCard(
                    visible = false,
                    helperCopyMaxLines = 2,
                    onReviewWeek = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("dashboard_week_closeout_card").assertCountEquals(0)
    }

    @Test
    fun weekCloseoutCard_visibilityTracksSundayCompletionState() {
        val sundayKey = "2026-02-15"
        val sundayPlanMeals = listOf(
            "Breakfast" to "r_breakfast",
            "Lunch" to "r_lunch",
            "Dinner" to "r_dinner"
        )
        lateinit var updateLogs: (Map<String, DailyLog>) -> Unit

        composeRule.setContent {
            var logs by remember { mutableStateOf<Map<String, DailyLog>>(emptyMap()) }
            updateLogs = { next -> logs = next }
            MaterialTheme {
                DashboardWeekCloseoutCard(
                    visible = isSundayCloseoutReady(
                        sundayKey = sundayKey,
                        sundayPlanMeals = sundayPlanMeals,
                        logs = logs
                    ),
                    helperCopyMaxLines = 2,
                    onReviewWeek = {}
                )
            }
        }

        composeRule.onAllNodesWithTag("dashboard_week_closeout_card").assertCountEquals(0)

        composeRule.runOnIdle {
            updateLogs(
                mapOf(
                    sundayKey to DailyLog(
                        date = sundayKey,
                        completedMealIds = listOf(
                            ProgressViewModel.buildMealKey("Breakfast", "r_breakfast"),
                            ProgressViewModel.buildMealKey("Lunch", "r_lunch"),
                            ProgressViewModel.buildMealKey("Dinner", "r_dinner")
                        )
                    )
                )
            )
        }

        composeRule.onNodeWithTag("dashboard_week_closeout_card").assertIsDisplayed()
    }
}
